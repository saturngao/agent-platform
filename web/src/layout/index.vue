<template>
  <div class="app-wrapper">
    <div v-if="device === 'mobile' && sidebar.opened" class="drawer-bg" @click="handleClickOutside" />
    <Sidebar
      class="sidebar-container"
      :class="{ 'sidebar-container--collapsed': !sidebar.opened && device !== 'mobile' }"
    />
    <div class="main-container">
      <Navbar />
      <AppMain />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onBeforeUnmount, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useAppStore } from '@/stores/modules/app'
import Sidebar from './components/Sidebar/index.vue'
import Navbar from './components/Navbar.vue'
import AppMain from './components/AppMain.vue'

const appStore = useAppStore()
const route = useRoute()

const sidebar = computed(() => appStore.sidebar)
const device = computed(() => appStore.device)

const WIDTH = 992

function handleClickOutside() {
  appStore.closeSidebar(false)
}

function isMobile() {
  const rect = document.body.getBoundingClientRect()
  return rect.width - 1 < WIDTH
}

function resizeHandler() {
  if (!document.hidden) {
    appStore.toggleDevice(isMobile() ? 'mobile' : 'desktop')
    if (isMobile()) {
      appStore.closeSidebar(false)
    }
  }
}

onMounted(() => {
  resizeHandler()
  window.addEventListener('resize', resizeHandler)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', resizeHandler)
})

watch(
  () => route.path,
  () => {
    if (device.value === 'mobile' && sidebar.value.opened) {
      appStore.closeSidebar(false)
    }
  }
)
</script>

<style lang="scss" scoped>
.app-wrapper {
  display: flex;
  width: 100%;
  height: 100%;
}
</style>
