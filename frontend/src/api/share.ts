/**
 * Agent 分享（v4.6.0）：免登录公开对话页 API。
 *
 * 管理端（需登录）走 client 的 get/put；公开端点直接 fetch（无 Authorization 头，
 * AuthInterceptor 对 /api/v1/share/** 放行）。
 */

export interface ShareConfig {
  enabled: boolean
  shareCode: string
  accessCode: string
}

export async function getShareConfig(agentId: number): Promise<ShareConfig> {
  const { request } = await import('./client')
  return request<ShareConfig>(`/workspace/agents/${agentId}/share`)
}

export async function setShareConfig(
  agentId: number,
  enabled: boolean,
  accessCode: string,
): Promise<ShareConfig> {
  const { request } = await import('./client')
  return request<ShareConfig>(`/workspace/agents/${agentId}/share`, {
    method: 'POST',
    body: JSON.stringify({ enabled, accessCode }),
  })
}

export interface ShareInfo {
  name: string
  description: string
  openingStatement: string | null
  suggestedQuestions: string[] | null
}

export async function fetchShareInfo(code: string, accessCode: string): Promise<ShareInfo> {
  const qs = accessCode ? `?accessCode=${encodeURIComponent(accessCode)}` : ''
  const resp = await fetch(`/api/v1/share/${code}/info${qs}`)
  const data = await resp.json()
  if (!resp.ok || data.code !== 'SUCCESS') {
    const err = new Error(data.message || '分享不存在或已关闭') as Error & { status?: number; code?: string }
    err.status = resp.status
    err.code = data.code
    throw err
  }
  return data.data as ShareInfo
}

export async function shareCreateRun(
  code: string,
  message: string,
  accessCode: string,
): Promise<{ runId: number; sessionId: number }> {
  const qs = accessCode ? `?accessCode=${encodeURIComponent(accessCode)}` : ''
  const resp = await fetch(`/api/v1/share/${code}/runs${qs}`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ message, accessCode }),
  })
  const data = await resp.json()
  if (!resp.ok || data.code !== 'SUCCESS') {
    throw new Error(data.message || '发送失败')
  }
  return data.data as { runId: number; sessionId: number }
}

/** 分享版 SSE（无 Authorization 头；帧格式与登录版一致）。 */
export function streamShareEvents(params: {
  code: string
  accessCode: string
  runId: number
  afterEventId: number
  onEvent: (ev: { seq: number; type: string; payload: Record<string, unknown> }) => void
  onError: (err: Error) => void
  onDone?: () => void
}): { abort: () => void } {
  const controller = new AbortController()
  async function consume() {
    const qs = params.accessCode ? `&accessCode=${encodeURIComponent(params.accessCode)}` : ''
    const resp = await fetch(
      `/api/v1/share/${params.code}/runs/${params.runId}/events?afterEventId=${params.afterEventId}${qs}`,
      { signal: controller.signal },
    )
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
      const blocks = buffer.split('\n\n')
      buffer = blocks.pop() ?? ''
      for (const block of blocks) {
        let seq = 0
        let type = 'message'
        let data = ''
        for (const line of block.split('\n')) {
          if (line.startsWith(':')) continue
          if (line.startsWith('id:')) seq = Number(line.slice(3).trim())
          else if (line.startsWith('event:')) type = line.slice(6).trim()
          else if (line.startsWith('data:')) data += line.slice(5).trim()
        }
        if (data) {
          try {
            params.onEvent({ seq, type, payload: JSON.parse(data) })
          } catch {
            // 负载解析失败的事件跳过
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
