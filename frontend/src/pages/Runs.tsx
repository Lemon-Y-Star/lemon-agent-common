import { useCallback, useEffect, useState } from 'react'
import { fetchRunSnapshot, fetchSessions, retryRun, type RunSnapshot, type SessionItem } from '../api/runtime'
import { ApiError } from '../api/client'
import { ToastHost, toast } from '../components/Toast'
import RunGraphView from '../components/RunGraphView'
import Markdown from '../components/Markdown'
import ThinkingPanel, { splitThink } from '../components/ThinkingPanel'

/**
 * 运行历史（PRD FR-18.5）：左列表（状态 / 会话 / traceId 过滤）+ 右详情（快照接口）。
 * 数据源：GET /agent/runs（服务端强制 user_id 过滤，traceId 精确匹配）+ GET /agent/runs/{id}。
 * 详情完整链路：输入 / 输出（含思考过程折叠）→ plan 步骤 → custom 执行图图例（点节点看节点输入输出）。
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
  const [traceId, setTraceId] = useState('')
  const [thinkOpen, setThinkOpen] = useState(false)

  const reload = useCallback(async (filterStatus: string, filterSession: string, filterTrace: string) => {
    try {
      const qs = new URLSearchParams()
      if (filterStatus) qs.set('status', filterStatus)
      if (filterSession) qs.set('sessionId', filterSession)
      if (filterTrace.trim()) qs.set('traceId', filterTrace.trim())
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
    void reload('', '', '')
  }, [reload])

  async function openDetail(runId: number) {
    try {
      setSelected(await fetchRunSnapshot(runId))
      setThinkOpen(false)
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '快照加载失败')
    }
  }

  const fmt = (s: string | null | undefined) => (s ? s.slice(0, 19).replace('T', ' ') : '—')

  /** 重试失败 / 超时运行：按原输入 / 策略重放，完成后打开新运行详情。 */
  async function onRetry(rid: number) {
    try {
      const nr = await retryRun(rid)
      toast(`已发起重试 #${nr.runId}`)
      await reload(status, sessionId, traceId)
      void openDetail(nr.runId)
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '重试失败')
    }
  }

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
            <select value={status} onChange={(e) => { setStatus(e.target.value); void reload(e.target.value, sessionId, traceId) }}>
              <option value="">全部</option>
              {STATUSES.map((s) => (
                <option key={s} value={s}>{statusTag(s).text}（{s}）</option>
              ))}
            </select>
          </div>
          <div className="field" style={{ margin: 0 }}>
            <label>会话</label>
            <select value={sessionId} onChange={(e) => { setSessionId(e.target.value); void reload(status, e.target.value, traceId) }}>
              <option value="">全部会话</option>
              {sessions.map((s) => (
                <option key={s.sessionId} value={s.sessionId}>{s.title}</option>
              ))}
            </select>
          </div>
          <div className="field" style={{ margin: 0, flex: 1, minWidth: 200 }}>
            <label>traceId（精确匹配，回车查询）</label>
            <input
              value={traceId}
              onChange={(e) => setTraceId(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') void reload(status, sessionId, traceId) }}
              placeholder="粘贴 traceId 反查日志，如 25e08b9eac9ec7aa152c60001aedb503"
              style={{ fontFamily: 'monospace' }}
            />
          </div>
          <div style={{ display: 'flex', gap: 6, alignItems: 'flex-end' }}>
            <button className="btn sm" onClick={() => void reload(status, sessionId, traceId)}>查询</button>
            <button className="btn sm" onClick={() => { setTraceId(''); void reload(status, sessionId, '') }}>清除</button>
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
                <th>traceId</th>
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
                    <td
                      style={{ fontFamily: 'monospace', fontSize: 11.5 }}
                      title={`点击复制 ${r.traceId}`}
                      onClick={(e) => {
                        e.stopPropagation()
                        void navigator.clipboard?.writeText(r.traceId).then(() => toast('traceId 已复制'))
                      }}
                    >
                      {r.traceId?.slice(0, 10)}…
                    </td>
                  </tr>
                )
              })}
              {items.length === 0 && (
                <tr><td colSpan={5}><div className="note gray">暂无运行记录</div></td></tr>
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
                  {(selected.status === 'FAILED' || selected.status === 'TIMEOUT') && (
                    <tr>
                      <th>重试</th>
                      <td>
                        <button className="btn sm primary" onClick={() => void onRetry(selected.runId)}>
                          🔁 重试
                        </button>
                        <span style={{ marginLeft: 8, color: 'var(--muted)', fontSize: 12 }}>
                          按原始输入 / 策略在原会话重放
                        </span>
                      </td>
                    </tr>
                  )}
                </tbody>
              </table>
              {selected.errorMsg && (
                <div className="note">{selected.errorMsg}</div>
              )}

              {(selected.input || selected.outputText) && (
                <div className="run-node-inspect" style={{ marginTop: 12, border: '1px solid #eef0f6', borderRadius: 10 }}>
                  <h4 style={{ marginBottom: 8 }}>输入 / 输出</h4>
                  <div className="run-io">
                    <div className="run-io-col">
                      <label>输入（用户原始请求）</label>
                      <pre>{selected.input || '—（旧数据未记录输入）'}</pre>
                    </div>
                    <div className="run-io-col">
                      <label>输出（最终产出）</label>
                      {(() => {
                        if (!selected.outputText) {
                          const hint = selected.status === 'RUNNING' ? '运行中…'
                            : selected.status === 'FAILED' ? '运行失败'
                              : selected.status === 'BLOCKED' ? '等待审批'
                                : '（无产出）'
                          return <div className="note gray">{hint}</div>
                        }
                        const parts = splitThink(selected.outputText)
                        return parts.answer
                          ? <Markdown text={parts.answer} />
                          : <div className="note gray">（无正文）</div>
                      })()}
                    </div>
                  </div>
                  {(() => {
                    if (!selected.outputText) return null
                    const parts = splitThink(selected.outputText)
                    return parts.think ? (
                      <div style={{ marginTop: 8 }}>
                        <ThinkingPanel
                          think={parts.think}
                          done={parts.done}
                          open={thinkOpen}
                          onToggle={() => setThinkOpen(!thinkOpen)}
                        />
                      </div>
                    ) : null
                  })()}
                </div>
              )}

              {(selected.plan?.length ?? 0) > 0 && (
                <div style={{ marginTop: 12 }}>
                  <h3 style={{ marginTop: 0 }}>计划步骤 <span className="sub">plan 策略</span></h3>
                  <table>
                    <thead><tr><th style={{ width: 36 }}>#</th><th>步骤</th><th style={{ width: 80 }}>状态</th></tr></thead>
                    <tbody>
                      {selected.plan!.map((st) => (
                        <tr key={st.index}>
                          <td>{st.index + 1}</td>
                          <td>
                            {st.title}
                            {st.output && (
                              <div className="note gray" style={{ marginTop: 4 }}>
                                {st.output.length > 120 ? `${st.output.slice(0, 120)}…` : st.output}
                              </div>
                            )}
                          </td>
                          <td>
                            {st.status === 'DONE' ? <span className="tag ok">完成</span>
                              : st.status === 'RUNNING' ? <span className="tag ok">执行中</span>
                                : <span className="tag gray">待执行</span>}
                          </td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}

              {selected.strategy === 'custom' && selected.graph && (
                <div style={{ marginTop: 12 }}>
                  <h3 style={{ marginTop: 0 }}>节点输入 / 输出</h3>
                  <RunGraphView graph={selected.graph} />
                </div>
              )}
              {selected.strategy === 'custom' && !selected.graph && (
                <div className="note gray" style={{ marginTop: 10 }}>
                  这个运行没有图快照（可能是旧版本数据）。
                </div>
              )}
            </>
          )}
        </div>
      </div>
      <ToastHost />
    </>
  )
}
