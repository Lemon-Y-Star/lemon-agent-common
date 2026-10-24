/**
 * 后端 API 统一封装：
 * - 自动携带 Authorization: Bearer（登录态）
 * - 统一解析响应包络 {code, message, data}，code !== 'SUCCESS' 抛 ApiError
 * - HTTP 401（未认证）清登录态并跳登录页（PRD FR-1）
 */

const TOKEN_KEY = 'lemon_agent_token'
const USER_KEY = 'lemon_agent_user'

export const API_BASE = '/api/v1'

export class ApiError extends Error {
  code: string

  constructor(code: string, message: string) {
    super(message)
    this.code = code
  }
}

export interface Envelope<T> {
  code: string
  message: string
  data: T
}

export interface AuthUser {
  userId: number
  username: string
  role: string
}

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function getStoredUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY)
  return raw ? (JSON.parse(raw) as AuthUser) : null
}

export function storeSession(token: string, user: AuthUser): void {
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function clearSession(): void {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
}

export function handleUnauthorized(): void {
  clearSession()
  window.location.href = '/login'
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const headers = new Headers(init.headers)
  headers.set('X-Source', 'web')
  const token = getToken()
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  if (init.body && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json')
  }

  const response = await fetch(`${API_BASE}${path}`, { ...init, headers })

  if (response.status === 401) {
    handleUnauthorized()
    throw new ApiError('UNAUTHORIZED', '登录状态已失效，请重新登录')
  }

  const envelope = (await response.json()) as Envelope<T>
  if (envelope.code !== 'SUCCESS') {
    throw new ApiError(envelope.code, envelope.message)
  }
  return envelope.data
}

export function post<T>(path: string, body?: unknown): Promise<T> {
  return request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) })
}

export function put<T>(path: string, body: unknown): Promise<T> {
  return request<T>(path, { method: 'PUT', body: JSON.stringify(body) })
}

export function del<T>(path: string): Promise<T> {
  return request<T>(path, { method: 'DELETE' })
}

export function get<T>(path: string): Promise<T> {
  return request<T>(path, { method: 'GET' })
}
