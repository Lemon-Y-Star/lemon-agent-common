import { FormEvent, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { apiLogin } from '../api/auth'
import { ApiError, storeSession } from '../api/client'

/** 登录页：成功后存会话并进入工作台。 */
export default function Login() {
  const navigate = useNavigate()
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
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
      navigate('/', { replace: true })
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '登录失败，请稍后重试')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="login-wrap">
      <div className="login-card">
        <h1>🤖 LemonAgent 工作台</h1>
        <p className="muted">登录后管理模型与 MCP 连接（对话与 Agent 搭建将于后续版本开放）</p>
        <form onSubmit={onSubmit}>
          <div className="field">
            <label>用户名</label>
            <input
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              autoComplete="username"
              required
            />
          </div>
          <div className="field">
            <label>密码</label>
            <input
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              autoComplete="current-password"
              required
            />
          </div>
          <button className="btn primary" type="submit" disabled={busy} style={{ width: '100%', justifyContent: 'center' }}>
            {busy ? '登录中…' : '登录'}
          </button>
          <p className="status-line" role="status">
            {error}
          </p>
        </form>
      </div>
    </div>
  )
}
