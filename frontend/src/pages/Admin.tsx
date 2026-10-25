import { useCallback, useEffect, useState } from 'react'
import { fetchAdminStats, type AdminStats } from '../api/workspace'
import { ApiError } from '../api/client'
import { ToastHost } from '../components/Toast'
import { STATUS_META } from './Usage'

/**
 * 管理端统计（PRD FR-22.1 简化版）：admin 角色可见的平台总量统计。
 *
 * 视觉与「用量与观测」同一套语言：KPI 卡 + 横向状态条（v3.8 重做，此前 .stat 类没有样式，
 * 页面是裸文字大表格）。
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

  const byStatus = Object.entries(stats?.runsByStatus ?? {})
    .map(([status, count]) => ({ status, count }))
    .sort((a, b) => b.count - a.count)
  const totalRuns = byStatus.reduce((s, x) => s + x.count, 0)

  const kpis: { lbl: string; num: string | number | undefined; sub: string }[] = [
    { lbl: '用户', num: stats?.users, sub: '注册账号数' },
    { lbl: '会话', num: stats?.sessions, sub: '对话会话总数' },
    { lbl: '运行', num: stats?.runs, sub: '全部运行（含进行中）' },
    { lbl: '消息', num: stats?.messages, sub: 'USER + ASSISTANT' },
    { lbl: '工单', num: stats?.tickets, sub: 'demo_ticket 创建量' },
    { lbl: '总 tokens', num: stats?.totalTokens?.toLocaleString(), sub: '输入 + 输出累计' },
  ]

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

      <div className="kpi-row k6">
        {kpis.map((k) => (
          <div className="kpi" key={k.lbl}>
            <div className="kpi-lbl">{k.lbl}</div>
            <div className="kpi-num">{k.num ?? '—'}</div>
            <div className="kpi-sub">{k.sub}</div>
          </div>
        ))}
      </div>

      <div className="card">
        <h3>运行状态分布 <span className="sub">共 {totalRuns.toLocaleString()} 条运行</span></h3>
        <div className="status-bars">
          {byStatus.map((s) => {
            const meta = STATUS_META[s.status] ?? { label: s.status, color: '#94a3b8' }
            const pct = totalRuns === 0 ? 0 : Math.round((s.count / totalRuns) * 100)
            return (
              <div className="status-row" key={s.status}>
                <span className="status-name">
                  <i style={{ background: meta.color }} />
                  {meta.label}
                </span>
                <div className="status-track">
                  <div className="status-fill" style={{ width: `${pct}%`, background: meta.color }} />
                </div>
                <span className="status-count">
                  {s.count.toLocaleString()}<em>{pct}%</em>
                </span>
              </div>
            )
          })}
          {byStatus.length === 0 && <div className="note gray">暂无运行</div>}
        </div>
      </div>
      <ToastHost />
    </>
  )
}
