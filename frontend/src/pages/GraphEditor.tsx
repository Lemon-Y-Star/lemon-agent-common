import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router-dom'
import {
  fetchGraph,
  saveGraph,
  type AgentGraph,
  type GraphEdge,
  type GraphNode,
  type GraphNodeType,
} from '../api/graph'
import { fetchTools, type ToolInfo } from '../api/agents'
import { runSandbox, type SandboxResult } from '../api/sandbox'
import { createRun, fetchRunSnapshot, type RunSnapshot } from '../api/runtime'
import { createWorkflow, fetchWorkflowGraph, saveWorkflowGraph } from '../api/workflow'
import { ApiError } from '../api/client'
import Modal from '../components/Modal'
import { ToastHost, toast } from '../components/Toast'

/**
 * 执行图画布（v3.2.1）：拖拽定位 + 连线 + 检查器 + 保存校验。
 *
 * 保存走服务端校验（类型/拓扑/工具存在/无环），前端只做「明显错误」的即时提示，
 * 避免两套校验规则打架：**能存下就一定能跑**这条保证由后端负责。
 *
 * v3.9 起同一画布复用于工作流（{@code workflowId} 传入即工作流模式）：
 * 加载/保存走工作流端点，保存 = 工作流新版本。
 */

const NODE_W = 150
const NODE_H = 96

const TYPE_LABEL: Record<GraphNodeType, string> = {
  START: '开始',
  LLM: '大模型',
  TOOL: '工具',
  CODE: '代码（Python）',
  APPROVAL: '审批门',
  END: '结束',
}

const TYPE_COLOR: Record<GraphNodeType, string> = {
  START: '#16a34a',
  LLM: '#4f46e5',
  TOOL: '#0891b2',
  CODE: '#9333ea',
  APPROVAL: '#d97706',
  END: '#475569',
}

/** 边的条件路由 → 编辑器的四选一（always / nonEmpty / empty / contains）。 */
type RouteKind = 'always' | 'nonEmpty' | 'empty' | 'startsWith' | 'contains'

function routeKind(route?: string | null): RouteKind {
  if (!route) return 'always'
  if (route === 'nonEmpty' || route === 'empty') return route
  if (route.startsWith('startsWith:')) return 'startsWith'
  if (route.startsWith('contains:')) return 'contains'
  return 'always'
}

function routeKeyword(route?: string | null): string {
  if (!route) return ''
  if (route.startsWith('startsWith:')) return route.slice('startsWith:'.length)
  if (route.startsWith('contains:')) return route.slice('contains:'.length)
  return ''
}

function buildRoute(kind: RouteKind, keyword: string): string | null {
  if (kind === 'always') return null
  if (kind === 'startsWith' || kind === 'contains') return `${kind}:${keyword.trim() || '关键词'}`
  return kind
}

const GAP_X = 40
const GAP_Y = 52

/** 网格位置：列数由画布实际宽度决定（宽屏 3~4 列、窄屏 2 列），避免节点排到可视区外。 */
function gridPos(index: number, columns: number): { x: number; y: number } {
  const cols = Math.max(1, columns)
  return {
    x: 40 + (index % cols) * (NODE_W + GAP_X),
    y: 40 + Math.floor(index / cols) * (NODE_H + GAP_Y),
  }
}

