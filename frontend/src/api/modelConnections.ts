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
