import { del, get, post, put } from './client'

/** 我的 Agent API（PRD FR-6 最小充分版）。 */
export interface AgentConfig {
  id: number
  userId: number | null
  name: string
  description: string
  instruction: string | null
  modelConnectionId: number | null
  enabled: boolean
  platformPublic: boolean
  usable: boolean
  version: number
  createdAt: string | null
  updatedAt: string | null
}

export interface AgentPage {
  platform: AgentConfig[]
  mine: AgentConfig[]
}

export function fetchAgents(): Promise<AgentPage> {
  return get<AgentPage>('/workspace/agents')
}

export function createAgent(payload: {
  name: string
  description: string
  instruction: string
}): Promise<AgentConfig> {
  return post<AgentConfig>('/workspace/agents', payload)
}

export function updateAgent(
  id: number,
  payload: { name: string; description: string; instruction: string; enabled?: boolean },
): Promise<AgentConfig> {
  return put<AgentConfig>(`/workspace/agents/${id}`, payload)
}

export function deleteAgent(id: number): Promise<null> {
  return del<null>(`/workspace/agents/${id}`)
}
