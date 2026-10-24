import { useCallback, useEffect, useState } from 'react'
import { fetchRunSnapshot, fetchSessions, type RunSnapshot, type SessionItem } from '../api/runtime'
import { ApiError } from '../api/client'
import { ToastHost, toast } from '../components/Toast'

/**
 * 运行历史（PRD FR-18.5）：左列表（按创建倒序 + 状态过滤）+ 右详情（快照接口）。
 * 数据源：GET /agent/runs（服务端强制 user_id 过滤）+ GET /agent/runs/{id}。
 */

const STATUS_TAGS: Record<string, { cls: string; text: string }> = {
  RUNNING: { cls: 'ok', text: '运行中' },
  BLOCKED: { cls: 'warn', text: '待审批' },
  COMPLETED: { cls: 'ok', text: '已完成' },
  FAILED: { cls: 'err', text: '失败' },
  CANCELLED: { cls: 'gray', text: '已取消' },
  TIMEOUT: { cls: 'warn', text: '已超时' },
}

const STATUSES = ['RUNNING', 'BLOCKED', 'COMPLETED', 'FAILED', 'CANCELLED', 'TIMEOUT']

function statusTag(status: string) {
  return STATUS_TAGS[status] ?? { cls: 'gray', text: status }
}

export default function Runs() {
  const [sessions, setSessions] = useState<SessionItem[]>([])
  const [status, setStatus] = useState('')
  const [sessionId, setSessionId] = useState('')
  interface RunListItem {
    runId: number
    status: string
    strategy: string
    errorCode?: string | null
    traceId: string
    lastSeq: number
    startedAt: string | null
    endedAt: string | null
  }
  const [items, setItems] = useState<RunListItem[]>([])
  const [total, setTotal] = useState(0)
  const [selected, setSelected] = useState<RunSnapshot | null>(null)
  const [error, setError] = useState('')

  const reload = useCallback(async (filterStatus: string, filterSession: string) => {
    try {
      const qs = new URLSearchParams()
      if (filterStatus) qs.set('status', filterStatus)
      if (filterSession) qs.set('sessionId', filterSession)
      qs.set('page', '1')
      qs.set('size', '50')
      const { request } = await import('../api/client')
      const data = await request<{ total: number; items: RunListItem[] }>(`/agent/runs?${qs.toString()}`)
      setItems(data.items)
      setTotal(data.total)
      setError('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载失败，请稍后重试')
    }
  }, [])

  useEffect(() => {
    void fetchSessions().then((list) => setSessions(list.items)).catch(() => undefined)
    void reload('', '')
  }, [reload])

  async function openDetail(runId: number) {
    try {
      setSelected(await fetchRunSnapshot(runId))
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '快照加载失败')
    }
  }

  const fmt = (s: string | null | undefined) => (s ? s.slice(0, 19).replace('T', ' ') : '—')

  return (
    <>
      <div className="page-head">
        <div>
          <h2>运行历史</h2>
          <div className="desc">
            全部运行的列表与快照：排障按状态筛失败 run、traceId 反查日志、BLOCKED 感知审批进度。
          </div>
        </div>
      </div>

      <div className="card">
        <div className="row">
          <div className="field" style={{ margin: 0 }}>
            <label>状态</label>
            <select value={status} onChange={(e) => { setStatus(e.target.value); void reload(e.target.value, sessionId) }}>
              <option value="">全部</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>{statusTag(s).text}（{s}）</option>
              ))}
            </select>
          </div>
          <div className="field" style={{ margin: 0 }}>
            <label>会话</label>
            <select value={sessionId} onChange={(e) => { setSessionId(e.target.value); void reload(status, e.target.value) }}>
              <option value="">全部会话</option>
              {sessions.map((s) => (
                <option key={s.sessionId} value={s.sessionId}>{s.title}</option>
              ))}
            </select>
          </div>
          <div style={{ display: 'flex', alignItems: 'flex-end' }}>
            <button className="btn sm" onClick={() => void reload(status, sessionId)}>刷新</button>
          </div>
        </div>
      </div>

      <p className="status-line" role="status">{error}</p>

      <div className="grid g2">
        <div className="card" style={{ marginBottom: 0 }}>
          <h3>运行列表 <span className="sub">{total} 条</span></h3>
          <table>
            <thead>
              <tr>
                <th>运行 ID</th>
                <th>状态</th>
                <th>策略</th>
                <th>开始时间</th>
              </tr>
            </thead>
            <tbody>
              {items.map((r) => {
                const tag = statusTag(r.status)
                return (
                  <tr
                    key={r.runId}
                    style={{ cursor: 'pointer' }}
                    onClick={() => void openDetail(r.runId)}
                  >
                    <td>#{r.runId}</td>
                    <td><span className={`tag ${tag.cls}`}>{tag.text}</span></td>
                    <td>{r.strategy}</td>
                    <td style={{ color: 'var(--muted)' }}>{fmt(r.startedAt)}</td>
                  </tr>
                )
              })}
              {items.length === 0 && (
                <tr><td colSpan={4}><div className="note gray">暂无运行记录</div></td></tr>
              )}
            </tbody>
          </table>
        </div>

        <div className="card" style={{ marginBottom: 0 }}>
          <h3>运行详情</h3>
          {!selected && <div className="note gray">点击左侧列表查看运行快照。</div>}
          {selected && (
            <>
              <table>
                <tbody>
                  <tr><th>运行 ID</th><td>#{selected.runId}</td></tr>
                  <tr><th>状态</th><td>
                    <span className={`tag ${statusTag(selected.status).cls}`}>{statusTag(selected.status).text}</span>
                  </td></tr>
                  <tr><th>策略</th><td>{selected.strategy}</td></tr>
                  <tr><th>traceId</th><td style={{ fontFamily: 'monospace', wordBreak: 'break-all' }}>{selected.traceId}</td></tr>
                  <tr><th>事件游标</th><td>lastSeq = {selected.lastSeq}</td></tr>
                  {selected.errorCode && (
                    <tr><th>失败归因</th><td>
                      <span className="tag err">{selected.errorCode}</span>
                      {selected.errorMsg && <div style={{ marginTop: 4 }}>{selected.errorMsg}</div>}
                    </td></tr>
                  )}
                  {selected.usage && (
                    <tr><th>用量</th><td>
                      输入 {selected.usage.inputTokens} · 输出 {selected.usage.outputTokens} tokens
                    </td></tr>
                  )}
                  <tr><th>开始时间</th><td>{fmt(selected.startedAt)}</td></tr>
                  <tr><th>结束时间</th><td>{fmt(selected.endedAt)}</td></tr>
                </tbody>
              </table>
              {selected.errorMsg && (
                <div className="note">{selected.errorMsg}</div>
              )}
            </>
          )}
        </div>
      </div>
      <ToastHost />
    </>
  )
}
