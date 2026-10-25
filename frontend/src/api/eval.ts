import { get, post } from './client'

/** 图评测回归 API（v3.5）。 */

export interface EvalItem {
  seq: number
  input: string
  runId: number
  status: string
  output: string | null
  skippedNodes: string[] | null
  inputTokens: number
  outputTokens: number
}

export interface EvalBatch {
  id: number
  agentId: number
  graphVersion: number | null
  note: string
  createdAt: string | null
  total: number
  finished: number
  items: EvalItem[]
}

export function createEvalBatch(agentId: number, inputs: string[], note: string): Promise<EvalBatch> {
  return post<EvalBatch>(`/workspace/agents/${agentId}/eval/batches`, { inputs, note })
}

export async function fetchEvalBatches(agentId: number): Promise<EvalBatch[]> {
  const data = await get<{ batches: EvalBatch[] }>(`/workspace/agents/${agentId}/eval/batches`)
  return data.batches
}

/** 跨 Agent 的批次总览（「我的评测」页）。 */
export interface EvalBatchOverview extends EvalBatch {
  agentName: string | null
}

export async function fetchEvalOverview(): Promise<{ batches: EvalBatchOverview[] }> {
  return get<{ batches: EvalBatchOverview[] }>('/workspace/evals')
}
