import { get } from './client'

/** LLM 观测 API（v3.8）。 */

export interface LlmRecent {
  runId: number
  status: string
  strategy: string
  errorCode?: string | null
  traceId: string
  startedAt: string | null
  endedAt: string | null
  modelName: string | null
  inputTokens: number | null
  outputTokens: number | null
  latencyMs: number | null
}

export interface LlmOverview {
  summary: { calls: number; failed: number; inputTokens: number; outputTokens: number; avgLatencyMs: number }
  byModel: {
    modelName: string
    calls: number
    failed: number
    inputTokens: number
    outputTokens: number
    avgLatencyMs: number
  }[]
  recent: LlmRecent[]
}

export function fetchLlmOverview(days = 7): Promise<LlmOverview> {
  return get<LlmOverview>(`/workspace/llm/overview?days=${days}`)
}
