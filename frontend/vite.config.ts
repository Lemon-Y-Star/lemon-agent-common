import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// 开发期把 /api 代理到本地后端（bootstrap 默认端口 18090），免 CORS 配置
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:18090',
        changeOrigin: true,
      },
    },
  },
})
