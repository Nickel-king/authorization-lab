import http from './http'

// 项目-团队协作绑定接口（仅团队维度，去除散装用户挂载）
// 元组模式：project:{projectId}#{relation}@team:{teamId}#member

// 获取项目已绑定的团队 + 穿透有效成员聚合视图（Tab1 + Tab2 单次接口）
export const fetchProjectTeamBinding = (projectId) => http.get(`/projects/${projectId}/teams`)

// 绑定协作团队（写入 project#{relation}@team#{teamId}#member）
export const bindProjectTeam = (projectId, payload) =>
  http.post(`/projects/${projectId}/teams`, payload)

// 切换团队-项目角色（viewer / editor / manager）
export const updateProjectTeamRelation = (projectId, tupleId, relation) =>
  http.put(`/projects/${projectId}/teams/${tupleId}`, { relation })

// 解除团队与项目的绑定
export const unbindProjectTeam = (projectId, tupleId) =>
  http.delete(`/projects/${projectId}/teams/${tupleId}`)