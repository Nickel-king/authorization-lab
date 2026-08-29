import http from './http'

// 授权策略管理接口封装
export const fetchPolicies = () => http.get('/policies')

export const createPolicy = (payload) => http.post('/policies', payload)

export const updatePolicy = (id, payload) => http.put(`/policies/${id}`, payload)

export const deletePolicy = (id) => http.delete(`/policies/${id}`)