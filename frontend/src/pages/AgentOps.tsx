import { useCallback, useEffect, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  fetchAgentLogs,
  fetchAgentMetrics,
  type AgentMetrics,
  type SessionLog,
} from '../api/agents'
import { ApiError } from '../api/client'
import { ToastHost } from '../components/Toast'

/**
 * Agent 运营页（v4.5.2，Dify「日志 / 监控」页同款）：
 * 日志 = 按会话聚合的对话列表；监控 = 按天聚合的消息数 / Token 用量 / 成功率 / 好评率。
 * 图表用内联 SVG 自绘（项目不引图表库，保持轻量）。
 */

const DAY_WINDOWS = [1, 7, 30] as const

export default function AgentOps() {
  const { agentId } = useParams()
  const id = Number(agentId)
  const navigate = useNavigate()
  const [tab, setTab] = useState<'logs' | 'metrics'>('logs')
  const [days, setDays] = useState<number>(7)
  const [logs, setLogs] = useState<SessionLog[] | null>(null)
  const [metrics, setMetrics] = useState<AgentMetrics | null>(null)
  const [error, setError] = useState('')

  const reload = useCallback(async () => {
    if (!Number.isFinite(id)) return
    setError('')
    try {
      if (tab === 'logs') {
        setLogs((await fetchAgentLogs(id, days)).sessions)
      } else {
        setMetrics(await fetchAgentMetrics(id, days))
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载失败')
    }
  }, [id, tab, days])

  useEffect(() => {
    void reload()
  }, [reload])

  return (
    <>
      <div className="page-head">
        <div>
          <h2>Agent 运营 · #{id}</h2>
          <div className="desc">
            日志与监控数据来自该 Agent 的真实运行记录（Dify 同款两层视图）；
            消息数以运行次数计（一次运行 = 一条用户消息）。
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          {DAY_WINDOWS.map((d) => (
            <button key={d} className={`btn sm ${days === d ? 'primary' : ''}`} onClick={() => setDays(d)}>
              近 {d} 天
            </button>
          ))}
          <button className="btn" onClick={() => navigate('/agents')}>返回</button>
        </div>
      </div>

      <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
        <button className={`btn sm ${tab === 'logs' ? 'primary' : ''}`} onClick={() => setTab('logs')}>日志</button>
        <button className={`btn sm ${tab === 'metrics' ? 'primary' : ''}`} onClick={() => setTab('metrics')}>监控</button>
      </div>

      <p className="status-line" role="status" style={{ color: 'var(--danger)' }}>{error}</p>

      {tab === 'logs' && (
        <div className="card">
          {logs === null ? (
            <div className="note gray">加载中…</div>
          ) : logs.length === 0 ? (
            <div className="note gray">该时间窗内还没有运行记录——去对话页用这个 Agent 聊几句再回来看。</div>
          ) : (
            <table>
              <thead>
                <tr>
                  <th>会话</th>
                  <th>消息数</th>
                  <th>成功 / 失败</th>
                  <th>Tokens</th>
                  <th>最近活跃</th>
                </tr>
              </thead>
              <tbody>
                {logs.map((s) => (
                  <tr key={s.sessionId}>
                    <td>
                      <a href={`/chat/${s.sessionId}`} style={{ color: 'var(--primary,#4f46e5)' }}>
                        {s.title || `会话 #${s.sessionId}`}
                      </a>
                    </td>
                    <td>{s.runCount}</td>
                    <td>
                      {s.okCount} / {s.runCount - s.okCount}
                    </td>
                    <td style={{ color: 'var(--muted)' }}>{s.totalTokens}</td>
                    <td style={{ color: 'var(--muted)', fontSize: 12 }}>
                      {s.lastAt?.slice(0, 19).replace('T', ' ')}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
      )}

      {tab === 'metrics' && (
        <>
          {metrics === null ? (
            <div className="note gray">加载中…</div>
          ) : (
            <>
              <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4,1fr)', gap: 10, marginBottom: 14 }}>
                <StatCard label="全部消息数" value={metrics.totalRuns} />
                <StatCard label="会话数" value={metrics.totalSessions} />
                <StatCard label="成功率" value={`${metrics.successRate}%`} />
                <StatCard label="Token 用量" value={metrics.totalTokens} sub={`好评率 ${metrics.likeRate}%`} />
              </div>
              <div className="card" style={{ padding: 14 }}>
                <h4 style={{ marginTop: 0 }}>每日趋势（消息数 / Token 用量）</h4>
                {metrics.days.length === 0 ? (
                  <div className="note gray">该时间窗内还没有数据。</div>
                ) : (
                  <TrendChart days={metrics.days} />
                )}
              </div>
            </>
          )}
        </>
      )}
      <ToastHost />
    </>
  )
}

function StatCard(props: { label: string; value: number | string; sub?: string }) {
  return (
    <div className="card" style={{ padding: '10px 14px' }}>
      <div style={{ fontSize: 12, color: 'var(--muted,#6b7280)' }}>{props.label}</div>
      <div style={{ fontSize: 24, fontWeight: 700 }}>{props.value}</div>
      {props.sub && <div style={{ fontSize: 12, color: 'var(--muted,#6b7280)' }}>{props.sub}</div>}
    </div>
  )
}

/** 内联 SVG 双系列柱状图：消息数（左轴概念）+ Token 用量（按比例缩放着色区分）。 */
function TrendChart(props: { days: AgentMetrics['days'] }) {
  const { days } = props
  const W = 640
  const H = 180
  const PAD = 28
  const maxRuns = Math.max(1, ...days.map((d) => d.runs))
  const maxTokens = Math.max(1, ...days.map((d) => d.tokens))
  const bw = days.length === 0 ? 0 : Math.max(6, (W - PAD * 2) / days.length - 6)
  return (
    <svg viewBox={`0 0 ${W} ${H}`} style={{ width: '100%', height: 200 }}>
      <line x1={PAD} y1={H - PAD} x2={W - PAD / 2} y2={H - PAD} stroke="#d5d9e2" />
      {days.map((d, i) => {
        const x = PAD + i * ((W - PAD * 2) / days.length) + 3
        const rh = ((H - PAD * 2) * d.runs) / maxRuns
        const th = ((H - PAD * 2) * d.tokens) / maxTokens
        return (
          <g key={d.date}>
            <rect x={x} y={H - PAD - rh} width={bw / 2} height={rh} fill="#6366f1" rx="2">
              <title>{`${d.date} 消息 ${d.runs}`}</title>
            </rect>
            <rect x={x + bw / 2 + 1} y={H - PAD - th} width={bw / 2} height={th} fill="#f59e0b" rx="2">
              <title>{`${d.date} Tokens ${d.tokens}`}</title>
            </rect>
            <text x={x + bw / 2} y={H - PAD + 12} fontSize="9" textAnchor="middle" fill="#6b7280">
              {d.date.slice(5)}
            </text>
          </g>
        )
      })}
      <rect x={PAD} y={6} width={10} height={10} fill="#6366f1" rx="2" />
      <text x={PAD + 14} y={15} fontSize="11" fill="#6b7280">消息数</text>
      <rect x={PAD + 70} y={6} width={10} height={10} fill="#f59e0b" rx="2" />
      <text x={PAD + 84} y={15} fontSize="11" fill="#6b7280">Token 用量</text>
    </svg>
  )
}
