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
import { createWorkflow } from '../api/workflow'
import { ApiError } from '../api/client'
import { ToastHost, toast } from '../components/Toast'

/**
 * 执行图画布（v3.2.1）：拖拽定位 + 连线 + 检查器 + 保存校验。
 *
 * 保存走服务端校验（类型/拓扑/工具存在/无环），前端只做「明显错误」的即时提示，
 * 避免两套校验规则打架：**能存下就一定能跑**这条保证由后端负责。
 */

const NODE_W = 150
const NODE_H = 96

const TYPE_LABEL: Record<GraphNodeType, string> = {
  START: '开始',
  LLM: '大模型',
  TOOL: '工具',
  APPROVAL: '审批门',
  END: '结束',
}

const TYPE_COLOR: Record<GraphNodeType, string> = {
  START: '#16a34a',
  LLM: '#4f46e5',
  TOOL: '#0891b2',
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

export default function GraphEditor() {
  const { agentId } = useParams()
  const id = Number(agentId)
  const navigate = useNavigate()

  const [graph, setGraph] = useState<AgentGraph | null>(null)
  const [tools, setTools] = useState<ToolInfo[]>([])
  const [selected, setSelected] = useState<string | null>(null)
  const [connectingFrom, setConnectingFrom] = useState<string | null>(null)
  const [dirty, setDirty] = useState(false)
  const [error, setError] = useState('')
  const [saving, setSaving] = useState(false)
  const [argsText, setArgsText] = useState('')

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
      const g = await fetchGraph(id)
      setGraph(g)
      setDirty(false)
      setError('')
      fitRef.current = true
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载执行图失败')
    }
  }, [id])

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

  return (
    <>
      <div className="page-head">
        <div>
          <h2>执行图 · Agent #{id}</h2>
          <div className="desc">
            五类节点（开始 / 大模型 / 工具 / 审批门 / 结束），按拓扑序调度；保存时服务端校验唯一 START、
            必达、无环与工具存在。<b>能存下就一定跑得起来。</b>
          </div>
        </div>
        <div style={{ display: 'flex', gap: 8, alignItems: 'center' }}>
          <span className="tag gray">v{graph?.version ?? 0}</span>
          {dirty && <span className="tag warn">未保存</span>}
          <button className="btn" onClick={() => navigate('/agents')}>返回</button>
          <button className="btn" onClick={() => navigate(`/agents/${id}/eval`)}>评测回归</button>
          <button className="btn" onClick={() => navigate('/chat')}>去对话</button>
          {editable && (
            <button className="btn" onClick={onAutoLayout}>整理布局</button>
          )}
          {editable && (
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
              {(['LLM', 'TOOL', 'APPROVAL', 'START', 'END'] as GraphNodeType[]).map((t) => (
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

          {(graph?.nodes ?? []).map((n) => (
            <div
              key={n.id}
              className={`ge-node ${selected === n.id ? 'selected' : ''} ${connectingFrom === n.id ? 'connecting' : ''}`}
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
              </div>
              <div className="ge-node-title">{n.title || n.id}</div>
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
          ))}
          </div>
          {emptyGraph && (
            <div className="ge-empty">
              <div className="ge-empty-icon">🧩</div>
              <b>这个 Agent 还没有执行图</b>
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
                          <label key={t.name}>
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
                            {t.name}
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
                        <option key={t.name} value={t.name}>{t.name}{t.needsApproval ? '（写操作）' : ''}</option>
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
      <ToastHost />
    </>
  )
}
