import { useEffect, useMemo, useRef, useState } from 'react'
import type { RunGraphView as Graph } from '../api/runtime'
import Markdown from './Markdown'

/**
 * 运行历史的执行图图例（只读）：节点按保存的 x/y 摆位，SVG 连线，状态着色。
 * 点节点看该节点的输入/输出——输入是「任务+上游产出」，输出是节点产出，都来自 graph_state_json。
 */

const NODE_W = 150
const NODE_H = 72

const TYPE_LABEL: Record<string, string> = {
  START: '开始', LLM: '大模型', TOOL: '工具', APPROVAL: '审批门', END: '结束',
}

const STATUS_META: Record<string, { text: string; dot: string }> = {
  DONE: { text: '已完成', dot: '#16a34a' },
  RUNNING: { text: '运行中', dot: '#2563eb' },
  PENDING: { text: '待执行', dot: '#94a3b8' },
  SKIPPED: { text: '已跳过', dot: '#d97706' },
}

export default function RunGraphView({ graph }: { graph: Graph }) {
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const wrapRef = useRef<HTMLDivElement>(null)
  const [wrapW, setWrapW] = useState(0)

  // 画布比容器宽时整体等比缩小，保证一屏看全；仍保留点击查看
  useEffect(() => {
    const el = wrapRef.current
    if (!el) return
    const update = () => setWrapW(el.clientWidth)
    update()
    const ro = new ResizeObserver(update)
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  // 没有坐标的节点（旧图）按 4 列网格兜底
  const positioned = useMemo(() => {
    return graph.nodes.map((n, i) => ({
      ...n,
      x: n.x ?? (i % 4) * (NODE_W + 46) + 20,
      y: n.y ?? Math.floor(i / 4) * (NODE_H + 46) + 20,
    }))
  }, [graph])

  const byId = useMemo(() => Object.fromEntries(positioned.map((n) => [n.id, n])), [positioned])
  const width = Math.max(...positioned.map((n) => n.x + NODE_W), 0) + 20
  const height = Math.max(...positioned.map((n) => n.y + NODE_H), 0) + 20
  const selected = selectedId ? byId[selectedId] : null
  const scale = width > 0 && wrapW > 40 ? Math.min(1, (wrapW - 2) / width) : 1

  return (
    <div className="run-graph">
      <div className="run-graph-legend">
        <span className="sub">执行图 v{graph.version} · 图例：</span>
        {Object.entries(STATUS_META).map(([k, v]) => (
          <span key={k} className="run-legend-item">
            <i style={{ background: v.dot }} />{v.text}
          </span>
        ))}
        <span style={{ marginLeft: 'auto', color: 'var(--muted)', fontSize: 12 }}>点节点看输入 / 输出</span>
      </div>

      <div className="run-graph-canvas" ref={wrapRef} style={{ height: height * scale }}>
        <div style={{ width, height, transform: `scale(${scale})`, transformOrigin: 'top left', position: 'absolute', top: 0, left: 0 }}>
        <svg width={width} height={height} style={{ position: 'absolute', inset: 0 }}>
          <defs>
            <marker id="rg-arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
              <path d="M0,0 L8,4 L0,8 Z" fill="#b6bdcb" />
            </marker>
          </defs>
          {graph.edges.map((e, i) => {
            const a = byId[e.from]
            const b = byId[e.to]
            if (!a || !b) return null
            const x1 = a.x + NODE_W / 2
            const y1 = a.y + NODE_H
            const x2 = b.x + NODE_W / 2
            const y2 = b.y
            const midY = (y1 + y2) / 2
            const skipped = b.status === 'SKIPPED'
            return (
              <g key={i}>
                <path
                  d={`M${x1},${y1} C${x1},${midY} ${x2},${midY} ${x2},${y2}`}
                  fill="none"
                  stroke={skipped ? '#e5b97f' : '#b6bdcb'}
                  strokeWidth={1.5}
                  strokeDasharray={skipped ? '5 4' : undefined}
                  markerEnd="url(#rg-arrow)"
                />
                {e.route && e.route !== 'always' && (
                  <text x={(x1 + x2) / 2} y={midY - 4} textAnchor="middle" fontSize={10} fill="#8a93a6">
                    {e.route}
                  </text>
                )}
              </g>
            )
          })}
        </svg>
        {positioned.map((n) => {
          const meta = STATUS_META[n.status] ?? STATUS_META.PENDING
          return (
            <div
              key={n.id}
              className={`run-node st-${n.status.toLowerCase()} ${selectedId === n.id ? 'sel' : ''}`}
              style={{ left: n.x, top: n.y, width: NODE_W, height: NODE_H }}
              onClick={() => setSelectedId(selectedId === n.id ? null : n.id)}
              title={`${n.title}（${meta.text}）`}
            >
              <div className="run-node-head">
                <i style={{ background: meta.dot }} />
                <b>{n.title}</b>
              </div>
              <div className="run-node-sub">{TYPE_LABEL[n.type] ?? n.type} · {meta.text}</div>
            </div>
          )
        })}
        </div>
      </div>

      {selected && (
        <div className="run-node-inspect">
          <h4>
            {selected.title}
            <span className="tag gray" style={{ marginLeft: 8 }}>{TYPE_LABEL[selected.type] ?? selected.type}</span>
            <span className={`tag ${selected.status === 'DONE' ? 'ok' : selected.status === 'SKIPPED' ? 'warn' : 'gray'}`}
              style={{ marginLeft: 6 }}>
              {STATUS_META[selected.status]?.text ?? selected.status}
            </span>
            <span className="x" onClick={() => setSelectedId(null)}>✕</span>
          </h4>
          <div className="run-io">
            <div className="run-io-col">
              <label>输入（任务 + 上游产出）</label>
              <pre>{selected.input || '—'}</pre>
            </div>
            <div className="run-io-col">
              <label>输出</label>
              {selected.output ? <Markdown text={selected.output} /> : <div className="note gray">—</div>}
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
