import { NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom'
import { apiLogout } from '../api/auth'
import InboxBell from './InboxBell'
import { clearSession, getStoredUser } from '../api/client'
import { ToastHost, toast } from './Toast'

/**
 * 应用骨架：完整复刻原型的白色固定侧栏（分组导航）+ 面包屑顶栏（铃铛 / 角色 tag / 用户名 / 头像）。
 * v1 未开放的页面按原型文案保留导航项，点击 toast 提示（原型交互习惯）。
 */

export default function Layout() {
  const navigate = useNavigate()
  const location = useLocation()
  const user = getStoredUser()

  const crumb =
    location.pathname.startsWith('/chat') ? '对话'
      : /^\/agents\/\d+\/graph/.test(location.pathname) ? '执行图'
      : /^\/agents\/\d+\/eval/.test(location.pathname) ? '评测回归'
      : location.pathname.startsWith('/agents')
      ? '我的 Agent'
      : location.pathname.startsWith('/models')
      ? '模型连接'
      : location.pathname.startsWith('/mcps')
      ? 'MCP 连接与工具'
      : location.pathname.startsWith('/knowledge')
      ? '我的知识库'
      : location.pathname.startsWith('/memory')
      ? '长期记忆'
      : location.pathname.startsWith('/workflows')
      ? '工作流'
      : location.pathname.startsWith('/artifacts')
      ? '产物与反馈'
      : location.pathname.startsWith('/evals')
      ? '我的评测'
      : location.pathname.startsWith('/llm')
      ? 'LLM 观测'
      : location.pathname.startsWith('/open-api')
      ? 'API 分发'
      : location.pathname.startsWith('/runs')
      ? '运行历史'
      : location.pathname.startsWith('/usage')
      ? '用量与观测'
      : location.pathname.startsWith('/admin')
      ? '网站统计'
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
          <NavLink className="nav-item" to="/agents">
            🤖 我的 Agent
          </NavLink>
          <NavLink className="nav-item" to="/models">
            🔌 模型连接
          </NavLink>
          <NavLink className="nav-item" to="/knowledge">📚 我的知识库</NavLink>
          <NavLink className="nav-item" to="/memory">🧠 长期记忆</NavLink>
          <NavLink className="nav-item" to="/workflows">🔀 工作流</NavLink>
        </div>
        <div className="nav-group">
          <div className="gt">运行与产物</div>
          <NavLink className="nav-item" to="/runs">
            🕘 运行历史
          </NavLink>
          <NavLink className="nav-item" to="/artifacts">📦 产物与反馈</NavLink>
          <NavLink className="nav-item" to="/evals">🧪 我的评测</NavLink>
        </div>
        <div className="nav-group">
          <div className="gt">观测</div>
          <NavLink className="nav-item" to="/usage">
            📈 用量与观测
          </NavLink>
          <NavLink className="nav-item" to="/llm">🔭 LLM 观测</NavLink>
          <NavLink className="nav-item" to="/mcps">
            🔧 MCP 连接与工具
          </NavLink>
          <NavLink className="nav-item" to="/open-api">🚪 API 分发</NavLink>
        </div>
        <div className="nav-group">
          <div className="gt">账户</div>
          <NavLink className="nav-item" to="/account">
            👤 开始使用与账户
          </NavLink>
        </div>
        <div className="nav-group">
          <div className="gt">管理</div>
          <NavLink className="nav-item" to="/admin">
            📊 网站统计
          </NavLink>
        </div>
        <div className="footer-note">v1 · 平台基座</div>
      </aside>

      <div className="main">
        <header className="topbar">
          <div className="crumb">
            LemonAgent / <b>{crumb}</b>
          </div>
          <div className="right">
            <InboxBell />
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
