import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api/client'
import {
  aiDraftKb, createKbDoc, deleteKbDoc, fetchKbDocDetail, fetchKbDocs, fetchKbMisses, searchKb,
  type KbDoc, type KbHit, type KbMissItem, type KbSearchResult,
} from '../api/knowledge'
import Modal from '../components/Modal'
import Markdown from '../components/Markdown'
import { ToastHost, toast } from '../components/Toast'
import { fmtLocal } from '../utils/fmtTime'

const DOC_SIZE = 8
const MISS_SIZE = 10

/** 我的知识库（v3.6 → v4.5.6）：文档管理（分页 + 详情弹窗）+ 检索测试 + 未命中队列（分页）。 */
export default function Knowledge() {
  const [docs, setDocs] = useState<KbDoc[]>([])
  const [docTotal, setDocTotal] = useState(0)
  const [docPage, setDocPage] = useState(1)
  const [vectorEnabled, setVectorEnabled] = useState(false)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [query, setQuery] = useState('')
  const [result, setResult] = useState<KbSearchResult | null>(null)
  const [misses, setMisses] = useState<KbMissItem[]>([])
  const [missTotal, setMissTotal] = useState(0)
  const [missPage, setMissPage] = useState(1)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  /** 文档详情弹窗（v4.5.6）：null=关闭；loading=拉取中；doc 拉到后渲染全文 */
  const [detail, setDetail] = useState<{ doc: KbDoc | null; loading: boolean } | null>(null)
  /** AI 补文档草稿弹窗（v4.6.0）：query = 待补问题；draft = 生成中/已生成的草稿 */
  const [aiDraft, setAiDraft] = useState<{ query: string; draft: string; generating: boolean } | null>(null)
  const streamingDraft = aiDraft?.generating === true
  const docPages = Math.max(1, Math.ceil(docTotal / DOC_SIZE))
  const missPages = Math.max(1, Math.ceil(missTotal / MISS_SIZE))

  const loadDocs = useCallback(async (page: number) => {
    const data = await fetchKbDocs(page, DOC_SIZE)
    setDocs(data.docs)
    setDocTotal(data.total)
    setVectorEnabled(data.vectorEnabled)
    return data.total
  }, [])

  const loadMisses = useCallback(async (page: number) => {
    const data = await fetchKbMisses(page, MISS_SIZE)
    setMisses(data.misses)
    setMissTotal(data.total)
    return data.total
  }, [])

  const reload = useCallback(async () => {
    try {
      const [t1, t2] = await Promise.all([loadDocs(docPage), loadMisses(missPage)])
      setError('')
      // 删除后当前页可能超出范围：回退一页重拉
      if (t1 > 0 && docPage > Math.ceil(t1 / DOC_SIZE)) {
        setDocPage(Math.ceil(t1 / DOC_SIZE))
      }
      if (t2 > 0 && missPage > Math.ceil(t2 / MISS_SIZE)) {
        setMissPage(Math.ceil(t2 / MISS_SIZE))
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载失败')
    }
  }, [loadDocs, loadMisses, docPage, missPage])

  useEffect(() => { void loadDocs(docPage).catch((e) => setError(e instanceof ApiError ? e.message : '加载失败')) },
    [loadDocs, docPage])
  useEffect(() => { void loadMisses(missPage).catch((e) => setError(e instanceof ApiError ? e.message : '加载失败')) },
    [loadMisses, missPage])

  async function onAdd() {
    if (!title.trim() || !content.trim()) {
      setError('标题和正文都不能为空')
      return
    }
    setBusy(true)
    try {
      await createKbDoc(title.trim(), content)
      setTitle('')
      setContent('')
      toast('文档已入库')
      setDocPage(1) // 新文档在最前（id 倒序），回第一页能看到
      await loadDocs(1)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '新增失败')
    } finally {
      setBusy(false)
    }
  }

  async function onDelete(id: number) {
    try {
      await deleteKbDoc(id)
      toast('已删除')
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '删除失败')
    }
  }

  async function onSearch() {
    if (!query.trim()) return
    try {
      setResult(await searchKb(query.trim(), 5))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '检索失败')
    }
  }

  /** AI 补文档（v4.6.0）：LLM 生成草稿 → 人工编辑 → 入库。 */
  async function onAiDraft(query: string) {
    setAiDraft({ query, draft: '', generating: true })
    try {
      const r = await aiDraftKb(query)
      setAiDraft({ query, draft: r.draft, generating: false })
    } catch (err) {
      setAiDraft(null)
      toast(err instanceof ApiError ? err.message : 'AI 生成草稿失败')
    }
  }

  /** 打开文档详情（v4.5.6）：拉全文，Markdown 渲染。 */
  function openDetail(id: number) {
    setDetail({ doc: null, loading: true })
    fetchKbDocDetail(id)
      .then((doc) => setDetail({ doc, loading: false }))
      .catch((err) => {
        setDetail(null)
        toast(err instanceof ApiError ? err.message : '详情加载失败')
      })
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h2>我的知识库</h2>
          <div className="desc">
            文档入库后，Agent 才能通过 <code>search_docs</code> 检索到（对话时自动调用）。
            当前检索模式：
            <span className={`tag ${vectorEnabled ? 'ok' : 'warn'}`} style={{ marginLeft: 6 }}>
              {vectorEnabled ? '关键词 + 向量双路' : '关键词降级（未配置 embedding）'}
            </span>
          </div>
        </div>
      </div>

      {error && <p className="status-line" role="status" style={{ color: 'var(--danger)' }}>{error}</p>}

      <div className="kb-wrap">
        <div className="card">
          <h3>新增文档</h3>
          <div className="field">
            <label>标题</label>
            <input value={title} onChange={(e) => setTitle(e.target.value)} placeholder="例如：售后服务流程" />
          </div>
          <div className="field">
            <label>正文（纯文本 / Markdown 均可）</label>
            <textarea rows={6} value={content} onChange={(e) => setContent(e.target.value)} placeholder="把要问的知识写进来…" />
          </div>
          <button className="btn primary" disabled={busy} onClick={() => void onAdd()}>{busy ? '入库中…' : '入库'}</button>

          <h3 style={{ marginTop: 18 }}>文档（{docTotal}）</h3>
          <div className="kb-docs kb-scroll">
            {docs.length === 0 && <div className="note gray">还没有文档</div>}
            {docs.map((d) => (
              <div key={d.id} className="kb-doc">
                <div className="kb-doc-head">
                  <b>{d.title}</b>
                  <span style={{ display: 'flex', gap: 6 }}>
                    <button className="btn sm" onClick={() => openDetail(d.id)}>详情</button>
                    <button className="btn sm danger" onClick={() => void onDelete(d.id)}>删除</button>
                  </span>
                </div>
                <div className="kb-doc-body">{d.content.slice(0, 160)}{d.content.length > 160 ? '…' : ''}</div>
              </div>
            ))}
          </div>
          <Pager page={docPage} pages={docPages} total={docTotal} onJump={setDocPage} />
        </div>

        <div className="card">
          <h3>检索测试</h3>
          <div className="row" style={{ display: 'flex', gap: 8 }}>
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') void onSearch() }}
              placeholder="输入关键词，回车检索（和 Agent 调用 search_docs 走同一条链路）"
              style={{ flex: 1 }}
            />
            <button className="btn" onClick={() => void onSearch()}>检索</button>
          </div>
          <div className="kb-hits kb-scroll">
            {result === null && <div className="note gray">检索结果会显示在这里，并标注命中来源</div>}
            {result !== null && result.hits.length === 0 && (
              <div className="note gray">没有命中——这条查询已进「未命中队列」，补充文档后即可被检索到</div>
            )}
            {result?.reranked && (
              <div className="note gray" style={{ margin: '4px 0' }}>
                已启用重排精排（<b>{result.hits.length}</b> 条）——重排前候选见下方对比
              </div>
            )}
            {result?.hits.map((h, i) => <KbHitView key={i} hit={h} />)}
            {result?.reranked && result.beforeRerank && result.beforeRerank.length > 0 && (
              <>
                <div className="note gray" style={{ margin: '8px 0 2px' }}>↳ 重排前候选（对比）：</div>
                {result.beforeRerank.map((h, i) => <KbHitView key={'b' + i} hit={h} muted />)}
              </>
            )}
          </div>
        </div>
      </div>

      <div className="card" style={{ marginTop: 14 }}>
        <h3>检索未命中队列（{missTotal}）</h3>
        <div className="note gray" style={{ marginBottom: 8 }}>
          检索 0 命中的查询按频次沉淀在这里——补充对应文档后再用同一问题回归，检索准确率就是这么涨的
        </div>
        <div className="kb-scroll">
          {misses.length === 0 && <div className="note gray">暂无未命中记录</div>}
          {misses.map((m, i) => (
            <div key={i} className="kb-doc" style={{ padding: '6px 0' }}>
              <div className="kb-doc-head">
                <span>{m.query}</span>
                <span style={{ display: 'flex', gap: 6, alignItems: 'center' }}>
                  <span className="tag warn">未命中 {m.hits} 次</span>
                  <button className="btn sm" disabled={streamingDraft} onClick={() => void onAiDraft(m.query)}>
                    AI 补文档
                  </button>
                </span>
              </div>
            </div>
          ))}
        </div>
        <Pager page={missPage} pages={missPages} total={missTotal} onJump={setMissPage} />
      </div>
      {/* AI 补文档草稿（v4.6.0）：生成 → 编辑 → 入库 */}
      {aiDraft && (
        <Modal title={`AI 补文档 · ${aiDraft.query}`} onClose={() => setAiDraft(null)}>
          {aiDraft.generating ? (
            <div className="note gray">AI 正在生成候选答案草稿（约 10-30 秒）…</div>
          ) : (
            <form
              onSubmit={async (e) => {
                e.preventDefault()
                try {
                  await createKbDoc(`【AI 补】${aiDraft.query.slice(0, 40)}`, aiDraft.draft)
                  toast('已入库，可用同一问题检索验证')
                  setAiDraft(null)
                  await loadDocs(docPage)
                } catch (err) {
                  toast(err instanceof ApiError ? err.message : '入库失败')
                }
              }}
            >
              <div className="field">
                <label>候选答案草稿（可编辑后入库）</label>
                <textarea
                  rows={14}
                  value={aiDraft.draft}
                  onChange={(e) => setAiDraft({ ...aiDraft, draft: e.target.value })}
                  style={{ fontFamily: 'monospace', fontSize: 12.5 }}
                />
              </div>
              <div className="modal-btns">
                <button type="button" className="btn" onClick={() => setAiDraft(null)}>取消</button>
                <button type="submit" className="btn primary">入库（【AI 补】前缀）</button>
              </div>
            </form>
          )}
        </Modal>
      )}
      <ToastHost />

      {/* 文档详情（v4.5.6）：标题/时间 + 全文 Markdown（多模态图链随文渲染） */}
      {detail && (
        <Modal title="文档详情" onClose={() => setDetail(null)}>
          {detail.loading && <div className="note gray">加载中…</div>}
          {detail.doc && (
            <>
              <div className="field">
                <label>{detail.doc.title}</label>
                <span className="hint">入库时间：{fmtLocal(detail.doc.createdAt, true)}</span>
              </div>
              <div className="kb-detail-body">
                <Markdown text={detail.doc.content || '（空文档）'} />
              </div>
            </>
          )}
        </Modal>
      )}
    </>
  )
}

