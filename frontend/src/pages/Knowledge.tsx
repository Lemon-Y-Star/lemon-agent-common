import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api/client'
import { createKbDoc, deleteKbDoc, fetchKbDocs, searchKb, type KbDoc, type KbHit } from '../api/knowledge'
import { ToastHost, toast } from '../components/Toast'

/** 我的知识库（v3.6）：文档管理 + 检索测试；未配 embedding 时自动降级为关键词检索。 */
export default function Knowledge() {
  const [docs, setDocs] = useState<KbDoc[]>([])
  const [vectorEnabled, setVectorEnabled] = useState(false)
  const [title, setTitle] = useState('')
  const [content, setContent] = useState('')
  const [query, setQuery] = useState('')
  const [hits, setHits] = useState<KbHit[] | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const reload = useCallback(async () => {
    try {
      const data = await fetchKbDocs()
      setDocs(data.docs)
      setVectorEnabled(data.vectorEnabled)
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
      setHits(await searchKb(query.trim(), 5))
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
            {hits === null && <div className="note gray">检索结果会显示在这里，并标注命中来源</div>}
            {hits?.length === 0 && <div className="note gray">没有命中——换个关键词，或先入库文档</div>}
            {hits?.map((h, i) => (
              <div key={i} className="kb-hit">
                <div className="kb-hit-head">
                  <b>{h.title}</b>
                  <span className={`tag ${h.source === 'vector' ? 'info' : 'gray'}`}>
                    {h.source === 'vector' ? '向量' : '关键词'}
                  </span>
                </div>
                <div className="kb-doc-body">{h.snippet}</div>
              </div>
            ))}
          </div>
        </div>
      </div>
      <ToastHost />
    </>
  )
}
