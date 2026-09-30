<template>
  <router-view />
</template>

<script setup>
import { onMounted } from 'vue'

onMounted(() => {
  // 移除 index.html 中的首屏 loading 占位
  document.getElementById('app-loading')?.remove()
})
</script>

<style lang="scss">
#app {
  height: 100%;
}
</style>
