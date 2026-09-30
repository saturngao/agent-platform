<template>
  <div v-if="!item.hidden">
    <!-- 只有一个可展示子菜单：直接渲染该子菜单 -->
    <template v-if="showAsSingle">
      <AppLink v-if="onlyOneChild.meta" :to="resolvePath(onlyOneChild.path)">
        <el-menu-item :index="resolvePath(onlyOneChild.path)">
          <el-icon v-if="onlyOneChild.meta && onlyOneChild.meta.icon">
            <component :is="resolveIcon(onlyOneChild.meta.icon)" />
          </el-icon>
          <template #title>{{ onlyOneChild.meta?.title }}</template>
        </el-menu-item>
      </AppLink>
    </template>

    <!-- 多级菜单 -->
    <el-sub-menu v-else :index="resolvePath(item.path)" teleported>
      <template #title>
        <el-icon v-if="item.meta && item.meta.icon">
          <component :is="resolveIcon(item.meta.icon)" />
        </el-icon>
        <span>{{ item.meta?.title }}</span>
      </template>
      <SidebarItem
        v-for="child in item.children"
        :key="child.path || child.name"
        :item="child"
        :base-path="resolvePath(child.path)"
      />
    </el-sub-menu>
  </div>
</template>

<script setup>
import { computed, getCurrentInstance, ref } from 'vue'
import AppLink from '../AppLink.vue'

const props = defineProps({
  item: {
    type: Object,
    required: true
  },
  basePath: {
    type: String,
    default: ''
  }
})

const instance = getCurrentInstance()

/** 只有一个可见子路由时，直接展示该子路由 */
const onlyOneChild = ref(null)
const showAsSingle = computed(() => {
  const children = (props.item.children || []).filter((c) => !c.hidden)
  if (!props.item.alwaysShow && children.length === 1 && !children[0].children) {
    onlyOneChild.value = children[0]
    return true
  }
  if (!props.item.children || props.item.children.length === 0) {
    onlyOneChild.value = { ...props.item, path: '' }
    return true
  }
  onlyOneChild.value = null
  return false
})

/** 图标名 -> 全局已注册的组件名（兼容后端下发 'user'/'User'/'user-filled' 等写法） */
function resolveIcon(name) {
  if (!name) return 'Menu'
  const components = instance?.appContext?.components || {}
  if (components[name]) return name
  const lower = String(name).toLowerCase()
  const hit = Object.keys(components).find((key) => key.toLowerCase() === lower)
  return hit || 'Menu'
}

/** 拼接完整路径 */
function resolvePath(routePath) {
  const path = routePath || ''
  if (/^https?:/.test(path)) return path
  if (path.startsWith('/')) return path
  if (!path) return props.basePath || '/'
  return `${props.basePath || ''}/${path}`.replace(/\/+/g, '/')
}
</script>

<style lang="scss" scoped>
.el-menu-item [class^='el-icon'],
.el-sub-menu__title [class^='el-icon'] {
  margin-right: 6px;
}
</style>
