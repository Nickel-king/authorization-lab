import http from './http'

// 团队接口封装（sys_team + sys_team_member），供“团队与用户组管理”页面
// 与“协作图谱与关系元组”页面使用。成员变动后端同步写回 ReBAC 元组。

// 查询团队列表（含关联部门名称 departmentName 与成员数 memberCount）
export const fetchTeams = () => http.get('/teams')

// 新增团队（name 必填 / code 唯一必填 / departmentId 可选 / description）
export const createTeam = (payload) => http.post('/teams', payload)

// 更新团队基本属性
export const updateTeam = (id, payload) => http.put(`/teams/${id}`, payload)

// 删除团队（级联解除成员记录与 member 元组）
export const deleteTeam = (id) => http.delete(`/teams/${id}`)

// 查询团队成员清单（TeamMemberVO：userId/displayName/username/department/teamRole/createdAt）
export const fetchTeamMembers = (teamId) => http.get(`/teams/${teamId}/members`)

// 批量添加团队成员（payload: { userIds: [], relation: 'member' }）
export const addTeamMembers = (teamId, payload) => http.post(`/teams/${teamId}/members`, payload)

// 移除团队成员
export const removeTeamMember = (teamId, userId) => http.delete(`/teams/${teamId}/members/${userId}`)

// 设置团队成员角色（payload: { role: 'member' | 'leader' }）
export const setTeamMemberRole = (teamId, userId, role) =>
  http.put(`/teams/${teamId}/members/${userId}/role`, { role })