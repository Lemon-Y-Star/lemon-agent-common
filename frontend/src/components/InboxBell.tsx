import { useCallback, useEffect, useState } from 'react'
import { fetchInbox, type InboxItem } from '../api/workspace'

/**
 * 顶栏铃铛 + 收件箱面板（PRD FR-21 简化版）：
 * 通知由服务端派生（待审批 + 近 7 天失败），已读由前端 localStorage 最后查看时间承载。
 */
const READ_KEY = 'lemon_agent_inbox_read_at'

export default function InboxBell() {
  const [open, setOpen] = useState(false)
  const [items, setItems] = useState<InboxItem[]>([])
  const [unread, setUnread] = useState(0)

  const reload = useCallback(async () => {
    try {
      const data = await fetchInbox()
      setItems(data.items)
      const lastRead = Number(localStorage.getItem(READ_KEY) ?? 0)
      setUnread(data.items.filter((i) => new Date(i.createdAt).getTime() > lastRead).length)
    } catch {
      // 静默：铃铛是辅助入口
    }
  }, [])

  useEffect(() => {
    void reload()
    const timer = setInterval(reload, 30_000)
    return () => clearInterval(timer)
  }, [reload])

  function markAllRead() {
    localStorage.setItem(READ_KEY, String(Date.now()))
    setUnread(0)
  }

  function go(item: InboxItem) {
    localStorage.setItem(READ_KEY, String(Date.now()))
    setUnread(0)
    setOpen(false)
    // 会话深链：前端会自动恢复该会话（RUN_FAILED 等历史也在会话消息里）
    window.location.assign(`/chat/${item.sessionId}`)
  }

  return (
    <div style={{ position: 'relative' }}>
      <div className="bell" onClick={() => { setOpen(!open); void reload() }}>
        🔔
        {unread > 0 && <span className="dot">{unread}</span>}
      </div>
      {open && (
        <div
          style={{
            position: 'absolute', right: 0, top: 40, width: 320, maxHeight: 420, overflowY: 'auto',
            background: '#fff', border: '1px solid var(--border)', borderRadius: 10,
            boxShadow: '0 8px 26px rgba(0,0,0,.14)', zIndex: 100, padding: 10,
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, marginBottom: 8 }}>
            <b style={{ fontSize: 13 }}>收件箱</b>
            <span className="tag gray">{items.length} 条</span>
            <button className="btn sm" style={{ marginLeft: 'auto' }} onClick={markAllRead}>一键已读</button>
          </div>
          {items.length === 0 && <div className="note gray">暂无通知。待审批与失败运行会出现在这里。</div>}
          {items.map((item, i) => (
            <div key={i} className="list-item" style={{ cursor: 'pointer' }} onClick={() => go(item)}>
              <span className={`tag ${item.kind === 'APPROVAL' ? 'warn' : 'err'}`}>
                {item.kind === 'APPROVAL' ? '待审批' : '失败'}
              </span>
              <span style={{ flex: 1, fontSize: 12 }}>{item.text}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
