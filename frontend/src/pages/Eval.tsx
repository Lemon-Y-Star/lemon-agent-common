import { useCallback, useEffect, useMemo, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import { createEvalBatch, fetchEvalBatches, type EvalBatch, type EvalItem } from '../api/eval'
import { ApiError } from '../api/client'
import { ToastHost, toast } from '../components/Toast'
import Markdown from '../components/Markdown'
import { stripThink } from '../components/ThinkingPanel'

/**
 * 图评测回归（v3.5）：一批用例按当前图版本跑一遍，改完再跑一批，两批并排看差异。
 *
 * 输出不存评测表——始终读运行的事实；批次只记「用例 → 运行」绑定与图版本。
 */

const SAMPLE = `紧急！生产库连接数打满，业务报错
想咨询一下发票怎么开`

/** 输出单元格：Markdown 渲染 + 限高滚动；运行中 / 失败给明确占位。 */
function OutputCell({ item }: { item: EvalItem | null | undefined }) {
  if (!item) return <div className="note gray">—</div>
  if (!item.output) {
    const hint = item.status === 'RUNNING' ? '运行中…'
      : item.status === 'FAILED' ? '运行失败'
        : item.status === 'BLOCKED' ? '等待审批'
          : '（无输出）'
    return <div className="note gray">{hint}</div>
  }
  return (
    <div className="eval-out">
      <Markdown text={stripThink(item.output)} />
    </div>
  )
}

export default function Eval() {
  const { agentId } = useParams()
  const id = Number(agentId)
  const navigate = useNavigate()

  const [inputs, setInputs] = useState(SAMPLE)
  const [note, setNote] = useState('')
  const [batches, setBatches] = useState<EvalBatch[]>([])
  const [left, setLeft] = useState<number | null>(null)
  const [right, setRight] = useState<number | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  const reload = useCallback(async () => {
    if (!Number.isFinite(id)) return
    try {
      const list = await fetchEvalBatches(id)
      setBatches(list)
      setLeft((prev) => prev ?? list[0]?.id ?? null)
      setRight((prev) => prev ?? list[1]?.id ?? null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载批次失败')
    }
  }, [id])

  useEffect(() => { void reload() }, [reload])

  // 有批次没跑完就轮询（评测是异步的）
  useEffect(() => {
    const pending = batches.some((b) => b.finished < b.total)
    if (!pending) return
    const timer = setInterval(() => void reload(), 3000)
    return () => clearInterval(timer)
  }, [batches, reload])

  async function onRun() {
    const cases = inputs.split('\n').map((s) => s.trim()).filter(Boolean)
    if (cases.length === 0) {
      setError('至少写一条用例')
      return
    }
    setBusy(true)
    setError('')
    try {
      const batch = await createEvalBatch(id, cases, note)
      toast(`已发起批次 #${batch.id}（图 v${batch.graphVersion}）`)
      setNote('')
      await reload()
      setLeft(batch.id)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '发起失败')
    } finally {
      setBusy(false)
    }
  }

  const leftBatch = useMemo(() => batches.find((b) => b.id === left) ?? null, [batches, left])
  const rightBatch = useMemo(() => batches.find((b) => b.id === right) ?? null, [batches, right])

  function diffRows() {
    const a = leftBatch?.items ?? []
    const b = rightBatch?.items ?? []
    const rows = Math.max(a.length, b.length)
    const result = []
    for (let i = 0; i < rows; i++) {
      const ai = a[i]
      const bi = b[i]
      const sameInput = (ai?.input ?? '') === (bi?.input ?? '')
      const sameOutput = (ai?.output ?? '') === (bi?.output ?? '')
      const sameSkip = JSON.stringify(ai?.skippedNodes ?? []) === JSON.stringify(bi?.skippedNodes ?? [])
      result.push({ i, ai, bi, sameInput, changed: !sameOutput || !sameSkip })
    }
    return result
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h2>图评测回归 · Agent #{id}</h2>
          <div className="desc">
            一批用例按当前执行图跑一遍；改图后再跑一批，两批并排看哪条用例的输出或分支变了。
            输出实时读运行，评测表只存「用例 → 运行」绑定。
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <button className="btn" onClick={() => navigate(`/agents/${id}/graph`)}>执行图</button>
          <button className="btn" onClick={() => navigate('/agents')}>返回</button>
        </div>
      </div>

      {error && <p className="status-line" role="status" style={{ color: 'var(--danger)' }}>{error}</p>}

      <div className="eval-wrap">
        <aside className="card">
          <h3>用例</h3>
          <textarea
            rows={6}
            value={inputs}
            onChange={(e) => setInputs(e.target.value)}
            placeholder="一行一条，最多 10 条"
            style={{ width: '100%', fontSize: 12.5, padding: '8px 10px', border: '1px solid #d5d9e2', borderRadius: 7, fontFamily: 'inherit' }}
          />
          <div className="field" style={{ marginTop: 8 }}>
            <label>备注（例如：改了分诊 prompt 之后）</label>
            <input value={note} onChange={(e) => setNote(e.target.value)} placeholder="本批改动说明" />
          </div>
          <button className="btn primary" disabled={busy} onClick={() => void onRun()}>
            {busy ? '发起中…' : '跑一批（当前图版本）'}
          </button>

          <h3 style={{ marginTop: 18 }}>历史批次</h3>
          {batches.length === 0 && <div className="note gray">还没有批次</div>}
          <div className="eval-batches">
            {batches.map((b) => (
              <div key={b.id} className="eval-batch-row">
                <div>
                  <b>#{b.id}</b> <span className="tag gray">v{b.graphVersion ?? '?'}</span>{' '}
                  <span className={`tag ${b.finished === b.total ? 'ok' : 'warn'}`}>
                    {b.finished}/{b.total}
                  </span>
                  {b.note && <div className="note gray" style={{ marginTop: 2 }}>{b.note}</div>}
                </div>
                <div className="eval-pick">
                  <button
                    className={`btn sm pick-a ${left === b.id ? 'on' : ''}`}
                    title="设为对比列 A"
                    onClick={() => setLeft(b.id)}
                  >A</button>
                  <button
                    className={`btn sm pick-b ${right === b.id ? 'on' : ''}`}
                    title="设为对比列 B"
                    onClick={() => setRight(b.id)}
                  >B</button>
                </div>
              </div>
            ))}
          </div>
        </aside>

        <div className="card eval-main">
          <h3>
            对比
            <span className="sub">
              A = {leftBatch ? `#${leftBatch.id}（v${leftBatch.graphVersion}）` : '未选'} ·
              B = {rightBatch ? `#${rightBatch.id}（v${rightBatch.graphVersion}）` : '未选'}
            </span>
          </h3>
          {!leftBatch && !rightBatch && <div className="note gray">先跑一批，或在左侧选择两个批次做对比</div>}
          {leftBatch && !rightBatch && (
            <div className="eval-table-wrap">
              <table>
                <colgroup>
                  <col style={{ width: 40 }} />
                  <col style={{ width: 180 }} />
                  <col />
                  <col style={{ width: 140 }} />
                </colgroup>
                <thead><tr><th>#</th><th>用例</th><th>输出</th><th>跳过节点</th></tr></thead>
                <tbody>
                  {leftBatch.items.map((it) => (
                    <tr key={it.seq}>
                      <td>{it.seq + 1}</td>
                      <td className="eval-case">{it.input}</td>
                      <td><OutputCell item={it} /></td>
                      <td style={{ fontSize: 12 }}>{(it.skippedNodes ?? []).join(', ') || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          {leftBatch && rightBatch && (
            <div className="eval-table-wrap">
              <table>
                <colgroup>
                  <col style={{ width: 40 }} />
                  <col style={{ width: 180 }} />
                  <col />
                  <col />
                  <col style={{ width: 118 }} />
                </colgroup>
                <thead>
                  <tr>
                    <th>#</th>
                    <th>用例</th>
                    <th><span className="ab-chip a">A</span>输出 #{leftBatch.id}<span className="th-sub">v{leftBatch.graphVersion ?? '?'}</span></th>
                    <th><span className="ab-chip b">B</span>输出 #{rightBatch.id}<span className="th-sub">v{rightBatch.graphVersion ?? '?'}</span></th>
                    <th>差异</th>
                  </tr>
                </thead>
                <tbody>
                  {diffRows().map((row) => (
                    <tr key={row.i} className={row.changed ? 'eval-changed' : ''}>
                      <td>{row.i + 1}</td>
                      <td className="eval-case">{row.ai?.input ?? row.bi?.input}</td>
                      <td><OutputCell item={row.ai} /></td>
                      <td><OutputCell item={row.bi} /></td>
                      <td>
                        {!row.sameInput && <div className="tag warn">用例不同</div>}
                        {row.sameInput && row.changed && <div className="tag err">输出/分支<br />有变化</div>}
                        {row.sameInput && !row.changed && <div className="tag ok">一致</div>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
      <ToastHost />
    </>
  )
}
