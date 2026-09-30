<template>
  <div class="sidebar-logo-container" :class="{ collapse: collapse }">
    <svg class="sidebar-logo" viewBox="0 0 32 32" fill="none" xmlns="http://www.w3.org/2000/svg">
      <rect width="32" height="32" rx="8" fill="#3b82f6" />
      <path d="M9 21.5 16 9l7 12.5H9Z" fill="#fff" opacity="0.95" />
      <circle cx="16" cy="19" r="2.2" fill="#3b82f6" />
    </svg>
    <span v-if="!collapse" class="sidebar-title">{{ title }}</span>
  </div>
</template>

<script setup>
import defaultSettings from '@/settings'

defineProps({
  collapse: {
    type: Boolean,
    default: false
  }
})

const title = defaultSettings.title
</script>
