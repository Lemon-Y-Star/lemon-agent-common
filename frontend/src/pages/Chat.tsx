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
import Markdown from '../components/Markdown'
import ThinkingPanel, { splitThink } from '../components/ThinkingPanel'

/** 工具名 → 展示名（对话工具芯片用）：内部工具给中文名，MCP/工作流去前缀。 */
function toolLabel(name: string): string {
  if (name === 'run_python') return 'Python 代码沙箱'
  if (name === 'search_docs') return '知识库检索'
  if (name === 'current_time') return '查询当前时间'
  if (name === 'create_ticket') return '创建工单'
  if (name.startsWith('run_wf_')) return '调用工作流'
  if (name.startsWith('mcp')) return 'MCP · ' + name.replace(/^mcp\d+_/, '')
  return name
}

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
  /** 工具执行耗时（tool_result 事件 durationMs，芯片行展示） */
  resultMs?: number
  /** 沙箱图表（v4.4.3，data URI）：tool_result 旁路，不进模型上下文，直接渲染 */
  images?: string[]
  /** 运行文件产物（v4.4.4）：落库后的下载元数据 */
  artifacts?: { id: number; name: string; contentType: string; sizeBytes: number }[]
  /** 回答正文内的沙箱图表（v4.4.5）：run_completed 时从工具芯片挪入，主视图展示 */
  answerImages?: string[]
  // 审批卡
  approvalId?: string
  decided?: 'APPROVE' | 'REJECT'
  // 计划卡（plan 策略）/ 执行图卡（custom 策略）
  planSteps?: { title: string; status: string; startedAt?: number; durationMs?: number }[]
  /** 执行图/计划首节点开始时间（ms 时间戳，step_started 事件携带） */
  graphStartedAt?: number
  /** 执行图/计划总耗时（run_completed 时用 ts 差值回填） */
  graphDurationMs?: number
  graphMode?: boolean
  // 并行执行时按节点区分气泡
  stepIndex?: number
  stepTitle?: string
  // 深度思考（v3.9）：thinkOpen = 面板展开；thinkTouched = 用户手动开合过（自动行为让位）
  thinkOpen?: boolean
  thinkTouched?: boolean
  // 等待可感知（v4.0.1）：thinking 开着但运行结束都没等到 <think> —— 渠道/模型未透出思考
  noThink?: boolean
  // 引用溯源 + token 用量（v4.1）
  refs?: string[]
  usage?: { input: number; output: number }
  // 耗时 + traceId 溯源（v4.1.3）：回答尾注展示，traceId 支持一键复制
  durationMs?: number
  traceId?: string
  // 失败重试（v3.9.1）
  retryable?: boolean
  failedInput?: string
  failedThinking?: boolean
}

/** 策略展示标签（策略已在 Agent 编辑页配置，对话页只读展示）。 */
const STRATEGY_LABELS: Record<string, string> = {
  auto: 'auto（自主迭代）',
  plan: 'plan（先规划再执行）',
  custom: 'custom（按执行图调度）',
  fixed: 'fixed（直接问答）',
}

