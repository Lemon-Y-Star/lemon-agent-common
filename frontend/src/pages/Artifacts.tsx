import { useCallback, useEffect, useState } from 'react'
import { fetchArtifacts, submitFeedback, type ArtifactItem } from '../api/artifacts'
import { ApiError } from '../api/client'
import { ToastHost, toast } from '../components/Toast'
import Markdown from '../components/Markdown'
import { stripThink } from '../components/ThinkingPanel'

/**
 * 产物与反馈（v3.8，FR-17 最小版）：我的有产出运行 + 运行级反馈。
 * 产物实时读运行（不另存事实）；反馈一人一运行一条，改评覆盖旧评。
 */

const fmt = (s: string | null | undefined) => (s ? s.slice(0, 19).replace('T', ' ') : '—')

function ArtifactCard({ item, onChanged }: { item: ArtifactItem; onChanged: () => void }) {
  const [comment, setComment] = useState(item.myComment ?? '')
  const [busy, setBusy] = useState(false)

  async function rate(rating: 'UP' | 'DOWN') {
    setBusy(true)
    try {
      await submitFeedback(item.runId, rating, comment)
      toast(rating === 'UP' ? '已赞，感谢反馈' : '已点踩，反馈已记录')
      onChanged()
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '提交失败')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="card" style={{ marginBottom: 12 }}>
      <div className="artifact-head">
        <b>运行 #{item.runId}</b>
        <span className="tag ok">已完成</span>
        <span style={{ color: 'var(--muted)', fontSize: 12 }}>{fmt(item.endedAt)}</span>
        <span style={{ marginLeft: 'auto', color: 'var(--muted)', fontSize: 12 }}>
          tokens {item.inputTokens ?? 0} / {item.outputTokens ?? 0}
        </span>
      </div>
      {item.output ? (
        <div className="eval-out">
          <Markdown text={stripThink(item.output)} />
        </div>
      ) : (
        <div className="note gray">（无产出）</div>
      )}
      <div className="artifact-feedback">
        <button
          className={`btn sm ${item.myRating === 'UP' ? 'primary' : ''}`}
          disabled={busy}
          onClick={() => void rate('UP')}
        >👍 好</button>
        <button
          className={`btn sm ${item.myRating === 'DOWN' ? 'danger' : ''}`}
          disabled={busy}
          onClick={() => void rate('DOWN')}
        >👎 差</button>
        <input
          value={comment}
          onChange={(e) => setComment(e.target.value)}
          placeholder="补充一句反馈（可选，随点赞/点踩一起保存）"
          style={{ flex: 1 }}
        />
        {item.myRating && (
          <span className="tag gray">已反馈：{item.myRating === 'UP' ? '好' : '差'}</span>
        )}
      </div>
    </div>
  )
}

export default function Artifacts() {
  const [items, setItems] = useState<ArtifactItem[]>([])
  const [total, setTotal] = useState(0)
  const [page, setPage] = useState(1)
  const [error, setError] = useState('')

  const reload = useCallback(async (p: number) => {
    try {
      const data = await fetchArtifacts(p, 10)
      setItems((prev) => (p === 1 ? data.items : [...prev, ...data.items]))
      setTotal(data.total)
      setPage(p)
      setError('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载失败')
    }
  }, [])

  useEffect(() => {
    void reload(1)
  }, [reload])

  return (
    <>
      <div className="page-head">
        <div>
          <h2>产物与反馈</h2>
          <div className="desc">
            有最终产出的运行就是产物（实时读运行，不另存一份事实）。给产物点好/差并留言，
            反馈挂在运行上——这是后续做「按反馈回归」的数据底座。
          </div>
        </div>
      </div>
      <p className="status-line" role="status">{error}</p>

      {items.map((a) => (
        <ArtifactCard key={a.runId} item={a} onChanged={() => void reload(1)} />
      ))}
      {items.length === 0 && (
        <div className="card"><div className="note gray">还没有产物——去对话页跑出第一个结果吧。</div></div>
      )}
      {items.length < total && (
        <div style={{ textAlign: 'center', margin: '8px 0 16px' }}>
          <button className="btn" onClick={() => void reload(page + 1)}>
            加载更多（{items.length}/{total}）
          </button>
        </div>
      )}
      <ToastHost />
    </>
  )
}
