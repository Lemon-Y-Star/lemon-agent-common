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
  /** 文档总数（v4.5.6 分页配套） */
  total: number
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

/** 文档列表（v4.5.6 分页，页码 1 起）。 */
export const fetchKbDocs = (page = 1, size = 8) =>
  get<KbDocs>(`/workspace/knowledge?page=${page}&size=${size}`)
/** 文档详情（v4.5.6）：弹窗看全文。 */
export const fetchKbDocDetail = (id: number) => get<KbDoc>(`/workspace/knowledge/${id}`)
export const createKbDoc = (title: string, content: string) => post<KbDoc>('/workspace/knowledge', { title, content })
export const deleteKbDoc = (id: number) => del<null>(`/workspace/knowledge/${id}`)
export const searchKb = (q: string, topK = 5) =>
  get<KbSearchResult>(`/workspace/knowledge/search?q=${encodeURIComponent(q)}&topK=${topK}`)
/** 未命中队列（v4.5.6 分页，页码 1 起）。 */
export const fetchKbMisses = (page = 1, size = 10) =>
  get<KbMisses>(`/workspace/knowledge/misses?page=${page}&size=${size}`)
/** AI 补文档草稿（v4.6.0）：kb_miss/标注查询 → LLM 生成候选答案（同步 ~20s）。 */
export const aiDraftKb = (query: string) =>
  post<{ draft: string }>('/workspace/knowledge/ai-draft', { query })
