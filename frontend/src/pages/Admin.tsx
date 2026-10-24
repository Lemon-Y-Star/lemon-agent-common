import { useCallback, useEffect, useState } from 'react'
import { fetchAdminStats, type AdminStats } from '../api/workspace'
import { ApiError } from '../api/client'
import { ToastHost } from '../components/Toast'

/**
 * 管理端统计（PRD FR-22.1 简化版）：admin 角色可见的平台总量统计。
 */
export default function Admin() {
  const [stats, setStats] = useState<AdminStats | null>(null)
  const [error, setError] = useState('')

  const reload = useCallback(async () => {
    try {
      setStats(await fetchAdminStats())
      setError('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载失败')
    }
  }, [])

  useEffect(() => {
    void reload()
  }, [reload])

  return (
    <>
      <div className="page-head">
        <div>
          <h2>网站统计</h2>
          <div className="desc">平台总量指标（admin 角色可见）。数据实时聚合自业务库。</div>
        </div>
        <div style={{ marginLeft: 'auto' }}>
          <button className="btn sm" onClick={() => void reload()}>刷新</button>
        </div>
      </div>

      <p className="status-line" role="status">{error}</p>

      <div className="grid g3" style={{ marginBottom: 16 }}>
        <div className="stat"><div className="lbl">用户</div><div className="num">{stats?.users ?? '—'}</div></div>
        <div className="stat"><div className="lbl">会话</div><div className="num">{stats?.sessions ?? '—'}</div></div>
        <div className="stat"><div className="lbl">运行</div><div className="num">{stats?.runs ?? '—'}</div></div>
        <div className="stat"><div className="lbl">消息</div><div className="num">{stats?.messages ?? '—'}</div></div>
        <div className="stat"><div className="lbl">工单</div><div className="num">{stats?.tickets ?? '—'}</div></div>
        <div className="stat"><div className="lbl">总 tokens</div><div className="num">{stats?.totalTokens.toLocaleString() ?? '—'}</div></div>
      </div>

      <div className="card">
        <h3>运行状态分布</h3>
        <table>
          <thead><tr><th>状态</th><th>数量</th></tr></thead>
          <tbody>
            {Object.entries(stats?.runsByStatus ?? {}).map(([status, count]) => (
              <tr key={status}><td>{status}</td><td>{count}</td></tr>
            ))}
            {Object.keys(stats?.runsByStatus ?? {}).length === 0 && (
              <tr><td colSpan={2}><div className="note gray">暂无运行</div></td></tr>
            )}
          </tbody>
        </table>
      </div>
      <ToastHost />
    </>
  )
}
