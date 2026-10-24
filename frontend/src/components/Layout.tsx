import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { apiLogout } from '../api/auth'
import { clearSession, getStoredUser } from '../api/client'
import { ToastHost, toast } from './Toast'

/**
 * 应用骨架：完整复刻原型的白色固定侧栏（分组导航）+ 面包屑顶栏（铃铛 / 角色 tag / 用户名 / 头像）。
 * v1 未开放的页面按原型文案保留导航项，点击 toast 提示（原型交互习惯）。
 */

const PENDING_TOAST = '该功能将在后续版本开放（v2 对话运行时 / v3 Agent 搭建）'

function NavPending(props: { children: string }) {
  return (
    <a
      className="nav-item"
      onClick={(e) => {
        e.preventDefault()
        toast(PENDING_TOAST)
      }}
      href="#"
    >
      {props.children}
    </a>
  )
}

export default function Layout() {
  const navigate = useNavigate()
  const location = useLocation()
  const user = getStoredUser()

  const crumb =
    location.pathname.startsWith('/chat') ? '对话'
      : location.pathname.startsWith('/models')
      ? '模型连接'
      : location.pathname.startsWith('/mcps')
        ? 'MCP 连接与工具'
        : '开始使用与账户'

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
        <div className="logo" onClick={() => toast('对话运行时将于 v2 开放')}>
          <span className="dot">L</span>LemonAgent
        </div>
        <div className="nav-group">
          <div className="gt">工作台</div>
          <NavLink className="nav-item" to="/chat">
            💬 对话
          </NavLink>
          <NavPending>🤖 我的 Agent</NavPending>
          <NavLink className="nav-item" to="/models">
            🔌 模型连接
          </NavLink>
          <NavPending>📚 我的知识库</NavPending>
          <NavPending>🧠 长期记忆</NavPending>
          <NavPending>🔀 工作流</NavPending>
        </div>
        <div className="nav-group">
          <div className="gt">运行与产物</div>
          <NavPending>🕘 运行历史</NavPending>
          <NavPending>📦 产物与反馈</NavPending>
          <NavPending>🧪 我的评测</NavPending>
        </div>
        <div className="nav-group">
          <div className="gt">观测</div>
          <NavPending>📈 用量与额度</NavPending>
          <NavPending>🔭 LLM 观测</NavPending>
          <NavLink className="nav-item" to="/mcps">
            🔧 MCP 连接与工具
          </NavLink>
          <NavPending>🚪 API 分发</NavPending>
        </div>
        <div className="nav-group">
          <div className="gt">账户</div>
          <NavLink className="nav-item" to="/account">
            👤 开始使用与账户
          </NavLink>
        </div>
        <div className="nav-group">
          <div className="gt">管理</div>
          <NavPending>📊 网站统计</NavPending>
        </div>
        <div className="footer-note">v1 · 平台基座</div>
      </aside>

      <div className="main">
        <header className="topbar">
          <div className="crumb">
            LemonAgent / <b>{crumb}</b>
          </div>
          <div className="right">
            <span className="tag gray">{user?.role?.toUpperCase() ?? 'USER'}</span>
            <span style={{ fontSize: '12.5px', color: 'var(--muted)' }}>{user?.username ?? ''}</span>
            <div
              className="avatar"
              title="开始使用与账户"
              onClick={() => navigate('/account')}
            >
              {(user?.username ?? '?').slice(0, 1)}
            </div>
            <button className="btn sm" onClick={onLogout}>
              退出
            </button>
          </div>
        </header>
        <div className="content">
          <Outlet />
        </div>
      </div>
      <ToastHost />
    </div>
  )
}
