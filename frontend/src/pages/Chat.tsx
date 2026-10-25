import { FormEvent, useCallback, useEffect, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  cancelRun,
  createRun,
  createSession,
  deleteSession,
  fetchRunSnapshot,
  fetchSessionDetail,
  fetchSessions,
  renameSession,
  streamRunEvents,
  submitApproval,
  type MessageItem,
  type SessionItem,
  type RuntimeEvent,
} from '../api/runtime'
import { fetchAgents, type AgentConfig } from '../api/agents'
import { ApiError } from '../api/client'
import ConfirmDialog from '../components/ConfirmDialog'
import Modal from '../components/Modal'
import { ToastHost, toast } from '../components/Toast'

/**
 * 对话运行时（v2）：会话侧栏 + 消息区 + SSE 流式气泡 + 工具调用与审批。
 *
 * 前端消费规则（契约 §5.3）：seq 去重、空洞立即以 lastSeq 重连补齐、
 * run_completed 的 outputText 覆盖气泡（§5.7 权威正文）；取消先调后端再 abort 本地流。
 * 审批流（v2.1）：approval_required 事件弹出审批卡（服务端同时 complete SSE），
 * BLOCKED 期间轮询运行快照（5s）感知超时；批准/拒绝提交后以 lastSeq 重连续流。
 */

interface Bubble {
  kind: 'msg' | 'tool' | 'approval' | 'plan'
  role?: 'USER' | 'ASSISTANT'
  content?: string
  runId?: number
  // 工具卡
  callId?: string
  toolName?: string
  args?: Record<string, unknown>
  riskLevel?: string
  resultBrief?: string
  resultOk?: boolean
  resultDone?: boolean
  // 审批卡
  approvalId?: string
  decided?: 'APPROVE' | 'REJECT'
  // 计划卡（plan 策略）/ 执行图卡（custom 策略）
  planSteps?: { title: string; status: string }[]
  graphMode?: boolean
}

const STRATEGIES = [
  { value: 'auto', label: 'auto（自主迭代）' },
  { value: 'plan', label: 'plan（先规划再执行）' },
  { value: 'custom', label: 'custom（按执行图调度）' },
]

