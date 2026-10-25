import { getToken } from './client'

/**
 * 对话运行时 API（契约 §4 的 9 个端点）。
 *
 * SSE 用 fetch + ReadableStream 手工解析而非 EventSource——事件流端点要带
 * Authorization 头（契约 §1.2），EventSource 不能自定义请求头。
 */

export interface SessionItem {
  sessionId: number
  title: string
  lastActiveAt: string | null
  messageCount: number
}

export interface SessionList {
  total: number
  items: SessionItem[]
}

export interface MessageItem {
  messageId: number
  role: 'USER' | 'ASSISTANT'
  content: string
  runId?: number | null
  createdAt: string | null
}

export interface RunBrief {
  runId: number
  status: string
  lastSeq: number
  traceId: string
  strategy: string
}

export interface SessionDetail {
  sessionId: number
  messages: MessageItem[]
  runs: RunBrief[]
}

export interface RunData {
  runId: number
  sessionId: number
  traceId: string
  status: string
  lastSeq: number
  duplicated: boolean
  startedAt: string | null
}

export async function fetchSessions(page = 1, size = 50): Promise<SessionList> {
  const { request } = await import('./client')
  return request<SessionList>(`/agent/sessions?page=${page}&size=${size}`)
}

export async function createSession(title?: string): Promise<{ sessionId: number }> {
  const { post } = await import('./client')
  return post<{ sessionId: number }>('/agent/sessions', title ? { title } : {})
}

export async function fetchSessionDetail(sessionId: number): Promise<SessionDetail> {
  const { get } = await import('./client')
  return get<SessionDetail>(`/agent/sessions/${sessionId}/messages`)
}

export async function renameSession(sessionId: number, title: string): Promise<{ sessionId: number }> {
  const { post } = await import('./client')
  return post<{ sessionId: number }>(`/agent/sessions/${sessionId}/rename`, { title })
}

export async function deleteSession(sessionId: number): Promise<null> {
  const { post } = await import('./client')
  return post<null>(`/agent/sessions/${sessionId}/delete`)
}

export async function createRun(params: {
  sessionId?: number
  agentId?: number
  input: string
  strategy: string
  clientRequestId: string
}): Promise<RunData> {
  const { post } = await import('./client')
  return post<RunData>('/agent/runs', params)
}

export async function cancelRun(runId: number): Promise<{ runId: number; status: string; lastSeq: number }> {
  const { post } = await import('./client')
  return post<{ runId: number; status: string; lastSeq: number }>(`/agent/runs/${runId}/cancel`)
}

/** 执行图运行视图（快照里的 graph 字段；仅 custom 策略非 null）。 */
export interface RunGraphView {
  version: number
  nodes: {
    id: string
    type: 'START' | 'LLM' | 'TOOL' | 'APPROVAL' | 'END'
    title: string
    status: 'DONE' | 'RUNNING' | 'PENDING' | 'SKIPPED'
    x?: number | null
    y?: number | null
    input?: string | null
    output?: string | null
  }[]
  edges: { from: string; to: string; route?: string | null }[]
}

export interface RunSnapshot {
  runId: number
  sessionId: number
  status: string
  strategy: string
  traceId: string
  lastSeq: number
  errorCode?: string | null
  errorMsg?: string | null
  pendingApproval?: {
    kind: string
    approvalId: string
    toolName?: string
    args?: Record<string, unknown>
    riskLevel: string
    expiresAt?: string
  } | null
  usage?: { inputTokens: number; outputTokens: number } | null
  graph?: RunGraphView | null
  startedAt?: string | null
  endedAt?: string | null
}

export async function fetchRunSnapshot(runId: number): Promise<RunSnapshot> {
  const { get } = await import('./client')
  return get<RunSnapshot>(`/agent/runs/${runId}`)
}

export async function submitApproval(
  runId: number,
  params: { approvalId: string; decision: 'APPROVE' | 'REJECT'; comment?: string },
): Promise<{ runId: number; status: string; lastSeq: number }> {
  const { post } = await import('./client')
  return post<{ runId: number; status: string; lastSeq: number }>(`/agent/runs/${runId}/approval`, params)
}

/** SSE 事件（seq 去重后的载荷）。 */
export interface RuntimeEvent {
  seq: number
  type: string
  payload: Record<string, unknown>
}

/**
 * 订阅运行事件流：fetch + ReadableStream 按行解析 SSE 帧。
 *
 * @returns abort 函数（取消订阅时调用，不要先于 cancel 接口调用——契约 4.9 前端顺序）
 */
export function streamRunEvents(params: {
  runId: number
  afterEventId: number
  onEvent: (ev: RuntimeEvent) => void
  onError: (err: Error) => void
  /** 服务端把流读完了（正常结束/终态 complete/BLOCKED 挂起断流），连接已关闭 */
  onDone?: () => void
}): { abort: () => void } {
  const controller = new AbortController()

  async function consume() {
    const headers: Record<string, string> = {
      Accept: 'text/event-stream',
      'X-Source': 'web',
    }
    const token = getToken()
    if (token) {
      headers.Authorization = `Bearer ${token}`
    }
    const resp = await fetch(`/api/v1/agent/runs/${params.runId}/events?afterEventId=${params.afterEventId}`, {
      headers,
      signal: controller.signal,
    })
    if (!resp.ok || !resp.body) {
      params.onError(new Error(`事件流连接失败：HTTP ${resp.status}`))
      return
    }
    const reader = resp.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    for (;;) {
      const { done, value } = await reader.read()
      if (done) {
        params.onDone?.()
        break
      }
      buffer += decoder.decode(value, { stream: true })
      // SSE 帧以空行分隔；按行解析 id/event/data 三元组
      const blocks = buffer.split('\n\n')
      buffer = blocks.pop() ?? ''
      for (const block of blocks) {
        let seq = 0
        let type = 'message'
        let data = ''
        for (const line of block.split('\n')) {
          if (line.startsWith(':')) continue // 心跳注释帧
          if (line.startsWith('id:')) seq = Number(line.slice(3).trim())
          else if (line.startsWith('event:')) type = line.slice(6).trim()
          else if (line.startsWith('data:')) data += line.slice(5).trim()
        }
        if (data) {
          try {
            params.onEvent({ seq, type, payload: JSON.parse(data) })
          } catch {
            // 负载解析失败的事件跳过，不影响后续事件
          }
        }
      }
    }
  }

  consume().catch((err) => {
    if (!controller.signal.aborted) {
      params.onError(err instanceof Error ? err : new Error(String(err)))
    }
  })

  return { abort: () => controller.abort() }
}
