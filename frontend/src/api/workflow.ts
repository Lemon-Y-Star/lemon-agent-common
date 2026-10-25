import { get, post, put } from './client'

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
