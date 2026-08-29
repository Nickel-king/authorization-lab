import http from './http'

// 用户相关接口封装（用户与组织管理）
// list: 返回含角色名称的用户列表 UserVO[]
export const fetchUsers = (params) => http.get('/users', { params })

// 指定用户的已分配角色 ID
export const fetchUserRoleIds = (userId) => http.get(`/users/${userId}/roles`)

// 新增用户（username / displayName / department / roleIds）
export const createUser = (payload) => http.post('/users', payload)

// 编辑用户主体属性（displayName / department）
export const updateUser = (userId, body) => http.put(`/users/${userId}`, body)

// 保存用户角色分配
export const saveUserRoles = (userId, roleIds) => http.put(`/users/${userId}/roles`, roleIds)

export const fetchProjects = (params) => http.get('/projects', { params })

// 新增项目（name / department / ownerId / description）
export const createProject = (payload) => http.post('/projects', payload)