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
  /** 默认运行策略：auto/plan/custom/fixed（对话页跟随此策略发起运行） */
  strategy: string
  /** 短期记忆窗口轮数（1 轮=一问一答）；null = 默认 10 轮；0 = 不带历史 */
  historyTurns: number | null
  /** 对话开场白；null/空 = 不展示（v4.5） */
  openingStatement: string | null
  /** 建议问题（至多 5 条）；null = 不展示（v4.5） */
  suggestedQuestions: string[] | null
  /** 绑定的知识库文档 ID；null/空 = 检索不限定范围（v4.5.2） */
  knowledgeDocIds: number[] | null
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
  /** 展示标签（中文优先：内置中文名 / MCP·连接名 / 工作流：名；为空回退 name） */
  label?: string
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
  /** 默认运行策略（可空 = auto） */
  strategy?: string
  /** 短期记忆窗口轮数（可空 = 默认 10；0 = 不带历史；上限 50） */
  historyTurns?: number | null
  /** 对话开场白（可空 = 不展示；v4.5） */
  openingStatement?: string | null
  /** 建议问题（可空 = 不展示；至多 5 条；v4.5） */
  suggestedQuestions?: string[] | null
  /** 绑定的知识库文档 ID（可空 = 不限定；至多 20 个；v4.5.2） */
  knowledgeDocIds?: number[] | null
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
    /** 默认运行策略（空 = auto） */
    strategy?: string
    /** 短期记忆窗口轮数：null = 保持不变；0 = 不带历史；上限 50 */
    historyTurns?: number | null
    /** 对话开场白（可空 = 清除；v4.5） */
    openingStatement?: string | null
    /** 建议问题（可空 = 清除；至多 5 条；v4.5） */
    suggestedQuestions?: string[] | null
    /** 绑定的知识库文档 ID（null = 保持不变；空数组 = 清除；至多 20 个；v4.5.2） */
    knowledgeDocIds?: number[] | null
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

/** 提示词 AI 优化（v4.5，Dify Prompt Generator 同款）。 */
export async function optimizePrompt(instruction: string): Promise<string> {
  const data = await post<{ optimized: string }>('/workspace/agents/optimize-prompt', { instruction })
  return data.optimized
}

/** 会话日志行（v4.5.2，Dify「日志」页同款）。 */
export interface SessionLog {
  sessionId: number
  title: string
  runCount: number
  okCount: number
  firstAt: string | null
  lastAt: string | null
  totalTokens: number
}

/** Agent 会话日志。 */
export async function fetchAgentLogs(agentId: number, days = 7): Promise<{ sessions: SessionLog[] }> {
  return get<{ sessions: SessionLog[] }>(`/workspace/agents/${agentId}/logs?days=${days}`)
}

/** 单日监控指标。 */
export interface DayMetric {
  date: string
  runs: number
  sessions: number
  tokens: number
}

/** Agent 监控数据（v4.5.2，Dify「监控」页同款）。 */
export interface AgentMetrics {
  days: DayMetric[]
  totalRuns: number
  totalSessions: number
  totalTokens: number
  successRate: number
  likeRate: number
}

/** Agent 监控指标。 */
export async function fetchAgentMetrics(agentId: number, days = 7): Promise<AgentMetrics> {
  return get<AgentMetrics>(`/workspace/agents/${agentId}/metrics?days=${days}`)
}
