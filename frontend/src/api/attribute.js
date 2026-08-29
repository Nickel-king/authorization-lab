import http from './http'

// 属性元数据字典接口封装
export const fetchAttributes = (category) =>
  http.get('/attributes', { params: { category } })

export const createAttribute = (payload) => http.post('/attributes', payload)

export const updateAttribute = (id, payload) =>
  http.put(`/attributes/${id}`, payload)

export const deleteAttribute = (id) => http.delete(`/attributes/${id}`)