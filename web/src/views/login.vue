<template>
  <div class="login-page">
    <div class="login-bg">
      <span class="circle circle-1" />
      <span class="circle circle-2" />
      <span class="circle circle-3" />
    </div>

    <div class="login-card">
      <div class="login-card__left">
        <div class="brand">
          <svg viewBox="0 0 32 32" width="34" height="34">
            <rect width="32" height="32" rx="8" fill="#ffffff" opacity="0.18" />
            <path d="M9 21.5 16 9l7 12.5H9Z" fill="#fff" />
          </svg>
          <span>AI Workflow</span>
        </div>
        <h2>AI 工作流与智能体平台</h2>
        <p>可视化编排工作流 · 统一管理大模型 · 快速构建智能体</p>
        <ul>
          <li>流程节点拖拽编排，一键发布执行</li>
          <li>多模型接入与调用审计</li>
          <li>智能体会话、工具与知识库管理</li>
        </ul>
      </div>

      <div class="login-card__right">
        <h3>账号登录</h3>
        <p class="sub">请使用管理员账号登录后台</p>

        <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="handleLogin">
          <el-form-item prop="username">
            <el-input v-model="form.username" placeholder="请输入账号" :prefix-icon="User" clearable />
          </el-form-item>
          <el-form-item prop="password">
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              :prefix-icon="Lock"
              show-password
              clearable
            />
          </el-form-item>

          <div class="login-extra">
            <el-checkbox v-model="form.remember">记住我</el-checkbox>
          </div>

          <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
            {{ loading ? '登录中...' : '登 录' }}
          </el-button>

          <div class="login-tip">
            默认账号：<b>admin</b> / <b>admin123</b>（以后端实际账号为准）
          </div>
        </el-form>
      </div>
    </div>

    <div class="login-footer">© 2026 AI 工作流与智能体平台 · 管理后台</div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { User, Lock } from '@element-plus/icons-vue'
import { useUserStore } from '@/stores/modules/user'
import { getRemember, setRemember, removeRemember } from '@/utils/auth'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const redirect = ref(route.query.redirect ? decodeURIComponent(String(route.query.redirect)) : '/')

const form = ref({
  username: '',
  password: '',
  remember: false
})

const rules = {
  username: [{ required: true, message: '请输入账号', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

onMounted(() => {
  const remember = getRemember() || {}
  if (remember.username) {
    form.value.username = remember.username
    form.value.remember = true
  }
})

async function handleLogin() {
  try {
    await formRef.value.validate()
  } catch (e) {
    return
  }
  loading.value = true
  try {
    await userStore.login({ username: form.value.username, password: form.value.password })
    if (form.value.remember) {
      setRemember({ username: form.value.username })
    } else {
      removeRemember()
    }
    router.push(redirect.value || '/')
  } catch (e) {
    /* 失败原因已由 request 拦截器统一提示 */
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.login-page {
  position: relative;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #eef4ff 0%, #f7f9fc 45%, #eaf1ff 100%);
  overflow: hidden;
}

.login-bg {
  position: absolute;
  inset: 0;
  .circle {
    position: absolute;
    border-radius: 50%;
    filter: blur(2px);
    opacity: 0.5;
  }
  .circle-1 {
    width: 340px;
    height: 340px;
    top: -90px;
    left: -80px;
    background: radial-gradient(circle at 30% 30%, #6ea8fe, rgba(110, 168, 254, 0));
  }
  .circle-2 {
    width: 420px;
    height: 420px;
    bottom: -160px;
    right: -100px;
    background: radial-gradient(circle at 60% 40%, #a78bfa, rgba(167, 139, 250, 0));
  }
  .circle-3 {
    width: 220px;
    height: 220px;
    bottom: 15%;
    left: 12%;
    background: radial-gradient(circle at 50% 50%, #67e8f9, rgba(103, 232, 249, 0));
  }
}

.login-card {
  position: relative;
  z-index: 2;
  display: flex;
  width: 860px;
  max-width: 92vw;
  min-height: 460px;
  background: #fff;
  border-radius: 16px;
  box-shadow: 0 18px 48px rgba(31, 45, 61, 0.12);
  overflow: hidden;
}

.login-card__left {
  flex: 1;
  padding: 44px 36px;
  color: #fff;
  background: linear-gradient(160deg, #3b82f6 0%, #6366f1 55%, #8b5cf6 100%);
  display: flex;
  flex-direction: column;
  justify-content: center;

  .brand {
    display: flex;
    align-items: center;
    gap: 10px;
    font-size: 15px;
    font-weight: 600;
    opacity: 0.92;
    margin-bottom: 26px;
  }

  h2 {
    margin: 0 0 12px;
    font-size: 24px;
    font-weight: 600;
  }

  p {
    margin: 0 0 24px;
    font-size: 13px;
    line-height: 1.7;
    opacity: 0.85;
  }

  ul {
    margin: 0;
    padding: 0;
    list-style: none;
    li {
      position: relative;
      padding-left: 16px;
      font-size: 13px;
      line-height: 2.1;
      opacity: 0.9;
      &::before {
        content: '';
        position: absolute;
        left: 0;
        top: 12px;
        width: 6px;
        height: 6px;
        border-radius: 50%;
        background: #fff;
        opacity: 0.85;
      }
    }
  }
}

.login-card__right {
  flex: 1;
  padding: 56px 44px;
  display: flex;
  flex-direction: column;
  justify-content: center;

  h3 {
    margin: 0 0 6px;
    font-size: 20px;
    color: #1f2937;
  }

  .sub {
    margin: 0 0 26px;
    font-size: 13px;
    color: #909399;
  }
}

.login-extra {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 14px;
}

.login-btn {
  width: 100%;
  border-radius: 8px;
  height: 42px;
  font-size: 15px;
  letter-spacing: 2px;
}

.login-tip {
  margin-top: 16px;
  font-size: 12px;
  color: #a8abb2;
  text-align: center;
}

.login-footer {
  position: relative;
  z-index: 2;
  margin-top: 24px;
  font-size: 12px;
  color: #909399;
}

@media (max-width: 768px) {
  .login-card__left {
    display: none;
  }
  .login-card__right {
    padding: 40px 24px;
  }
}
</style>
