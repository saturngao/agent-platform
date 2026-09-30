<template>
  <a v-if="isExternal" :href="to" target="_blank" rel="noopener" class="app-link">
    <slot />
  </a>
  <router-link v-else :to="to" custom v-slot="{ navigate }">
    <div class="app-link" @click="navigate">
      <slot />
    </div>
  </router-link>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  to: {
    type: [String, Object],
    required: true
  }
})

const isExternal = computed(() => {
  const path = typeof props.to === 'string' ? props.to : props.to?.path
  return /^(https?:|mailto:|tel:)/.test(path || '')
})
</script>

<style lang="scss" scoped>
.app-link {
  display: block;
  width: 100%;
}
</style>
