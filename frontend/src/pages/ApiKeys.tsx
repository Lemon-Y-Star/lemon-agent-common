import { useCallback, useEffect, useState } from 'react'
import { createApiKey, fetchApiKeys, revokeApiKey, type ApiKeyItem } from '../api/apikeys'
import { ApiError } from '../api/client'
import { ToastHost, toast } from '../components/Toast'

/**
 * API 分发（v3.8，FR-24 最小版）：API 密钥管理 + 开放端点调用文档。
 * 库里只存哈希，明文只在创建时出现一次；吊销即时失效。
 */

const fmt = (s: string | null | undefined) => (s ? s.slice(0, 19).replace('T', ' ') : '—')

const CURL_SAMPLE = (agentId: number, wait = 60) => `curl -X POST http://127.0.0.1:18090/api/v1/open/agents/${agentId}/invoke \\
  -H "Authorization: Bearer sk-你的完整密钥" \\
  -H "Content-Type: application/json" \\
  -d '{"input":"现在几点了？","strategy":"auto","waitSeconds":${wait}}'`

export default function ApiKeys() {
  const [keys, setKeys] = useState<ApiKeyItem[]>([])
  const [name, setName] = useState('')
  const [created, setCreated] = useState<{ id: number; name: string; key: string } | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const reload = useCallback(async () => {
    try {
      const data = await fetchApiKeys()
      setKeys(data.keys)
      setError('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载失败')
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  async function onCreate() {
    if (!name.trim()) return
    setBusy(true)
    setError('')
    try {
      const data = await createApiKey(name.trim())
      setCreated(data)
      setName('')
      await reload()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '创建失败')
    } finally {
      setBusy(false)
    }
  }

  async function onRevoke(k: ApiKeyItem) {
    if (!window.confirm(`吊销密钥「${k.name}」（${k.keyPrefix}…）？使用它的外部调用会立即 401。`)) return
    try {
      await revokeApiKey(k.id)
      toast('密钥已吊销')
      await reload()
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '吊销失败')
    }
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h2>API 分发</h2>
          <div className="desc">
            把 Agent 分发给外部系统调用：创建密钥 → 用密钥打开放端点。库里只存 SHA-256 哈希，
            明文只在创建时显示一次；吊销即时失效。
          </div>
        </div>
      </div>
      <p className="status-line" role="status">{error}</p>

      <div className="card">
        <h3>创建密钥</h3>
        <div style={{ display: 'flex', gap: 8, maxWidth: 560 }}>
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            placeholder="密钥名称（如：客服系统接入）"
            onKeyDown={(e) => { if (e.key === 'Enter') void onCreate() }}
          />
          <button className="btn primary" disabled={busy || !name.trim()} onClick={() => void onCreate()}>
            {busy ? '创建中…' : '＋ 创建'}
          </button>
        </div>

        {created && (
          <div className="apikey-created">
            <b>密钥已创建（{created.name}）——现在复制，离开本页后不再显示：</b>
            <div className="apikey-key">
              <code>{created.key}</code>
              <button
                className="btn sm primary"
                onClick={() => { void navigator.clipboard?.writeText(created.key); toast('已复制') }}
              >复制</button>
            </div>
            <div className="note gray">这是唯一一次展示完整明文，请立即保存到你的系统配置里。</div>
          </div>
        )}
      </div>

      <div className="card">
        <h3>我的密钥 <span className="sub">{keys.length} 个</span></h3>
        <table>
          <thead>
            <tr><th>名称</th><th>密钥</th><th>状态</th><th>最近调用</th><th>创建时间</th><th>操作</th></tr>
          </thead>
          <tbody>
            {keys.map((k) => (
              <tr key={k.id}>
                <td><b>{k.name}</b></td>
                <td style={{ fontFamily: 'monospace' }}>{k.keyPrefix}…</td>
                <td>{k.enabled ? <span className="tag ok">启用</span> : <span className="tag gray">已吊销</span>}</td>
                <td style={{ color: 'var(--muted)', fontSize: 12 }}>{fmt(k.lastUsedAt)}</td>
                <td style={{ color: 'var(--muted)', fontSize: 12 }}>{fmt(k.createdAt)}</td>
                <td>
                  {k.enabled && <button className="btn sm danger" onClick={() => void onRevoke(k)}>吊销</button>}
                </td>
              </tr>
            ))}
            {keys.length === 0 && (
              <tr><td colSpan={6}><div className="note gray">还没有密钥，上面创建一个。</div></td></tr>
            )}
          </tbody>
        </table>
      </div>

      <div className="card">
        <h3>调用方式 <span className="sub">POST /api/v1/open/agents/&#123;agentId&#125;/invoke</span></h3>
        <pre className="apikey-curl">{CURL_SAMPLE(12)}</pre>
        <div className="note gray">
          同步等待产物（默认 60s，上限 120s）；超时也会返回 runId，用
          <code style={{ margin: '0 4px' }}>GET /api/v1/open/runs/&#123;runId&#125;</code>
          续查。strategy 可选 auto / plan / fixed / custom（custom 需该 Agent 有执行图或挂载工作流）。
          可调用范围与对话一致：平台公共 Agent + 本人启用中的 Agent。
        </div>
      </div>
      <ToastHost />
    </>
  )
}
