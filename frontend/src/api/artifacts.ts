import { get, post } from './client'

/** 产物与反馈 API（v3.8）。 */

export interface ArtifactItem {
  runId: number
  sessionId: number
  agentId: number | null
  input: string | null
  output: string | null
  status: string
  inputTokens: number | null
  outputTokens: number | null
  endedAt: string | null
  myRating: 'UP' | 'DOWN' | null
  myComment: string | null
}

export async function fetchArtifacts(page = 1, size = 10): Promise<{ items: ArtifactItem[]; total: number }> {
  return get<{ items: ArtifactItem[]; total: number }>(`/workspace/artifacts?page=${page}&size=${size}`)
}

export async function submitFeedback(
  runId: number,
  rating: 'UP' | 'DOWN',
  comment: string,
): Promise<unknown> {
  return post(`/workspace/artifacts/${runId}/feedback`, { rating, comment })
}
