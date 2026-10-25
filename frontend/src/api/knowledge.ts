import { del, get, post } from './client'

/** 知识库（v3.6）：文档管理 + 检索测试。 */

export interface KbDoc {
  id: number
  title: string
  content: string
  createdAt: string | null
}

export interface KbDocs {
  docs: KbDoc[]
  vectorEnabled: boolean
}

export interface KbHit {
  title: string
  snippet: string
  source: 'keyword' | 'vector'
}

export const fetchKbDocs = () => get<KbDocs>('/workspace/knowledge')
export const createKbDoc = (title: string, content: string) => post<KbDoc>('/workspace/knowledge', { title, content })
export const deleteKbDoc = (id: number) => del<null>(`/workspace/knowledge/${id}`)
export const searchKb = (q: string, topK = 5) =>
  get<KbHit[]>(`/workspace/knowledge/search?q=${encodeURIComponent(q)}&topK=${topK}`)
