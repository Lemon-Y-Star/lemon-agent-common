import { FormEvent, useState } from 'react'
import { Link } from 'react-router-dom'
import { apiChangePassword } from '../api/auth'
import { ApiError, clearSession, getStoredUser } from '../api/client'
import { ToastHost, toast } from '../components/Toast'

/**
 * P13「开始使用与账户」（PRD FR-2）。
 *
 * 改密校验链严格按 FR-2.4 的分支顺序：
 * ① 两次新密码不一致 → 提示并终止（不发请求）；
 * ② 新密码 <10 位 或 UTF-8 字节数 >72（TextEncoder 口径，汉字按 3 字节算）→ 提示并终止；
 * ③ 通过才提交；成功后清登录态、提示重新登录，但页面不自动跳转（FR-2.5 刻意设计）。
 */
function utf8Length(s: string): number {
  return new TextEncoder().encode(s).length
}

export default function Account() {
  const user = getStoredUser()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [confirmPassword, setConfirmPassword] = useState('')
  const [status, setStatus] = useState('')
  const [statusOk, setStatusOk] = useState(false)
  const [busy, setBusy] = useState(false)

  async function onSubmit(e: FormEvent) {
    e.preventDefault()
    // ① 两次输入的新密码不一致
    if (newPassword !== confirmPassword) {
      setStatusOk(false)
      setStatus('两次输入的新密码不一致。')
      return
    }
    // ② 新密码至少 10 位，且 UTF-8 长度不超过 72 字节（注意：汉字按字节算，不按字符数）
    if (utf8Length(newPassword) > 72 || newPassword.length < 10) {
      setStatusOk(false)
      setStatus('新密码至少 10 位，且 UTF-8 长度不超过 72 字节。')
      return
    }
    // ③ 通过，提交
    setBusy(true)
    setStatusOk(false)
    setStatus('正在保存…')
    try {
      await apiChangePassword({ currentPassword, newPassword })
      setStatusOk(true)
      setStatus('密码已修改。请返回对话页重新登录。')
      toast('密码已修改，请重新登录')
      // PRD FR-2.5：成功后清登录态，但页面不自动跳转
      clearSession()
    } catch (err) {
      setStatusOk(false)
      setStatus(err instanceof ApiError ? err.message : '保存失败，请稍后重试')
    } finally {
      setBusy(false)
      // 失败时三个框全部清空（不是只清当前密码），成功同样重置
      setCurrentPassword('')
      setNewPassword('')
      setConfirmPassword('')
    }
  }

  return (
    <>
      <div className="page-head">
        <div>
          <h2>开始使用与账户</h2>
          <div className="desc">登录身份：{user ? user.username : '未知'} · 新用户从这里进入各配置页</div>
        </div>
      </div>

      <div className="card">
        <h3>搭建你的第一个 Agent</h3>
        <p className="muted" style={{ fontSize: 12.5, lineHeight: 1.75 }}>
          可以先试用配置好的公共 Agent，也可以从骨架逐个配置节点。模型、工具和知识库分别管理，再绑定到节点。
        </p>
        <div className="steps">
          <article>
            <h4>1 · 先体验一个 Agent</h4>
            <p>
              在工作台选用公共 Agent，回到对话页提出一个小任务。Fixed 按固定节点运行，Auto
              自主迭代，Flow 先规划再执行。
            </p>
            <a
              onClick={(e) => {
                e.preventDefault()
                toast('对话运行时将于 v2 开放')
              }}
              href="#"
            >
              选择 Agent →
            </a>
          </article>
          <article>
            <h4>2 · 选择模型来源</h4>
            <p>
              试用可以使用平台提供的模型。需要个人模型时，创建自己的连接，并明确是否允许辅助功能使用平台模型。
            </p>
            <Link to="/models">管理模型连接 →</Link>
          </article>
          <article>
            <h4>3 · 配置并启用</h4>
            <p>
              为各节点选择模型、Prompt、Advisor 和 MCP 连接。修改先存为草稿，确认后启用；节点调试默认不执行工具。
            </p>
            <a
              onClick={(e) => {
                e.preventDefault()
                toast('Agent 搭建将于 v3 开放')
              }}
              href="#"
            >
              进入 Agent 工作台 →
            </a>
          </article>
          <article>
            <h4>4 · 添加资料与验证</h4>
            <p>
              按需上传个人知识文档，等待入库成功，再用检索测试确认命中。绑定知识库后，执行一次实际任务并查看记录。
            </p>
            <a
              onClick={(e) => {
                e.preventDefault()
                toast('知识库将于后续版本开放')
              }}
              href="#"
            >
              管理知识库 →
            </a>
          </article>
        </div>
      </div>

      <div className="card">
        <h3>修改密码</h3>
        <form className="pwd-form" id="passwordForm" onSubmit={onSubmit}>
          <label>当前密码</label>
          <input
            type="password"
            autoComplete="current-password"
            required
            value={currentPassword}
            onChange={(e) => setCurrentPassword(e.target.value)}
          />
          <label>新密码</label>
          <input
            type="password"
            autoComplete="new-password"
            required
            minLength={10}
            maxLength={72}
            value={newPassword}
            onChange={(e) => setNewPassword(e.target.value)}
          />
          <label>确认新密码</label>
          <input
            type="password"
            autoComplete="new-password"
            required
            minLength={10}
            maxLength={72}
            value={confirmPassword}
            onChange={(e) => setConfirmPassword(e.target.value)}
          />
          <button className="btn primary" type="submit" id="savePassword" disabled={busy} style={{ marginTop: 16 }}>
            保存新密码
          </button>
          <div className={`pwd-status ${statusOk ? 'ok' : status ? 'err' : ''}`} id="passwordStatus" role="status">
            {status}
          </div>
        </form>
      </div>
      <ToastHost />
    </>
  )
}