export default function Chat() {
  const params = useParams()
  const navigate = useNavigate()
  const [sessions, setSessions] = useState<SessionItem[]>([])
  const [activeSession, setActiveSession] = useState<number | null>(
    params.sessionId ? Number(params.sessionId) : null)

  const [bubbles, setBubbles] = useState<Bubble[]>([])
  const [input, setInput] = useState('')
  const [strategy, setStrategy] = useState('auto')
  const [streaming, setStreaming] = useState(false)
  const [connected, setConnected] = useState(true)
  const [activeRunId, setActiveRunId] = useState<number | null>(null)
  const [agents, setAgents] = useState<AgentConfig[]>([])
  const [agentId, setAgentId] = useState<number | null>(null)
  const [renaming, setRenaming] = useState<{ sessionId: number; title: string } | null>(null)
  const [deleting, setDeleting] = useState<SessionItem | null>(null)

  const bodyRef = useRef<HTMLDivElement>(null)
  const abortRef = useRef<{ abort: () => void } | null>(null)
  const lastSeqRef = useRef(0)
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)

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

  // Agent 列表（对话页顶部可切换；默认选平台第一个启用的）
  const reloadAgents = useCallback(async () => {
    try {
      const page = await fetchAgents()
      const all = [...page.platform, ...page.mine].filter((a) => a.usable)
      setAgents(all)
      setAgentId((prev) => prev ?? (all[0]?.id ?? null))
    } catch {
      // Agent 列表失败不阻塞对话
    }
  }, [])

  useEffect(() => {
    void reloadSessions()
    void reloadAgents()
    return () => {
      if (pollRef.current) clearInterval(pollRef.current)
    }
  }, [reloadSessions, reloadAgents])


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
      if (String(sessionId) !== params.sessionId) {
        navigate(`/chat/${sessionId}`, { replace: true })
      }
      setBubbles(detail.messages.map((m: MessageItem) => ({
        kind: 'msg' as const,
        role: m.role,
        content: m.content,
        runId: m.runId ?? undefined,
      })))
      // 刷新恢复：会话里有 RUNNING run → 立即以 lastSeq 续流（动线 D）
      const running = detail.runs.find((r) => r.status === 'RUNNING' || r.status === 'BLOCKED')
      if (running) {
        setActiveRunId(running.runId)
        lastSeqRef.current = running.lastSeq
        setStreaming(true)
        subscribe(running.runId)
      } else {
        setActiveRunId(null)
      }
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '会话加载失败')
    }
  }, [streaming, params.sessionId, navigate])


  /** 失败/取消提示气泡：push 前查重（SSE 事件与快照兜底可能双路到达同一条消息）。 */
  const pushNotice = useCallback((runId: number | undefined, text: string) => {
    setBubbles((prev) => {
      const last = prev[prev.length - 1]
      if (last && last.kind === 'msg' && last.role === 'ASSISTANT'
          && last.runId === runId && last.content === `⚠️ ${text}`) {
        return prev
      }
      return [...prev, { kind: 'msg', role: 'ASSISTANT', content: `⚠️ ${text}`, runId }]
    })
  }, [])

  function finishRun() {
    if (pollRef.current) {
      clearInterval(pollRef.current)
      pollRef.current = null
    }
    setStreaming(false)
    setActiveRunId(null)
  }

  /** BLOCKED 期间轮询运行快照：感知审批超时（→ FAILED）或审批完成（→ RUNNING 续流）。 */
  const startBlockedPolling = useCallback((runId: number) => {
    if (pollRef.current) clearInterval(pollRef.current)
    pollRef.current = setInterval(async () => {
      try {
        const snap = await fetchRunSnapshot(runId)
        if (snap.status === 'RUNNING') {
          if (pollRef.current) clearInterval(pollRef.current)
          pollRef.current = null
          // 审批已通过并续跑：以服务端 lastSeq 重连续流
          lastSeqRef.current = snap.lastSeq
          setConnected(true)
          subscribe(runId)
        } else if (snap.status === 'FAILED' || snap.status === 'CANCELLED' || snap.status === 'TIMEOUT') {
          if (pollRef.current) clearInterval(pollRef.current)
          pollRef.current = null
          const msg = snap.errorMsg ?? snap.errorCode ?? '运行已结束'
          setBubbles((prev) => [...prev,
            { kind: 'msg', role: 'ASSISTANT', content: `⚠️ ${msg}`, runId }])
          setStreaming(false)
          setActiveRunId(null)
        }
      } catch {
        // 轮询失败静默重试
      }
    }, 5000)
  }, [])

  /** 订阅事件流：去重 → 空洞重连 → 终态/挂起收口。 */
  const subscribe = useCallback((runId: number) => {
    setConnected(true)
    const handle = streamRunEvents({
      runId,
      afterEventId: lastSeqRef.current,
      onEvent: (ev: RuntimeEvent) => {
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
            const last = next[next.length - 1]
            if (last && last.kind === 'msg' && last.role === 'ASSISTANT' && last.runId === runId) {
              next[next.length - 1] = { ...last, content: last.content + delta }
            } else {
              next.push({ kind: 'msg', role: 'ASSISTANT', content: delta, runId })
            }
            return next
          })
        } else if (ev.type === 'graph_started') {
          const raw = (ev.payload.nodes ?? []) as { id: string; type: string; title: string }[]
          setBubbles((prev) => [...prev, {
            kind: 'plan',
            runId,
            graphMode: true,
            planSteps: raw.map((x) => ({
              title: `${x.title}${x.type && x.type !== 'LLM' ? `（${x.type}）` : ''}`,
              status: 'PENDING',
            })),
          }])
        } else if (ev.type === 'plan_created') {
          const raw = (ev.payload.steps ?? []) as { index: number; title: string }[]
          setBubbles((prev) => [...prev, {
            kind: 'plan',
            runId,
            planSteps: raw.map((x) => ({ title: String(x.title ?? ''), status: 'PENDING' })),
          }])
        } else if (ev.type === 'step_started') {
          const idx = Number(ev.payload.stepIndex ?? 0)
          setBubbles((prev) => prev.map((b) => (
            b.kind === 'plan' && b.runId === runId && b.planSteps
              ? {
                  ...b,
                  planSteps: b.planSteps.map((st, i) => (
                    i === idx ? { ...st, status: 'RUNNING' }
                      : i < idx && st.status !== 'DONE' ? { ...st, status: 'DONE' } : st
                  )),
                }
              : b
          )))
        } else if (ev.type === 'step_completed') {
          const idx = Number(ev.payload.stepIndex ?? 0)
          const skipped = !!ev.payload.skipped
          setBubbles((prev) => prev.map((b) => (
            b.kind === 'plan' && b.runId === runId && b.planSteps
              ? {
                  ...b,
                  planSteps: b.planSteps.map((st, i) => (
                    i === idx ? { ...st, status: skipped ? 'SKIPPED' : 'DONE' } : st
                  )),
                }
              : b
          )))
        } else if (ev.type === 'tool_call') {
          setBubbles((prev) => [...prev, {
            kind: 'tool',
            runId,
            callId: String(ev.payload.callId ?? ''),
            toolName: String(ev.payload.toolName ?? ''),
            args: (ev.payload.args ?? {}) as Record<string, unknown>,
            riskLevel: String(ev.payload.riskLevel ?? 'LOW'),
          }])
        } else if (ev.type === 'approval_required') {
          setBubbles((prev) => [...prev, {
            kind: 'approval',
            runId,
            approvalId: String(ev.payload.approvalId ?? ''),
            toolName: String(ev.payload.toolName ?? ''),
            args: (ev.payload.args ?? {}) as Record<string, unknown>,
            riskLevel: String(ev.payload.riskLevel ?? 'HIGH'),
          }])
          // 服务端已 complete SSE（BLOCKED 挂起）→ 启动快照轮询感知超时
          startBlockedPolling(runId)
        } else if (ev.type === 'tool_result') {
          const callId = String(ev.payload.callId ?? '')
          setBubbles((prev) => prev.map((b) => (
            b.kind === 'tool' && b.callId === callId
              ? { ...b, resultBrief: String(ev.payload.resultBrief ?? ''), resultOk: !!ev.payload.ok, resultDone: true }
              : b
          )))
        } else if (ev.type === 'approval_resolved') {
          const approvalId = String(ev.payload.approvalId ?? '')
          const decision = String(ev.payload.decision ?? '')
          setBubbles((prev) => prev.map((b) => (
            b.kind === 'approval' && b.approvalId === approvalId
              ? { ...b, decided: decision === 'APPROVE' ? 'APPROVE' : 'REJECT' }
              : b
          )))
        } else if (ev.type === 'run_completed') {
          // 权威正文覆盖（§5.7）：只覆盖最后一条 ASSISTANT 文本气泡
          const outputText = String(ev.payload.outputText ?? '')
          setBubbles((prev) => {
            const next = [...prev]
            for (let i = next.length - 1; i >= 0; i--) {
              const b = next[i]
              if (b.kind === 'msg' && b.role === 'ASSISTANT' && b.runId === runId) {
                next[i] = { ...b, content: outputText }
                break
              }
            }
            return next
          })
          finishRun()
          void reloadSessions()
        } else if (ev.type === 'run_failed') {
          const errorMsg = String(ev.payload.errorMsg ?? ev.payload.errorCode ?? '运行失败')
          pushNotice(runId, errorMsg)
          finishRun()
          void reloadSessions()
        } else if (ev.type === 'run_cancelled') {
          // 取消：气泡保留已流出的部分内容并标注，没有内容则显示取消提示
          setBubbles((prev) => {
            const next = [...prev]
            for (let i = next.length - 1; i >= 0; i--) {
              const b = next[i]
              if (b.kind === 'msg' && b.role === 'ASSISTANT' && b.runId === runId) {
                next[i] = { ...b, content: b.content ? `${b.content}
（已取消）` : '⚠️ 已取消' }
                break
              }
            }
            return next
          })
          finishRun()
        }
      },
      onError: (err) => {
        // 连接中断：以 lastSeq 重连续流（断线重连是常态路径，不是异常路径）
        if (!streaming) return
        setConnected(false)
        setTimeout(() => subscribe(runId), 1200)
        toast(err.message)
      },
      onDone: () => {
        // 服务端 complete 了流：终态（正常收口）或 BLOCKED 挂起（approval_required 已启动轮询）
      },
    })
    abortRef.current = handle
  }, [streaming, activeRunId, reloadSessions, startBlockedPolling])

  // 路由带 sessionId 时（刷新/深链），自动定位回原会话并恢复消息与运行
  const initialSessionRef = useRef<string | null | undefined>(undefined)
  useEffect(() => {
    if (initialSessionRef.current !== undefined) return
    initialSessionRef.current = params.sessionId ?? null
    if (params.sessionId) {
      void openSession(Number(params.sessionId))
    }
  }, [params.sessionId, openSession, reloadSessions])

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
      if (pollRef.current) {
        clearInterval(pollRef.current)
        pollRef.current = null
      }
      setStreaming(false)
      setActiveRunId(null)
    }
  }, [activeRunId])

  const onApproval = useCallback(async (bubble: Bubble, decision: 'APPROVE' | 'REJECT') => {
    if (!bubble.approvalId || !activeRunId) return
    try {
      const result = await submitApproval(activeRunId, {
        approvalId: bubble.approvalId,
        decision,
      })
      toast(decision === 'APPROVE' ? '已批准，任务继续执行' : '已拒绝')
      // 停掉 BLOCKED 轮询；以响应里的 lastSeq 重连续流（REJECT → FAILED 走快照兜底）
      if (pollRef.current) {
        clearInterval(pollRef.current)
        pollRef.current = null
      }
      if (result.status === 'RUNNING') {
        lastSeqRef.current = result.lastSeq
        setConnected(true)
        subscribe(activeRunId)
      } else {
        // REJECT：run_failed 事件通常已在流上送达并由查重保护；快照兜底防事件丢失
        const snap = await fetchRunSnapshot(activeRunId)
        pushNotice(activeRunId, snap.errorMsg ?? '任务已终止')
        finishRun()
        void reloadSessions()
      }
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '审批提交失败')
    }
  }, [activeRunId, subscribe, reloadSessions])

  const onRename = useCallback(async (e: FormEvent) => {
    e.preventDefault()
    if (!renaming) return
    const title = renaming.title.trim()
    if (!title) return
    try {
      await renameSession(renaming.sessionId, title)
      toast('已重命名')
      setRenaming(null)
      await reloadSessions()
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '重命名失败')
    }
  }, [renaming, reloadSessions])

  const onDelete = useCallback(async () => {
    if (!deleting) return
    try {
      await deleteSession(deleting.sessionId)
      toast('会话已删除')
      const wasActive = activeSession === deleting.sessionId
      setDeleting(null)
      await reloadSessions()
      if (wasActive) {
        setActiveSession(null)
        setBubbles([])
        navigate('/chat', { replace: true })
      }
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '删除失败')
      setDeleting(null)
    }
  }, [deleting, activeSession, reloadSessions, navigate])

  const onSend = useCallback(async () => {
    const text = input.trim()
    if (!text || streaming) return
    setStreaming(true)
    setInput('')
    setBubbles((prev) => [...prev, { kind: 'msg', role: 'USER', content: text }])
    try {
      const run = await createRun({
        sessionId: activeSession ?? undefined,
        agentId: agentId ?? undefined,
        input: text,
        strategy,
        clientRequestId: crypto.randomUUID(),
      })
      if (!activeSession) {
        setActiveSession(run.sessionId)
        navigate(`/chat/${run.sessionId}`, { replace: true })
      }
      setActiveRunId(run.runId)
      lastSeqRef.current = run.lastSeq
      // 占位气泡立即出现：取消/失败时用户能看到这条运行的痕迹
      setBubbles((prev) => [...prev, { kind: 'msg', role: 'ASSISTANT', content: '', runId: run.runId }])
      subscribe(run.runId)
      void reloadSessions()
    } catch (err) {
      setStreaming(false)
      const message = err instanceof ApiError
        ? (err.code === 'SESSION_BUSY' ? '该会话已有正在执行的运行，请等待完成或取消' : err.message)
        : '发起运行失败'
      pushNotice(activeSession ?? undefined, message)
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

  function renderBubble(b: Bubble, i: number) {
    if (b.kind === 'plan') {
      const done = (b.planSteps ?? []).filter((x) => x.status === 'DONE').length
      return (
        <div key={i} className="plan-card">
          <div className="plan-head">
            {b.graphMode ? '🧩 执行图' : '🗺️ 执行计划'}
            <span className="tag info">{done}/{b.planSteps?.length ?? 0} 节点完成</span>
          </div>
          <ol className="plan-steps">
            {(b.planSteps ?? []).map((st, idx) => (
              <li
                key={idx}
                className={`plan-step ${st.status === 'DONE' ? 'done'
                  : st.status === 'SKIPPED' ? 'skip'
                    : st.status === 'RUNNING' ? 'run' : ''}`}
                title={st.status === 'SKIPPED' ? '条件路由未命中，已跳过' : undefined}
              >
                <span className="plan-idx">
                  {st.status === 'DONE' ? '✓' : st.status === 'SKIPPED' ? '—' : st.status === 'RUNNING' ? '▶' : idx + 1}
                </span>
                <span>{st.title}</span>
              </li>
            ))}
          </ol>
        </div>
      )
    }
    if (b.kind === 'tool') {
      return (
        <div key={i} className={`tool-call-card ${b.resultDone && !b.resultOk ? 'err' : ''}`}>
          <div className="tcc-head">
            🔧 调用工具 {b.toolName}
            <span className={`tag ${b.riskLevel === 'HIGH' ? 'err' : 'info'}`}>{b.riskLevel === 'HIGH' ? '高风险 · 需审批' : '只读'}</span>
          </div>
          <div className="tcc-args">{JSON.stringify(b.args)}</div>
          {b.resultDone && <div className="tcc-res">{b.resultOk ? '✅ ' : '❌ '}{b.resultBrief}</div>}
        </div>
      )
    }
    if (b.kind === 'approval') {
      return (
        <div key={i} className="tool-call-card approval">
          <div className="tcc-head">🛡️ 审批请求：{b.toolName} <span className="tag err">{b.riskLevel}</span></div>
          <div className="tcc-args">{JSON.stringify(b.args)}</div>
          <div className="tcc-res">该工具会改变外部状态（写入演示工单表），需要你确认后才会执行。</div>
          {b.decided ? (
            <div className="tcc-res">已处理：{b.decided === 'APPROVE' ? '✅ 已批准' : '❌ 已拒绝'}</div>
          ) : (
            <div className="btns">
              <button className="btn sm primary" onClick={() => onApproval(b, 'APPROVE')}>批准执行</button>
              <button className="btn sm danger" onClick={() => onApproval(b, 'REJECT')}>拒绝</button>
            </div>
          )}
        </div>
      )
    }
    return (
      <div key={i} className={`msg ${b.role === 'USER' ? 'user' : 'ai'}`}>
        <div className="mk">{b.role === 'USER' ? '🧑' : '🤖'}</div>
        <div className="bubble">{b.content || (streaming ? '…' : '')}</div>
      </div>
    )
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h2>对话运行时</h2>
          <div className="desc">
            流式驱动执行。SSE 用 fetch + ReadableStream 手工解析（可带鉴权头），断线以 afterEventId 重连补齐；
            高风险工具（创建工单）会挂起等待审批。
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
            <select
              className="btn sm"
              style={{ width: 'auto' }}
              value={agentId ?? ''}
              onChange={(e) => setAgentId(Number(e.target.value))}
              title="当前 Agent"
            >
              {agents.map((a) => (
                <option key={a.id} value={a.id}>{a.name}{a.platformPublic ? '（平台）' : ''}</option>
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
                选择左侧会话或直接在下方输入发起对话。试试说「帮我创建一个工单，标题是 xxx，优先级 P1」——
                高风险写操作会弹出审批卡。
              </div>
            )}
            {bubbles.map((b, i) => renderBubble(b, i))}
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
              <span className="sess-actions" onClick={(e) => e.stopPropagation()}>
                <button
                  className="btn sm"
                  title="重命名"
                  onClick={() => setRenaming({ sessionId: s.sessionId, title: s.title })}
                >
                  ✏️
                </button>
                <button className="btn sm danger" title="删除" onClick={() => setDeleting(s)}>
                  🗑
                </button>
              </span>
            </div>
          ))}
          {sessions.length === 0 && <div className="note gray">暂无会话</div>}
        </div>
      </div>
      {renaming && (
        <Modal title="重命名会话" onClose={() => setRenaming(null)}>
          <form onSubmit={onRename}>
            <div className="field">
              <label>新标题</label>
              <input
                autoFocus
                value={renaming.title}
                onChange={(e) => setRenaming({ ...renaming, title: e.target.value })}
                required
                maxLength={100}
              />
            </div>
            <div className="modal-btns">
              <button type="button" className="btn" onClick={() => setRenaming(null)}>取消</button>
              <button type="submit" className="btn primary">保存</button>
            </div>
          </form>
        </Modal>
      )}

      {deleting && (
        <ConfirmDialog
          title="删除会话"
          message={`确认删除会话「${deleting.title}」？其下全部消息、运行记录与事件将一并删除，不可恢复。`}
          confirmText="删除"
          onConfirm={onDelete}
          onCancel={() => setDeleting(null)}
        />
      )}
      <ToastHost />
    </>
  )
}
