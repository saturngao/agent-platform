<template>
  <div class="navbar">
    <div class="navbar__left">
      <div class="hamburger" @click="toggleSideBar">
        <el-icon>
          <component :is="sidebar.opened ? 'Fold' : 'Expand'" />
        </el-icon>
      </div>
      <el-breadcrumb separator="/">
        <el-breadcrumb-item v-for="(item, index) in breadcrumbs" :key="index">
          {{ item.meta?.title }}
        </el-breadcrumb-item>
      </el-breadcrumb>
    </div>

    <div class="navbar__right">
      <el-tooltip content="全屏" placement="bottom" effect="light">
        <div class="hamburger" @click="toggleFullscreen">
          <el-icon><FullScreen /></el-icon>
        </div>
      </el-tooltip>

      <el-dropdown trigger="click" @command="handleCommand">
        <div class="navbar__user">
          <el-avatar :size="26" :src="avatarUrl">
            {{ (userStore.nickname || userStore.username || 'U').slice(0, 1) }}
          </el-avatar>
          <span>{{ userStore.nickname || userStore.username }}</span>
          <el-icon><CaretBottom /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item command="profile">
              <el-icon><User /></el-icon>个人中心
            </el-dropdown-item>
            <el-dropdown-item command="logout" divided>
              <el-icon><SwitchButton /></el-icon>退出登录
            </el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
    </div>

    <!-- 个人中心 -->
    <el-dialog v-model="profileVisible" title="个人中心" width="480px" append-to-body>
      <el-descriptions :column="1" border>
        <el-descriptions-item label="账号">{{ userStore.username || '-' }}</el-descriptions-item>
        <el-descriptions-item label="昵称">{{ userStore.nickname || '-' }}</el-descriptions-item>
        <el-descriptions-item label="邮箱">{{ userStore.userInfo?.email || '-' }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ userStore.userInfo?.phonenumber || '-' }}</el-descriptions-item>
        <el-descriptions-item label="部门">{{ userStore.userInfo?.deptName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="角色">
          <el-tag v-for="r in userStore.roles" :key="r" size="small" type="primary" style="margin-right: 4px">
            {{ r }}
          </el-tag>
          <span v-if="!userStore.roles.length">-</span>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="profileVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { useAppStore } from '@/stores/modules/app'
import { useUserStore } from '@/stores/modules/user'
import { usePermissionStore } from '@/stores/modules/permission'
import { resetRouter } from '@/router'
import { resetDynamicFlag } from '@/permission'

const route = useRoute()
const router = useRouter()
const appStore = useAppStore()
const userStore = useUserStore()
const permissionStore = usePermissionStore()

const sidebar = computed(() => appStore.sidebar)
const avatarUrl = computed(() => userStore.avatar || '')
const profileVisible = ref(false)

const breadcrumbs = computed(() => route.matched.filter((item) => item.meta && item.meta.title))

function toggleSideBar() {
  appStore.toggleSidebar()
}

function toggleFullscreen() {
  if (document.fullscreenElement) {
    document.exitFullscreen()
  } else {
    document.documentElement.requestFullscreen?.()
  }
}

async function handleCommand(command) {
  if (command === 'profile') {
    if (!userStore.userInfo?.userId) {
      await userStore.getInfo()
    }
    profileVisible.value = true
    return
  }
  if (command === 'logout') {
    try {
      await ElMessageBox.confirm('确定注销并退出系统吗？', '提示', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      })
    } catch (e) {
      return
    }
    await userStore.logout()
    permissionStore.resetRoutes()
    resetRouter()
    resetDynamicFlag()
    router.push('/login')
  }
}
</script>
