<template>
  <div class="error-page">
    <div class="error-page__code">404</div>
    <h2>页面不存在</h2>
    <p class="text-muted">抱歉，你访问的页面不存在或已被移除</p>
    <div class="error-page__actions">
      <el-button type="primary" @click="$router.push('/')">返回首页</el-button>
      <el-button @click="$router.back()">返回上一页</el-button>
    </div>
  </div>
</template>

<script setup></script>

<style scoped lang="scss">
.error-page {
  height: 100%;
  min-height: 60vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;

  &__code {
    font-size: 96px;
    font-weight: 700;
    line-height: 1;
    background: linear-gradient(135deg, #3b82f6, #8b5cf6);
    -webkit-background-clip: text;
    color: transparent;
  }

  h2 {
    margin: 16px 0 8px;
    font-size: 20px;
    color: #303133;
  }

  p {
    margin: 0 0 24px;
    font-size: 13px;
  }

  &__actions {
    display: flex;
    gap: 12px;
  }
}
</style>
