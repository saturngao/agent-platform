import { createRouter, createWebHistory } from 'vue-router'

const Layout = () => import('@/layout/index.vue')

/** 静态路由：登录页、404、根布局；其余菜单由后端下发后动态注入 */
export const constantRoutes = [
  {
    path: '/login',
    component: () => import('@/views/login.vue'),
    hidden: true
  },
  {
    path: '/404',
    component: () => import('@/views/error/404.vue'),
    hidden: true
  },
  {
    path: '/',
    component: Layout,
    redirect: '/index',
    hidden: true,
    children: [
      {
        path: 'index',
        name: 'Index',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '首页', icon: 'HomeFilled', noCache: true }
      }
    ]
  },
  /* ------------------------- 隐藏路由（不出现在菜单中） ------------------------ */
  {
    path: '/workflow/designer',
    component: Layout,
    hidden: true,
    children: [
      {
        path: ':defId?',
        name: 'WorkflowDesigner',
        component: () => import('@/views/workflow/designer/index.vue'),
        meta: { title: '流程设计器', activeMenu: '/workflow/list', noCache: true }
      }
    ]
  },
  {
    path: '/agent/chat',
    component: Layout,
    hidden: true,
    children: [
      {
        path: ':agentId',
        name: 'AgentChat',
        component: () => import('@/views/agent/chat/index.vue'),
        meta: { title: '智能体对话', activeMenu: '/agent/list', noCache: true }
      }
    ]
  }
]

/** 兜底路由：动态路由加载完成后追加 */
export const notFoundRoute = {
  path: '/:pathMatch(.*)*',
  redirect: '/404',
  hidden: true
}

const router = createRouter({
  history: createWebHistory(),
  routes: constantRoutes,
  scrollBehavior: () => ({ top: 0 })
})

/** 移除所有动态路由（退出登录时调用） */
export function resetRouter() {
  const keepNames = new Set(constantRoutes.map((r) => r.name).filter(Boolean))
  router.getRoutes().forEach((route) => {
    if (route.name && !keepNames.has(route.name)) {
      router.removeRoute(route.name)
    }
  })
}

export default router
