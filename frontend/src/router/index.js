import { createRouter, createWebHistory } from 'vue-router'
import AdminLayout from '@/layouts/AdminLayout.vue'

// 前端路由表：7 个授权治理菜单页统一挂载于 AdminLayout 下
const routes = [
  {
    path: '/',
    component: AdminLayout,
    redirect: '/system/departments',
    children: [
      {
        path: 'system/departments',
        name: 'departments',
        component: () => import('@/views/system/Departments.vue'),
        meta: { title: '部门与组织架构' }
      },
      {
        path: 'system/teams',
        name: 'teams',
        component: () => import('@/views/system/Teams.vue'),
        meta: { title: '团队与用户组管理' }
      },
      {
        path: 'system/users',
        name: 'users',
        component: () => import('@/views/system/Users.vue'),
        meta: { title: '用户与身份管理' }
      },
      {
        path: 'system/roles',
        name: 'roles',
        component: () => import('@/views/system/Roles.vue'),
        meta: { title: '角色与功能授权' }
      },
      {
        path: 'authz/policies',
        name: 'policies',
        component: () => import('@/views/authz/Policies.vue'),
        meta: { title: '细粒度策略中心' }
      },
      {
        path: 'authz/relations',
        name: 'relations',
        component: () => import('@/views/authz/Relations.vue'),
        meta: { title: '协作与关系图谱' }
      },
      {
        path: 'authz/simulator',
        name: 'simulator',
        component: () => import('@/views/authz/Simulator.vue'),
        meta: { title: '授权模拟与决策解释' }
      },
      {
        path: 'workspace/projects',
        name: 'workspace-projects',
        component: () => import('@/views/workspace/Projects.vue'),
        meta: { title: '科研项目工作台' }
      },
      {
        path: 'workspace/reports',
        name: 'workspace-reports',
        component: () => import('@/views/workspace/Reports.vue'),
        meta: { title: '报表统计中心' }
      }
    ]
  }
]

// 使用 HTML5 History 模式创建路由实例
const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router