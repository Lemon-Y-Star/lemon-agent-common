import { del, get, post, put } from './client'

/**
 * MCP 连接 API（PRD FR-12）：平台公共连接只读（editable=false），自建连接可增删改。
 */

export type TransportType = 'SSE' | 'STDIO' | 'STREAMABLE'

export interface McpConnection {
  id: number
  userId: number | null
  name: string
  transportType: string
  url: string | null
  command: string | null
  hasSecret: boolean
  enabled: boolean
  editable: boolean
  platformPublic: boolean
  usable: boolean
  unavailableReason: string | null
  version: number
  createdAt: string | null
  updatedAt: string | null
}

export interface McpConnectionPayload {
  name: string
  transportType: string
  url?: string
  command?: string
  secret?: string
  enabled?: boolean
}

export function fetchMcps(): Promise<McpConnection[]> {
  return get<McpConnection[]>('/workspace/mcps')
}

export function createMcp(payload: McpConnectionPayload): Promise<McpConnection> {
  return post<McpConnection>('/workspace/mcps', payload)
}

export function updateMcp(id: number, payload: McpConnectionPayload): Promise<McpConnection> {
  return put<McpConnection>(`/workspace/mcps/${id}`, payload)
}

export function deleteMcp(id: number): Promise<null> {
  return del<null>(`/workspace/mcps/${id}`)
}
