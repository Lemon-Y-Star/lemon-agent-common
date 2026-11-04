import { FormEvent, useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api/client'
import {
  createAgent,
  deleteAgent,
  fetchAgents,
  fetchTools,
  optimizePrompt,
  updateAgent,
  type AgentConfig,
  type AgentPage,
  type ToolInfo,
} from '../api/agents'
import { fetchModelConnections, type ModelConnection } from '../api/modelConnections'
import { fetchKbDocs, type KbDoc } from '../api/knowledge'
import {
  fetchWorkflow,
  fetchWorkflows,
  type WorkflowItem,
  type WorkflowVersionItem,
} from '../api/workflow'
import Switch from '../components/Switch'
import { useNavigate } from 'react-router-dom'
import { ToastHost, toast } from '../components/Toast'
import { getShareConfig, setShareConfig, type ShareConfig } from '../api/share'

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
  /** 默认运行策略；对话页跟随此策略发起运行（v4.0 前置到 Agent） */
  strategy: string
  /** 短期记忆窗口轮数（1 轮=一问一答）；null = 默认 10 轮；0 = 不带历史 */
  historyTurns: number | null
  /** 对话开场白（编辑态字符串；空 = 不展示，v4.5） */
  openingStatement: string
  /** 建议问题编辑态：一行一个（空 = 不展示，v4.5） */
  suggestedQuestions: string
  /** 绑定的知识库文档 ID（空 = 不限定范围，v4.5.2） */
  knowledgeDocIds: number[]
  /** 提示词优化进行中（按钮转圈） */
  optimizing?: boolean
  modelConnectionId: number | null
  /** 挂载的工作流；null = 用 Agent 自己的执行图 */
  workflowId: number | null
  /** 挂载的工作流版本；null = 跟随工作流当前生效版本 */
  workflowVersion: number | null
}

const ALL_TOOLS_HINT = '全部工具'

/** 运行策略（与后端 Strategy 枚举同口径）；对话页按 Agent 配置发起，不再手选。 */
const STRATEGIES = [
  { value: 'auto', label: 'auto（自主迭代）' },
  { value: 'plan', label: 'plan（先规划再执行）' },
  { value: 'custom', label: 'custom（按执行图调度）' },
  { value: 'fixed', label: 'fixed（直接问答）' },
]