export default function Chat() {
  const params = useParams()
  const navigate = useNavigate()
  const [sessions, setSessions] = useState<SessionItem[]>([])
  const [activeSession, setActiveSession] = useState<number | null>(
    params.sessionId ? Number(params.sessionId) : null)

  const [bubbles, setBubbles] = useState<Bubble[]>([])
  const [input, setInput] = useState('')
  const [thinkingOn, setThinkingOn] = useState(false)
  const [streaming, setStreaming] = useState(false)
  const [connected, setConnected] = useState(true)
  const [activeRunId, setActiveRunId] = useState<number | null>(null)
  // 等待可感知（v4.0.1）：运行起始时间 + 已运行秒数，替代无反馈的黑盒等待
  const [runStartAt, setRunStartAt] = useState<number | null>(null)
  const [elapsed, setElapsed] = useState(0)
  const [agents, setAgents] = useState<AgentConfig[]>([])
  const [agentId, setAgentId] = useState<number | null>(null)
  const [renaming, setRenaming] = useState<{ sessionId: number; title: string } | null>(null)
  const [deleting, setDeleting] = useState<SessionItem | null>(null)
  /** 图表灯箱（v4.4.4）：data URI 不能顶层导航，点击改页内预览 */
  const [imgPreview, setImgPreview] = useState<string | null>(null)

  const bodyRef = useRef<HTMLDivElement>(null)
  const abortRef = useRef<{ abort: () => void } | null>(null)
  const lastSeqRef = useRef(0)
  const pollRef = useRef<ReturnType<typeof setInterval> | null>(null)
  /** 图运行元信息（runId → 条件路由判定节点的 stepIndex 集合）：这类节点的输出是路由信号，不生成气泡。 */
  const graphMetaRef = useRef<Record<number, { branch: Set<number> }>>({})
  /** 最近一次发送的元信息（失败重试时重放输入 / 思考开关）。 */
  const lastMetaRef = useRef<{ input: string; thinking: boolean }>({ input: '', thinking: false })

  // custom 策略的前置校验：选中的 Agent 既没有执行图也没挂工作流时，直接提示而不是发一个必败的请求
  const selectedAgent = agents.find((a) => a.id === agentId) ?? null
  // 策略跟随 Agent 配置（v4.0 前置到 Agent 编辑页）；Agent 未加载到时按 auto 兜底
  const strategy = selectedAgent?.strategy ?? 'auto'
  const customNotReady = strategy === 'custom' && selectedAgent !== null
    && (selectedAgent.graphVersion ?? 0) === 0 && !selectedAgent.workflowId
  // 反向提示：挂载了工作流但策略不是 custom——工作流不会被执行（auto / plan 不走图路由）
  const workflowHint = strategy !== 'custom' && selectedAgent !== null && !!selectedAgent.workflowId

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
      setBubbles(detail.messages
        // 历史兜底（v4.4.2）：早期版本把「只思考无正文」的中间轮也落了库，剥思考块后为空的消息不渲染（run164 实测一串空泡）
        .filter((m: MessageItem) => !(m.role === 'ASSISTANT' && splitThink(m.content).answer.trim() === ''))
        .map((m: MessageItem) => {
        // 历史回答的耗时/traceId：按 runId 从会话详情的运行摘要回填（v4.1.3）
        const run = m.runId != null ? detail.runs.find((r) => r.runId === m.runId) : undefined
        return {
          kind: 'msg' as const,
          role: m.role,
          content: m.content,
          runId: m.runId ?? undefined,
          traceId: run?.traceId,
          durationMs: run?.durationMs ?? undefined,
        }
      }))
      // 刷新恢复：会话里有 RUNNING run → 立即以 lastSeq 续流（动线 D）
      const running = detail.runs.find((r) => r.status === 'RUNNING' || r.status === 'BLOCKED')
      if (running) {
        setActiveRunId(running.runId)
        lastSeqRef.current = running.lastSeq
        setStreaming(true)
        // 快照没有 startedAt：断线恢复从本机时刻起算，秒数偏小但不误导
        setRunStartAt(Date.now())
        setElapsed(0)
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
    setRunStartAt(null)
  }

  // 等待可感知：每秒跳一次运行秒数（streaming 期间才计时）
  useEffect(() => {
    if (!streaming || runStartAt === null) return
    const t = setInterval(() => setElapsed(Math.max(0, Math.floor((Date.now() - runStartAt) / 1000))), 1000)
    return () => clearInterval(t)
  }, [streaming, runStartAt])

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
          const stepIndex = Number(ev.payload.stepIndex ?? 0)
          setBubbles((prev) => {
            const next = [...prev]
            // 并行执行时不同节点的 token 会交错到达：按 (runId, stepIndex) 归位到各自气泡
            for (let i = next.length - 1; i >= 0; i--) {
              const b = next[i]
              if (b.kind === 'msg' && b.role === 'ASSISTANT' && b.runId === runId
                && (b.stepIndex ?? 0) === stepIndex) {
                const merged = b.content + delta
                // 深度思考：首个 <think> 到达即自动展开面板（用户没手动操作过时）
                const thinkOpen = merged.includes('<think>') && b.thinkOpen === undefined && !b.thinkTouched
                  ? true
                  : b.thinkOpen
                next[i] = { ...b, content: merged, thinkOpen }
                return next
              }
            }
            const meta = graphMetaRef.current[runId]
            if (meta && meta.branch.has(stepIndex)) {
              return next  // 路由判定节点的输出（如意图单词）不作为回答气泡展示
            }
            const plan = next.find((x) => x.kind === 'plan' && x.runId === runId)
            const title = plan?.planSteps?.[stepIndex]?.title
            next.push({
              kind: 'msg', role: 'ASSISTANT', content: delta, runId, stepIndex, stepTitle: title,
              thinkOpen: delta.includes('<think>') ? true : undefined,
            })
            return next
          })
        } else if (ev.type === 'graph_started') {
          const raw = (ev.payload.nodes ?? []) as { id: string; type: string; title: string }[]
          // 条件路由判定节点（多条条件出边的节点）产出的「意图单词」是路由信号，不生成回答气泡
          const edges = (ev.payload.edges ?? []) as { from: string; route?: string | null }[]
          const ids = raw.map((x) => x.id)
          const branch = new Set<number>()
          edges.forEach((e) => {
            if (e.route) {
              const idx = ids.indexOf(e.from)
              if (idx >= 0) branch.add(idx)
            }
          })
          graphMetaRef.current[runId] = { branch }
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
          const startedTs = Number(ev.payload.ts ?? Date.now())
          setBubbles((prev) => prev.map((b) => (
            b.kind === 'plan' && b.runId === runId && b.planSteps
              ? {
                  ...b,
                  graphStartedAt: b.graphStartedAt ?? startedTs,
                  planSteps: b.planSteps.map((st, i) => (
                    i === idx ? { ...st, status: 'RUNNING', startedAt: startedTs }
                      : i < idx && st.status !== 'DONE' ? { ...st, status: 'DONE' } : st
                  )),
                }
              : b
          )))
        } else if (ev.type === 'step_completed') {
          const idx = Number(ev.payload.stepIndex ?? 0)
          const skipped = !!ev.payload.skipped
          const doneTs = Number(ev.payload.ts ?? Date.now())
          setBubbles((prev) => prev.map((b) => (
            b.kind === 'plan' && b.runId === runId && b.planSteps
              ? {
                  ...b,
                  planSteps: b.planSteps.map((st, i) => (
                    i === idx ? { ...st, status: skipped ? 'SKIPPED' : 'DONE', durationMs: skipped ? undefined : Math.max(0, doneTs - (st.startedAt ?? doneTs)) } : st
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
          const refs = Array.isArray(ev.payload.refs) ? (ev.payload.refs as string[]) : undefined
          setBubbles((prev) => prev.map((b) => (
            b.kind === 'tool' && b.callId === callId
              ? { ...b, resultBrief: String(ev.payload.resultBrief ?? ''), resultOk: !!ev.payload.ok,
                  resultDone: true, resultMs: Number(ev.payload.durationMs ?? 0) || undefined,
                  images: Array.isArray(ev.payload.images) ? (ev.payload.images as string[]) : undefined,
                  artifacts: Array.isArray(ev.payload.artifacts) ? (ev.payload.artifacts as { id: number; name: string; contentType: string; sizeBytes: number }[]) : undefined,
                  refs: refs && refs.length > 0 ? refs : undefined }
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
          const usagePayload = ev.payload.usage as { inputTokens?: number; outputTokens?: number } | undefined
          setBubbles((prev) => {
            const completedTs = Number(ev.payload.ts ?? Date.now())
            // 执行图/计划卡总耗时：run_completed ts 与首节点开始 ts 的差值回填
            const base = prev.map((b) => (
              b.kind === 'plan' && b.runId === runId && b.graphStartedAt
                ? { ...b, graphDurationMs: Math.max(0, completedTs - b.graphStartedAt) }
                // 沙箱图表（v4.4.5）：完成后从工具芯片挪进正文回答气泡（芯片区只留 📎 产物）
                : b.kind === 'tool' && b.runId === runId && b.images
                  ? { ...b, images: undefined }
                  : b
            ))
            const texts = base.filter((b) => b.kind === 'msg' && b.role === 'ASSISTANT' && b.runId === runId)
            // 引用溯源（v4.1）：收集本 run 的 search_docs 命中文档标题，去重后挂到回答气泡
            const runRefs = Array.from(new Set(prev
              .filter((b) => b.kind === 'tool' && b.runId === runId && b.refs)
              .flatMap((b) => b.refs ?? [])))
            // 沙箱图表（v4.4.5）：收集本 run 工具产生的图片，挂到回答气泡正文内
            const runImages = Array.from(new Set(prev
              .filter((b) => b.kind === 'tool' && b.runId === runId && b.images)
              .flatMap((b) => b.images ?? [])))
            // 单气泡（auto/plan）以权威正文覆盖；多气泡（并行图）保留各节点文本，避免重复一遍
            if (texts.length > 1) {
              return base
            }
            const next = [...base]
            for (let i = next.length - 1; i >= 0; i--) {
              const b = next[i]
              if (b.kind === 'msg' && b.role === 'ASSISTANT' && b.runId === runId) {
                // 深度思考：结论输出完自动收起思考面板（用户手动开合过则尊重其选择）；
                // thinking 开着但全程没等到 <think> → 打 noThink 标记，收口后提示（等待可感知 v4.0.1）
                next[i] = {
                  ...b,
                  content: outputText,
                  thinkOpen: b.thinkTouched ? b.thinkOpen : false,
                  noThink: !!lastMetaRef.current.thinking && !outputText.includes('<think>') || undefined,
                  refs: runRefs.length > 0 ? runRefs : undefined,
                  answerImages: runImages.length > 0 ? runImages : undefined,
                  usage: usagePayload
                    ? { input: Number(usagePayload.inputTokens ?? 0), output: Number(usagePayload.outputTokens ?? 0) }
                    : undefined,
                  // 耗时与 traceId（v4.1.3）：run_completed 直出，回答尾注展示
                  durationMs: Number(ev.payload.durationMs ?? 0) > 0 ? Number(ev.payload.durationMs) : undefined,
                  traceId: String(ev.payload.traceId ?? '') || undefined,
                }
                break
              }
            }
            return next
          })
          finishRun()
          void reloadSessions()
        } else if (ev.type === 'run_failed') {
          const errorMsg = String(ev.payload.errorMsg ?? ev.payload.errorCode ?? '运行失败')
          // 失败气泡带「重试」：按原始输入 / 策略 / 深度思考重放（v3.9.1）
          setBubbles((prev) => [...prev, {
            kind: 'msg', role: 'ASSISTANT', content: `⚠️ ${errorMsg}`, runId,
            retryable: true,
            failedInput: lastMetaRef.current.input,
            failedThinking: lastMetaRef.current.thinking,
          }])
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

  /** 发送本体：输入文本 + 是否深度思考。普通发送与失败重试共用（v3.9.1）。 */
  const doSend = useCallback(async (text: string, thinking: boolean) => {
    if (!text || streaming) return
    if (customNotReady) {
      pushNotice(activeSession ?? undefined, '该 Agent 还没有执行图、也没挂载工作流，custom 模式跑不了——先画一张执行图或挂载工作流')
      return
    }
    lastMetaRef.current = { input: text, thinking }
    setStreaming(true)
    setBubbles((prev) => [...prev, { kind: 'msg', role: 'USER', content: text }])
    try {
      const run = await createRun({
        sessionId: activeSession ?? undefined,
        agentId: agentId ?? undefined,
        input: text,
        strategy,
        thinking: thinking || undefined,
        clientRequestId: crypto.randomUUID(),
      })
      if (!activeSession) {
        setActiveSession(run.sessionId)
        navigate(`/chat/${run.sessionId}`, { replace: true })
      }
      setActiveRunId(run.runId)
      lastSeqRef.current = run.lastSeq
      setRunStartAt(Date.now())
      setElapsed(0)
      // 占位气泡：普通策略立即出现（取消/失败时可见痕迹）；
      // custom 图运行按节点分气泡，空占位只会留一个白条，不创建
      if (strategy !== 'custom') {
        setBubbles((prev) => [...prev, { kind: 'msg', role: 'ASSISTANT', content: '', runId: run.runId }])
      }
      subscribe(run.runId)
      void reloadSessions()
    } catch (err) {
      setStreaming(false)
      const message = err instanceof ApiError
        ? (err.code === 'SESSION_BUSY' ? '该会话已有正在执行的运行，请等待完成或取消' : err.message)
        : '发起运行失败'
      pushNotice(activeSession ?? undefined, message)
    }
  }, [streaming, customNotReady, activeSession, strategy, agentId, subscribe, reloadSessions, navigate])

  const onSend = useCallback(async () => {
    const text = input.trim()
    if (!text || streaming) return
    setInput('')
    await doSend(text, thinkingOn)
  }, [input, streaming, thinkingOn, doSend])

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

  /** 回答操作条（v4.5）：复制正文（剥思考块后） */
  async function copyAnswer(b: Bubble) {
    const parts = b.content ? splitThink(b.content) : { answer: '' }
    const text = (parts.answer || b.content || '').trim()
    if (!text) {
      toast('没有可复制的正文')
      return
    }
    try {
      await navigator.clipboard.writeText(text)
      toast('已复制')
    } catch {
      toast('复制失败（浏览器未授权剪贴板）')
    }
  }

  /** 回答操作条（v4.5）：用该回答之前最后一条用户输入重新发起运行 */
  function regenerateFrom(index: number) {
    if (streaming) {
      toast('运行进行中，先等待完成或取消')
      return
    }
    for (let k = index - 1; k >= 0; k--) {
      const prev = bubbles[k]
      if (prev.kind === 'msg' && prev.role === 'USER' && prev.content) {
        void doSend(prev.content, thinkingOn)
        return
      }
    }
    toast('找不到原始输入，无法重新生成')
  }

  function renderBubble(b: Bubble, i: number) {
    if (b.kind === 'plan') {
      const done = (b.planSteps ?? []).filter((x) => x.status === 'DONE').length
      return (
        <div key={i} className="plan-card">
          <div className="plan-head">
            {b.graphMode ? '🧩 执行图' : '🗺️ 执行计划'}
            <span className="tag info">{done}/{b.planSteps?.length ?? 0} 节点完成</span>
            {/* 等待可感知（v4.3.1）：运行中实时跳秒（复用全局 elapsed tick）；完成后显示总耗时 */}
            {streaming && b.planSteps?.some((x) => x.status === 'RUNNING')
              ? <span className="tag info">⏱ 已运行 {elapsed}s</span>
              : b.graphDurationMs != null
                ? <span className="tag gray">⏱ 总耗时 {(b.graphDurationMs / 1000).toFixed(1)}s</span>
                : null}
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
                {/* 单节点耗时（v4.3.1）：step_started/completed 的 ts 差值 */}
                {st.durationMs != null && (
                  <span className="note gray" style={{ marginLeft: 6 }}>{(st.durationMs / 1000).toFixed(1)}s</span>
                )}
              </li>
            ))}
          </ol>
          {/* 节点耗时瀑布（v4.5，Dify 工作流运行详情同款）：横向条形按耗时对比，瓶颈节点一眼可见 */}
          {b.planSteps && b.planSteps.some((x) => x.durationMs != null) && (() => {
            const timed = b.planSteps.filter((x) => x.durationMs != null && (x.durationMs as number) > 0)
            if (timed.length === 0) return null
            const max = Math.max(...timed.map((x) => x.durationMs as number))
            return (
              <div className="waterfall">
                {timed.map((x, k) => (
                  <div key={k} className="wf-row">
                    <span className="wf-name" title={x.title}>{x.title}</span>
                    <span className="wf-bar-wrap">
                      <span
                        className={`wf-bar ${x.status === 'SKIPPED' ? 'skip' : ''}`}
                        style={{ width: `${Math.max(3, ((x.durationMs as number) / max) * 100)}%` }}
                      />
                    </span>
                    <span className="wf-ms">{(x.durationMs as number / 1000).toFixed(1)}s</span>
                  </div>
                ))}
              </div>
            )
          })()}
        </div>
      )
    }
    if (b.kind === 'tool') {
      // 紧凑芯片（v4.4.2）：默认只显示一行「🔧 友好名 · 状态」，代码/结果收进可展开详情，不再铺原始 JSON
      const label = toolLabel(b.toolName ?? '')
      const code = typeof b.args?.code === 'string' ? (b.args.code as string) : undefined
      const detail = code ?? JSON.stringify(b.args, null, 2)
      return (
        <div key={i} className={`tool-call-card slim ${b.resultDone && !b.resultOk ? 'err' : ''}`}>
          <details>
            <summary className="tcc-head">
              <span className="tcc-icon">🔧</span>
              <span className="tcc-name">{label}</span>
              {!b.resultDone && <span className="tag info">执行中…</span>}
              {b.resultDone && (b.resultOk
                ? <span className="tag ok">完成{b.resultMs != null ? ` ${(b.resultMs / 1000).toFixed(1)}s` : ''}</span>
                : <span className="tag err">失败</span>)}
            </summary>
            <div className="tcc-args">{detail}</div>
            {b.resultDone && <div className="tcc-res">{b.resultOk ? '✅ ' : '❌ '}{b.resultBrief}</div>}
          </details>
          {b.refs && b.refs.length > 0 && <div className="tcc-res">📚 命中：{b.refs.join('｜')}</div>}
          {b.artifacts && b.artifacts.length > 0 && (
            <div className="tcc-res">📎 已存为产物：{b.artifacts.map((a) => (
              <a key={a.id} className="tcc-file" href={`/api/v1/workspace/artifacts/${a.id}/download`}>
                {a.name}（{(a.sizeBytes / 1024).toFixed(1)}KB）
              </a>
            ))}</div>
          )}
          {b.images && b.images.length > 0 && (
            <div className="tcc-imgs">
              {b.images.map((src, k) => (
                <img key={k} src={src} alt={`图表 ${k + 1}`} onClick={() => setImgPreview(src)} />
              ))}
            </div>
          )}
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
        <div className="msg-body">
          {b.stepTitle && <div className="msg-step">{b.stepTitle}</div>}
          <div className={`bubble ${b.role === 'ASSISTANT' ? 'md-bubble' : ''}`}>
            {b.retryable && (
              <div className="retry-row">
                <button
                  className="btn sm"
                  disabled={streaming}
                  onClick={() => b.failedInput && void doSend(b.failedInput, b.failedThinking ?? false)}
                >
                  🔁 重试
                </button>
                <span className="note gray">按原输入 / 策略重新发起一次运行</span>
              </div>
            )}
            {b.role === 'ASSISTANT' && b.content && (() => {
              const parts = splitThink(b.content)
              if (!parts.think) {
                return parts.answer
                  ? <Markdown text={parts.answer} />
                  : (b.content || (streaming ? '…' : ''))
              }
              return (
                <>
                  <ThinkingPanel
                    think={parts.think}
                    done={parts.done}
                    open={b.thinkOpen ?? false}
                    onToggle={() => setBubbles((prev) => prev.map((x, xi) => (
                      xi === i ? { ...x, thinkOpen: !(x.thinkOpen ?? false), thinkTouched: true } : x
                    )))}
                  />
                  {parts.answer
                    ? <Markdown text={parts.answer} />
                    : (parts.done ? <div className="note gray">（无正文）</div> : null)}
                </>
              )
            })()}
            {/* 沙箱图表（v4.4.5）：run_completed 后从工具芯片挪入回答正文，主视图展示（两种思考路径统一在这渲染） */}
            {b.role === 'ASSISTANT' && b.answerImages && b.answerImages.length > 0 && (
              <div className="tcc-imgs">
                {b.answerImages.map((src, k) => (
                  <img key={k} src={src} alt={`图表 ${k + 1}`} onClick={() => setImgPreview(src)} />
                ))}
              </div>
            )}
            {b.role === 'USER' && (b.content || (streaming ? '…' : ''))}
            {b.role === 'ASSISTANT' && !b.content
              && (streaming
                ? (b.runId === activeRunId
                  ? <span className="note gray">等待模型响应 · {elapsed}s</span>
                  : '…')
                : '')}
            {/* 等待可感知（v4.0.1）：正文流式期间的计时尾注 */}
            {b.role === 'ASSISTANT' && b.content && streaming && b.runId === activeRunId && (
              <div className="note gray" style={{ marginTop: 4 }}>⏳ 生成中 · {elapsed}s</div>
            )}
            {/* 等待可感知（v4.0.1）：thinking 开着但渠道没透出思考内容 */}
            {b.role === 'ASSISTANT' && b.noThink && (
              <div className="note gray" style={{ marginTop: 4 }}>
                🧠 本次未返回思考内容——渠道可能丢弃了 CoT 指令（带 tools 的中转渠道已知问题），或模型没有按 {'<think>'} 标签输出
              </div>
            )}
            {/* 引用溯源（v4.1）：回答依据的命中文档 */}
            {b.role === 'ASSISTANT' && b.refs && b.refs.length > 0 && (
              <div className="note gray" style={{ marginTop: 4 }}>📚 依据：{b.refs.join('｜')}</div>
            )}
            {/* token 用量（v4.1）：run_completed.usage 直出 */}
            {b.role === 'ASSISTANT' && b.usage && (b.usage.input > 0 || b.usage.output > 0) && (
              <div className="note gray" style={{ marginTop: 2 }}>Tokens ↑{b.usage.input} ↓{b.usage.output}</div>
            )}
            {/* 回答操作条（v4.5，Dify 同款）：复制正文 / 按原输入重新生成 */}
            {b.role === 'ASSISTANT' && b.content && (b.usage || !streaming || b.runId !== activeRunId) && (
              <div className="msg-actions">
                <button className="btn xs" onClick={() => void copyAnswer(b)}>📋 复制</button>
                <button className="btn xs" disabled={streaming} onClick={() => regenerateFrom(i)}>🔄 重新生成</button>
              </div>
            )}
            {/* 耗时 + traceId 溯源（v4.1.3）：traceId 带一键复制，排查问题贴给后端即可对上全链路日志 */}
            {b.role === 'ASSISTANT' && !streaming && (b.durationMs || b.traceId) && (
              <div className="note gray" style={{ marginTop: 2, display: 'flex', alignItems: 'center', gap: 10 }}>
                {b.durationMs ? <span>⏱ 耗时 {(b.durationMs / 1000).toFixed(1)}s</span> : null}
                {b.traceId ? (
                  <span style={{ display: 'inline-flex', alignItems: 'center', gap: 4 }}>
                    <span>traceId: {b.traceId.slice(0, 8)}…</span>
                    <button
                      className="btn sm"
                      style={{ padding: '0 6px', fontSize: 11, lineHeight: '16px' }}
                      title="复制完整 traceId"
                      onClick={() => {
                        if (b.traceId) {
                          void navigator.clipboard.writeText(b.traceId)
                          toast('traceId 已复制')
                        }
                      }}
                    >复制</button>
                  </span>
                ) : null}
              </div>
            )}
          </div>
        </div>
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
            <span className="tag gray" title="运行策略在「我的 Agent → 编辑」里配置，对话页跟随 Agent 发起">
              策略 {STRATEGY_LABELS[strategy] ?? strategy}
            </span>
            <button
              className={`btn sm ${thinkingOn ? 'primary' : ''}`}
              disabled={strategy === 'plan' || strategy === 'custom'}
              title={strategy === 'plan' || strategy === 'custom'
                ? '深度思考仅 auto / fixed 策略可用（plan / custom 已有计划卡 / 节点进度）'
                : '深度思考：流式时自动展开推理面板，结论输出完自动收起；可随时手动开合'}
              onClick={() => setThinkingOn(!thinkingOn)}
            >
              🧠 思考{thinkingOn ? '·开' : ''}
            </button>
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
          {customNotReady && (
            <div style={{ margin: '6px 12px 0', padding: '6px 10px', borderRadius: 8, background: '#fdf3e3', border: '1px solid #f3d9a4', color: '#92400e', fontSize: 12.5 }}>
              当前 Agent 还没有执行图、也没挂载工作流，custom 模式跑不了——先到
              <a href={`/agents/${agentId}/graph`} style={{ margin: '0 4px' }}>执行图</a>
              画一张，或在「我的 Agent → 编辑」里挂载工作流。
            </div>
          )}
          {workflowHint && (
            <div style={{ margin: '6px 12px 0', padding: '6px 10px', borderRadius: 8, background: '#eef2ff', border: '1px solid #c7d2fe', color: '#3730a3', fontSize: 12.5 }}>
              当前 Agent 挂载了工作流「{selectedAgent!.workflowName}」——只有
              <b style={{ margin: '0 3px' }}>custom（按执行图调度）</b>策略会执行它，auto / plan 不会走图路由。
            </div>
          )}
          <div className="chat-body" ref={bodyRef}>
            {bubbles.length === 0 && (selectedAgent?.openingStatement || (selectedAgent?.suggestedQuestions?.length ?? 0) > 0) ? (
              // 开场白 + 建议问题（v4.5，Dify Opening Statement / Suggested Questions 同款）
              <div className="chat-welcome">
                {selectedAgent?.openingStatement && (
                  <div className="msg ai">
                    <div className="mk">🤖</div>
                    <div className="msg-body"><div className="bubble md-bubble">{selectedAgent.openingStatement}</div></div>
                  </div>
                )}
                {(selectedAgent?.suggestedQuestions?.length ?? 0) > 0 && (
                  <div className="suggestions">
                    {selectedAgent!.suggestedQuestions!.map((q, k) => (
                      <button key={k} className="suggestion-chip" disabled={streaming} onClick={() => void doSend(q, thinkingOn)}>
                        {q}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            ) : bubbles.length === 0 ? (
              <div className="note gray">
                选择左侧会话或直接在下方输入发起对话。试试说「帮我创建一个工单，标题是 xxx，优先级 P1」——
                高风险写操作会弹出审批卡。
              </div>
            ) : null}
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
      {imgPreview && (
        <div className="img-lightbox" onClick={() => setImgPreview(null)}>
          <img src={imgPreview} alt="图表预览" />
          <div className="img-lightbox-bar">
            <a className="btn sm" href={imgPreview} download="chart.png">⬇ 下载 PNG</a>
            <span className="note gray">点击空白处关闭</span>
          </div>
        </div>
      )}
      <ToastHost />
    </>
  )
}
