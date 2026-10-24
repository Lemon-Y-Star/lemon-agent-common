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
import ConfirmDialog from '../components/ConfirmDialog'
import Switch from '../components/Switch'
import { ToastHost, toast } from '../components/Toast'

/**
 * 模型连接管理（PRD FR-13）：辅助服务来源 + 个人 BYOK 连接增删改查。
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
  const [deleting, setDeleting] = useState<ModelConnection | null>(null)
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

  async function onDelete() {
    if (!deleting) return
    try {
      await deleteModelConnection(deleting.id)
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
      toast(form.id === null ? '连接已创建' : '连接已更新')
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
      <div className="page-head">
        <div>
          <h2>模型连接</h2>
          <div className="desc">
            每个节点独立选择模型来源：平台模型（PLATFORM）或个人连接（USER_BYOK）。
            保存后凭据加密落库、不回显。
          </div>
        </div>
      </div>

      <div className="card">
        <h3>
          辅助服务与费用来源 <span className="sub">辅助来源：{page?.auxiliaryProviderSource ?? 'PLATFORM'}</span>
        </h3>
        <table>
          <thead>
            <tr>
              <th>用途</th>
              <th>来源</th>
              <th>是否允许平台承担</th>
            </tr>
          </thead>
          <tbody>
            {(page?.auxiliaryPurposes ?? []).map((purpose) => (
              <tr key={purpose}>
                <td>{purpose}</td>
                <td>
                  <span className="tag info">PLATFORM</span>
                </td>
                <td>
                  <span className="tag ok">允许</span>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        <div className="note blue">
          允许的个人连接主机：
          {(page?.allowedHosts ?? []).map((host) => (
            <code key={host} style={{ margin: '0 6px 0 0' }}>
              {host}
            </code>
          ))}
          。保存后不回显凭据。
        </div>
      </div>

      <div className="card">
        <h3>
          我的连接 <span className="sub">{page?.connections.length ?? 0} 条</span>
          <span style={{ marginLeft: 'auto' }}>
            <button className="btn sm primary" onClick={openCreate}>
              ＋ 添加连接
            </button>
          </span>
        </h3>
        <p className="status-line" role="status">
          {error}
        </p>
        <table>
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
            {(page?.connections ?? []).map((conn) => (
              <tr key={conn.id}>
                <td>{conn.name}</td>
                <td>{conn.provider}</td>
                <td className="muted" style={{ color: 'var(--muted)' }}>
                  {conn.baseUrl}
                </td>
                <td style={{ color: 'var(--muted)' }}>{conn.models.join('、') || '—'}</td>
                <td>
                  {conn.hasSecret ? (
                    <span className="tag info">已配置 ····{conn.secretTail}</span>
                  ) : (
                    <span className="tag gray">未配置</span>
                  )}
                </td>
                <td>
                  {conn.allowAuxiliary ? <span className="tag ok">允许</span> : <span className="tag gray">否</span>}
                </td>
                <td>
                  {conn.enabled ? <span className="tag ok">启用</span> : <span className="tag err">停用</span>}
                </td>
                <td style={{ whiteSpace: 'nowrap' }}>
                  <button className="btn sm" onClick={() => openEdit(conn)}>
                    编辑
                  </button>{' '}
                  <button className="btn sm danger" onClick={() => setDeleting(conn)}>
                    删除
                  </button>
                </td>
              </tr>
            ))}
            {page && page.connections.length === 0 && (
              <tr>
                <td colSpan={8}>
                  <div className="note gray">暂无个人连接。试用阶段可以直接使用平台模型，按需再添加。</div>
                </td>
              </tr>
            )}
          </tbody>
        </table>
      </div>

      {form && (
        <div className="mask" onClick={() => setForm(null)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <h3>
              {form.id === null ? '添加个人连接' : `编辑连接：${form.name}`}
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
                  <label>提供方</label>
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
                <label>{form.id === null ? 'API Key *' : 'API Key（留空 = 保留原密钥）'}</label>
                <input
                  type="password"
                  value={form.apiKey}
                  onChange={(e) => setForm({ ...form, apiKey: e.target.value })}
                  placeholder="sk-••••••••"
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
              <div className="switch-row">
                <Switch on={form.allowAuxiliary} onChange={(on) => setForm({ ...form, allowAuxiliary: on })} />
                允许辅助功能（路由、摘要、向量）使用此连接
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

      {deleting && (
        <ConfirmDialog
          title="删除连接"
          message={`确认删除连接「${deleting.name}」？删除后不可恢复，绑定该连接的节点将回退为平台模型。`}
          confirmText="删除"
          onConfirm={onDelete}
          onCancel={() => setDeleting(null)}
        />
      )}
      <ToastHost />
    </>
  )
}
