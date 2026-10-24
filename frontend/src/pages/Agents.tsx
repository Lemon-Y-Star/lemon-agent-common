import { FormEvent, useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api/client'
import { createAgent, deleteAgent, fetchAgents, updateAgent, type AgentConfig, type AgentPage } from '../api/agents'
import { ToastHost, toast } from '../components/Toast'

/**
 * 我的 Agent（PRD FR-6 最小充分版）：平台公共只读 + 个人自建增删改。
 * Agent = 名称 + 描述 + 系统提示词（+ 可选绑定模型连接）；对话页可选用。
 */

interface FormState {
  id: number | null
  name: string
  description: string
  instruction: string
}

export default function Agents() {
  const [page, setPage] = useState<AgentPage | null>(null)
  const [form, setForm] = useState<FormState | null>(null)
  const [deleting, setDeleting] = useState<AgentConfig | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const reload = useCallback(async () => {
    try {
      setPage(await fetchAgents())
      setError('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载失败')
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  async function onDelete() {
    if (!deleting) return
    try {
      await deleteAgent(deleting.id)
      toast('Agent 已删除')
      setDeleting(null)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '删除失败')
      setDeleting(null)
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!form) return
    const payload = {
      name: form.name.trim(),
      description: form.description.trim(),
      instruction: form.instruction.trim(),
    }
    setBusy(true)
    setError('')
    try {
      if (form.id === null) {
        await createAgent(payload)
      } else {
        await updateAgent(form.id, payload)
      }
      toast(form.id === null ? 'Agent 已创建' : 'Agent 已更新')
      setForm(null)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '保存失败')
    } finally {
      setBusy(false)
    }
  }

  function agentTable(list: AgentConfig[], readonly: boolean) {
    return (
      <table>
        <thead>
          <tr>
            <th>名称</th>
            <th>描述</th>
            <th>状态</th>
            <th>版本</th>
            {readonly || <th>操作</th>}
          </tr>
        </thead>
        <tbody>
          {list.map((a) => {
            const owned = !readonly
            return (
              <tr key={a.id}>
                <td>
                  {a.name}
                  {!owned && <span className="tag gray" style={{ marginLeft: 6 }}>平台公共</span>}
                </td>
                <td style={{ color: 'var(--muted)' }}>{a.description || '—'}</td>
                <td>
                  {a.usable ? <span className="tag ok">启用</span> : <span className="tag err">停用</span>}
                </td>
                <td style={{ color: 'var(--muted)' }}>v{a.version}</td>
                {owned || (
                  <td style={{ whiteSpace: 'nowrap' }}>
                    <button
                      className="btn sm"
                      onClick={() => setForm({ id: a.id, name: a.name, description: a.description, instruction: a.instruction ?? '' })}
                    >
                      编辑
                    </button>{' '}
                    <button className="btn sm danger" onClick={() => setDeleting(a)}>删除</button>
                  </td>
                )}
              </tr>
            )
          })}
          {list.length === 0 && (
            <tr><td colSpan={5}><div className="note gray">暂无</div></td></tr>
          )}
        </tbody>
      </table>
    )
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h2>我的 Agent</h2>
          <div className="desc">
            Agent = 人格（系统提示词）+ 可用工具 + 模型来源。平台公共 Agent 所有人可用但不可修改；
            自建 Agent 在对话页顶部可随时切换。
          </div>
        </div>
      </div>

      <div className="card">
        <h3>
          平台公共 Agent <span className="sub">{page?.platform.length ?? 0} 个 · 不可编辑</span>
        </h3>
        {agentTable(page?.platform ?? [], true)}
      </div>

      <div className="card">
        <h3>
          我的 Agent <span className="sub">{page?.mine.length ?? 0} 个</span>
          <span style={{ marginLeft: 'auto' }}>
            <button className="btn sm primary" onClick={() => setForm({ id: null, name: '', description: '', instruction: '' })}>
              ＋ 创建 Agent
            </button>
          </span>
        </h3>
        <p className="status-line" role="status">{error}</p>
        {agentTable(page?.mine ?? [], false)}
      </div>

      {form && (
        <div className="mask" onClick={() => setForm(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>
              {form.id === null ? '创建 Agent' : `编辑 Agent：${form.name}`}
              <span className="x" onClick={() => setForm(null)}>✕</span>
            </h3>
            <form onSubmit={onSubmit}>
              <div className="field">
                <label>名称 *（1..100 字符）</label>
                <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required maxLength={100} />
              </div>
              <div className="field">
                <label>描述</label>
                <input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="一句话说明这个 Agent 干什么" />
              </div>
              <div className="field">
                <label>系统提示词 *（1..4000 字符，定义人格与任务）</label>
                <textarea
                  value={form.instruction}
                  onChange={(e) => setForm({ ...form, instruction: e.target.value })}
                  rows={6}
                  required
                  style={{ width: '100%', border: '1px solid #d5d9e2', borderRadius: 7, padding: '8px 10px', fontSize: 13, fontFamily: 'inherit', resize: 'vertical' }}
                  placeholder="你是……助手。请用中文……需要创建工单时调用 create_ticket……"
                />
              </div>
              <p className="status-line" role="status">{error}</p>
              <div className="modal-btns">
                <button type="button" className="btn" onClick={() => setForm(null)}>取消</button>
                <button type="submit" className="btn primary" disabled={busy}>
                  {busy ? '保存中…' : '保存'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {deleting && (
        <div className="mask" onClick={() => setDeleting(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>删除 Agent<span className="x" onClick={() => setDeleting(null)}>✕</span></h3>
            <p style={{ fontSize: 13, color: '#4b5563', lineHeight: 1.8 }}>
              确认删除 Agent「{deleting.name}」？删除后对话页将无法再选用它（历史运行不受影响）。
            </p>
            <div className="modal-btns">
              <button className="btn" onClick={() => setDeleting(null)}>取消</button>
              <button className="btn primary" onClick={() => void onDelete()}>删除</button>
            </div>
          </div>
        </div>
      )}
      <ToastHost />
    </>
  )
}
