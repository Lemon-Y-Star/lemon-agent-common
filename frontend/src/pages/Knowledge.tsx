import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api/client'
import {
  createKbDoc, deleteKbDoc, fetchKbDocs, fetchKbMisses, searchKb,
  type KbDoc, type KbHit, type KbMissItem, type KbSearchResult,
} from '../api/knowledge'
import { ToastHost, toast } from '../components/Toast'

/** 我的知识库（v3.6）：文档管理 + 检索测试；未配 embedding 时自动降级为关键词检索。 */
export default function Knowledge() {
  const [docs, setDocs] = useState<KbDoc[]>([])
  const [vectorEnabled, setVectorEnabled] = useState(false)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [query, setQuery] = useState('')
  const [result, setResult] = useState<KbSearchResult | null>(null)
  const [misses, setMisses] = useState<KbMissItem[]>([])
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const reload = useCallback(async () => {
    try {
      const [data, missData] = await Promise.all([fetchKbDocs(), fetchKbMisses(20)])
      setDocs(data.docs)
      setVectorEnabled(data.vectorEnabled)
      setMisses(missData.misses)
      setError('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载失败')
    }
  }, [])

  useEffect(() => { void reload() }, [reload])

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
      await reload()
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

          <h3 style={{ marginTop: 18 }}>文档（{docs.length}）</h3>
          <div className="kb-docs">
            {docs.length === 0 && <div className="note gray">还没有文档</div>}
            {docs.map((d) => (
              <div key={d.id} className="kb-doc">
                <div className="kb-doc-head">
                  <b>{d.title}</b>
                  <button className="btn sm danger" onClick={() => void onDelete(d.id)}>删除</button>
                </div>
                <div className="kb-doc-body">{d.content.slice(0, 160)}{d.content.length > 160 ? '…' : ''}</div>
              </div>
            ))}
          </div>
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
          <div className="kb-hits">
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
        <h3>检索未命中队列（{misses.length}）</h3>
        <div className="note gray" style={{ marginBottom: 8 }}>
          检索 0 命中的查询按频次沉淀在这里——补充对应文档后再用同一问题回归，检索准确率就是这么涨的
        </div>
        {misses.length === 0 && <div className="note gray">暂无未命中记录</div>}
        {misses.map((m, i) => (
          <div key={i} className="kb-doc" style={{ padding: '6px 0' }}>
            <div className="kb-doc-head">
              <span>{m.query}</span>
              <span className="tag warn">未命中 {m.hits} 次</span>
            </div>
          </div>
        ))}
      </div>
      <ToastHost />
    </>
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
