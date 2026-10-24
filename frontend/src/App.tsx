import { Navigate, Route, Routes } from 'react-router-dom'
import { getToken } from './api/client'
import Agents from './pages/Agents'
import Chat from './pages/Chat'
import Admin from './pages/Admin'
import Runs from './pages/Runs'
import Usage from './pages/Usage'
import Layout from './components/Layout'
import Account from './pages/Account'
import Login from './pages/Login'
import Mcps from './pages/Mcps'
import ModelConnections from './pages/ModelConnections'

/**
 * 路由表：登录页独立；业务页共用 Layout（侧栏 + 顶栏）。
 * v1 只开放「模型连接 / MCP 连接 / 账户」三个页面，
 * 对话运行时与 Agent 搭建等入口占位置灰（PRD 页面清单 P01/P02，后续切片实现）。
 */
export default function App() {
  const loggedIn = getToken() !== null
  return (
    <Routes>
      <Route path="/login" element={loggedIn ? <Navigate to="/" replace /> : <Login />} />
      <Route element={loggedIn ? <Layout /> : <Navigate to="/login" replace />}>
        <Route path="/" element={<Navigate to="/chat" replace />} />
        <Route path="/runs" element={<Runs />} />
        <Route path="/usage" element={<Usage />} />
        <Route path="/admin" element={<Admin />} />
        <Route path="/chat" element={<Chat />} />
        <Route path="/chat/:sessionId" element={<Chat />} />
        <Route path="/account" element={<Account />} />
        <Route path="/agents" element={<Agents />} />
        <Route path="/models" element={<ModelConnections />} />
        <Route path="/mcps" element={<Mcps />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