export default function GraphEditor({ workflowId }: { workflowId?: number } = {}) {
  const { agentId } = useParams()
  const isWorkflow = workflowId !== undefined
  const id = isWorkflow ? (workflowId as number) : Number(agentId)
  const navigate = useNavigate()

  const [graph, setGraph] = useState<AgentGraph | null>(null)
  const [tools, setTools] = useState<ToolInfo[]>([])
  const [selected, setSelected] = useState<string | null>(null)
  const [connectingFrom, setConnectingFrom] = useState<string | null>(null)
  const [dirty, setDirty] = useState(false)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [argsText, setArgsText] = useState('')
  /** CODE 节点试运行状态（v4.4）：null=未运行；运行中 disabled */
  const [sandboxRun, setSandboxRun] = useState<{ loading: boolean; result?: SandboxResult; error?: string } | null>(null)
  /** 画布整图试运行（v4.5，Dify Workflow Run 同款）：null=未开；input=输入框；running=轮询中；done=出结果 */
  const [canvasRun, setCanvasRun] = useState<{
    phase: 'input' | 'running' | 'done'
    input: string
    runId?: number
    snapshot?: RunSnapshot | null
    error?: string
  } | null>(null)
  /** 节点运行染色（独立于弹窗状态：关闭弹窗后画布保留上一次运行的结果，直到下一次试运行重置） */
  const [runStatusMap, setRunStatusMap] = useState<Record<string, { status: string; output: string | null }> | null>(null)

  const dragRef = useRef<{ id: string; dx: number; dy: number } | null>(null)
  const canvasRef = useRef<HTMLDivElement | null>(null)
  const innerRef = useRef<HTMLDivElement | null>(null)
  const fitRef = useRef(false)

  /** 画布能放几列（留 40px 边距）。 */
  const columns = useCallback(() => {
    const width = canvasRef.current?.clientWidth ?? 760
    return Math.max(1, Math.floor((width - 40) / (NODE_W + GAP_X)))
  }, [])

  const reload = useCallback(async () => {
    if (!Number.isFinite(id)) return
    try {
      const g = isWorkflow ? await fetchWorkflowGraph(id) : await fetchGraph(id)
      setGraph(g)
      setDirty(false)
      setError('')
      fitRef.current = true
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载执行图失败')
    }
  }, [id, isWorkflow])

  useEffect(() => {
    void reload()
    void fetchTools().then(setTools).catch(() => setTools([]))
  }, [reload])

  // 加载后按当前画布宽度补齐坐标；若已有坐标排到可视区外，就地重排（只影响本地，不置脏）
  useEffect(() => {
    if (!graph || !fitRef.current || graph.nodes.length === 0) return
    const cols = columns()
    const maxRight = Math.max(...graph.nodes.map((n) => (n.x ?? 0) + NODE_W))
    const canvasWidth = canvasRef.current?.clientWidth ?? 760
    const needsLayout = graph.nodes.some((n) => n.x == null || n.y == null) || maxRight > canvasWidth - 12
    if (needsLayout) {
      setGraph((g) => (g ? { ...g, nodes: g.nodes.map((n, i) => ({ ...n, ...gridPos(i, cols) })) } : g))
    }
    fitRef.current = false
  }, [graph, columns])

  const selectedNode = useMemo(
    () => graph?.nodes.find((n) => n.id === selected) ?? null,
    [graph, selected],
  )

  useEffect(() => {
    setArgsText(selectedNode?.args ? JSON.stringify(selectedNode.args, null, 2) : '')
  }, [selectedNode?.id])

  const editable = graph?.editable ?? false

  function updateNode(nodeId: string, patch: Partial<GraphNode>) {
    setGraph((g) => (g ? { ...g, nodes: g.nodes.map((n) => (n.id === nodeId ? { ...n, ...patch } : n)) } : g))
    setDirty(true)
  }

  function addNode(type: GraphNodeType) {
    if (!graph) return
    const exist = new Set(graph.nodes.map((n) => n.id))
    let i = graph.nodes.length + 1
    while (exist.has(`n${i}`)) i++
    const node: GraphNode = { id: `n${i}`, type, title: TYPE_LABEL[type], ...gridPos(graph.nodes.length, columns()) }
    setGraph({ ...graph, nodes: [...graph.nodes, node] })
    setSelected(node.id)
    setDirty(true)
  }

  function removeNode(nodeId: string) {
    if (!graph) return
    setGraph({
      ...graph,
      nodes: graph.nodes.filter((n) => n.id !== nodeId),
      edges: graph.edges.filter((e) => e.from !== nodeId && e.to !== nodeId),
    })
    if (selected === nodeId) setSelected(null)
    if (connectingFrom === nodeId) setConnectingFrom(null)
    setDirty(true)
  }

  function connectTo(targetId: string) {
    if (!graph || !connectingFrom || connectingFrom === targetId) return
    if (graph.edges.some((e) => e.from === connectingFrom && e.to === targetId)) {
      setConnectingFrom(null)
      return
    }
    setGraph({ ...graph, edges: [...graph.edges, { from: connectingFrom, to: targetId }] })
    setConnectingFrom(null)
    setDirty(true)
  }

  function updateEdgeRoute(edge: GraphEdge, route: string | null) {
    if (!graph) return
    setGraph({
      ...graph,
      edges: graph.edges.map((e) => (
        e.from === edge.from && e.to === edge.to ? { ...e, route } : e
      )),
    })
    setDirty(true)
  }

  function removeEdge(edge: GraphEdge) {
    if (!graph) return
    setGraph({
      ...graph,
      edges: graph.edges.filter((e) => !(e.from === edge.from && e.to === edge.to)),
    })
    setDirty(true)
  }

  function onCanvasMouseMove(e: React.MouseEvent) {
    const drag = dragRef.current
    if (!drag || !innerRef.current) return
    const rect = innerRef.current.getBoundingClientRect()
    updateNode(drag.id, {
      x: Math.max(0, Math.round(e.clientX - rect.left - drag.dx)),
      y: Math.max(0, Math.round(e.clientY - rect.top - drag.dy)),
    })
  }

  async function onSave() {
    if (!graph) return
    setSaving(true)
    setError('')
    try {
      if (isWorkflow) {
        const detail = await saveWorkflowGraph(id, {
          nodes: graph.nodes.map((n) => ({ ...n })),
          edges: graph.edges,
        })
        setGraph((g) => (g ? { ...g, version: detail.currentVersion } : g))
        setDirty(false)
        toast(`已保存为 v${detail.currentVersion}`)
        return
      }
      const saved = await saveGraph(id, {
        nodes: graph.nodes.map((n) => ({ ...n })),
        edges: graph.edges,
      })
      const cols = columns()
      setGraph({ ...saved, nodes: saved.nodes.map((n, i) => ({ ...n, ...gridPos(i, cols) })) })
      setDirty(false)
      toast(`已保存为 v${saved.version}`)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '保存失败')
    } finally {
      setSaving(false)
    }
  }

  /** 另存为工作流：把当前 Agent 的执行图复制成工作流的 v1（v3.7）。 */
  async function onSaveAsWorkflow() {
    if (!graph || graph.nodes.length === 0) {
      setError('执行图是空的，先加节点再另存为工作流')
      return
    }
    const suggested = graph.nodes.find((n) => n.title)?.title ?? `Agent #${id} 的工作流`
    const name = window.prompt('工作流名称', suggested)
    if (!name || !name.trim()) return
    setSaving(true)
    setError('')
    try {
      const detail = await createWorkflow({ name: name.trim(), sourceAgentId: id })
      toast(`已另存为工作流「${detail.name}」v${detail.currentVersion}`)
      navigate('/workflows')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '另存为工作流失败')
    } finally {
      setSaving(false)
    }
  }

  function onArgsChange(text: string) {
    setArgsText(text)
    if (!selectedNode) return
    if (!text.trim()) {
      updateNode(selectedNode.id, { args: null })
      return
    }
    try {
      updateNode(selectedNode.id, { args: JSON.parse(text) as Record<string, unknown> })
      setError('')
    } catch {
      setError('入参不是合法 JSON，保存前请修正')
    }
  }

  function nodeToolsValue(node: GraphNode): string {
    if (node.tools === undefined || node.tools === null) return '__inherit__'
    if (node.tools.length === 0) return '__none__'
    return '__custom__'
  }

  const nodeById = new Map((graph?.nodes ?? []).map((n) => [n.id, n]))
  const emptyGraph = (graph?.nodes.length ?? 0) === 0
  const contentW = emptyGraph ? 0 : Math.max(320, ...(graph?.nodes ?? []).map((n) => (n.x ?? 0) + NODE_W + 40))
  const contentH = emptyGraph ? 0 : Math.max(480, ...(graph?.nodes ?? []).map((n) => (n.y ?? 0) + NODE_H + 40))

  /** 把节点按网格重排（位置丢失/挤在一起时一键恢复）。 */
  function onAutoLayout() {
    if (!graph) return
    const cols = columns()
    setGraph({ ...graph, nodes: graph.nodes.map((n, i) => ({ ...n, ...gridPos(i, cols) })) })
    setDirty(true)
  }

  /** 画布试运行轮询（v4.5）：2.5s 拉快照直到终态，节点染色实时更新；alive 防卸载后 setState。 */
  const canvasRunAlive = useRef(true)
  useEffect(() => {
    canvasRunAlive.current = true
    return () => {
      canvasRunAlive.current = false
    }
  }, [])

  async function pollCanvasRun(runId: number) {
    const terminal = ['COMPLETED', 'FAILED', 'BLOCKED', 'CANCELLED']
    for (;;) {
      await new Promise((r) => setTimeout(r, 2500))
      if (!canvasRunAlive.current) return
      try {
        const snap = await fetchRunSnapshot(runId)
        if (!canvasRunAlive.current) return
        setCanvasRun((prev) => (prev && prev.runId === runId ? { ...prev, snapshot: snap } : prev))
        // 节点染色独立保存：关弹窗不清，下一次试运行开始时重置
        const nodes = snap.graph?.nodes ?? []
        if (nodes.length > 0) {
          const map: Record<string, { status: string; output: string | null }> = {}
          for (const n of nodes) map[n.id] = { status: n.status, output: n.output ?? null }
          setRunStatusMap(map)
        }
        if (terminal.includes(snap.status)) {
          setCanvasRun((prev) => (prev && prev.runId === runId ? { ...prev, phase: 'done' } : prev))
          return
        }
      } catch (err) {
        if (!canvasRunAlive.current) return
        setCanvasRun((prev) => (prev && prev.runId === runId
          ? { ...prev, phase: 'done', error: err instanceof Error ? err.message : '查询运行状态失败' }
          : prev))
        return
      }
    }
  }

  /** 开始画布试运行：custom 策略发一次真实运行，节点按快照染色。 */
  async function onStartCanvasRun() {
    if (!canvasRun || !canvasRun.input.trim()) return
    setRunStatusMap(null)
    try {
      const created = await createRun({
        agentId: id,
        input: canvasRun.input.trim(),
        strategy: 'custom',
        clientRequestId: `canvas_run_${Date.now()}`,
      })
      setCanvasRun({ phase: 'running', input: canvasRun.input, runId: created.runId, snapshot: null })
      void pollCanvasRun(created.runId)
    } catch (err) {
      setCanvasRun((prev) => (prev
        ? { ...prev, phase: 'done', error: err instanceof ApiError ? err.message : '发起试运行失败' }
        : prev))
    }
  }

  /** 画布节点的运行染色（v4.5）：从独立染色 Map 查（关弹窗不清除）。 */
  function runNodeStatus(nodeId: string): string | null {
    const n = runStatusMap?.[nodeId]
    return n ? n.status : null
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h2>{isWorkflow ? `工作流画布 · #${id}` : `执行图 · Agent #${id}`}</h2>
          <div className="desc">
            五类节点（开始 / 大模型 / 工具 / 审批门 / 结束），按拓扑序调度；保存时服务端校验唯一 START、
            必达、无环与工具存在。<b>能存下就一定跑得起来。</b>
            {isWorkflow && ' 工作流模式下每次保存生成新版本；发布为工具后由 auto/plan 对话调用。'}
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <span className="tag gray">v{graph?.version ?? 0}</span>
          {dirty && <span className="tag warn">未保存</span>}
          <button className="btn" onClick={() => navigate(isWorkflow ? '/workflows' : '/agents')}>返回</button>
          {!isWorkflow && (
            <button
              className="btn"
              disabled={canvasRun?.phase === 'running'}
              onClick={() => setCanvasRun({ phase: 'input', input: '' })}
            >
              ▶ 试运行
            </button>
          )}
          {!isWorkflow && <button className="btn" onClick={() => navigate(`/agents/${id}/eval`)}>评测回归</button>}
          {!isWorkflow && <button className="btn" onClick={() => navigate('/chat')}>去对话</button>}
          {editable && (
            <button className="btn" onClick={onAutoLayout}>整理布局</button>
          )}
          {editable && !isWorkflow && (
            <button className="btn" onClick={() => void onSaveAsWorkflow()}>另存为工作流</button>
          )}
          {editable && (
            <button className="btn primary" disabled={saving || !dirty} onClick={() => void onSave()}>
              {saving ? '保存中…' : '保存'}
            </button>
          )}
        </div>
      </div>

      {!editable && graph && (
        <div className="note gray" style={{ marginBottom: 10 }}>
          平台公共 Agent 的执行图只读（可查看结构，不可编辑）。
        </div>
      )}
      {error && <p className="status-line" role="status" style={{ color: 'var(--danger)' }}>{error}</p>}

      <div className="ge-wrap">
        <aside className="ge-palette card">
          <h3>节点</h3>
          {editable ? (
            <div className="ge-palette-btns">
              {(['LLM', 'TOOL', 'CODE', 'APPROVAL', 'START', 'END'] as GraphNodeType[]).map((t) => (
                <button key={t} className="btn sm" onClick={() => addNode(t)}>＋ {TYPE_LABEL[t]}</button>
              ))}
            </div>
          ) : (
            <div className="note gray">只读</div>
          )}
          <div className="ge-tips">
            <b>连线</b>：点节点上的「连线」，再点目标节点的「连到这里」。
            <br />
            <b>拖拽</b>：直接拖动节点卡片调整位置（位置随图保存）。
            <br />
            <b>审批门</b>：运行到这里会挂起，等你批准后继续。
            <br />
            <b>条件分支</b>：给出边设「非空 / 为空 / 包含关键词」，没命中的下游节点会被跳过（不会报错）。
          </div>
          {connectingFrom && (
            <div className="ge-connecting">
              正在从 {connectingFrom} 连线… 点目标节点「连到这里」，或点此
              <button className="btn sm" onClick={() => setConnectingFrom(null)}>取消</button>
            </div>
          )}
        </aside>

        <div
          className="ge-canvas"
          ref={canvasRef}
          onMouseMove={onCanvasMouseMove}
          onMouseUp={() => { dragRef.current = null }}
          onMouseLeave={() => { dragRef.current = null }}
          onClick={() => { if (!connectingFrom) setSelected(null) }}
        >
          <div
            className="ge-canvas-inner"
            ref={innerRef}
            style={{ width: contentW, height: contentH }}
          >
          <svg className="ge-links" width="100%" height="100%">
            <defs>
              <marker id="arrow" markerWidth="8" markerHeight="8" refX="7" refY="4" orient="auto">
                <path d="M0,0 L8,4 L0,8 z" fill="#94a3b8" />
              </marker>
            </defs>
            {(graph?.edges ?? []).map((e, i) => {
              const from = nodeById.get(e.from)
              const to = nodeById.get(e.to)
              if (!from || !to) return null
              const x1 = (from.x ?? 0) + NODE_W
              const y1 = (from.y ?? 0) + NODE_H / 2
              const x2 = to.x ?? 0
              const y2 = (to.y ?? 0) + NODE_H / 2
              const mid = (x1 + x2) / 2
              return (
                <g key={i}>
                  <path
                    d={`M ${x1} ${y1} C ${mid} ${y1}, ${mid} ${y2}, ${x2} ${y2}`}
                    fill="none"
                    stroke={e.route ? '#d97706' : '#94a3b8'}
                    strokeWidth="1.6"
                    strokeDasharray={e.route ? '5 3' : undefined}
                    markerEnd="url(#arrow)"
                  />
                  {e.route && (
                    <text x={mid - 6} y={(y1 + y2) / 2 - 6} fontSize="10" fill="#b45309">{e.route}</text>
                  )}
                </g>
              )
            })}
          </svg>

          {(graph?.nodes ?? []).map((n) => {
            const rn = runNodeStatus(n.id)
            return (
              <div
                key={n.id}
                className={`ge-node ${selected === n.id ? 'selected' : ''} ${connectingFrom === n.id ? 'connecting' : ''} ${rn ? `run-${rn.toLowerCase()}` : ''}`}
                style={{ left: n.x ?? 0, top: n.y ?? 0, width: NODE_W, height: NODE_H, borderLeft: `3px solid ${TYPE_COLOR[n.type]}` }}
                onMouseDown={(e) => {
                  if (!editable) return
                  const rect = innerRef.current?.getBoundingClientRect()
                  if (!rect) return
                  dragRef.current = { id: n.id, dx: e.clientX - rect.left - (n.x ?? 0), dy: e.clientY - rect.top - (n.y ?? 0) }
                  setSelected(n.id)
                }}
                onClick={(e) => { e.stopPropagation(); setSelected(n.id) }}
              >
                <div className="ge-node-head">
                  <span className="ge-node-type" style={{ background: TYPE_COLOR[n.type] }}>{TYPE_LABEL[n.type]}</span>
                  <span className="ge-node-id">{n.id}</span>
                  {rn && (
                    <span className={`ge-run-dot ${rn === 'DONE' ? 'ok' : rn === 'RUNNING' ? 'run' : rn === 'SKIPPED' ? 'skip' : 'err'}`}
                      title={rn}>{rn === 'DONE' ? '✓' : rn === 'RUNNING' ? '▶' : rn === 'SKIPPED' ? '—' : '✗'}</span>
                  )}
                </div>
                <div className="ge-node-title">{n.title || n.id}</div>
                {rn === 'DONE' && runStatusMap?.[n.id]?.output && (
                  <div className="ge-run-output" title={runStatusMap[n.id].output ?? ''}>
                    {runStatusMap[n.id].output}
                  </div>
                )}
                {editable && (
                  <div className="ge-node-actions">
                    <button
                      className="btn sm"
                      onMouseDown={(e) => e.stopPropagation()}
                      onClick={(e) => { e.stopPropagation(); setConnectingFrom(n.id) }}
                    >
                      连线
                    </button>
                    {connectingFrom && connectingFrom !== n.id && (
                      <button
                        className="btn sm primary"
                        onMouseDown={(e) => e.stopPropagation()}
                        onClick={(e) => { e.stopPropagation(); connectTo(n.id) }}
                      >
                        连到这里
                      </button>
                    )}
                  </div>
                )}
              </div>
            )
          })}
          </div>
          {emptyGraph && (
            <div className="ge-empty">
              <div className="ge-empty-icon">🧩</div>
              <b>{isWorkflow ? '这个工作流还没有图' : '这个 Agent 还没有执行图'}</b>
              <p>
                {editable
                  ? '点左侧「＋ 大模型」加节点，用「连线」串成 START → … → END，保存后即可用 custom 策略跑。'
                  : '平台公共 Agent 的执行图只读；要自己画，请先创建一个自己的 Agent。'}
              </p>
              {editable && (
                <button className="btn primary sm" onClick={() => addNode('LLM')}>
                  ＋ 先加一个大模型节点
                </button>
              )}
            </div>
          )}
        </div>

        <aside className="ge-inspector card">
          <h3>检查器</h3>
          {!selectedNode && <div className="note gray">选中一个节点查看/编辑属性</div>}
          {selectedNode && (
            <div className="ge-form">
              <div className="field">
                <label>节点 ID（只读）</label>
                <input value={selectedNode.id} readOnly />
              </div>
              <div className="field">
                <label>标题</label>
                <input
                  value={selectedNode.title ?? ''}
                  disabled={!editable}
                  onChange={(e) => updateNode(selectedNode.id, { title: e.target.value })}
                />
              </div>
              <div className="field">
                <label>类型（只读）</label>
                <input value={`${selectedNode.type} · ${TYPE_LABEL[selectedNode.type]}`} readOnly />
              </div>

              {selectedNode.type === 'LLM' && (
                <>
                  <div className="field">
                    <label>节点指令（本步做什么）</label>
                    <textarea
                      rows={5}
                      value={selectedNode.prompt ?? ''}
                      disabled={!editable}
                      onChange={(e) => updateNode(selectedNode.id, { prompt: e.target.value })}
                      placeholder="例如：调用 current_time 获取当前时间，并原样给出时间。"
                    />
                  </div>
                  <div className="field">
                    <label>节点可用工具</label>
                    <select
                      value={nodeToolsValue(selectedNode)}
                      disabled={!editable}
                      onChange={(e) => {
                        const v = e.target.value
                        if (v === '__inherit__') updateNode(selectedNode.id, { tools: undefined })
                        else if (v === '__none__') updateNode(selectedNode.id, { tools: [] })
                        else updateNode(selectedNode.id, { tools: tools.map((t) => t.name) })
                      }}
                    >
                      <option value="__inherit__">继承 Agent 的授权</option>
                      <option value="__none__">不装配任何工具</option>
                      <option value="__custom__">自定义白名单</option>
                    </select>
                    {nodeToolsValue(selectedNode) === '__custom__' && (
                      <div className="ge-toolchecks">
                        {tools.map((t) => (
                          <label key={t.name} title={t.description}>
                            <input
                              type="checkbox"
                              disabled={!editable}
                              checked={(selectedNode.tools ?? []).includes(t.name)}
                              onChange={(e) => {
                                const cur = new Set(selectedNode.tools ?? [])
                                if (e.target.checked) cur.add(t.name)
                                else cur.delete(t.name)
                                updateNode(selectedNode.id, { tools: [...cur] })
                              }}
                            />
                            {t.label || t.name}
                          </label>
                        ))}
                      </div>
                    )}
                  </div>
                </>
              )}

              {selectedNode.type === 'TOOL' && (
                <>
                  <div className="field">
                    <label>工具</label>
                    <select
                      value={selectedNode.tool ?? ''}
                      disabled={!editable}
                      onChange={(e) => updateNode(selectedNode.id, { tool: e.target.value })}
                    >
                      <option value="">选择工具…</option>
                      {tools.filter((t) => t.available).map((t) => (
                        <option key={t.name} value={t.name} title={t.description}>
                          {t.label || t.name}{t.needsApproval ? '（写操作）' : ''}
                        </option>
                      ))}
                    </select>
                  </div>
                  <div className="field">
                    <label>固定入参（JSON，作者配置 = 已授权，不再走审批）</label>
                    <textarea
                      rows={5}
                      value={argsText}
                      disabled={!editable}
                      onChange={(e) => onArgsChange(e.target.value)}
                      placeholder={'{\n  "title": "日常巡检",\n  "priority": "P2"\n}'}
                    />
                  </div>
                </>
              )}

              {selectedNode.type === 'CODE' && (
                <>
                  <div className="field">
                    <label>Python 代码（隔离沙箱执行：禁网、限内存/超时；已预装 numpy/pandas）</label>
                    <textarea
                      rows={12}
                      value={selectedNode.code ?? ''}
                      disabled={!editable}
                      onChange={(e) => updateNode(selectedNode.id, { code: e.target.value })}
                      className="ge-code"
                      placeholder={"# 用 print() 输出结果；上游节点产出在 upstream 列表里\nimport math\nprint('圆周率:', math.pi)"}
                      spellCheck={false}
                    />
                  </div>
                  <div className="field">
                    <button
                      type="button"
                      className="btn"
                      disabled={!editable || sandboxRun?.loading || !(selectedNode.code ?? '').trim()}
                      onClick={async () => {
                        setSandboxRun({ loading: true })
                        try {
                          const r = await runSandbox(selectedNode.code ?? '')
                          setSandboxRun({ loading: false, result: r })
                        } catch (err) {
                          setSandboxRun({ loading: false, error: err instanceof ApiError ? err.message : '沙箱调用失败' })
                        }
                      }}
                    >
                      {sandboxRun?.loading ? '运行中…' : '▶ 试运行（10s 上限）'}
                    </button>
                  </div>
                  {sandboxRun?.error && <p className="note danger">{sandboxRun.error}</p>}
                  {sandboxRun?.result && (
                    <div className="field">
                      <label>
                        运行结果（{sandboxRun.result.durationMs}ms，退出码 {sandboxRun.result.exitCode}
                        {sandboxRun.result.timedOut ? '，已超时终止' : ''}
                        {sandboxRun.result.truncated ? '，输出被截断' : ''}）
                      </label>
                      <pre className="ge-code-out">{(sandboxRun.result.stdout || '(无输出)')
                        + (sandboxRun.result.stderr ? '\n[stderr] ' + sandboxRun.result.stderr : '')}</pre>
                    </div>
                  )}
                </>
              )}

              {selectedNode.type === 'APPROVAL' && (
                <div className="field">
                  <label>审批提示语</label>
                  <textarea
                    rows={3}
                    value={selectedNode.approvalPrompt ?? ''}
                    disabled={!editable}
                    onChange={(e) => updateNode(selectedNode.id, { approvalPrompt: e.target.value })}
                    placeholder="例如：计划已生成，是否继续执行？"
                  />
                </div>
              )}

              <div className="field">
                <label>出边（{graph?.edges.filter((e) => e.from === selectedNode.id).length ?? 0}）</label>
                <div className="ge-edges">
                  {(graph?.edges ?? []).filter((e) => e.from === selectedNode.id).map((e) => (
                    <div key={`${e.from}-${e.to}`} className="ge-edge-col">
                      <div className="ge-edge-row">
                        <span>{e.from} → {e.to}</span>
                        {editable && (
                          <button className="btn sm danger" onClick={() => removeEdge(e)}>删除</button>
                        )}
                      </div>
                      <div className="ge-edge-route">
                        <select
                          disabled={!editable || selectedNode.type === 'START'}
                          value={routeKind(e.route)}
                          onChange={(ev) => updateEdgeRoute(
                            e, buildRoute(ev.target.value as RouteKind, routeKeyword(e.route)),
                          )}
                        >
                          <option value="always">恒走</option>
                          <option value="nonEmpty">上游产出非空</option>
                          <option value="empty">上游产出为空</option>
                          <option value="startsWith">上游产出以关键词开头（推荐）</option>
                          <option value="contains">上游产出包含关键词</option>
                        </select>
                        {(routeKind(e.route) === 'startsWith' || routeKind(e.route) === 'contains') && (
                          <input
                            disabled={!editable}
                            defaultValue={routeKeyword(e.route)}
                            placeholder="关键词（≤20 字）"
                            onBlur={(ev) => updateEdgeRoute(
                              e, buildRoute(routeKind(e.route), ev.target.value),
                            )}
                          />
                        )}
                      </div>
                    </div>
                  ))}
                  {(graph?.edges ?? []).filter((e) => e.from === selectedNode.id).length === 0 && (
                    <div className="note gray">还没有出边（END 节点不需要）</div>
                  )}
                </div>
                <label style={{ marginTop: 8 }}>
                  入边（{(graph?.edges ?? []).filter((e) => e.to === selectedNode.id).length}）
                </label>
                <div className="ge-edges">
                  {(graph?.edges ?? []).filter((e) => e.to === selectedNode.id).map((e) => (
                    <div key={`${e.from}-${e.to}-in`} className="ge-edge-row">
                      <span>{e.from} → {e.to}</span>
                    </div>
                  ))}
                </div>
              </div>

              {editable && (
                <button className="btn danger" onClick={() => removeNode(selectedNode.id)}>
                  删除节点
                </button>
              )}
            </div>
          )}
        </aside>
      </div>

      {/* 画布整图试运行（v4.5，Dify Workflow Run 同款）：不进对话直接跑一次，节点实时染色 */}
      {canvasRun && (
        <Modal
          title={canvasRun.phase === 'input' ? '画布试运行' : `试运行 · #${canvasRun.runId ?? ''}`}
          onClose={() => {
            if (canvasRun.phase !== 'running') setCanvasRun(null)
          }}
        >
          {canvasRun.phase === 'input' && (
            <form
              onSubmit={(e) => {
                e.preventDefault()
                void onStartCanvasRun()
              }}
            >
              <div className="field">
                <label>测试输入（将以 custom 策略真实运行一次本图，产生运行记录）</label>
                <textarea
                  autoFocus
                  value={canvasRun.input}
                  onChange={(e) => setCanvasRun({ ...canvasRun, input: e.target.value })}
                  rows={4}
                  required
                  style={{ width: '100%', border: '1px solid #d5d9e2', borderRadius: 7, padding: '8px 10px', fontSize: 13, fontFamily: 'inherit', resize: 'vertical' }}
                  placeholder="输入测试问题，如：计算 2 的 10 次方"
                />
              </div>
              <div className="btns">
                <button type="button" className="btn" onClick={() => setCanvasRun(null)}>取消</button>
                <button type="submit" className="btn primary">开始运行</button>
              </div>
            </form>
          )}
          {canvasRun.phase === 'running' && (
            <div className="note info" style={{ margin: '8px 0' }}>
              ⏳ 运行中…画布节点会实时染色（绿=完成，蓝=执行中，灰=条件路由跳过）。
            </div>
          )}
          {canvasRun.phase === 'done' && (
            <div>
              {canvasRun.error && <p className="note danger">{canvasRun.error}</p>}
              {canvasRun.snapshot?.status === 'BLOCKED' && (
                <p className="note" style={{ color: 'var(--warning, #b45309)' }}>
                  🛡️ 运行在审批门挂起——画布试运行不处理审批，请到对话页继续该运行。
                </p>
              )}
              {canvasRun.snapshot && (
                <>
                  <div className="rd-meta" style={{ margin: '8px 0' }}>
                    <span>
                      状态{' '}
                      {canvasRun.snapshot.status === 'COMPLETED' ? <b className="tag ok">SUCCESS</b>
                        : canvasRun.snapshot.status === 'FAILED' ? <b className="tag err">FAILED</b>
                          : <b className="tag gray">{canvasRun.snapshot.status}</b>}
                    </span>
                    {canvasRun.snapshot.usage && (
                      <span>Tokens：↑{canvasRun.snapshot.usage.inputTokens} ↓{canvasRun.snapshot.usage.outputTokens}</span>
                    )}
                    <span style={{ gridColumn: '1 / -1' }}>traceId：{canvasRun.snapshot.traceId}</span>
                  </div>
                  <h4>最终输出</h4>
                  <pre className="rd-io">{canvasRun.snapshot.outputText || '（无输出）'}</pre>
                </>
              )}
              <div className="btns" style={{ marginTop: 10 }}>
                <button className="btn" onClick={() => setCanvasRun({ phase: 'input', input: canvasRun.input })}>再跑一次</button>
                <button className="btn primary" onClick={() => setCanvasRun(null)}>关闭</button>
              </div>
            </div>
          )}
        </Modal>
      )}
      <ToastHost />
    </>
  )
}
