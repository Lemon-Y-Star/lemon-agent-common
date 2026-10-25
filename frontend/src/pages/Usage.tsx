import { useCallback, useEffect, useState } from 'react'
import { fetchUsage, type UsageOverview } from '../api/workspace'
import { ApiError } from '../api/client'
import { ToastHost } from '../components/Toast'

/**
 * 用量与观测（FR-16 简化版）：KPI 卡 + 按天柱状 + 状态分布条。
 *
 * 视觉铁律：数字优先（大号等宽数字）、图表用纯 CSS 画（不引图表库），
 * 零数据的日期也要占位——否则柱状图会"少几天"，让人以为是数据丢了。
 */

/** 运行状态 → 中文标签 + 主题色（用量页与管理端统计共用，改色两处同步）。 */
export const STATUS_META: Record<string, { label: string; color: string }> = {
  COMPLETED: { label: '成功', color: '#16a34a' },
  FAILED: { label: '失败', color: '#dc2626' },
  CANCELLED: { label: '已取消', color: '#94a3b8' },
  BLOCKED: { label: '待审批', color: '#d97706' },
  RUNNING: { label: '运行中', color: '#2563eb' },
  TIMEOUT: { label: '超时', color: '#7c3aed' },
}

function fmt(n: number | undefined) {
  return n == null ? '—' : n.toLocaleString('zh-CN')
}

export default function Usage() {
  const [days, setDays] = useState(7)
  const [data, setData] = useState<UsageOverview | null>(null)
  const [error, setError] = useState('')

  const reload = useCallback(async (d: number) => {
    try {
      setData(await fetchUsage(d))
      setError('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载失败')
    }
  }, [])

  useEffect(() => {
    void reload(days)
  }, [days, reload])

  const daily = data?.daily ?? []
  const maxRuns = Math.max(1, ...daily.map((d) => d.runs))
  const statusCounts = data?.statusCounts ?? []
  const totalByStatus = statusCounts.reduce((sum, s) => sum + s.count, 0)
  const completed = statusCounts.find((s) => s.status === 'COMPLETED')?.count ?? 0
  const successRate = totalByStatus === 0 ? '—' : `${Math.round((completed / totalByStatus) * 100)}%`

  return (
    <>
      <div className="page-head">
        <div>
          <h2>用量与观测</h2>
          <div className="desc">个人维度的运行次数与 token 用量（按天聚合，窗口最长 30 天）。</div>
        </div>
        <div style={{ marginLeft: 'auto' }}>
          <select className="btn sm" style={{ width: 'auto' }} value={days} onChange={(e) => setDays(Number(e.target.value))}>
            <option value={7}>近 7 天</option>
            <option value={14}>近 14 天</option>
            <option value={30}>近 30 天</option>
          </select>
        </div>
      </div>

      <p className="status-line" role="status">{error}</p>

      <div className="kpi-row">
        <div className="kpi">
          <div className="kpi-lbl">运行数 · {data?.days ?? days} 天</div>
          <div className="kpi-num">{fmt(data?.totalRuns)}</div>
          <div className="kpi-sub">平均每天 {daily.length ? (Math.round(((data?.totalRuns ?? 0) / daily.length) * 10) / 10) : 0} 次</div>
        </div>
        <div className="kpi">
          <div className="kpi-lbl">输入 tokens</div>
          <div className="kpi-num">{fmt(data?.totalInputTokens)}</div>
          <div className="kpi-sub">含系统提示词与上下文</div>
        </div>
        <div className="kpi">
          <div className="kpi-lbl">输出 tokens</div>
          <div className="kpi-num">{fmt(data?.totalOutputTokens)}</div>
          <div className="kpi-sub">模型生成部分</div>
        </div>
        <div className="kpi">
          <div className="kpi-lbl">成功率</div>
          <div className="kpi-num">{successRate}</div>
          <div className="kpi-sub">成功 {completed} / 共 {totalByStatus}</div>
        </div>
      </div>

      <div className="card">
        <h3>
          按天运行数
          <span className="sub">悬停看当天 tokens</span>
        </h3>
        <div className="bars">
          {daily.map((d) => (
            <div
              className="bar-col"
              key={d.date}
              title={`${d.date}：${d.runs} 次运行 · 输入 ${fmt(d.inputTokens)} / 输出 ${fmt(d.outputTokens)} tokens`}
            >
              <div className="bar-val">{d.runs}</div>
              <div className="bar-track">
                <div
                  className={`bar-fill ${d.runs === 0 ? 'empty' : ''}`}
                  style={{ height: `${Math.max((d.runs / maxRuns) * 100, d.runs > 0 ? 6 : 2)}%` }}
                />
              </div>
              <div className="bar-date">{d.date.slice(5)}</div>
            </div>
          ))}
          {daily.length === 0 && <div className="note gray">窗口内没有数据</div>}
        </div>
      </div>

      <div className="card">
        <h3>状态分布 <span className="sub">按运行条数</span></h3>
        <div className="status-bars">
          {statusCounts.map((s) => {
            const meta = STATUS_META[s.status] ?? { label: s.status, color: '#94a3b8' }
            const pct = totalByStatus === 0 ? 0 : Math.round((s.count / totalByStatus) * 100)
            return (
              <div className="status-row" key={s.status}>
                <span className="status-name">
                  <i style={{ background: meta.color }} />
                  {meta.label}
                </span>
                <div className="status-track">
                  <div className="status-fill" style={{ width: `${pct}%`, background: meta.color }} />
                </div>
                <span className="status-count">{s.count}<em>{pct}%</em></span>
              </div>
            )
          })}
          {statusCounts.length === 0 && <div className="note gray">窗口内无运行</div>}
        </div>
      </div>
      <ToastHost />
    </>
  )
}
