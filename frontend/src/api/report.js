import http from './http'

// 报表资源相关接口封装（统计与财务报表）
export const fetchReports = (params) => http.get('/reports', { params })

export const createReport = (payload) => http.post('/reports', payload)

export const deleteReport = (id) => http.delete(`/reports/${id}`)