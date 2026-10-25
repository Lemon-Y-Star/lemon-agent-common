import { FormEvent, useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api/client'
import {
  createAgent,
  deleteAgent,
  fetchAgents,
  fetchTools,
  updateAgent,
  type AgentConfig,
  type AgentPage,
  type ToolInfo,
} from '../api/agents'
import { fetchModelConnections, type ModelConnection } from '../api/modelConnections'
import Switch from '../components/Switch'
import { useNavigate } from 'react-router-dom'
import { ToastHost, toast } from '../components/Toast'

/**
 * 我的 Agent（PRD FR-6 最小充分版 + v3.0 三要素）：
 * 人格（系统提示词）+ 可用工具（白名单）+ 模型来源（绑定连接）；平台公共只读，自建可增删改停启。
 */

interface FormState {
  id: number | null
  name: string
  description: string
  instruction: string
  /** null = 全部工具 */
  tools: string[] | null
  modelConnectionId: number | null
}

const ALL_TOOLS_HINT = '全部工具'

export default function Agents() {
  const navigate = useNavigate()
  const [page, setPage] = useState<AgentPage | null>(null)
  const [tools, setTools] = useState<ToolInfo[]>([])
  const [connections, setConnections] = useState<ModelConnection[]>([])
  const [form, setForm] = useState<FormState | null>(null)
  const [deleting, setDeleting] = useState<AgentConfig | null>(null)
  const [toggling, setToggling] = useState<number | null>(null)
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
    // 工具目录与模型连接只用于表单渲染，失败不阻塞列表
    void fetchTools().then(setTools).catch(() => setTools([]))
    void fetchModelConnections()
      .then((p) => setConnections(p.connections.filter((c) => c.enabled)))
      .catch(() => setConnections([]))
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

  /** 启用 / 停用：带上完整配置（后端 update 是整体覆盖语义）。 */
  async function onToggle(agent: AgentConfig, enabled: boolean) {
    setToggling(agent.id)
    setError('')
    try {
      await updateAgent(agent.id, {
        name: agent.name,
        description: agent.description,
        instruction: agent.instruction ?? '',
        tools: agent.tools,
        modelConnectionId: agent.modelConnectionId ?? 0,
        enabled,
      })
      toast(enabled ? 'Agent 已启用' : 'Agent 已停用（对话页不再可选）')
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '操作失败')
    } finally {
      setToggling(null)
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!form) return
    const payload = {
      name: form.name.trim(),
      description: form.description.trim(),
      instruction: form.instruction.trim(),
      tools: form.tools,
    }
    setBusy(true)
    setError('')
    try {
      if (form.id === null) {
        await createAgent({ ...payload, modelConnectionId: form.modelConnectionId })
      } else {
        await updateAgent(form.id, {
          ...payload,
          modelConnectionId: form.modelConnectionId ?? 0,
        })
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

  function toolsLabel(agent: AgentConfig) {
    if (agent.tools === null) return ALL_TOOLS_HINT
    if (agent.tools.length === 0) return '（无）'
    return agent.tools.join(' / ')
  }

  function modelLabel(agent: AgentConfig) {
    if (agent.modelConnectionId === null) return '跟随默认'
    return connections.find((c) => c.id === agent.modelConnectionId)?.name ?? `#${agent.modelConnectionId}`
  }

  function agentTable(list: AgentConfig[], readonly: boolean) {
    return (
      <table>
        <thead>
          <tr>
            <th>名称</th>
            <th>描述</th>
            <th>可用工具</th>
            <th>模型来源</th>
            <th>状态</th>
            <th>版本</th>
            {!readonly && <th>操作</th>}
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
                  {!owned && a.graphVersion > 0 && (
                    <button
                      className="btn sm"
                      style={{ marginLeft: 6 }}
                      onClick={() => navigate(`/agents/${a.id}/graph`)}
                    >
                      执行图 v{a.graphVersion}
                    </button>
                  )}
                </td>
                <td style={{ color: 'var(--muted)' }}>{a.description || '—'}</td>
                <td style={{ color: 'var(--muted)', fontSize: 12 }}>{toolsLabel(a)}</td>
                <td style={{ color: 'var(--muted)', fontSize: 12 }}>{modelLabel(a)}</td>
                <td>
                  {owned ? (
                    <span style={{ display: 'inline-flex', alignItems: 'center', gap: 6 }}>
                      <Switch on={a.enabled} onChange={(on) => void onToggle(a, on)} />
                      <span style={{ fontSize: 12, color: 'var(--muted)' }}>
                        {toggling === a.id ? '…' : a.enabled ? '启用' : '停用'}
                      </span>
                    </span>
                  ) : a.usable ? (
                    <span className="tag ok">启用</span>
                  ) : (
                    <span className="tag err">停用</span>
                  )}
                </td>
                <td style={{ color: 'var(--muted)' }}>v{a.version}</td>
                {!readonly && (
                  <td style={{ whiteSpace: 'nowrap' }}>
                    <button
                      className="btn sm"
                      onClick={() =>
                        setForm({
                          id: a.id,
                          name: a.name,
                          description: a.description,
                          instruction: a.instruction ?? '',
                          tools: a.tools,
                          modelConnectionId: a.modelConnectionId,
                        })
                      }
                    >
                      编辑
                    </button>{' '}
                    <button className="btn sm danger" onClick={() => setDeleting(a)}>删除</button>
                    <button className="btn sm" onClick={() => navigate(`/agents/${a.id}/graph`)}>
                      执行图{a.graphVersion > 0 ? ` v${a.graphVersion}` : ''}
                    </button>
                  </td>
                )}
              </tr>
            )
          })}
          {list.length === 0 && (
            <tr><td colSpan={readonly ? 6 : 7}><div className="note gray">暂无</div></td></tr>
          )}
        </tbody>
      </table>
    )
  }

  const allToolsChecked = form?.tools === null
  const selectedTools = form?.tools ?? []

  function toggleAllTools(on: boolean) {
    if (!form) return
    setForm({ ...form, tools: on ? null : tools.filter((t) => t.available).map((t) => t.name) })
  }

  function toggleOneTool(name: string, on: boolean) {
    if (!form) return
    const base = form.tools ?? tools.filter((t) => t.available).map((t) => t.name)
    const next = on ? [...base, name] : base.filter((n) => n !== name)
    setForm({ ...form, tools: next })
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h2>我的 Agent</h2>
          <div className="desc">
            Agent = 人格（系统提示词）+ 可用工具 + 模型来源。平台公共 Agent 所有人可用但不可修改；
            自建 Agent 可随时停用，对话页顶部可切换。
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
            <button
              className="btn sm primary"
              onClick={() => setForm({
                id: null, name: '', description: '', instruction: '',
                tools: null, modelConnectionId: null,
              })}
            >
              ＋ 创建 Agent
            </button>
          </span>
        </h3>
        <p className="status-line" role="status">{error}</p>
        {agentTable(page?.mine ?? [], false)}
      </div>

      {form && (
        <div className="mask" onClick={() => setForm(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()} style={{ width: 560, maxWidth: '92vw' }}>
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
                  rows={5}
                  required
                  style={{ width: '100%', border: '1px solid #d5d9e2', borderRadius: 7, padding: '8px 10px', fontSize: 13, fontFamily: 'inherit', resize: 'vertical' }}
                  placeholder="你是……助手。请用中文……需要创建工单时调用 create_ticket……"
                />
              </div>

              <div className="field">
                <label>模型来源</label>
                <select
                  value={form.modelConnectionId ?? ''}
                  onChange={(e) => setForm({ ...form, modelConnectionId: e.target.value === '' ? null : Number(e.target.value) })}
                  style={{ width: '100%' }}
                >
                  <option value="">跟随我的默认连接（最新启用）</option>
                  {connections.map((c) => (
                    <option key={c.id} value={c.id}>{c.name}（{c.models[0] ?? '未配置模型'}）</option>
                  ))}
                </select>
              </div>

              <div className="field">
                <label>
                  可用工具
                  <span className="hint">未勾选的工具不会下发给模型；写操作工具运行时会挂起等审批</span>
                </label>
                <label className="tools-all">
                  <input type="checkbox" checked={allToolsChecked} onChange={(e) => toggleAllTools(e.target.checked)} />
                  <span>全部工具（默认，后续新增工具自动可用）</span>
                </label>
                <div className="tools-box">
                  {tools.map((t) => (
                    <label key={t.name} className={`tool-row ${t.available ? '' : 'off'}`}>
                      <input
                        type="checkbox"
                        checked={allToolsChecked || selectedTools.includes(t.name)}
                        disabled={!t.available || allToolsChecked}
                        onChange={(e) => toggleOneTool(t.name, e.target.checked)}
                      />
                      <span className="tool-info">
                        <span className="tool-name">{t.name}</span>
                        {t.needsApproval && <span className="tag warn">需审批</span>}
                        {!t.available && <span className="tag gray">不可用</span>}
                        <span className="tool-desc">{t.description}</span>
                      </span>
                    </label>
                  ))}
                  {tools.length === 0 && <div className="note gray">工具目录加载失败，保存时保持原设置</div>}
                </div>
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
