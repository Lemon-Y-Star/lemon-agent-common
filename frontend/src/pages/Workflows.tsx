import { FormEvent, useCallback, useEffect, useState } from 'react'
import { fetchAgents, type AgentConfig } from '../api/agents'
import { ApiError } from '../api/client'
import {
  createWorkflow,
  fetchWorkflow,
  fetchWorkflows,
  saveWorkflowVersion,
  type WorkflowDetail,
  type WorkflowItem,
} from '../api/workflow'
import { ToastHost, toast } from '../components/Toast'

/**
 * 我的工作流（v3.7，PRD FR-25 最小闭环）。
 *
 * 工作流是与 Agent 解耦的独立实体：一份版本化图定义可被多个 Agent 挂载。
 * 建法只有一条——从某个 Agent 的当前执行图「另存」，这样图的来源始终可追溯；
 * 之后改 Agent 自己的图不影响工作流，反之亦然。
 */

export default function Workflows() {
  const [items, setItems] = useState<WorkflowItem[]>([])
  const [agents, setAgents] = useState<AgentConfig[]>([])
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const [sourceAgentId, setSourceAgentId] = useState<number | null>(null)

  const [detail, setDetail] = useState<WorkflowDetail | null>(null)
  const [versionAgent, setVersionAgent] = useState<Record<number, number>>({})

  const reload = useCallback(async () => {
    try {
      const [wf, ag] = await Promise.all([fetchWorkflows(), fetchAgents()])
      setItems(wf.workflows)
      setAgents(ag.mine)
      setError('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载失败')
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  async function onCreate(e: FormEvent) {
    e.preventDefault()
    const title = name.trim()
    if (!title) return
    setBusy(true)
    setError('')
    try {
      const created = await createWorkflow({
        name: title,
        description: description.trim() || undefined,
        sourceAgentId,
      })
      toast(sourceAgentId ? `已创建并另存为 v${created.currentVersion}` : '工作流已创建（还没有版本）')
      setName('')
      setDescription('')
      setSourceAgentId(null)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '创建失败')
    } finally {
      setBusy(false)
    }
  }

  async function openDetail(id: number) {
    try {
      setDetail(await fetchWorkflow(id))
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载详情失败')
    }
  }

  async function onSaveVersion(workflowId: number) {
    const source = versionAgent[workflowId]
    if (!source) {
      setError('先选一个来源 Agent（把它的当前执行图另存为新版本）')
      return
    }
    setBusy(true)
    setError('')
    try {
      const updated = await saveWorkflowVersion(workflowId, { sourceAgentId: source })
      toast(`已保存 v${updated.currentVersion}`)
      setDetail(updated)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '保存版本失败')
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h2>工作流</h2>
          <div className="desc">
            独立实体：一份版本化的图，可被多个 Agent 挂载（在 Agent 编辑里选）。
            建法是从某个 Agent 的当前执行图「另存」——之后两边互不影响。
          </div>
        </div>
      </div>

      <div className="card">
        <h3>新建工作流</h3>
        <form onSubmit={onCreate} style={{ display: 'grid', gap: 10, maxWidth: 640 }}>
          <div className="field">
            <label>名称 *</label>
            <input value={name} onChange={(e) => setName(e.target.value)} required placeholder="例如：工单分诊流程" />
          </div>
          <div className="field">
            <label>描述</label>
            <input value={description} onChange={(e) => setDescription(e.target.value)} placeholder="一句话说明这个工作流干什么" />
          </div>
          <div className="field">
            <label>
              来源 Agent（可选）
              <span className="hint">选了就把该 Agent 的当前执行图另存为工作流 v1；不选则建空工作流</span>
            </label>
            <select
              value={sourceAgentId ?? ''}
              onChange={(e) => setSourceAgentId(e.target.value === '' ? null : Number(e.target.value))}
              style={{ width: '100%' }}
            >
              <option value="">（不选）建空工作流</option>
              {agents.map((a) => (
                <option key={a.id} value={a.id}>
                  {a.name}
                  {a.graphVersion > 0 ? `（执行图 v${a.graphVersion}）` : '（还没有执行图）'}
                </option>
              ))}
            </select>
          </div>
          <div>
            <button className="btn primary" disabled={busy || !name.trim()}>{busy ? '提交中…' : '＋ 创建'}</button>
          </div>
        </form>
        <p className="status-line" role="status">{error}</p>
      </div>

      <div className="card">
        <h3>我的工作流 <span className="sub">{items.length} 个</span></h3>
        {items.length === 0 ? (
          <div className="note gray">还没有工作流。到某个 Agent 的执行图里点「另存为工作流」最快。</div>
        ) : (
          <table>
            <thead>
              <tr>
                <th>名称</th>
                <th>描述</th>
                <th>当前版本</th>
                <th>版本数</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {items.map((w) => (
                <tr key={w.id}>
                  <td>
                    <b>{w.name}</b>
                    {!w.enabled && <span className="tag gray" style={{ marginLeft: 6 }}>停用</span>}
                  </td>
                  <td style={{ color: 'var(--muted)', fontSize: 12 }}>{w.description || '—'}</td>
                  <td>{w.currentVersion > 0 ? `v${w.currentVersion}` : '—'}</td>
                  <td>{w.versionCount}</td>
                  <td style={{ whiteSpace: 'nowrap' }}>
                    <button className="btn sm" onClick={() => void openDetail(w.id)}>版本</button>{' '}
                    <select
                      value={versionAgent[w.id] ?? ''}
                      onChange={(e) => setVersionAgent({ ...versionAgent, [w.id]: Number(e.target.value) })}
                      style={{ maxWidth: 160 }}
                    >
                      <option value="">选来源 Agent…</option>
                      {agents.map((a) => (
                        <option key={a.id} value={a.id}>{a.name}</option>
                      ))}
                    </select>{' '}
                    <button className="btn sm" disabled={busy} onClick={() => void onSaveVersion(w.id)}>
                      另存新版本
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {detail && (
        <div className="mask" onClick={() => setDetail(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()} style={{ width: 560, maxWidth: '92vw' }}>
            <h3>
              工作流「{detail.name}」的版本
              <span className="x" onClick={() => setDetail(null)}>✕</span>
            </h3>
            {detail.versions.length === 0 ? (
              <div className="note gray">还没有版本。选一个来源 Agent 点「另存新版本」。</div>
            ) : (
              <table>
                <thead>
                  <tr><th>版本</th><th>说明</th><th>节点数</th><th>状态</th></tr>
                </thead>
                <tbody>
                  {detail.versions.map((v) => (
                    <tr key={v.version}>
                      <td>v{v.version}</td>
                      <td style={{ fontSize: 12 }}>{v.note || '—'}</td>
                      <td>{v.nodeCount}</td>
                      <td>{v.active ? <span className="tag ok">生效中</span> : <span className="tag gray">历史</span>}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
            <div className="note gray" style={{ marginTop: 10 }}>
              挂载在 Agent 上才会跑；挂载入口在「我的 Agent → 编辑 → 挂载工作流」。
            </div>
          </div>
        </div>
      )}

      <ToastHost />
    </>
  )
}
