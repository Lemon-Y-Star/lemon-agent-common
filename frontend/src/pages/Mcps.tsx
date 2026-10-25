import { FormEvent, useCallback, useEffect, useState } from 'react'
import { ApiError, getStoredUser } from '../api/client'
import {
  adminCreateMcp,
  adminUpdateMcp,
  createMcp,
  deleteMcp,
  fetchMcps,
  updateMcp,
  McpConnection,
  McpConnectionPayload,
} from '../api/mcps'
import ConfirmDialog from '../components/ConfirmDialog'
import Switch from '../components/Switch'
import { ToastHost, toast } from '../components/Toast'

/**
 * MCP 连接管理（PRD FR-12）：平台公共连接（editable=false）对普通用户只读展示，
 * 「我的 MCP 连接」区块支持新建 / 编辑 / 删除。
 * v3.9 起 admin 可在页面内直接编辑平台公共连接（名称/端点/启停）并新增平台连接（密文不出库）。
 */

interface FormState {
  id: number | null
  name: string
  transportType: string
  url: string
  command: string
  secret: string
  enabled: boolean
}

const EMPTY_FORM: FormState = {
  id: null,
  name: '',
  transportType: 'SSE',
  url: '',
  command: '',
  secret: '',
  enabled: true,
}

function transportTag(type: string): { text: string; cls: string } {
  if (type === 'STDIO') return { text: 'STDIO · 本地进程', cls: 'gray' }
  if (type === 'STREAMABLE') return { text: 'STREAMABLE', cls: 'info' }
  return { text: 'SSE', cls: 'info' }
}

