import { get, post } from './client'

/** API 分发密钥 API（v3.8）。 */

export interface ApiKeyItem {
  id: number
  name: string
  keyPrefix: string
  enabled: boolean
  lastUsedAt: string | null
  createdAt: string | null
}

export function fetchApiKeys(): Promise<{ keys: ApiKeyItem[] }> {
  return get<{ keys: ApiKeyItem[] }>('/workspace/apikeys')
}

/** 创建密钥——明文只在本次响应里出现。 */
export function createApiKey(name: string): Promise<{ id: number; name: string; key: string }> {
  return post<{ id: number; name: string; key: string }>('/workspace/apikeys', { name })
}

export function revokeApiKey(id: number): Promise<null> {
  return post<null>(`/workspace/apikeys/${id}/revoke`)
}
