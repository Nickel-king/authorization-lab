import http from './http'

// 授权决策检查接口封装
export const checkAuthorization = (payload) =>
  http.post('/authorization/check', payload)

// 权限模拟与诊断接口封装
export const runSimulator = (payload) => http.post('/simulator/run', payload)