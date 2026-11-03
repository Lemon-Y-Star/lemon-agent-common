import { useEffect, useState } from 'react'
import Modal from './Modal'
import { fetchRunSnapshot, type RunSnapshot } from '../api/runtime'
import { splitThink } from './ThinkingPanel'

/**
 * 消息级运行详情弹窗（v4.5，Dify「对话日志」同款）：
 * 每条 AI 回答对应一次运行，点开看元信息（状态/耗时/Tokens/traceId）、
 * 节点/步骤执行视图（custom 图看 nodes，auto/plan 看步骤）与输入/输出原文。
 * 数据全来自现成的运行快照端点，零后端改动。
 */
export default function RunDetailModal(props: { runId: number; onClose: () => void }) {
  const [snap, setSnap] = useState<RunSnapshot | null>(null)
  const [error, setError] = useState('')
  const [showRaw, setShowRaw] = useState(false)

  useEffect(() => {
    let alive = true
    setSnap(null)
    setError('')
    fetchRunSnapshot(props.runId)
      .then((s) => {
        if (alive) setSnap(s)
      })
      .catch((err) => {
        if (alive) setError(err instanceof Error ? err.message : '加载运行详情失败')
      })
    return () => {
      alive = false
    }
  }, [props.runId])

  const durationMs = snap?.startedAt && snap?.endedAt
    ? Math.max(0, new Date(snap.endedAt).getTime() - new Date(snap.startedAt).getTime())
    : null
  const answer = snap?.outputText ? splitThink(snap.outputText).answer || snap.outputText : ''

  return (
    <Modal title={`运行详情 · #${props.runId}`} onClose={props.onClose}>
      {error && <div className="note" style={{ color: 'var(--danger)' }}>{error}</div>}
      {!snap && !error && <div className="note gray">加载中…</div>}
      {snap && (
        <div className="run-detail">
          <div className="rd-meta">
            <span>
              状态{' '}
              {snap.status === 'COMPLETED' ? <b className="tag ok">SUCCESS</b>
                : snap.status === 'FAILED' ? <b className="tag err">FAILED</b>
                  : snap.status === 'BLOCKED' ? <b className="tag warn">等待审批</b>
                    : <b className="tag gray">{snap.status}</b>}
            </span>
            <span>Agent：{snap.agentName ?? '平台默认'}</span>
            <span>策略：{snap.strategy}</span>
            <span>耗时：{durationMs != null ? `${(durationMs / 1000).toFixed(2)}s` : '—'}</span>
            <span>
              Tokens：{snap.usage ? `↑${snap.usage.inputTokens} ↓${snap.usage.outputTokens}` : '—'}
            </span>
            <span style={{ gridColumn: '1 / -1' }}>traceId：{snap.traceId}</span>
            {snap.errorMsg && (
              <span style={{ gridColumn: '1 / -1', color: 'var(--danger)' }}>错误：{snap.errorMsg}</span>
            )}
          </div>

          {snap.graph && snap.graph.nodes.length > 0 && (
            <>
              <h4>节点执行</h4>
              <ol className="rd-steps">
                {snap.graph.nodes.map((n) => (
                  <li key={n.id} className={`rd-step rd-${n.status.toLowerCase()}`}>
                    <span className="rd-idx">
                      {n.status === 'DONE' ? '✓' : n.status === 'SKIPPED' ? '—' : n.status === 'RUNNING' ? '▶' : '·'}
                    </span>
                    <span className="rd-title">{n.title}</span>
                    <span className="note gray">{n.status}</span>
                  </li>
                ))}
              </ol>
            </>
          )}

          {!snap.graph && snap.plan && snap.plan.length > 0 && (
            <>
              <h4>执行步骤</h4>
              <ol className="rd-steps">
                {snap.plan.map((st) => (
                  <li key={st.index} className={`rd-step rd-${st.status.toLowerCase()}`}>
                    <span className="rd-idx">{st.status === 'DONE' ? '✓' : '·'}</span>
                    <span className="rd-title">{st.title}</span>
                    <span className="note gray">{st.status}</span>
                  </li>
                ))}
              </ol>
            </>
          )}

          <h4>输入</h4>
          <pre className="rd-io">{snap.input ?? '（旧运行未记录输入）'}</pre>

          <h4>输出</h4>
          <pre className="rd-io">{answer || '（未完成，无输出）'}</pre>

          <button className="btn sm" style={{ marginTop: 8 }} onClick={() => setShowRaw((v) => !v)}>
            {showRaw ? '收起原始输出' : '查看原始输出（含思考块）'}
          </button>
          {showRaw && <pre className="rd-io">{snap.outputText ?? '—'}</pre>}
        </div>
      )}
    </Modal>
  )
}
