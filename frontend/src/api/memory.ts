import { del, get, post, put } from './client'

/** 长期记忆（v3.6）：用户级事实/偏好，运行时拼进 system prompt。 */

export interface Memory {
  id: number
  content: string
  enabled: boolean
  createdAt: string | null
}

export interface Memories {
  items: Memory[]
  injectLimit: number
}

export const fetchMemories = () => get<Memories>('/workspace/memories')
export const createMemory = (content: string) => post<Memory>('/workspace/memories', { content })
export const toggleMemory = (id: number, enabled: boolean) =>
  put<null>(`/workspace/memories/${id}`, { enabled })
export const deleteMemory = (id: number) => del<null>(`/workspace/memories/${id}`)
