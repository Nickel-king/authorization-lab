import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// Vite 构建配置
export default defineConfig({
  // 启用 Vue 单文件组件插件
  plugins: [vue()],
  resolve: {
    alias: {
      // 使用 '@' 指向 /src，简化深层 import 路径
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      // 开发环境将 /api 代理到后端授权服务 8080
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true
      }
    }
  }
})