import { del, get, post, put } from './client'

/** 我的 Agent API（PRD FR-6 最小充分版）。 */
export interface AgentConfig {
  id: number
  userId: number | null
  name: string
  description: string
  instruction: string | null
  /** 可用工具白名单；null = 全部已注册工具 */
  tools: string[] | null
  /** 当前执行图版本；0 = 还没有图 */
  graphVersion: number
  /** 挂载的工作流；null = 用 Agent 自己的执行图 */
  workflowId: number | null
  /** 挂载的工作流版本；null = 跟随工作流当前生效版本 */
  workflowVersion: number | null
  /** 挂载的工作流名称（未挂载为 null） */
  workflowName: string | null
  modelConnectionId: number | null
  enabled: boolean
  platformPublic: boolean
  usable: boolean
  version: number
  createdAt: string | null
  updatedAt: string | null
}

/** 可授权工具项（工具注册表的对外投影）。 */
export interface ToolInfo {
  name: string
  description: string
  needsApproval: boolean
  available: boolean
}

export interface AgentPage {
  platform: AgentConfig[]
  mine: AgentConfig[]
}

export function fetchAgents(): Promise<AgentPage> {
  return get<AgentPage>('/workspace/agents')
}

/** 工具目录（Agent 表单勾选用）。 */
export async function fetchTools(): Promise<ToolInfo[]> {
  const data = await get<{ tools: ToolInfo[] }>('/workspace/tools')
  return data.tools
}

export function createAgent(payload: {
  name: string
  description: string
  instruction: string
  tools: string[] | null
  modelConnectionId: number | null
  /** 挂载工作流（可空） */
  workflowId?: number | null
  /** 挂载的工作流版本（可空 = 跟随当前生效版本） */
  workflowVersion?: number | null
}): Promise<AgentConfig> {
  return post<AgentConfig>('/workspace/agents', payload)
}

export function updateAgent(
  id: number,
  payload: {
    name: string
    description: string
    instruction: string
    tools: string[] | null
    /** null = 保持不变；0 = 清除绑定 */
    modelConnectionId?: number | null
    enabled?: boolean
    /** 挂载工作流：null = 保持不变；0 = 取消挂载 */
    workflowId?: number | null
    /** 挂载版本：null = 保持不变；0 = 清除（跟随最新） */
    workflowVersion?: number | null
  },
): Promise<AgentConfig> {
  return put<AgentConfig>(`/workspace/agents/${id}`, payload)
}

export function deleteAgent(id: number): Promise<null> {
  return del<null>(`/workspace/agents/${id}`)
}