export default function Mcps() {
  const [items, setItems] = useState<McpConnection[]>([])
  const [error, setError] = useState('')
  const [form, setForm] = useState<FormState | null>(null)
  const [deleting, setDeleting] = useState<McpConnection | null>(null)
  const [busy, setBusy] = useState(false)
  const [adminForm, setAdminForm] = useState<FormState | null>(null)
  const isAdmin = getStoredUser()?.role === 'admin'

  const reload = useCallback(async () => {
    try {
      setItems(await fetchMcps())
      setError('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载失败，请稍后重试')
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  const platform = items.filter((m) => m.platformPublic)
  const mine = items.filter((m) => !m.platformPublic)

  function openCreate() {
    setForm({ ...EMPTY_FORM })
    setError('')
  }

  function openEdit(conn: McpConnection) {
    setForm({
      id: conn.id,
      name: conn.name,
      transportType: conn.transportType,
      url: conn.url ?? '',
      command: conn.command ?? '',
      secret: '',
      enabled: conn.enabled,
    })
    setError('')
  }

  /** admin：编辑平台公共连接（密文不出库，留空即保留）。 */
  function openAdminEdit(conn: McpConnection) {
    setAdminForm({
      id: conn.id,
      name: conn.name,
      transportType: conn.transportType,
      url: conn.url ?? '',
      command: conn.command ?? '',
      secret: '',
      enabled: conn.enabled,
    })
    setError('')
  }

  function openAdminCreate() {
    setAdminForm({ ...EMPTY_FORM })
    setError('')
  }

  async function onAdminSubmit(e: FormEvent) {
    e.preventDefault()
    if (!adminForm) return
    const payload = {
      name: adminForm.name.trim(),
      transportType: adminForm.transportType,
      url: adminForm.transportType === 'STDIO' ? undefined : adminForm.url.trim(),
      command: adminForm.transportType === 'STDIO' ? adminForm.command.trim() : undefined,
      status: adminForm.enabled ? 1 : 0,
    }
    setBusy(true)
    setError('')
    try {
      if (adminForm.id === null) {
        await adminCreateMcp(payload)
        toast('平台公共连接已创建')
      } else {
        await adminUpdateMcp(adminForm.id, payload)
        toast('平台公共连接已更新（对所有用户生效）')
      }
      setAdminForm(null)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '保存失败，请稍后重试')
    } finally {
      setBusy(false)
    }
  }

  async function onDelete() {
    if (!deleting) return
    try {
      await deleteMcp(deleting.id)
      toast('连接已删除')
      setDeleting(null)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '删除失败，请稍后重试')
      setDeleting(null)
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!form) return
    const payload: McpConnectionPayload = {
      name: form.name.trim(),
      transportType: form.transportType,
      url: form.transportType === 'STDIO' ? undefined : form.url.trim(),
      command: form.transportType === 'STDIO' ? form.command.trim() : undefined,
      secret: form.secret.trim() || undefined,
      enabled: form.enabled,
    }
    setBusy(true)
    setError('')
    try {
      if (form.id === null) {
        await createMcp(payload)
      } else {
        await updateMcp(form.id, payload)
      }
      toast(form.id === null ? '连接已创建' : '连接已更新')
      setForm(null)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '保存失败，请稍后重试')
    } finally {
      setBusy(false)
    }
  }

  function renderTable(list: McpConnection[], readonly: boolean) {
    return (
      <table>
        <thead>
          <tr>
            <th>名称</th>
            <th>传输</th>
            <th>端点 / 命令</th>
            <th>密钥</th>
            <th>状态</th>
            <th>版本</th>
            {readonly ? (isAdmin && <th>管理</th>) : <th>操作</th>}
          </tr>
        </thead>
        <tbody>
          {list.map((conn) => {
            const t = transportTag(conn.transportType)
            return (
              <tr key={conn.id}>
                <td>{conn.name}</td>
                <td>
                  <span className={`tag ${t.cls}`}>{t.text}</span>
                </td>
                <td style={{ color: 'var(--muted)', wordBreak: 'break-all' }}>
                  {conn.command ?? conn.url ?? '—'}
                </td>
                <td>
                  {conn.hasSecret ? <span className="tag info">已配置</span> : <span className="tag gray">无</span>}
                </td>
                <td>
                  {conn.usable ? (
                    <span className="tag ok">可用</span>
                  ) : (
                    <span className="tag err">{conn.unavailableReason ?? '已停用'}</span>
                  )}
                </td>
                <td style={{ color: 'var(--muted)' }}>v{conn.version}</td>
                {readonly ? (
                  isAdmin && (
                    <td style={{ whiteSpace: 'nowrap' }}>
                      <button className="btn sm" onClick={() => openAdminEdit(conn)}>管理编辑</button>
                    </td>
                  )
                ) : (
                  <td style={{ whiteSpace: 'nowrap' }}>
                    <button className="btn sm" onClick={() => openEdit(conn)}>
                      编辑
                    </button>{' '}
                    <button className="btn sm danger" onClick={() => setDeleting(conn)}>
                      删除
                    </button>
                  </td>
                )}
              </tr>
            )
          })}
          {list.length === 0 && (
            <tr>
              <td colSpan={7}>
                <div className="note gray">暂无连接</div>
              </td>
            </tr>
          )}
        </tbody>
      </table>
    )
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h2>MCP 连接与工具</h2>
          <div className="desc">
            平台公共连接由管理员预置、所有用户可用但不可修改（unavailableReason 展示不可用原因）；
            个人任务所需的自建连接在「我的 MCP 连接」维护，配置变更版本 +1。
          </div>
        </div>
      </div>

      <div className="card">
        <h3>
          平台公共 MCP{' '}
          <span className="sub">
            {platform.length} 个 · {isAdmin ? '管理员可编辑（对所有用户生效）' : '不可编辑'}
          </span>
          {isAdmin && (
            <span style={{ marginLeft: 'auto' }}>
              <button className="btn sm primary" onClick={openAdminCreate}>＋ 新增平台连接</button>
            </span>
          )}
        </h3>
        {renderTable(platform, true)}
      </div>

      <div className="card">
        <h3>
          我的 MCP 连接 <span className="sub">{mine.length} 条</span>
          <span style={{ marginLeft: 'auto' }}>
            <button className="btn sm primary" onClick={openCreate}>
              ＋ 添加连接
            </button>
          </span>
        </h3>
        <p className="status-line" role="status">
          {error}
        </p>
        {renderTable(mine, false)}
      </div>

      {form && (
        <div className="mask" onClick={() => setForm(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>
              {form.id === null ? '添加 MCP 连接' : `编辑连接：${form.name}`}
              <span className="x" onClick={() => setForm(null)}>
                ✕
              </span>
            </h3>
            <form onSubmit={onSubmit}>
              <div className="row">
                <div className="field">
                  <label>连接名称 *</label>
                  <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
                </div>
                <div className="field">
                  <label>传输类型 *</label>
                  <select
                    value={form.transportType}
                    onChange={(e) => setForm({ ...form, transportType: e.target.value })}
                  >
                    <option value="SSE">SSE</option>
                    <option value="STREAMABLE">Streamable HTTP</option>
                    <option value="STDIO">STDIO（本地进程）</option>
                  </select>
                </div>
              </div>
              {form.transportType === 'STDIO' ? (
                <div className="field">
                  <label>启动命令 *</label>
                  <input
                    value={form.command}
                    onChange={(e) => setForm({ ...form, command: e.target.value })}
                    placeholder="uvx weather-mcp"
                    required
                  />
                </div>
              ) : (
                <div className="field">
                  <label>HTTP 端点 *</label>
                  <input
                    value={form.url}
                    onChange={(e) => setForm({ ...form, url: e.target.value })}
                    placeholder="https://mcp.example.com/sse"
                    required
                  />
                </div>
              )}
              <div className="field">
                <label>{form.id === null ? '密钥（可选）' : '密钥（留空 = 保留原密钥）'}</label>
                <input type="password" value={form.secret} onChange={(e) => setForm({ ...form, secret: e.target.value })} />
              </div>
              {form.id !== null && (
                <div className="switch-row">
                  <Switch on={form.enabled} onChange={(on) => setForm({ ...form, enabled: on })} />
                  启用该连接
                </div>
              )}
              <p className="status-line" role="status">
                {error}
              </p>
              <div className="modal-btns">
                <button type="button" className="btn" onClick={() => setForm(null)}>
                  取消
                </button>
                <button type="submit" className="btn primary" disabled={busy}>
                  {busy ? '保存中…' : '保存连接'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {adminForm && (
        <div className="mask" onClick={() => setAdminForm(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>
              {adminForm.id === null ? '新增平台公共连接' : `管理编辑：${adminForm.name}`}
              <span className="x" onClick={() => setAdminForm(null)}>✕</span>
            </h3>
            <div className="note gray" style={{ marginBottom: 8 }}>
              管理端直改平台行，对所有用户生效；密钥密文不出库、不在此展示（需要鉴权的端点请预置密文或走迁移）。
            </div>
            <form onSubmit={onAdminSubmit}>
              <div className="row">
                <div className="field">
                  <label>连接名称 *</label>
                  <input value={adminForm.name} onChange={(e) => setAdminForm({ ...adminForm, name: e.target.value })} required />
                </div>
                <div className="field">
                  <label>传输类型</label>
                  <select
                    value={adminForm.transportType}
                    onChange={(e) => setAdminForm({ ...adminForm, transportType: e.target.value })}
                  >
                    <option value="SSE">SSE</option>
                    <option value="STREAMABLE">Streamable HTTP</option>
                    <option value="STDIO">STDIO（本地进程）</option>
                  </select>
                </div>
              </div>
              {adminForm.transportType === 'STDIO' ? (
                <div className="field">
                  <label>启动命令</label>
                  <input value={adminForm.command} onChange={(e) => setAdminForm({ ...adminForm, command: e.target.value })} placeholder="uvx weather-mcp" />
                </div>
              ) : (
                <div className="field">
                  <label>HTTP 端点 *</label>
                  <input value={adminForm.url} onChange={(e) => setAdminForm({ ...adminForm, url: e.target.value })} placeholder="https://mcp.example.com/sse" required />
                </div>
              )}
              <div className="switch-row">
                <Switch on={adminForm.enabled} onChange={(on) => setAdminForm({ ...adminForm, enabled: on })} />
                启用该连接（停用后所有用户不可用）
              </div>
              <p className="status-line" role="status">{error}</p>
              <div className="modal-btns">
                <button type="button" className="btn" onClick={() => setAdminForm(null)}>取消</button>
                <button type="submit" className="btn primary" disabled={busy}>
                  {busy ? '保存中…' : '保存'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {deleting && (
        <ConfirmDialog
          title="删除 MCP 连接"
          message={`确认删除 MCP 连接「${deleting.name}」？删除后不可恢复，绑定它的节点将无法再调用该服务。`}
          confirmText="删除"
          onConfirm={onDelete}
          onCancel={() => setDeleting(null)}
        />
      )}
      <ToastHost />
    </>
  )
}
