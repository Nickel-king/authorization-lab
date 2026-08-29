import http from './http'

// 部门与组织架构接口封装（sys_department）
// tree: 返回部门组织树根节点（DepartmentTreeNodeVO 含 children 内嵌子部门）

// 查询部门组织树
export const fetchDepartments = () => http.get('/departments')

// 新增部门（顶级传空 parentId，子级传父部门 ID）
export const createDepartment = (payload) => http.post('/departments', payload)

// 更新部门（name / sortOrder / parentId）
export const updateDepartment = (id, payload) => http.put(`/departments/${id}`, payload)

// 删除部门（存在子部门时后端会拒绝）
export const deleteDepartment = (id) => http.delete(`/departments/${id}`)