import { FormEvent, useState } from 'react'
import { apiLogin } from '../api/auth'
import { ApiError, storeSession } from '../api/client'

/**
 * 登录页：左侧品牌区 + 右侧表单卡。
 *
 * 交互细节：密码可见性切换、回车提交（表单原生）、错误区固定高度避免抖动、
 * 演示账号一键填入（内部演示环境，省得每次手打）。
 */
export default function Login() {
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [showPwd, setShowPwd] = useState(false)
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    setBusy(true)
    setError('')
    try {
      const result = await apiLogin({ username, password })
      storeSession(result.token, {
        userId: result.userId,
        username: result.username,
        role: result.role,
      })
      // 整页导航而不是 SPA navigate：登录态已落 localStorage，刷新后首渲染即已登录，
      // 避免 Router transition 渲染期三元判定与 <Navigate> 的时序竞态（实测会把路由弹回 /login）
      window.location.assign('/')
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '登录失败，请稍后重试')
      setBusy(false)
    }
  }

  function fillDemo() {
    setUsername('admin')
    setPassword('lemon123456')
    setError('')
  }

  return (
    <div className="auth-wrap">
      <aside className="auth-brand">
        <div className="auth-brand-glow" aria-hidden />
        <div className="auth-brand-inner">
          <div className="auth-logo">
            <span className="auth-logo-mark">L</span>
            <span className="auth-logo-text">LemonAgent<em>AI Agent Station</em></span>
          </div>

          <h1 className="auth-headline">
            把 Agent 做成
            <br />
            <span>可回归、可溯源的工程制品</span>
          </h1>
          <p className="auth-sub">
            执行图编排 · 双路知识检索 · 工具审批闭环 · 版本化评测回归，
            一个工程视角的 Agent 工作台。
          </p>

          <ul className="auth-points">
            <li><i>🧩</i><b>执行图调度</b><span>五类节点 / 条件路由 / 并行执行</span></li>
            <li><i>🧪</i><b>评测回归</b><span>一批用例跨版本并排对比</span></li>
            <li><i>🛡️</i><b>审批闭环</b><span>写操作挂起、批准后续跑</span></li>
          </ul>

          <div className="auth-brand-foot">v3.7 · Spring Boot 4.1 / Spring AI 2.0 / React 18</div>
        </div>
      </aside>

      <main className="auth-main">
        <form className="auth-card" onSubmit={onSubmit}>
          <h2>欢迎回来</h2>
          <p className="auth-hint">登录后管理模型连接、MCP 工具与你的 Agent</p>

          <label className="auth-field">
            <span>用户名</span>
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              placeholder="输入用户名"
              autoFocus
              required
            />
          </label>

          <label className="auth-field">
            <span>密码</span>
            <div className="auth-input-wrap">
              <input
                type={showPwd ? 'text' : 'password'}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                autoComplete="current-password"
                placeholder="输入密码"
                required
              />
              <button
                type="button"
                className="auth-eye"
                onClick={() => setShowPwd((v) => !v)}
                tabIndex={-1}
                aria-label={showPwd ? '隐藏密码' : '显示密码'}
              >
                {showPwd ? '隐藏' : '显示'}
              </button>
            </div>
          </label>

          <div className="auth-error" role="status">{error}</div>

          <button className="auth-submit" type="submit" disabled={busy}>
            {busy ? (<><span className="auth-spinner" />登录中…</>) : '登录'}
          </button>

          <div className="auth-demo">
            <span>演示账号：<code>admin</code> / <code>lemon123456</code></span>
            <button type="button" onClick={fillDemo}>一键填入</button>
          </div>
        </form>
        <div className="auth-copyright">© 2026 LemonAgent · 内部演示环境</div>
      </main>
    </div>
  )
}
