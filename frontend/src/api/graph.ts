import { get, put } from './client'

/** 执行图 API（v3.2.0 后端 / v3.2.1 画布）。 */

export type GraphNodeType = 'START' | 'LLM' | 'TOOL' | 'CODE' | 'APPROVAL' | 'END'

export interface GraphNode {
  id: string
  type: GraphNodeType
  title?: string
  prompt?: string
  tool?: string
  args?: Record<string, unknown> | null
  /** CODE 节点：Python 源码（沙箱执行，代码内可读 upstream 列表 = 上游产出） */
  code?: string
  approvalPrompt?: string
  tools?: string[] | null
  x?: number | null
  y?: number | null
}

export interface GraphEdge {
  from: string
  to: string
  route?: string | null
}

export interface AgentGraph {
  version: number
  nodes: GraphNode[]
  edges: GraphEdge[]
  editable: boolean
}

export function fetchGraph(agentId: number): Promise<AgentGraph> {
  return get<AgentGraph>(`/workspace/agents/${agentId}/graph`)
}

export function saveGraph(
  agentId: number,
  graph: { nodes: GraphNode[]; edges: GraphEdge[] },
): Promise<AgentGraph> {
  return put<AgentGraph>(`/workspace/agents/${agentId}/graph`, graph)
}
