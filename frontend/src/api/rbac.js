import http from './http'

// RBAC 角色与权限管理接口封装
export const fetchRoles = () => http.get('/rbac/roles')

export const createRole = (payload) => http.post('/rbac/roles', payload)

export const fetchRoleDetail = (id) => http.get(`/rbac/roles/${id}`)

export const saveRolePermissions = (id, permissionIds) =>
  http.put(`/rbac/roles/${id}/permissions`, permissionIds)

export const saveRoleUsers = (id, userIds) =>
  http.put(`/rbac/roles/${id}/users`, userIds)

export const fetchPermissionTree = () => http.get('/rbac/permissions/tree')