import router, { notFoundRoute } from '@/router'
import { getToken } from '@/utils/auth'
import { useUserStore } from '@/stores/modules/user'
import { usePermissionStore } from '@/stores/modules/permission'
import { ElMessage } from 'element-plus'

const whiteList = ['/login', '/404']

/** 动态路由是否已经注入（整个应用生命周期只加载一次） */
let dynamicLoaded = false

export function resetDynamicFlag() {
  dynamicLoaded = false
}

router.beforeEach(async (to, from, next) => {
  const token = getToken()

  // 1. 未登录
  if (!token) {
    if (whiteList.includes(to.path)) {
      next()
    } else {
      next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
    }
    return
  }

  // 2. 已登录访问登录页 -> 回首页
  if (to.path === '/login') {
    next({ path: '/' })
    return
  }

  const userStore = useUserStore()
  const permissionStore = usePermissionStore()

  // 3. 首次进入（或刷新页面）加载动态路由
  if (!permissionStore.isGenerated || !dynamicLoaded) {
    try {
      if (!userStore.roles.length) {
        await userStore.getInfo()
      }
      const { getRouters } = await import('@/api/auth')
      const routers = await getRouters()
      const accessRoutes = permissionStore.generateRoutes(routers)
      accessRoutes.forEach((route) => router.addRoute(route))
      router.addRoute(notFoundRoute)
      dynamicLoaded = true
      // 确保 addRoute 已完成
      next({ ...to, replace: true })
    } catch (error) {
      console.error('[permission] 动态路由加载失败：', error)
      ElMessage.error(error?.message || '路由加载失败，请重新登录')
      await userStore.logout()
      permissionStore.resetRoutes()
      resetDynamicFlag()
      next(`/login?redirect=${encodeURIComponent(to.fullPath)}`)
    }
    return
  }

  next()
})

router.afterEach((to) => {
  const title = to.meta?.title
  document.title = title ? `${title} - AI 工作流与智能体平台` : 'AI 工作流与智能体平台'
})

export default router
