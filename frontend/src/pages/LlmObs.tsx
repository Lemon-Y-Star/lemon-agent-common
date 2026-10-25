import { useCallback, useEffect, useState } from 'react'
import { fetchLlmOverview, type LlmOverview } from '../api/llm'
import { ApiError } from '../api/client'
import { ToastHost } from '../components/Toast'

/**
 * LLM 观测（v3.8）：按模型聚合的调用量 / token / 时长 / 失败率 + 最近调用。
 * 数据源是 agent_run（执行器把实际用的模型名回填到 run 上），观测不另建事实表。
 */

const ms = (v: number | null | undefined) => (v == null ? '—' : v >= 1000 ? `${(v / 1000).toFixed(1)}s` : `${v}ms`)
const n = (v: number | null | undefined) => (v == null ? '—' : v.toLocaleString())

const WINDOWS = [
  { days: 1, label: '近 24 小时' },
  { days: 7, label: '近 7 天' },
  { days: 30, label: '近 30 天' },
  { days: 0, label: '全部' },
]

export default function LlmObs() {
  const [days, setDays] = useState(7)
  const [data, setData] = useState<LlmOverview | null>(null)
  const [error, setError] = useState('')

  const reload = useCallback(async (d: number) => {
    try {
      setData(await fetchLlmOverview(d))
      setError('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载失败')
    }
  }, [])

  useEffect(() => {
    void reload(days)
  }, [reload, days])

  const summary = data?.summary
  const successRate = summary && summary.calls > 0
    ? Math.round(((summary.calls - summary.failed) / summary.calls) * 100)
    : null

  return (
    <>
      <div className="page-head">
        <div>
          <h2>LLM 观测</h2>
          <div className="desc">
            按模型聚合的调用量 / token / 端到端时长 / 失败率。模型名是执行器发起调用时回填到运行上的
            实际值（连接的首选模型），不是配置声明值。
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <select className="btn sm" style={{ width: 'auto' }} value={days} onChange={(e) => setDays(Number(e.target.value))}>
            {WINDOWS.map((w) => <option key={w.days} value={w.days}>{w.label}</option>)}
          </select>
        </div>
      </div>
      <p className="status-line" role="status">{error}</p>

      <div className="kpi-row">
        <div className="kpi">
          <div className="kpi-lbl">调用次数</div>
          <div className="kpi-num">{n(summary?.calls)}</div>
          <div className="kpi-sub">时间窗内的全部运行</div>
        </div>
        <div className="kpi">
          <div className="kpi-lbl">成功率</div>
          <div className="kpi-num">{successRate == null ? '—' : `${successRate}%`}</div>
          <div className="kpi-sub">失败 {n(summary?.failed)}（FAILED / TIMEOUT）</div>
        </div>
        <div className="kpi">
          <div className="kpi-lbl">平均端到端时长</div>
          <div className="kpi-num">{ms(summary?.avgLatencyMs)}</div>
          <div className="kpi-sub">提交 → 终态（含工具与排队）</div>
        </div>
        <div className="kpi">
          <div className="kpi-lbl">tokens 合计</div>
          <div className="kpi-num">{n((summary?.inputTokens ?? 0) + (summary?.outputTokens ?? 0))}</div>
          <div className="kpi-sub">输入 {n(summary?.inputTokens)} · 输出 {n(summary?.outputTokens)}</div>
        </div>
      </div>

      <div className="grid g2">
        <div className="card" style={{ marginBottom: 0 }}>
          <h3>按模型聚合</h3>
          <table>
            <thead>
              <tr><th>模型</th><th>调用</th><th>失败</th><th>tokens（入/出）</th><th>平均时长</th></tr>
            </thead>
            <tbody>
              {(data?.byModel ?? []).map((m) => (
                <tr key={m.modelName}>
                  <td style={{ fontFamily: 'monospace', fontSize: 12 }}>{m.modelName}</td>
                  <td>{n(m.calls)}</td>
                  <td>{m.failed > 0 ? <span className="tag err">{m.failed}</span> : '0'}</td>
                  <td style={{ fontVariantNumeric: 'tabular-nums' }}>{n(m.inputTokens)} / {n(m.outputTokens)}</td>
                  <td>{ms(m.avgLatencyMs)}</td>
                </tr>
              ))}
              {(data?.byModel ?? []).length === 0 && (
                <tr><td colSpan={5}><div className="note gray">时间窗内没有调用</div></td></tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="card" style={{ marginBottom: 0 }}>
          <h3>最近调用 <span className="sub">最多 15 条</span></h3>
          <table>
            <thead>
              <tr><th>运行</th><th>模型</th><th>状态</th><th>时长</th><th>tokens（入/出）</th></tr>
            </thead>
            <tbody>
              {(data?.recent ?? []).map((r) => (
                <tr key={r.runId}>
                  <td>#{r.runId}<div style={{ fontFamily: 'monospace', fontSize: 10.5, color: 'var(--muted)' }}>{r.traceId?.slice(0, 10)}…</div></td>
                  <td style={{ fontFamily: 'monospace', fontSize: 12 }}>{r.modelName ?? '—'}</td>
                  <td>
                    {r.status === 'COMPLETED' ? <span className="tag ok">完成</span>
                      : r.status === 'RUNNING' ? <span className="tag ok">运行中</span>
                        : r.status === 'FAILED' || r.status === 'TIMEOUT' ? <span className="tag err">{r.status}</span>
                          : <span className="tag gray">{r.status}</span>}
                  </td>
                  <td style={{ fontVariantNumeric: 'tabular-nums' }}>{ms(r.latencyMs)}</td>
                  <td style={{ fontVariantNumeric: 'tabular-nums' }}>{n(r.inputTokens)} / {n(r.outputTokens)}</td>
                </tr>
              ))}
              {(data?.recent ?? []).length === 0 && (
                <tr><td colSpan={5}><div className="note gray">还没有调用</div></td></tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
      <ToastHost />
    </>
  )
}
