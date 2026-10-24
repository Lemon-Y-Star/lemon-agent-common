import { useCallback, useEffect, useRef, useState } from 'react'
import {
  cancelRun,
  createRun,
  createSession,
  fetchSessionDetail,
  fetchSessions,
  streamRunEvents,
  type MessageItem,
  type SessionItem,
} from '../api/runtime'
import { ApiError } from '../api/client'
import { ToastHost, toast } from '../components/Toast'

/**
 * 对话运行时（v2）：会话侧栏 + 消息区 + SSE 流式气泡。
 *
 * 前端消费规则（契约 §5.3）：seq 去重、空洞立即以 lastSeq 重连补齐、
 * run_completed 的 outputText 覆盖气泡（§5.7 权威正文）；取消先调后端再 abort 本地流。
 */

interface Bubble {
  role: 'USER' | 'ASSISTANT'
  content: string
  runId?: number
}

const STRATEGIES = [
  { value: 'auto', label: 'auto（自主迭代）' },
  { value: 'fixed', label: 'fixed（未开放）' },
  { value: 'flow', label: 'flow（未开放）' },
  { value: 'custom', label: 'custom（未开放）' },
]

export default function Chat() {
  const [sessions, setSessions] = useState<SessionItem[]>([])
  const [activeSession, setActiveSession] = useState<number | null>(null)
  const [bubbles, setBubbles] = useState<Bubble[]>([])
  const [input, setInput] = useState('')
  const [strategy, setStrategy] = useState('auto')
  const [streaming, setStreaming] = useState(false)
  const [connected, setConnected] = useState(true)
  const [activeRunId, setActiveRunId] = useState<number | null>(null)

  const bodyRef = useRef<HTMLDivElement>(null)
  const abortRef = useRef<{ abort: () => void } | null>(null)
  const lastSeqRef = useRef(0)

  const reloadSessions = useCallback(async () => {
    try {
      const list = await fetchSessions()
      setSessions(list.items)
      return list.items
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '会话列表加载失败')
      return []
    }
  }, [])

  // 进入页面：拉会话列表；无会话不自动创建（首次发送时懒创建）
  useEffect(() => {
    void reloadSessions()
  }, [reloadSessions])

  // 消息区自动滚底
  useEffect(() => {
    bodyRef.current?.scrollTo({ top: bodyRef.current.scrollHeight })
  }, [bubbles])

  const openSession = useCallback(async (sessionId: number) => {
    if (streaming) {
      toast('运行进行中，先等待完成或取消')
      return
    }
    try {
      const detail = await fetchSessionDetail(sessionId)
      setActiveSession(sessionId)
      setBubbles(detail.messages.map((m: MessageItem) => ({
        role: m.role,
        content: m.content,
        runId: m.runId ?? undefined,
      })))
      // 刷新恢复：会话里有 RUNNING run → 立即以 lastSeq 续流（动线 D）
      const running = detail.runs.find((r) => r.status === 'RUNNING' || r.status === 'BLOCKED')
      if (running) {
        setActiveRunId(running.runId)
        lastSeqRef.current = running.lastSeq
        setBubbles((prev) => [...prev, { role: 'ASSISTANT', content: '', runId: running.runId }])
        subscribe(running.runId)
      } else {
        setActiveRunId(null)
      }
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '会话加载失败')
    }
  }, [streaming])

  /** 订阅事件流：去重 → 空洞重连 → 终态收口。 */
  const subscribe = useCallback((runId: number) => {
    setConnected(true)
    const handle = streamRunEvents({
      runId,
      afterEventId: lastSeqRef.current,
      onEvent: (ev) => {
        // 去重 + 空洞自检（契约 §5.3 的 acceptEvent）
        if (ev.seq <= lastSeqRef.current) return
        if (ev.seq !== lastSeqRef.current + 1) {
          // 空洞：丢弃本条，立即重连补齐
          abortRef.current?.abort()
          subscribe(runId)
          return
        }
        lastSeqRef.current = ev.seq
        if (ev.type === 'token') {
          const delta = String(ev.payload.delta ?? '')
          setBubbles((prev) => {
            const next = [...prev]
            const lastBubble = next[next.length - 1]
            if (lastBubble && lastBubble.role === 'ASSISTANT' && lastBubble.runId === runId) {
              next[next.length - 1] = { ...lastBubble, content: lastBubble.content + delta }
            }
            return next
          })
        } else if (ev.type === 'run_completed') {
          // 权威正文覆盖（§5.7）
          const outputText = String(ev.payload.outputText ?? '')
          setBubbles((prev) => {
            const next = [...prev]
            const lastBubble = next[next.length - 1]
            if (lastBubble && lastBubble.role === 'ASSISTANT' && lastBubble.runId === runId) {
              next[next.length - 1] = { ...lastBubble, content: outputText }
            }
            return next
          })
          setStreaming(false)
          setActiveRunId(null)
          void reloadSessions()
        } else if (ev.type === 'run_failed') {
          const errorMsg = String(ev.payload.errorMsg ?? ev.payload.errorCode ?? '运行失败')
          setBubbles((prev) => {
            const next = [...prev]
            const lastBubble = next[next.length - 1]
            if (lastBubble && lastBubble.role === 'ASSISTANT' && lastBubble.runId === runId) {
              next[next.length - 1] = { ...lastBubble, content: `⚠️ ${errorMsg}` }
            }
            return next
          })
          setStreaming(false)
          setActiveRunId(null)
          void reloadSessions()
        } else if (ev.type === 'run_cancelled') {
          setStreaming(false)
          setActiveRunId(null)
        }
      },
      onError: (err) => {
        // 连接中断：以 lastSeq 重连续流（断线重连是常态路径，不是异常路径）
        if (!streaming) return
        setConnected(false)
        setTimeout(() => {
          if (activeRunId === runId || lastSeqRef.current > 0) {
            subscribe(runId)
          }
        }, 1200)
        toast(err.message)
      },
    })
    abortRef.current = handle
  }, [streaming, activeRunId, reloadSessions])

  const onCancel = useCallback(async () => {
    if (!activeRunId) return
    // 契约 4.9 前端顺序：先通知后端，收到响应后再 abort 本地 SSE
    try {
      await cancelRun(activeRunId)
      toast('已取消')
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '取消失败')
    } finally {
      abortRef.current?.abort()
      setStreaming(false)
      setActiveRunId(null)
    }
  }, [activeRunId])

  const onSend = useCallback(async () => {
    const text = input.trim()
    if (!text || streaming) return
    setStreaming(true)
    setInput('')
    setBubbles((prev) => [...prev, { role: 'USER', content: text }, { role: 'ASSISTANT', content: '' }])
    try {
      const run = await createRun({
        sessionId: activeSession ?? undefined,
        input: text,
        strategy,
        clientRequestId: crypto.randomUUID(),
      })
      if (!activeSession) {
        setActiveSession(run.sessionId)
      }
      setActiveRunId(run.runId)
      lastSeqRef.current = run.lastSeq
      // runId 挂到刚创建的 ASSISTANT 气泡上，随后订阅事件流
      setBubbles((prev) => {
        const next = [...prev]
        const lastBubble = next[next.length - 1]
        if (lastBubble && lastBubble.role === 'ASSISTANT') {
          next[next.length - 1] = { ...lastBubble, runId: run.runId }
        }
        return next
      })
      subscribe(run.runId)
      void reloadSessions()
    } catch (err) {
      setStreaming(false)
      const message = err instanceof ApiError
        ? (err.code === 'SESSION_BUSY' ? '该会话已有正在执行的运行，请等待完成或取消' : err.message)
        : '发起运行失败'
      setBubbles((prev) => {
        const next = [...prev]
        const lastBubble = next[next.length - 1]
        if (lastBubble && lastBubble.role === 'ASSISTANT' && lastBubble.content === '') {
          next[next.length - 1] = { ...lastBubble, content: `⚠️ ${message}` }
        }
        return next
      })
    }
  }, [input, streaming, activeSession, strategy, subscribe, reloadSessions])

  const onNewSession = useCallback(async () => {
    if (streaming) {
      toast('运行进行中，先等待完成或取消')
      return
    }
    const created = await createSession()
    await reloadSessions()
    setActiveSession(created.sessionId)
    setBubbles([])
    toast('新会话已创建')
  }, [streaming, reloadSessions])

  return (
    <>
      <div className="page-head">
        <div>
          <h2>对话运行时</h2>
          <div className="desc">
            流式驱动执行。SSE 用 fetch + ReadableStream 手工解析（可带鉴权头），断线以 afterEventId 重连补齐。
          </div>
        </div>
      </div>

      {!connected && (
        <div className="conn-strip">
          <span>连接已断开，正在恢复…</span>
        </div>
      )}

      <div className="chat-wrap">
        <div className="chat-main">
          <div className="chat-head">
            <select className="btn sm" style={{ width: 'auto' }} value={strategy} onChange={(e) => setStrategy(e.target.value)}>
              {STRATEGIES.map((s) => (
                <option key={s.value} value={s.value}>{s.label}</option>
              ))}
            </select>
            <span className={`tag ${streaming ? 'ok' : 'gray'}`}>{streaming ? '已连接 · SSE' : '空闲'}</span>
            <span style={{ marginLeft: 'auto' }} />
            {streaming && activeRunId && (
              <button className="btn sm danger" onClick={onCancel}>
                取消运行
              </button>
            )}
          </div>
          <div className="chat-body" ref={bodyRef}>
            {bubbles.length === 0 && (
              <div className="note gray">
                选择左侧会话或直接在下方输入发起对话（首次发送会自动创建会话）。运行前请先在「模型连接」页配置并启用的个人连接。
              </div>
            )}
            {bubbles.map((b, i) => (
              <div key={i} className={`msg ${b.role === 'USER' ? 'user' : 'ai'}`}>
                <div className="mk">{b.role === 'USER' ? '🧑' : '🤖'}</div>
                <div className="bubble">{b.content || (streaming ? '…' : '')}</div>
              </div>
            ))}
          </div>
          <div className="chat-input">
            <div className="rowline">
              <textarea
                placeholder="输入消息，Enter 发送（Shift+Enter 换行）"
                value={input}
                disabled={streaming}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault()
                    void onSend()
                  }
                }}
              />
              <button className="btn primary" disabled={streaming || !input.trim()} onClick={onSend}>
                发送
              </button>
            </div>
          </div>
        </div>

        <div className="side-panel">
          <h4>
            会话
            <button className="btn sm" onClick={onNewSession}>＋ 新建</button>
          </h4>
          {sessions.map((s) => (
            <div
              key={s.sessionId}
              className={`sess-item ${activeSession === s.sessionId ? 'active' : ''}`}
              onClick={() => openSession(s.sessionId)}
            >
              {s.title}
              <span className="st">{s.messageCount} 条消息 · {s.lastActiveAt?.slice(0, 16).replace('T', ' ')}</span>
            </div>
          ))}
          {sessions.length === 0 && <div className="note gray">暂无会话</div>}
        </div>
      </div>
      <ToastHost />
    </>
  )
}