/** 轻分页器（v4.5.6）：上一页/下一页 + 页码指示；单页时隐藏。 */
function Pager({ page, pages, total, onJump }: {
  page: number
  pages: number
  total: number
  onJump: (p: number) => void
}) {
  if (pages <= 1) return null
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginTop: 10 }}>
      <button className="btn sm" disabled={page <= 1} onClick={() => onJump(page - 1)}>上一页</button>
      <span className="note gray" style={{ margin: 0 }}>
        第 {page} / {pages} 页 · 共 {total} 条
      </span>
      <button className="btn sm" disabled={page >= pages} onClick={() => onJump(page + 1)}>下一页</button>
    </div>
  )
}

/** 单条命中渲染（v4.1：rerank 标签 + 多模态图片缩略）。 */
function KbHitView({ hit, muted }: { hit: KbHit; muted?: boolean }) {
  const sourceTag = hit.source === 'rerank' ? '精排' : hit.source === 'vector' ? '向量' : '关键词'
  const tagClass = hit.source === 'rerank' ? 'ok' : hit.source === 'vector' ? 'info' : 'gray'
  return (
    <div className="kb-hit" style={muted ? { opacity: 0.65 } : undefined}>
      <div className="kb-hit-head">
        <b>{hit.title}</b>
        <span className={`tag ${tagClass}`}>{sourceTag}</span>
      </div>
      <div className="kb-doc-body">{hit.snippet}</div>
      {hit.images && hit.images.length > 0 && (
        <div style={{ display: 'flex', gap: 6, flexWrap: 'wrap', marginTop: 6 }}>
          {hit.images.map((u) => (
            <img key={u} src={u} alt={hit.title}
                 style={{ width: 72, height: 54, objectFit: 'cover', borderRadius: 6, border: '1px solid #e2e6ef' }} />
          ))}
        </div>
      )}
    </div>
  )
}
