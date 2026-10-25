import { useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api/client'
import { createMemory, deleteMemory, fetchMemories, toggleMemory, type Memory } from '../api/memory'
import Switch from '../components/Switch'
import { ToastHost, toast } from '../components/Toast'

/** 长期记忆（v3.6）：用户级事实/偏好，运行时自动拼进 system prompt（最多注入 N 条）。 */
export default function MemoryPage() {
  const [items, setItems] = useState<Memory[]>([])
  const [limit, setLimit] = useState(8)
  const [content, setContent] = useState('')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const reload = useCallback(async () => {
    try {
      const data = await fetchMemories()
      setItems(data.items)
      setLimit(data.injectLimit)
      setError('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载失败')
    }
  }, [])

  useEffect(() => { void reload() }, [reload])

  async function onAdd() {
    if (!content.trim()) {
      setError('写一句要记住的事实或偏好')
      return
    }
    setBusy(true)
    try {
      await createMemory(content.trim())
      setContent('')
      toast('已记住')
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '新增失败')
    } finally {
      setBusy(false)
    }
  }

  async function onToggle(m: Memory, on: boolean) {
    try {
      await toggleMemory(m.id, on)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '操作失败')
    }
  }

  async function onDelete(m: Memory) {
    try {
      await deleteMemory(m.id)
      toast('已忘记这条')
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '删除失败')
    }
  }

  const enabledCount = items.filter((m) => m.enabled).length

  return (
    <>
      <div className="page-head">
        <div>
          <h2>长期记忆</h2>
          <div className="desc">
            这里记的是**跨会话**的事实与偏好（比如「我在杭州」「回答尽量短」）。
            每次运行会把你启用的记忆拼到系统提示词末尾——只注入最近 {limit} 条、总量 1200 字以内，
            避免把人格撑爆。停用的记忆不注入但保留。
          </div>
        </div>
      </div>

      {error && <p className="status-line" role="status" style={{ color: 'var(--danger)' }}>{error}</p>}

      <div className="card" style={{ maxWidth: 760 }}>
        <h3>新增一条 <span className="sub">当前生效 {enabledCount} / {items.length} 条</span></h3>
        <div style={{ display: 'flex', gap: 8 }}>
          <input
            value={content}
            onChange={(e) => setContent(e.target.value)}
            onKeyDown={(e) => { if (e.key === 'Enter') void onAdd() }}
            placeholder="例如：我在杭州，回答尽量简洁"
            style={{ flex: 1 }}
          />
          <button className="btn primary" disabled={busy} onClick={() => void onAdd()}>{busy ? '保存中…' : '记住'}</button>
        </div>

        <h3 style={{ marginTop: 18 }}>记忆列表</h3>
        {items.length === 0 && <div className="note gray">还没有记忆。加一条试试，然后在对话里问相关的问题。</div>}
        <div className="mem-list">
          {items.map((m) => (
            <div key={m.id} className={`mem-row ${m.enabled ? '' : 'off'}`}>
              <span className="mem-content">{m.content}</span>
              <span className="mem-actions">
                <Switch on={m.enabled} onChange={(on) => void onToggle(m, on)} />
                <button className="btn sm danger" onClick={() => void onDelete(m)}>删除</button>
              </span>
            </div>
          ))}
        </div>
      </div>
      <ToastHost />
    </>
  )
}
