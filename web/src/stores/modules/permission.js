import { defineStore } from 'pinia'
import { constantRoutes } from '@/router'

const Layout = () => import('@/layout/index.vue')
const NotFound = () => import('@/views/error/404.vue')

/** src/views 下所有 .vue 组件（用于后端 component 字段映射） */
const viewModules = import.meta.glob('/src/views/**/*.vue')

/**
 * 后端 component 字段 -> 前端组件
 * 'Layout'                    -> src/layout/index.vue
 * 'system/user/index'         -> src/views/system/user/index.vue
 * 'system/user/index.vue'     -> 兼容带后缀
 * '/system/user/index'        -> 兼容带前导斜杠
 */
export function resolveComponent(component) {
  if (!component) return Layout
  if (component === 'Layout' || component === 'layout') return Layout

  let comp = String(component).trim().replace(/^\/+/, '').replace(/\.vue$/, '')
  const candidates = [`/src/views/${comp}.vue`]

  // 兼容 component 指向目录下的 index（'system/user' -> system/user/index.vue）
  if (!comp.endsWith('/index')) candidates.push(`/src/views/${comp}/index.vue`)

  for (const key of candidates) {
    if (viewModules[key]) return viewModules[key]
  }
  // 命中失败时兜底到 404 组件，避免整个应用白屏
  console.warn('[permission] 未找到组件映射：', component, '-> 已回退到 404')
  return NotFound
}

/** 路径规范化 */
function normalizePath(menu, isTop) {
  let path = (menu?.path || '').trim()
  if (isTop) {
    if (!path || path === '/') {
      path = '/' + (menu.name || menu.id || 'index')
    }
    return path.startsWith('/') ? path : '/' + path
  }
  // 子路由保持相对路径（去掉多余的前导斜杠，交给 vue-router 拼接）
  return path.replace(/^\/+/, '')
}

/**
 * 由完整路径生成全局唯一的路由名
 * '/workflow/list' -> 'workflow-list'
 *
 * 为什么不能直接用后端下发的 name：后端按菜单名生成，可能重复
 * （例如「流程列表」和「智能体管理」的子项都叫 List）。
 * vue-router 中同名路由后注册会顶掉先注册的，导致先注册的那条路径
 * 匹配不到、直接落到 catch-all 跳 404。改用路径派生名可彻底避免。
 */
function uniqueRouteName(fullPath, fallback) {
  const slug = String(fullPath || '')
    .replace(/^\/+/, '')
    .replace(/\/+/g, '-')
    .replace(/-+$/, '')
  return slug || fallback || 'route'
}

/** RouterVO -> vue-router 路由记录 */
export function buildRoute(menu, isTop = true, parentPath = '') {
  const path = normalizePath(menu, isTop)
  // 完整路径：顶层为绝对路径，子级与父级拼接
  const fullPath = isTop
    ? path
    : `${parentPath.replace(/\/+$/, '')}/${path.replace(/^\/+/, '')}`

  const route = {
    path,
    // 路由名必须全局唯一，否则会互相顶掉
    name: uniqueRouteName(fullPath, menu.name),
    hidden: !!menu.hidden,
    component: resolveComponent(menu.component),
    meta: {
      title: menu.meta?.title || menu.name || '',
      icon: menu.meta?.icon || '',
      noCache: !!menu.meta?.noCache
    }
  }

  if (menu.redirect) route.redirect = menu.redirect
  if (menu.alwaysShow !== undefined) route.alwaysShow = !!menu.alwaysShow

  const children = Array.isArray(menu.children) ? menu.children.filter(Boolean) : []
  if (children.length > 0) {
    route.children = children.map((child) => buildRoute(child, false, fullPath))
    // 没有 redirect 时保持父级不可点击（RuoYi 约定）
    if (!route.redirect) route.redirect = 'noRedirect'
  }
  return route
}

export const usePermissionStore = defineStore('permission', {
  state: () => ({
    /** 静态路由 + 动态路由（侧边栏渲染使用） */
    routes: [],
    /** 仅动态路由（router.addRoute 使用） */
    dynamicRoutes: [],
    /** 是否已生成过动态路由 */
    isGenerated: false
  }),
  actions: {
    /** 由后端 RouterVO[] 生成路由记录 */
    generateRoutes(routers) {
      const list = Array.isArray(routers) ? routers : []
      const dynamicRoutes = list.filter(Boolean).map((menu) => buildRoute(menu, true))
      this.dynamicRoutes = dynamicRoutes
      this.routes = constantRoutes.concat(dynamicRoutes)
      this.isGenerated = true
      return dynamicRoutes
    },
    /** 退出登录时重置，保证下次登录重新加载 */
    resetRoutes() {
      this.routes = []
      this.dynamicRoutes = []
      this.isGenerated = false
    }
  }
})

export default usePermissionStore
