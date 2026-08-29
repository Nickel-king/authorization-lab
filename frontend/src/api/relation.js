import http from './http'

// ReBAC 关系元组与关系链接口封装
export const fetchTuples = (params) => http.get('/relations', { params })

export const createTuple = (payload) => http.post('/relations', payload)

export const updateTuple = (id, payload) => http.put(`/relations/${id}`, payload)

export const deleteTuple = (id) => http.delete(`/relations/${id}`)

export const fetchPath = (params) => http.get('/relations/path', { params })