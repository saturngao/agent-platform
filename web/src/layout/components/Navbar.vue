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
            <el-dropdown-item command="password">
              <el-icon><Lock /></el-icon>修改密码
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

    <!-- 修改密码 -->
    <el-dialog v-model="pwdVisible" title="修改密码" width="420px" append-to-body @closed="pwdFormRef?.resetFields()">
      <el-form ref="pwdFormRef" :model="pwdForm" :rules="pwdRules" label-width="90px">
        <el-form-item label="旧密码" prop="oldPassword">
          <el-input v-model="pwdForm.oldPassword" type="password" show-password placeholder="请输入当前密码" autocomplete="off" />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="pwdForm.newPassword" type="password" show-password placeholder="不少于 6 位" autocomplete="off" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="pwdForm.confirmPassword" type="password" show-password placeholder="再次输入新密码" autocomplete="off" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="pwdSubmitting" @click="submitPassword">确定修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAppStore } from '@/stores/modules/app'
import { useUserStore } from '@/stores/modules/user'
import { usePermissionStore } from '@/stores/modules/permission'
import { resetRouter } from '@/router'
import { resetDynamicFlag } from '@/permission'
import { updatePwd } from '@/api/system'

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
  if (command === 'password') {
    pwdVisible.value = true
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
    await doLogout()
  }
}

async function doLogout() {
  await userStore.logout()
  permissionStore.resetRoutes()
  resetRouter()
  resetDynamicFlag()
  router.push('/login')
}

/* ------------------------------ 修改密码 ------------------------------ */
const pwdVisible = ref(false)
const pwdSubmitting = ref(false)
const pwdFormRef = ref(null)
const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: ''
})
const pwdRules = {
  oldPassword: [{ required: true, message: '请输入旧密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度 6 到 32 个字符', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function submitPassword() {
  if (!pwdFormRef.value) return
  try {
    await pwdFormRef.value.validate()
  } catch (e) {
    return
  }
  pwdSubmitting.value = true
  try {
    await updatePwd({ oldPassword: pwdForm.oldPassword, newPassword: pwdForm.newPassword })
    pwdVisible.value = false
    pwdForm.oldPassword = ''
    pwdForm.newPassword = ''
    pwdForm.confirmPassword = ''
    await ElMessageBox.alert('密码修改成功，请使用新密码重新登录。', '提示', {
      confirmButtonText: '重新登录',
      type: 'success',
      callback: () => {}
    })
    await doLogout()
  } catch (e) {
    // request 拦截器已弹出后端错误信息
  } finally {
    pwdSubmitting.value = false
  }
}
</script>
