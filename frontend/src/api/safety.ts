import { get, post, del } from './client'

/** 敏感词管理 API（v3.9.2，admin only）：平台级词库，输入拦截 + 输出脱敏共用。 */

export interface SensitiveWordItem {
  id: number
  word: string
}

export function fetchSensitiveWords(): Promise<SensitiveWordItem[]> {
  return get<SensitiveWordItem[]>('/admin/sensitive-words')
}

export function addSensitiveWord(word: string): Promise<SensitiveWordItem> {
  return post<SensitiveWordItem>('/admin/sensitive-words', { word })
}

export function deleteSensitiveWord(id: number): Promise<null> {
  return del<null>(`/admin/sensitive-words/${id}`)
}
