import { useCallback, useEffect, useState } from 'react'
import { fetchEvalOverview, type EvalBatchOverview } from '../api/eval'
import { useNavigate } from 'react-router-dom'
import { ApiError } from '../api/client'
import { ToastHost } from '../components/Toast'

/**
 * 我的评测（v3.8）：跨 Agent 的评测批次总览。
 * 每批在哪个 Agent、哪一版图、跑完没有——深看某个 Agent 的并排对比，跳到它的评测页。
 */

const fmt = (s: string | null | undefined) => (s ? s.slice(0, 19).replace('T', ' ') : '—')

export default function Evals() {
  const [batches, setBatches] = useState<EvalBatchOverview[]>([])
  const [error, setError] = useState('')
  const navigate = useNavigate()

  const reload = useCallback(async () => {
    try {
      const data = await fetchEvalOverview()
      setBatches(data.batches)
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
          <h2>我的评测</h2>
          <div className="desc">
            跨 Agent 的评测批次总览：改图前跑一批当基线、改完再跑一批，两批并排看哪条用例变了。
            点「打开评测页」看该 Agent 的 A/B 对比与节点分支差异。
          </div>
        </div>
      </div>
      <p className="status-line" role="status">{error}</p>

      <div className="card">
        <table>
          <thead>
            <tr>
              <th>批次</th>
              <th>Agent</th>
              <th>图版本</th>
              <th>进度</th>
              <th>备注</th>
              <th>发起时间</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {batches.map((b) => (
              <tr key={b.id}>
                <td><b>#{b.id}</b></td>
                <td>{b.agentName ?? `#${b.agentId}`}</td>
                <td>{b.graphVersion != null ? `v${b.graphVersion}` : '—'}</td>
                <td>
                  <span className={`tag ${b.finished === b.total ? 'ok' : 'warn'}`}>
                    {b.finished}/{b.total}
                  </span>
                </td>
                <td style={{ color: 'var(--muted)', fontSize: 12 }}>{b.note || '—'}</td>
                <td style={{ color: 'var(--muted)', fontSize: 12 }}>{fmt(b.createdAt)}</td>
                <td>
                  <button className="btn sm" onClick={() => navigate(`/agents/${b.agentId}/eval`)}>
                    打开评测页
                  </button>
                </td>
              </tr>
            ))}
            {batches.length === 0 && (
              <tr><td colSpan={7}><div className="note gray">
                还没有评测批次——到某个 Agent 的「执行图 → 评测回归」里跑一批。
              </div></td></tr>
            )}
          </tbody>
        </table>
      </div>
      <ToastHost />
    </>
  )
}
