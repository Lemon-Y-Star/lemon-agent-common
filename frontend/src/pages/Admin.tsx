import { useCallback, useEffect, useState } from 'react'
import { fetchAdminStats, type AdminStats } from '../api/workspace'
import { ApiError, getStoredUser } from '../api/client'
import { ToastHost, toast } from '../components/Toast'
import { STATUS_META } from './Usage'
import { addSensitiveWord, deleteSensitiveWord, fetchSensitiveWords, type SensitiveWordItem } from '../api/safety'

/**
 * 管理端统计（PRD FR-22.1 简化版）：admin 角色可见的平台总量统计。
 *
 * 视觉与「用量与观测」同一套语言：KPI 卡 + 横向状态条（v3.8 重做，此前 .stat 类没有样式，
 * 页面是裸文字大表格）。
 */
export default function Admin() {
  const [stats, setStats] = useState<AdminStats | null>(null)
  const [error, setError] = useState('')
  const isAdmin = getStoredUser()?.role === 'admin'
  const [words, setWords] = useState<SensitiveWordItem[]>([])
  const [newWord, setNewWord] = useState('')

  const reload = useCallback(async () => {
    try {
      setStats(await fetchAdminStats())
      setError('')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '加载失败')
    }
  }, [])

  const reloadWords = useCallback(async () => {
    try {
      setWords(await fetchSensitiveWords())
    } catch {
      setWords([])  // 非 admin 访问会被拒绝，静默隐藏卡片
    }
  }, [])

  useEffect(() => {
    void reload()
    if (isAdmin) void reloadWords()
  }, [reload, reloadWords, isAdmin])

  async function onAddWord() {
    if (!newWord.trim()) return
    try {
      await addSensitiveWord(newWord.trim())
      toast('敏感词已添加')
      setNewWord('')
      await reloadWords()
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '添加失败')
    }
  }

  async function onDeleteWord(w: SensitiveWordItem) {
    try {
      await deleteSensitiveWord(w.id)
      toast('敏感词已删除')
      await reloadWords()
    } catch (err) {
      toast(err instanceof ApiError ? err.message : '删除失败')
    }
  }

  const byStatus = Object.entries(stats?.runsByStatus ?? {})
    .map(([status, count]) => ({ status, count }))
    .sort((a, b) => b.count - a.count)
  const totalRuns = byStatus.reduce((s, x) => s + x.count, 0)

  const kpis: { lbl: string; num: string | number | undefined; sub: string }[] = [
    { lbl: '用户', num: stats?.users, sub: '注册账号数' },
    { lbl: '会话', num: stats?.sessions, sub: '对话会话总数' },
    { lbl: '运行', num: stats?.runs, sub: '全部运行（含进行中）' },
    { lbl: '消息', num: stats?.messages, sub: 'USER + ASSISTANT' },
    { lbl: '工单', num: stats?.tickets, sub: 'demo_ticket 创建量' },
    { lbl: '总 tokens', num: stats?.totalTokens?.toLocaleString(), sub: '输入 + 输出累计' },
  ]

  return (
    <>
      <div className="page-head">
        <div>
          <h2>网站统计</h2>
          <div className="desc">平台总量指标（admin 角色可见）。数据实时聚合自业务库。</div>
        </div>
        <div style={{ marginLeft: 'auto' }}>
          <button className="btn sm" onClick={() => void reload()}>刷新</button>
        </div>
      </div>

      <p className="status-line" role="status">{error}</p>

      <div className="kpi-row k6">
        {kpis.map((k) => (
          <div className="kpi" key={k.lbl}>
            <div className="kpi-lbl">{k.lbl}</div>
            <div className="kpi-num">{k.num ?? '—'}</div>
            <div className="kpi-sub">{k.sub}</div>
          </div>
        ))}
      </div>

      <div className="card">
        <h3>运行状态分布 <span className="sub">共 {totalRuns.toLocaleString()} 条运行</span></h3>
        <div className="status-bars">
          {byStatus.map((s) => {
            const meta = STATUS_META[s.status] ?? { label: s.status, color: '#94a3b8' }
            const pct = totalRuns === 0 ? 0 : Math.round((s.count / totalRuns) * 100)
            return (
              <div className="status-row" key={s.status}>
                <span className="status-name">
                  <i style={{ background: meta.color }} />
                  {meta.label}
                </span>
                <div className="status-track">
                  <div className="status-fill" style={{ width: `${pct}%`, background: meta.color }} />
                </div>
                <span className="status-count">
                  {s.count.toLocaleString()}<em>{pct}%</em>
                </span>
              </div>
            )
          })}
          {byStatus.length === 0 && <div className="note gray">暂无运行</div>}
        </div>
      </div>
      {isAdmin && (
        <div className="card">
          <h3>
            敏感词管理 <span className="sub">{words.length} 个 · 输入命中即拒绝，输出命中替换为【已屏蔽】</span>
          </h3>
          <div style={{ display: 'flex', gap: 8, maxWidth: 560, marginBottom: 10 }}>
            <input
              value={newWord}
              onChange={(e) => setNewWord(e.target.value)}
              placeholder="输入敏感词后回车"
              onKeyDown={(e) => { if (e.key === 'Enter') void onAddWord() }}
            />
            <button className="btn primary" disabled={!newWord.trim()} onClick={() => void onAddWord()}>＋ 添加</button>
          </div>
          {words.length === 0 ? (
            <div className="note gray">词库为空——不做任何过滤。</div>
          ) : (
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8 }}>
              {words.map((w) => (
                <span key={w.id} className="tag gray" style={{ fontSize: 12.5 }}>
                  {w.word}
                  <button
                    className="btn sm danger"
                    style={{ marginLeft: 6, padding: '0 6px' }}
                    onClick={() => void onDeleteWord(w)}
                  >✕</button>
                </span>
              ))}
            </div>
          )}
        </div>
      )}
      <ToastHost />
    </>
  )
}
