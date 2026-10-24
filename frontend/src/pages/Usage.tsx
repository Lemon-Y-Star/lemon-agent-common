import { useCallback, useEffect, useState } from 'react'
import { fetchUsage, type UsageOverview } from '../api/workspace'
import { ApiError } from '../api/client'
import { ToastHost } from '../components/Toast'

/**
 * 用量与观测（PRD FR-16/17 简化版）：stat 卡 + 按天柱状 + 状态分布。
 */
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

  const maxRuns = Math.max(1, ...(data?.daily ?? []).map((d) => d.runs))

  return (
    <>
      <div className="page-head">
        <div>
          <h2>用量与观测</h2>
          <div className="desc">个人维度的运行次数与 token 用量统计（按天聚合，窗口最长 30 天）。</div>
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

      <div className="grid g3" style={{ marginBottom: 16 }}>
        <div className="stat">
          <div className="lbl">运行数（{data?.days ?? days} 天）</div>
          <div className="num">{data?.totalRuns ?? '—'}</div>
        </div>
        <div className="stat">
          <div className="lbl">输入 tokens</div>
          <div className="num">{data?.totalInputTokens.toLocaleString() ?? '—'}</div>
        </div>
        <div className="stat">
          <div className="lbl">输出 tokens</div>
          <div className="num">{data?.totalOutputTokens.toLocaleString() ?? '—'}</div>
        </div>
      </div>

      <div className="card">
        <h3>按天运行数</h3>
        <div className="bars">
          {(data?.daily ?? []).map((d) => (
            <div className="b" key={d.date} title={`${d.date}：${d.runs} 次运行，输入 ${d.inputTokens} / 输出 ${d.outputTokens} tokens`}>
              <i style={{ height: `${Math.round((d.runs / maxRuns) * 100)}%`, minHeight: d.runs > 0 ? 6 : 2 }} />
              <em>{d.runs}</em>
              <span>{d.date.slice(5)}</span>
            </div>
          ))}
        </div>
      </div>

      <div className="card">
        <h3>状态分布</h3>
        <table>
          <thead><tr><th>状态</th><th>数量</th></tr></thead>
          <tbody>
            {(data?.statusCounts ?? []).map((s) => (
              <tr key={s.status}>
                <td>{s.status}</td>
                <td>{s.count}</td>
              </tr>
            ))}
            {(data?.statusCounts ?? []).length === 0 && (
              <tr><td colSpan={2}><div className="note gray">窗口内无运行</div></td></tr>
            )}
          </tbody>
        </table>
      </div>
      <ToastHost />
    </>
  )
}
