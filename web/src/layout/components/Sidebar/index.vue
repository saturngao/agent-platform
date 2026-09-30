<template>
  <div>
    <Logo v-if="showLogo" :collapse="isCollapse" />
    <el-scrollbar wrap-class="scrollbar-wrapper">
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :collapse-transition="false"
        :unique-opened="false"
        mode="vertical"
        background-color="#ffffff"
        text-color="#303133"
        active-text-color="#3b82f6"
      >
        <SidebarItem
          v-for="route in menuRoutes"
          :key="route.path"
          :item="route"
          :base-path="route.path"
        />
      </el-menu>
    </el-scrollbar>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/modules/app'
import { usePermissionStore } from '@/stores/modules/permission'
import defaultSettings from '@/settings'
import Logo from './Logo.vue'
import SidebarItem from './SidebarItem.vue'

const route = useRoute()
const appStore = useAppStore()
const permissionStore = usePermissionStore()

const showLogo = defaultSettings.sidebarLogo
const isCollapse = computed(() => !appStore.sidebar.opened)

/** 侧边栏菜单 = 静态路由 + 后端动态路由（过滤 hidden） */
const menuRoutes = computed(() => {
  const list = permissionStore.routes.length ? permissionStore.routes : permissionStore.dynamicRoutes
  return (list || []).filter((r) => !r.hidden)
})

const activeMenu = computed(() => route.meta?.activeMenu || route.path)
</script>

<style lang="scss" scoped>
.scrollbar-wrapper {
  height: calc(100vh - 56px);
}

:deep(.el-scrollbar__bar.is-vertical) {
  width: 4px;
}
</style>
