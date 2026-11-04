import { del, get, post, put } from './client'

/**
 * 模型连接 API（PRD FR-13）。密钥只在创建/更新时提交，接口永不回传明文。
 */

export interface ModelConnection {
  id: number
  name: string
  provider: string
  baseUrl: string
  hasSecret: boolean
  secretTail: string | null
  models: string[]
  enabled: boolean
  allowAuxiliary: boolean
  /** 健康状态：ok / fail / unknown（v4.1 定时探测） */
  healthStatus: 'ok' | 'fail' | 'unknown'
  healthLatencyMs: number | null
  healthCheckedAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface ModelConnectionsPage {
  secretsAvailable: boolean
  allowedHosts: string[]
  auxiliaryProviderSource: string
  auxiliaryPurposes: string[]
  connections: ModelConnection[]
  allowPlatformAuxiliaries: boolean
}

export interface ModelConnectionPayload {
  name: string
  provider: string
  baseUrl: string
  apiKey: string
  models: string[]
  allowAuxiliary: boolean
  enabled?: boolean
}

export function fetchModelConnections(): Promise<ModelConnectionsPage> {
  return get<ModelConnectionsPage>('/workspace/model-connections')
}

export function createModelConnection(payload: ModelConnectionPayload): Promise<ModelConnection> {
  return post<ModelConnection>('/workspace/model-connections', payload)
}

export function updateModelConnection(id: number, payload: ModelConnectionPayload): Promise<ModelConnection> {
  return put<ModelConnection>(`/workspace/model-connections/${id}`, payload)
}

export function deleteModelConnection(id: number): Promise<null> {
  return del<null>(`/workspace/model-connections/${id}`)
}

/** 立即探测当前用户的启用连接（v4.1「立即检测」按钮）。 */
export function runHealthCheck(): Promise<{ id: number; name: string; healthStatus: string; healthLatencyMs: number | null }[]> {
  return post<{ id: number; name: string; healthStatus: string; healthLatencyMs: number | null }[]>(
    '/workspace/model-connections/health-check', {})
}

/** 多模型对比单项结果（v4.8）：失败时 error 带原因、content 为 null。 */
export interface ModelCompareItem {
  connectionId: number
  name: string
  model: string
  content: string | null
  latencyMs: number | null
  inputTokens: number
  outputTokens: number
  error: string | null
}

/** 多模型对比（v4.8）：同一问题并发发给选中连接，结果按传入顺序返回。 */
export function compareModels(query: string, connectionIds: number[], systemPrompt?: string): Promise<ModelCompareItem[]> {
  return post<ModelCompareItem[]>('/workspace/model-connections/compare', {
    query,
    connectionIds,
    systemPrompt: systemPrompt?.trim() || undefined,
  })
}
