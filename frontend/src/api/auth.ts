import { get, post, type AuthUser } from './client'

export interface LoginParams {
  username: string
  password: string
}

export interface LoginResult {
  token: string
  userId: number
  username: string
  role: string
}

export function apiLogin(params: LoginParams): Promise<LoginResult> {
  return post<LoginResult>('/auth/login', params)
}

export function apiLogout(): Promise<null> {
  return post<null>('/auth/logout')
}

export function apiMe(): Promise<AuthUser> {
  return get<AuthUser>('/auth/me')
}

export interface ChangePasswordParams {
  currentPassword: string
  newPassword: string
}

export function apiChangePassword(params: ChangePasswordParams): Promise<null> {
  return post<null>('/auth/password', params)
}
