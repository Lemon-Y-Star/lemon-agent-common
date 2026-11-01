import { del, get, post, put } from './client'
import type { AgentGraph, GraphEdge, GraphNode } from './graph'

/** 工作流 API（v3.7）：独立实体，可被多个 Agent 挂载。 */

export interface WorkflowItem {
  id: number
  name: string
  description: string | null
  enabled: boolean
  /** 当前生效版本；0 = 还没有版本 */
  currentVersion: number
  versionCount: number
  createdAt: string | null
  updatedAt: string | null
}

export interface WorkflowVersionItem {
  version: number
  note: string | null
  active: boolean
  nodeCount: number
  createdAt: string | null
}

export interface WorkflowDetail extends WorkflowItem {
  versions: WorkflowVersionItem[]
}

export function fetchWorkflows(): Promise<{ workflows: WorkflowItem[] }> {
  return get<{ workflows: WorkflowItem[] }>('/workspace/workflows')
}

/** 创建工作流；sourceAgentId 非空 = 把该 Agent 的当前执行图另存为 v1。 */
export function createWorkflow(payload: {
  name: string
  description?: string
  sourceAgentId?: number | null
}): Promise<WorkflowDetail> {
  return post<WorkflowDetail>('/workspace/workflows', payload)
}

export function fetchWorkflow(workflowId: number): Promise<WorkflowDetail> {
  return get<WorkflowDetail>(`/workspace/workflows/${workflowId}`)
}

/** 从某个 Agent 的当前执行图另存为工作流下一版本。 */
export function saveWorkflowVersion(
  workflowId: number,
  payload: { sourceAgentId: number; note?: string },
): Promise<WorkflowDetail> {
  return put<WorkflowDetail>(`/workspace/workflows/${workflowId}/versions`, payload)
}

/** 删除工作流（被 Agent 挂载时后端会拒绝）。 */
export function deleteWorkflow(workflowId: number): Promise<null> {
  return del<null>(`/workspace/workflows/${workflowId}`)
}

/** 读取工作流当前生效版本的图（独立画布编辑用；version=0 表示还没有版本）。 */
export function fetchWorkflowGraph(workflowId: number): Promise<AgentGraph> {
  return get<AgentGraph>(`/workspace/workflows/${workflowId}/graph`)
}

/** 独立画布保存 = 新版本（服务端 GraphValidator 校验）。 */
export function saveWorkflowGraph(
  workflowId: number,
  graph: { nodes: GraphNode[]; edges: GraphEdge[] },
): Promise<WorkflowDetail> {
  return put<WorkflowDetail>(`/workspace/workflows/${workflowId}/graph`, graph)
}

/** 图版本 diff（v4.1）：节点/边增删改，人类可读字符串列表。 */
export interface GraphDiff {
  fromVersion: number
  toVersion: number
  nodesAdded: string[]
  nodesRemoved: string[]
  nodesChanged: string[]
  edgesAdded: string[]
  edgesRemoved: string[]
}

/** 版本回滚（v4.1）：历史版本另存为新生效版本（非破坏式）。 */
export function rollbackWorkflow(workflowId: number, version: number): Promise<WorkflowDetail> {
  return post<WorkflowDetail>(`/workspace/workflows/${workflowId}/versions/${version}/rollback`, {})
}

/** 两版图 diff。 */
export function diffWorkflow(workflowId: number, from: number, to: number): Promise<GraphDiff> {
  return get<GraphDiff>(`/workspace/workflows/${workflowId}/diff?from=${from}&to=${to}`)
}

/** Agent 执行图版本列表（倒序）。 */
export function fetchAgentGraphVersions(agentId: number): Promise<number[]> {
  return get<number[]>(`/workspace/agents/${agentId}/graph/versions`)
}

/** Agent 执行图回滚。 */
export function rollbackAgentGraph(agentId: number, version: number): Promise<unknown> {
  return post<unknown>(`/workspace/agents/${agentId}/graph/rollback/${version}`, {})
}
