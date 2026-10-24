import { get } from './client'

/** 用量与观测 API（PRD FR-16/17 简化版）。 */
export interface DailyUsage {
  date: string
  runs: number
  inputTokens: number
  outputTokens: number
}

export interface StatusCount {
  status: string
  count: number
}

export interface UsageOverview {
  days: number
  totalRuns: number
  totalInputTokens: number
  totalOutputTokens: number
  statusCounts: StatusCount[]
  daily: DailyUsage[]
}

export function fetchUsage(days = 7): Promise<UsageOverview> {
  return get<UsageOverview>(`/workspace/usage?days=${days}`)
}

/** 管理端统计（PRD FR-22.1，admin only）。 */
export interface AdminStats {
  users: number
  sessions: number
  runs: number
  messages: number
  tickets: number
  runsByStatus: Record<string, number>
  totalTokens: number
}

export function fetchAdminStats(): Promise<AdminStats> {
  return get<AdminStats>('/admin/stats')
}

/** 收件箱（PRD FR-21 简化版：派生通知，已读由前端记录）。 */
export interface InboxItem {
  kind: 'APPROVAL' | 'RUN_FAILED' | 'RUN_COMPLETED'
  runId: number
  sessionId: number
  text: string
  createdAt: string
}

export interface Inbox {
  items: InboxItem[]
  unreadHint: number
}

export function fetchInbox(): Promise<Inbox> {
  return get<Inbox>('/workspace/notifications')
}
