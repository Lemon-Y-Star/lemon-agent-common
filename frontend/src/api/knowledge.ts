import { del, get, post } from './client'

/** 知识库（v3.6 → v4.1）：文档管理 + 检索测试 + 未命中队列。 */

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
  source: 'keyword' | 'vector' | 'rerank'
  /** 多模态随文带出的图片（v4.1） */
  images?: string[] | null
}

/** 检索详细结果：hits + 重排前候选（v4.1 可诊断） */
export interface KbSearchResult {
  hits: KbHit[]
  beforeRerank: KbHit[] | null
  reranked: boolean
}

export interface KbMissItem {
  query: string
  hits: number
  lastSeenAt: string | null
}

export interface KbMisses {
  misses: KbMissItem[]
  total: number
}

export const fetchKbDocs = () => get<KbDocs>('/workspace/knowledge')
export const createKbDoc = (title: string, content: string) => post<KbDoc>('/workspace/knowledge', { title, content })
export const deleteKbDoc = (id: number) => del<null>(`/workspace/knowledge/${id}`)
export const searchKb = (q: string, topK = 5) =>
  get<KbSearchResult>(`/workspace/knowledge/search?q=${encodeURIComponent(q)}&topK=${topK}`)
export const fetchKbMisses = (limit = 20) => get<KbMisses>(`/workspace/knowledge/misses?limit=${limit}`)
