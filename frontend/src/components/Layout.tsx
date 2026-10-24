import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { apiLogout } from '../api/auth'
import { clearSession, getStoredUser } from '../api/client'

/**
 * 应用骨架：左侧导航 + 顶栏身份与登出。
 * 「对话运行时」「Agent 搭建」为后续切片功能，v1 置灰占位。
 */
export default function Layout() {
  const navigate = useNavigate()
  const user = getStoredUser()

  async function onLogout() {
    try {
      await apiLogout()
    } catch {
      // 令牌已失效也继续清本地会话
    }
    clearSession()
    navigate('/login', { replace: true })
  }

  return (
    <div className="app">
      <aside className="sidebar">
        <div className="logo">🤖 LemonAgent 工作台</div>
        <div className="nav-group">使用</div>
        <a className="nav-item disabled" title="后续切片上线">
          💬 对话运行时
        </a>
        <a className="nav-item disabled" title="后续切片上线">
          🧩 Agent 搭建
        </a>
        <div className="nav-group">资源管理</div>
        <NavLink className="nav-item" to="/models">
          🔌 模型连接
        </NavLink>
        <NavLink className="nav-item" to="/mcps">
          🛰️ MCP 连接
        </NavLink>
        <div className="nav-group">账户</div>
        <NavLink className="nav-item" to="/account">
          👤 开始使用与账户
        </NavLink>
      </aside>
      <div className="main">
        <header className="topbar">
          <span className="user">
            {user ? `${user.username}（${user.role}）` : ''}
          </span>
          <button className="btn" onClick={onLogout}>
            退出登录
          </button>
        </header>
        <div className="content">
          <Outlet />
        </div>
      </div>
    </div>
  )
}
