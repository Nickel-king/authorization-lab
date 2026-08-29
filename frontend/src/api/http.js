import axios from 'axios'
import { ElMessage } from 'element-plus'

// 创建统一的 axios 实例：以 /api 为基址
const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

// 请求拦截器：目前无需额外注入鉴权头（控制台视为管理员入口）
http.interceptors.request.use(
  (config) => config,
  (error) => Promise.reject(error)
)

// 响应拦截器：统一解包后端 ApiResponse { success, data, message }
http.interceptors.response.use(
  (response) => {
    const body = response.data
    // 兼容非 ApiResponse 结构的直接返回
    if (body && typeof body === 'object' && 'success' in body) {
      if (body.success) {
        return body.data
      }
      ElMessage.error(body.message || '请求失败')
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  (error) => {
    const msg = error.response?.data?.message || error.message || '网络错误'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

export default http