import { useCallback, useEffect, useRef, useState } from 'react'
import { useParams } from 'react-router-dom'
import { fetchShareInfo, shareCreateRun, streamShareEvents, type ShareInfo } from '../api/share'
import Markdown from '../components/Markdown'
import { splitThink } from '../components/ThinkingPanel'

/**
 * 公开对话页（v4.6.0，免登录）：/s/:shareCode。
 *
 * 精简版对话：开场白 + 建议问题 + 流式气泡 + 工具摘要行。
 * 不带侧栏/审批操作/重试——访客只读流程；会话挂在 Agent 主人名下（主人侧栏可见「分享」会话）。
 */

interface Bubble {
  kind: 'msg' | 'tool' | 'notice'
  role?: 'USER' | 'ASSISTANT'
  content?: string
  toolName?: string
  brief?: string
  ok?: boolean
  done?: boolean
}

export default function ShareChat() {
  const { code } = useParams()
  const accessCodeRef = useRef('')
  const [info, setInfo] = useState<ShareInfo | null>(null)
  const [needCode, setNeedCode] = useState(false)
  const [codeInput, setCodeInput] = useState('')
  const [loadError, setLoadError] = useState('')
  const [bubbles, setBubbles] = useState<Bubble[]>([])
  const [input, setInput] = useState('')
  const [streaming, setStreaming] = useState(false)
  const seqRef = useRef(0)
  const bodyRef = useRef<HTMLDivElement>(null)

  const loadInfo = useCallback(async (ac: string) => {
    try {
      setLoadError('')
      const data = await fetchShareInfo(code ?? '', ac)
      accessCodeRef.current = ac
      setInfo(data)
      setNeedCode(false)
      setBubbles(data.openingStatement
        ? [{ kind: 'msg', role: 'ASSISTANT', content: data.openingStatement }]
        : [])
    } catch (err) {
      const e = err as Error & { status?: number; code?: string }
      if (e.status === 403 || e.status === 404) {
        if (!ac) {
          setNeedCode(true)
          setLoadError('')
        } else {
          setLoadError(e.message || '访问口令不正确')
          setNeedCode(true)
        }
      } else {
        setLoadError(e.message || '分享不存在或已关闭')
      }
    }
  }, [code])

  useEffect(() => { void loadInfo('') }, [loadInfo])

  useEffect(() => {
    bodyRef.current?.scrollTo({ top: bodyRef.current.scrollHeight })
  }, [bubbles])

  function subscribe(runId: number) {
    streamShareEvents({
      code: code ?? '',
      accessCode: accessCodeRef.current,
      runId,
      afterEventId: seqRef.current,
      onEvent: (ev) => {
        if (ev.seq <= seqRef.current) return
        seqRef.current = ev.seq
        const p = ev.payload as Record<string, unknown>
        if (ev.type === 'message_delta') {
          const delta = String(p.delta ?? '')
          setBubbles((prev) => {
            const last = [...prev].reverse().find((b) => b.kind === 'msg' && b.role === 'ASSISTANT')
            const idx: number = last ? prev.indexOf(last) : -1
            if (idx < 0) return [...prev, { kind: 'msg', role: 'ASSISTANT', content: delta }]
            const copy = [...prev]
            copy[idx] = { ...copy[idx], content: (copy[idx].content ?? '') + delta }
            return copy
          })
        } else if (ev.type === 'tool_call') {
          setBubbles((prev) => [...prev, {
            kind: 'tool', toolName: String(p.toolName ?? ''), done: false,
          }])
        } else if (ev.type === 'tool_result') {
          setBubbles((prev) => {
            const idx = [...prev].reverse().findIndex((b) => b.kind === 'tool' && !b.done)
            const realIdx = idx >= 0 ? prev.length - 1 - idx : -1
            if (realIdx < 0) return prev
            const copy = [...prev]
            copy[realIdx] = {
              ...copy[realIdx],
              done: true,
              ok: !!p.ok,
              brief: String(p.resultBrief ?? '').slice(0, 120),
            }
            return copy
          })
        } else if (ev.type === 'run_completed') {
          const out = String(p.outputText ?? '')
          const thinkEnd = out.indexOf('</think>')
          const body = thinkEnd >= 0 ? out.slice(thinkEnd + 8).trim() : out.trim()
          setBubbles((prev) => {
            // 权威正文覆盖流式气泡（契约 §5.7）
            const idx: number = [...prev].reverse().findIndex((b) => b.kind === 'msg' && b.role === 'ASSISTANT')
            const realIdx = idx >= 0 ? prev.length - 1 - idx : -1
            if (realIdx < 0) return [...prev, { kind: 'msg', role: 'ASSISTANT', content: body }]
            const copy = [...prev]
            copy[realIdx] = { ...copy[realIdx], content: body }
            return copy
          })
        } else if (ev.type === 'run_failed') {
          setBubbles((prev) => [...prev, {
            kind: 'notice', content: String(p.errorMsg ?? '运行失败'),
          }])
        }
      },
      onError: () => {
        setStreaming(false)
        setBubbles((prev) => [...prev, { kind: 'notice', content: '连接中断，请刷新页面重试' }])
      },
      onDone: () => setStreaming(false),
    })
  }

  async function onSend() {
    const text = input.trim()
    if (!text || streaming) return
    setInput('')
    setStreaming(true)
    setBubbles((prev) => [...prev, { kind: 'msg', role: 'USER', content: text }])
    try {
      const run = await shareCreateRun(code ?? '', text, accessCodeRef.current)
      seqRef.current = 0
      subscribe(run.runId)
    } catch (err) {
      setStreaming(false)
      setBubbles((prev) => [...prev, { kind: 'notice', content: (err as Error).message }])
    }
  }

  // ---------- 口令门 ----------
  if (needCode) {
    return (
      <div className="share-page">
        <div className="share-card">
          <h2>🔒 需要访问口令</h2>
          <p className="note gray">这个分享页由访问口令保护，请输入口令进入对话。</p>
          {loadError && <p className="note danger">{loadError}</p>}
          <div style={{ display: 'flex', gap: 8 }}>
            <input
              value={codeInput}
              onChange={(e) => setCodeInput(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') void loadInfo(codeInput.trim()) }}
              placeholder="访问口令"
              style={{ flex: 1 }}
            />
            <button className="btn primary" onClick={() => void loadInfo(codeInput.trim())}>进入</button>
          </div>
        </div>
      </div>
    )
  }

  // ---------- 加载失败 ----------
  if (!info) {
    return (
      <div className="share-page">
        <div className="share-card">
          <h2>对话分享</h2>
          <p className="note gray">{loadError || '加载中…'}</p>
        </div>
      </div>
    )
  }

  // ---------- 对话 ----------
  return (
    <div className="share-page">
      <div className="share-card">
        <div className="share-head">
          <h2>{info.name}</h2>
          <span className="tag gray">公开对话</span>
        </div>
        {info.description && <p className="note gray" style={{ marginTop: 4 }}>{info.description}</p>}

        <div className="share-body" ref={bodyRef}>
          {bubbles.map((b, i) => {
            if (b.kind === 'tool') {
              return (
                <div key={i} className="tool-call-card slim">
                  <div className="tcc-head">
                    <span className="tcc-icon">🔧</span>
                    <span className="tcc-name">{b.toolName}</span>
                    {b.done ? <span className={`tag ${b.ok ? 'ok' : 'err'}`}>{b.ok ? '完成' : '失败'}</span>
                      : <span className="tag info">执行中…</span>}
                  </div>
                  {b.done && b.brief && <div className="tcc-res">{b.brief}</div>}
                </div>
              )
            }
            if (b.kind === 'notice') {
              return <div key={i} className="note danger">{b.content}</div>
            }
            const isUser = b.role === 'USER'
            const split = isUser ? { answer: b.content ?? '' } : splitThink(b.content ?? '')
            return (
              <div key={i} className={`bubble-row ${isUser ? 'me' : ''}`}>
                <div className={`bubble ${isUser ? 'me' : ''}`}>
                  {isUser
                    ? <div>{split.answer}</div>
                    : <Markdown text={split.answer || '（思考中…）'} />}
                </div>
              </div>
            )
          })}
          {streaming && <div className="note gray" style={{ marginTop: 4 }}>⏳ 生成中…</div>}
        </div>

        <div className="share-chips">
          {(info.suggestedQuestions ?? []).map((q) => (
            <button key={q} className="suggestion-chip" disabled={streaming}
                    onClick={() => { setInput(q) }}>
              {q}
            </button>
          ))}
        </div>

        <div className="share-input">
          <textarea
            rows={2}
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault()
                void onSend()
              }
            }}
            placeholder="输入你的问题…（Enter 发送）"
            disabled={streaming}
          />
          <button className="btn primary" disabled={streaming || !input.trim()} onClick={() => void onSend()}>
            {streaming ? '生成中…' : '发送'}
          </button>
        </div>
        <p className="note gray" style={{ fontSize: 11, marginTop: 6 }}>
          本页为公开对话分享 · 内容由 AI 生成，请注意甄别
        </p>
      </div>
    </div>
  )
}
