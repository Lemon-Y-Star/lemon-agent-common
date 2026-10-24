import { FormEvent, useCallback, useEffect, useState } from 'react'
import { ApiError } from '../api/client'
import {
  createModelConnection,
  deleteModelConnection,
  fetchModelConnections,
  updateModelConnection,
  ModelConnection,
  ModelConnectionsPage,
  ModelConnectionPayload,
} from '../api/modelConnections'

/**
 * 模型连接管理（PRD FR-13）：列表 + 新建 / 编辑 / 删除。
 * 密钥只在表单里"写入"：接口不回传明文，编辑时留空 = 保留原密钥。
 */

interface FormState {
  id: number | null
  name: string
  provider: string
  baseUrl: string
  apiKey: string
  models: string
  allowAuxiliary: boolean
  enabled: boolean
}

const EMPTY_FORM: FormState = {
  id: null,
  name: '',
  provider: 'openai',
  baseUrl: '',
  apiKey: '',
  models: '',
  allowAuxiliary: false,
  enabled: true,
}

export default function ModelConnections() {
  const [page, setPage] = useState<ModelConnectionsPage | null>(null)
  const [form, setForm] = useState<FormState | null>(null)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  const reload = useCallback(async () => {
    try {
      setPage(await fetchModelConnections())
      setError('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载失败，请稍后重试')
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  function openCreate() {
    setForm({ ...EMPTY_FORM })
    setError('')
  }

  function openEdit(conn: ModelConnection) {
    setForm({
      id: conn.id,
      name: conn.name,
      provider: conn.provider,
      baseUrl: conn.baseUrl,
      // 编辑时不回填密钥：留空 = 保留原密钥（PRD 语义：接口永不回传明文）
      apiKey: '',
      models: conn.models.join(', '),
      allowAuxiliary: conn.allowAuxiliary,
      enabled: conn.enabled,
    })
    setError('')
  }

  async function onDelete(conn: ModelConnection) {
    if (!window.confirm(`确认删除连接「${conn.name}」？删除后不可恢复。`)) {
      return
    }
    try {
      await deleteModelConnection(conn.id)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '删除失败，请稍后重试')
    }
  }

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    if (!form) return
    const payload: ModelConnectionPayload = {
      name: form.name.trim(),
      provider: form.provider.trim(),
      baseUrl: form.baseUrl.trim(),
      apiKey: form.apiKey.trim(),
      models: form.models
        .split(/[,，\s]+/)
        .map((s) => s.trim())
        .filter(Boolean),
      allowAuxiliary: form.allowAuxiliary,
      enabled: form.enabled,
    }
    setBusy(true)
    setError('')
    try {
      if (form.id === null) {
        await createModelConnection(payload)
      } else {
        await updateModelConnection(form.id, payload)
      }
      setForm(null)
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '保存失败，请稍后重试')
    } finally {
      setBusy(false)
    }
  }

  return (
    <>
      <h1 className="page-title">模型连接</h1>
      <p className="page-desc muted">
        管理你的个人模型连接（BYOK）。端点主机需在平台白名单内：
        {page ? page.allowedHosts.join('、') : '…'}
      </p>

      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 10 }}>
          <h2>我的连接（{page ? page.connections.length : 0}）</h2>
          <button className="btn primary" onClick={openCreate}>
            ＋ 新建连接
          </button>
        </div>
        <p className="status-line" role="status">
          {error}
        </p>
        <table className="grid">
          <thead>
            <tr>
              <th>名称</th>
              <th>提供方</th>
              <th>端点</th>
              <th>模型</th>
              <th>密钥</th>
              <th>辅助</th>
              <th>状态</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {page?.connections.map((conn) => (
              <tr key={conn.id}>
                <td>{conn.name}</td>
                <td>{conn.provider}</td>
                <td className="muted">{conn.baseUrl}</td>
                <td className="muted">{conn.models.join('、') || '—'}</td>
                <td>
                  {conn.hasSecret ? (
                    <span className="badge info">已配置 ····{conn.secretTail}</span>
                  ) : (
                    <span className="badge plain">未配置</span>
                  )}
                </td>
                <td>{conn.allowAuxiliary ? <span className="badge ok">允许</span> : <span className="badge plain">否</span>}</td>
                <td>
                  {conn.enabled ? <span className="badge ok">启用</span> : <span className="badge off">停用</span>}
                </td>
                <td style={{ whiteSpace: 'nowrap' }}>
                  <button className="btn" onClick={() => openEdit(conn)}>
                    编辑
                  </button>{' '}
                  <button className="btn danger" onClick={() => onDelete(conn)}>
                    删除
                  </button>
                </td>
              </tr>
            ))}
            {page && page.connections.length === 0 && (
              <tr>
                <td colSpan={8} className="muted">
                  还没有个人连接，点击右上角「新建连接」创建。试用阶段可以直接使用平台模型。
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {form && (
        <div className="modal-mask" onClick={() => setForm(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>{form.id === null ? '新建模型连接' : `编辑连接：${form.name}`}</h3>
            <form onSubmit={onSubmit}>
              <div className="field-row">
                <div className="field">
                  <label>连接名称 *</label>
                  <input value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
                </div>
                <div className="field">
                  <label>提供方 *</label>
                  <select value={form.provider} onChange={(e) => setForm({ ...form, provider: e.target.value })}>
                    <option value="openai">openai</option>
                    <option value="deepseek">deepseek</option>
                    <option value="siliconflow">siliconflow</option>
                    <option value="dashscope">dashscope</option>
                    <option value="custom">custom</option>
                  </select>
                </div>
              </div>
              <div className="field">
                <label>端点（OpenAI 兼容，须在白名单内）*</label>
                <input
                  value={form.baseUrl}
                  onChange={(e) => setForm({ ...form, baseUrl: e.target.value })}
                  placeholder="https://api.deepseek.com/v1"
                  required
                />
              </div>
              <div className="field">
                <label>{form.id === null ? 'API 密钥 *' : 'API 密钥（留空 = 保留原密钥）'}</label>
                <input
                  type="password"
                  value={form.apiKey}
                  onChange={(e) => setForm({ ...form, apiKey: e.target.value })}
                  required={form.id === null}
                />
              </div>
              <div className="field">
                <label>可用模型（逗号分隔）</label>
                <input
                  value={form.models}
                  onChange={(e) => setForm({ ...form, models: e.target.value })}
                  placeholder="deepseek-chat, deepseek-reasoner"
                />
              </div>
              <label className="checkbox">
                <input
                  type="checkbox"
                  checked={form.allowAuxiliary}
                  onChange={(e) => setForm({ ...form, allowAuxiliary: e.target.checked })}
                />
                允许辅助功能（路由、摘要、向量）使用此连接
              </label>
              {form.id !== null && (
                <label className="checkbox" style={{ marginTop: 8 }}>
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