export default function Agents() {
  const navigate = useNavigate()
  const [page, setPage] = useState<AgentPage | null>(null)
  const [tools, setTools] = useState<ToolInfo[]>([])
  const [connections, setConnections] = useState<ModelConnection[]>([])
  const [kbDocs, setKbDocs] = useState<KbDoc[]>([])
  const [workflows, setWorkflows] = useState<WorkflowItem[]>([])
  const [wfVersions, setWfVersions] = useState<WorkflowVersionItem[]>([])
  const [form, setForm] = useState<FormState | null>(null)
  const [deleting, setDeleting] = useState<AgentConfig | null>(null)
  const [toggling, setToggling] = useState<number | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  // v4.6.0 分享卡片：编辑弹窗打开时拉取当前 Agent 的分享配置
  const [share, setShare] = useState<ShareConfig | null>(null)
  const [shareEnabled, setShareEnabled] = useState(false)
  const [shareAccess, setShareAccess] = useState('')
  const [shareBusy, setShareBusy] = useState(false)

  // 编辑弹窗打开/切换 Agent 时拉取分享配置（v4.6.0）
  useEffect(() => {
    if (form?.id) {
      void getShareConfig(form.id)
        .then((cfg) => {
          setShare(cfg)
          setShareEnabled(cfg.enabled)
          setShareAccess(cfg.accessCode ?? '')
        })
        .catch(() => setShare(null))
    } else {
      setShare(null)
      setShareEnabled(false)
      setShareAccess('')
    }
  }, [form?.id])

  /** 保存分享设置（开关/口令变更）并刷新本地态。 */
  async function onShareSave(enabled: boolean, access: string) {
    if (!form?.id) return
    setShareBusy(true)
    try {
      const cfg = await setShareConfig(form.id, enabled, access)
      setShare(cfg)
      setShareEnabled(cfg.enabled)
      setShareAccess(cfg.accessCode ?? '')
      toast(cfg.enabled ? '分享已开启，链接已生成' : '分享已关闭')
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '分享设置失败')
    } finally {
      setShareBusy(false)
    }
  }

  /** 克隆 Agent（v4.6.0）：成功后刷新列表并提示。 */
  async function onClone(agentId: number, name: string) {
    try {
      const { request } = await import('../api/client')
      await request(`/workspace/agents/${agentId}/clone`, {
        method: 'POST',
        body: JSON.stringify({ name: `${name} 副本` }),
      })
      toast('已克隆')
      await reload()
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '克隆失败')
    }
  }

  /** 导出 Agent 配置 JSON（v4.6.0）：blob 下载。 */
  async function onExport(agentId: number, name: string) {
    try {
      const { request } = await import('../api/client')
      const data = await request<Record<string, unknown>>(`/workspace/agents/${agentId}/export`)
      const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' })
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `agent-${agentId}-${name}.json`
      a.click()
      URL.revokeObjectURL(url)
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '导出失败')
    }
  }

  /** 导入 Agent 配置 JSON（v4.6.0）：绑定类 ID 不迁移，导入后手动重绑。 */
  async function onImportFile(file: File) {
    try {
      const text = await file.text()
      const data = JSON.parse(text)
      const { request } = await import('../api/client')
      await request('/workspace/agents/import', { method: 'POST', body: JSON.stringify(data) })
      toast('导入成功（绑定类配置请在编辑页重绑）')
      await reload()
    } catch (err) {
      toast(err instanceof Error ? err.message : '导入失败（JSON 格式或字段缺失）')
    }
  }

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
    void fetchWorkflows().then((d) => setWorkflows(d.workflows)).catch(() => setWorkflows([]))
    void fetchKbDocs().then((d) => setKbDocs(d.docs)).catch(() => setKbDocs([]))
  }, [reload])

  /** 选中工作流时顺带拉版本列表（用于「指定版本」下拉）。 */
  async function onPickWorkflow(id: number | null, version: number | null = null) {
    setForm((prev) => (prev ? { ...prev, workflowId: id, workflowVersion: version } : prev))
    if (id === null) {
      setWfVersions([])
      return
    }
    try {
      const detail = await fetchWorkflow(id)
      setWfVersions(detail.versions)
    } catch {
      setWfVersions([])
    }
  }

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
        openingStatement: agent.openingStatement ?? '',
        suggestedQuestions: agent.suggestedQuestions ?? [],
        knowledgeDocIds: agent.knowledgeDocIds ?? [],
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
      strategy: form.strategy,
      historyTurns: form.historyTurns,
      openingStatement: form.openingStatement.trim(),
      suggestedQuestions: form.suggestedQuestions
        .split('\n')
        .map((s) => s.trim())
        .filter(Boolean)
        .slice(0, 5),
      knowledgeDocIds: form.knowledgeDocIds,
    }
    setBusy(true)
    setError('')
    try {
      if (form.id === null) {
        await createAgent({
          ...payload,
          modelConnectionId: form.modelConnectionId,
          workflowId: form.workflowId,
          workflowVersion: form.workflowVersion,
        })
      } else {
        await updateAgent(form.id, {
          ...payload,
          modelConnectionId: form.modelConnectionId ?? 0,
          // 后端 null = 保持不变，0 = 取消挂载；表单里「不挂载」就是这个 0
          workflowId: form.workflowId ?? 0,
          workflowVersion: form.workflowVersion ?? 0,
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

  function strategyLabel(agent: AgentConfig) {
    return STRATEGIES.find((s) => s.value === (agent.strategy ?? 'auto'))?.label ?? agent.strategy
  }

  /** 提示词 AI 优化（v4.5）：调模型改写 instruction，结果直接回填（未保存前可改回） */
  async function onOptimize() {
    if (!form || !form.instruction.trim()) {
      setError('先写一版提示词再优化')
      return
    }
    setForm({ ...form, optimizing: true })
    setError('')
    try {
      const optimized = await optimizePrompt(form.instruction.trim())
      setForm((prev) => (prev ? { ...prev, instruction: optimized, optimizing: false } : prev))
      toast('提示词已优化，确认后记得保存')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '优化失败')
      setForm((prev) => (prev ? { ...prev, optimizing: false } : prev))
    }
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
            <th>运行策略</th>
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
                <td style={{ color: 'var(--muted)', fontSize: 12 }}>{strategyLabel(a)}</td>
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
                      onClick={() => {
                        setForm({
                          id: a.id,
                          name: a.name,
                          description: a.description,
                          instruction: a.instruction ?? '',
                          tools: a.tools,
                          strategy: a.strategy ?? 'auto',
                          historyTurns: a.historyTurns,
                          openingStatement: a.openingStatement ?? '',
                          suggestedQuestions: (a.suggestedQuestions ?? []).join('\n'),
                          knowledgeDocIds: a.knowledgeDocIds ?? [],
                          modelConnectionId: a.modelConnectionId,
                          workflowId: a.workflowId,
                          workflowVersion: a.workflowVersion,
                        })
                        if (a.workflowId !== null) {
                          void onPickWorkflow(a.workflowId, a.workflowVersion)
                        } else {
                          setWfVersions([])
                        }
                      }}
                    >
                      编辑
                    </button>{' '}
                    <button className="btn sm danger" onClick={() => setDeleting(a)}>删除</button>
                    <button
                      className="btn sm"
                      title="全字段复制为新 Agent"
                      onClick={() => void onClone(a.id, a.name)}
                    >
                      克隆
                    </button>
                    <button className="btn sm" title="下载配置 JSON" onClick={() => void onExport(a.id, a.name)}>
                      导出
                    </button>{' '}
                    <button className="btn sm" onClick={() => navigate(`/agents/${a.id}/ops`)}>
                      运营
                    </button>{' '}
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
              onClick={() => {
                setForm({
                  id: null, name: '', description: '', instruction: '',
                  tools: null, strategy: 'auto', historyTurns: null,
                  openingStatement: '', suggestedQuestions: '', knowledgeDocIds: [], modelConnectionId: null,
                  workflowId: null, workflowVersion: null,
                })
                setWfVersions([])
              }}
            >
              ＋ 创建 Agent
            </button>
            <label className="btn" style={{ cursor: 'pointer' }}>
              导入 Agent
              <input
                type="file"
                accept=".json,application/json"
                style={{ display: 'none' }}
                onChange={(e) => {
                  const file = e.target.files?.[0]
                  e.target.value = ''
                  if (file) void onImportFile(file)
                }}
              />
            </label>
          </span>
        </h3>
        <p className="status-line" role="status">{error}</p>
        {agentTable(page?.mine ?? [], false)}
      </div>

      {form && (
        <div className="mask" onClick={() => setForm(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()} style={{ width: 720, maxWidth: '94vw' }}>
            <h3>
              {form.id === null ? '创建 Agent' : `编辑 Agent：${form.name}`}
              <span className="x" onClick={() => setForm(null)}>✕</span>
            </h3>
            <form onSubmit={onSubmit}>
              <div className="row">
                <div className="field">
                  <label>名称 *（1..100 字符）</label>
                  <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required maxLength={100} />
                </div>
                <div className="field">
                  <label>描述</label>
                  <input value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} placeholder="一句话说明这个 Agent 干什么" />
                </div>
              </div>
              <div className="field">
                <label>
                  系统提示词 *（1..4000 字符，定义人格与任务）
                  <button
                    type="button"
                    className="btn sm"
                    style={{ marginLeft: 8, verticalAlign: 'middle' }}
                    disabled={form.optimizing}
                    onClick={() => void onOptimize()}
                  >
                    {form.optimizing ? 'AI 优化中…' : '✨ AI 优化'}
                  </button>
                </label>
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
                <label>
                  对话开场白
                  <span className="hint">会话开始时展示的欢迎语；留空 = 不展示，上限 500 字</span>
                </label>
                <textarea
                  value={form.openingStatement}
                  onChange={(e) => setForm({ ...form, openingStatement: e.target.value })}
                  rows={2}
                  maxLength={500}
                  style={{ width: '100%', border: '1px solid #d5d9e2', borderRadius: 7, padding: '8px 10px', fontSize: 13, fontFamily: 'inherit', resize: 'vertical' }}
                  placeholder="你好！我是数据计算助手，任何需要精确计算的问题都可以交给我。"
                />
              </div>

              <div className="field">
                <label>
                  建议问题
                  <span className="hint">一行一个，至多 5 条；开局渲染为可点击引导按钮</span>
                </label>
                <textarea
                  value={form.suggestedQuestions}
                  onChange={(e) => setForm({ ...form, suggestedQuestions: e.target.value })}
                  rows={3}
                  style={{ width: '100%', border: '1px solid #d5d9e2', borderRadius: 7, padding: '8px 10px', fontSize: 13, fontFamily: 'inherit', resize: 'vertical' }}
                  placeholder={'帮我算 100 万房贷 30 年月供\n用 matplotlib 画一个正弦曲线'}
                />
              </div>

              <div className="field">
                <label>
                  AGENT 知识库
                  <span className="hint">勾选后 search_docs 只检索勾选文档；不勾 = 搜全部</span>
                </label>
                {kbDocs.length === 0 ? (
                  <div className="note gray">知识库还没有文档，去「知识库」页上传后即可绑定</div>
                ) : (
                  <div className="kb-checks" style={{ maxHeight: 120, overflow: 'auto', border: '1px solid #d5d9e2', borderRadius: 7, padding: '6px 10px' }}>
                    {kbDocs.map((d) => (
                      <label key={d.id}>
                        <input
                          type="checkbox"
                          checked={form.knowledgeDocIds.includes(d.id)}
                          onChange={(e) => setForm({
                            ...form,
                            knowledgeDocIds: e.target.checked
                              ? [...form.knowledgeDocIds, d.id]
                              : form.knowledgeDocIds.filter((x) => x !== d.id),
                          })}
                        />
                        <span>{d.title}</span>
                      </label>
                    ))}
                  </div>
                )}
              </div>

              {/* v4.6.0 分享卡片：免登录公开对话页（仅已有 Agent 可分享） */}
              {form.id !== null && (
                <div className="field">
                  <label>
                    公开分享
                    <span className="hint">开启后生成免登录对话链接（访客凭链接/口令使用，会话与运行都归你名下可见）</span>
                  </label>
                  <div style={{ display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                    <label style={{ display: 'flex', alignItems: 'center', gap: 4, fontSize: 13 }}>
                      <input
                        type="checkbox"
                        checked={shareEnabled}
                        disabled={shareBusy}
                        onChange={(e) => void onShareSave(e.target.checked, shareAccess)}
                      />
                      开启分享
                    </label>
                    <input
                      value={shareAccess}
                      placeholder="访问口令（可空）"
                      maxLength={12}
                      disabled={shareBusy}
                      style={{ width: 160 }}
                      onBlur={() => { if (shareEnabled) void onShareSave(true, shareAccess) }}
                    />
                    {shareEnabled && share?.shareCode && (
                      <button
                        type="button"
                        className="btn sm"
                        disabled={shareBusy}
                        onClick={() => {
                          const link = `${window.location.origin}/s/${share.shareCode}`
                          void navigator.clipboard?.writeText(link).then(() => toast('分享链接已复制'))
                        }}
                      >
                        复制链接（/s/{share.shareCode}）
                      </button>
                    )}
                  </div>
                </div>
              )}

              <div className="row">
                <div className="field">
                  <label>
                    短期记忆轮数
                    <span className="hint">1 轮 = 一问一答；0 = 只看本轮；留空 = 默认 10 轮</span>
                  </label>
                  <input
                    type="number"
                    min={0}
                    max={50}
                    value={form.historyTurns ?? ''}
                    onChange={(e) => {
                      const v = e.target.value
                      setForm({
                        ...form,
                        historyTurns: v === '' ? null : Math.max(0, Math.min(50, Number(v))),
                      })
                    }}
                    placeholder="默认 10 轮"
                  />
                </div>
                <div className="field">
                  <label>
                    运行策略
                    <span className="hint">对话页按此策略发起运行</span>
                  </label>
                  <select
                    value={form.strategy}
                    onChange={(e) => setForm({ ...form, strategy: e.target.value })}
                  >
                    {STRATEGIES.map((s) => (
                      <option key={s.value} value={s.value}>{s.label}</option>
                    ))}
                  </select>
                </div>
              </div>

              <div className="row">
                <div className="field">
                  <label>模型来源</label>
                  <select
                    value={form.modelConnectionId ?? ''}
                    onChange={(e) => setForm({ ...form, modelConnectionId: e.target.value === '' ? null : Number(e.target.value) })}
                  >
                    <option value="">跟随我的默认连接（最新启用）</option>
                    {connections.map((c) => (
                      <option key={c.id} value={c.id}>{c.name}（{c.models[0] ?? '未配置模型'}）</option>
                    ))}
                  </select>
                </div>
                <div className="field">
                  <label>
                    挂载工作流
                    <span className="hint">custom 跑工作流的图（v3.7）</span>
                  </label>
                  <select
                    value={form.workflowId ?? ''}
                    onChange={(e) => void onPickWorkflow(e.target.value === '' ? null : Number(e.target.value))}
                  >
                    <option value="">不挂载（用我自己的执行图）</option>
                    {workflows.map((w) => (
                      <option key={w.id} value={w.id}>
                        {w.name}（当前 v{w.currentVersion} · {w.versionCount} 个版本）
                      </option>
                    ))}
                  </select>
                </div>
              </div>

              {form.workflowId !== null && (
                <div className="field">
                  <label>工作流版本</label>
                  <select
                    value={form.workflowVersion ?? ''}
                    onChange={(e) => setForm({ ...form, workflowVersion: e.target.value === '' ? null : Number(e.target.value) })}
                    style={{ width: '100%' }}
                  >
                    <option value="">
                      跟随最新（当前 v{wfVersions.find((v) => v.active)?.version ?? 0}）
                    </option>
                    {wfVersions.map((v) => (
                      <option key={v.version} value={v.version}>
                        v{v.version} · {v.nodeCount} 节点{v.note ? ` · ${v.note}` : ''}
                      </option>
                    ))}
                  </select>
                </div>
              )}

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
                        <span className="tool-name" title={t.description}>{t.label || t.name}</span>
                        {t.needsApproval && <span className="tag warn">需审批</span>}
                        {!t.available && <span className="tag gray">不可用</span>}
                        <span className="tool-desc" title={t.description}>{t.description}</span>
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
