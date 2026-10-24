import { FormEvent, useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api/client'
import {
  createMcp,
  deleteMcp,
  fetchMcps,
  updateMcp,
  McpConnection,
  McpConnectionPayload,
} from '../api/mcps'

/**
 * MCP 连接管理（PRD FR-12）：平台公共连接（editable=false）只读展示，
 * 「我的 MCP 连接」区块支持新建 / 编辑 / 删除。
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

function transportBadge(type: string): string {
  if (type === 'STDIO') return '本地进程'
  if (type === 'STREAMABLE') return 'Streamable HTTP'
  return 'SSE'
}

export default function Mcps() {
  const [items, setItems] = useState<McpConnection[]>([])
  const [error, setError] = useState('')
  const [form, setForm] = useState<FormState | null>(null)
  const [busy, setBusy] = useState(false)

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

  async function onDelete(conn: McpConnection) {
    if (!window.confirm(`确认删除 MCP 连接「${conn.name}」？删除后不可恢复。`)) {
      return
    }
    try {
      await deleteMcp(conn.id)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '删除失败，请稍后重试')
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
      <table className="grid">
        <thead>
          <tr>
            <th>名称</th>
            <th>传输</th>
            <th>端点 / 命令</th>
            <th>密钥</th>
            <th>状态</th>
            {readonly || <th>操作</th>}
          </tr>
        </thead>
        <tbody>
          {list.map((conn) => (
            <tr key={conn.id}>
              <td>
                {conn.name}
                {conn.platformPublic && (
                  <>
                    {' '}
                    <span className="badge plain">平台公共</span>
                  </>
                )}
              </td>
              <td>{transportBadge(conn.transportType)}</td>
              <td className="muted">{conn.command ?? conn.url ?? '—'}</td>
              <td>
                {conn.hasSecret ? <span className="badge info">已配置</span> : <span className="badge plain">无</span>}
              </td>
              <td>
                {conn.usable ? (
                  <span className="badge ok">可用</span>
                ) : (
                  <span className="badge off">{conn.unavailableReason ?? '已停用'}</span>
                )}
              </td>
              {readonly || (
                <td style={{ whiteSpace: 'nowrap' }}>
                  <button className="btn" onClick={() => openEdit(conn)}>
                    编辑
                  </button>{' '}
                  <button className="btn danger" onClick={() => onDelete(conn)}>
                    删除
                  </button>
                </td>
              )}
            </tr>
          ))}
          {list.length === 0 && (
            <tr>
              <td colSpan={6} className="muted">
                暂无连接
              </td>
            </tr>
          )}
        </tbody>
      </table>
    )
  }

  return (
    <>
      <h1 className="page-title">MCP 连接</h1>
      <p className="page-desc muted">
        平台公共连接由管理员预置、所有用户可用但不可修改；个人任务所需的自建连接在下方「我的 MCP 连接」维护。
      </p>

      <div className="card">
        <h2>平台公共 MCP（{platform.length} 个，不可编辑）</h2>
        {renderTable(platform, true)}
      </div>

      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
          <h2>我的 MCP 连接（{mine.length}）</h2>
          <button className="btn primary" onClick={openCreate}>
            ＋ 新建连接
          </button>
        </div>
        <p className="status-line" role="status">
          {error}
        </p>
        {renderTable(mine, false)}
      </div>

      {form && (
        <div className="modal-mask" onClick={() => setForm(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>{form.id === null ? '新建 MCP 连接' : `编辑连接：${form.name}`}</h3>
            <form onSubmit={onSubmit}>
              <div className="field-row">
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
                <label className="checkbox">
                  <input
                    type="checkbox"
                    checked={form.enabled}
                    onChange={(e) => setForm({ ...form, enabled: e.target.checked })}
                  />
                  启用该连接
                </label>
              )}
              <p className="status-line" role="status">
                {error}
              </p>
              <div className="modal-actions">
                <button type="button" className="btn" onClick={() => setForm(null)}>
                  取消
                </button>
                <button type="submit" className="btn primary" disabled={busy}>
                  {busy ? '保存中…' : '保存'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </>
  )
}
