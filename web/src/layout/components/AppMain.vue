<template>
  <section class="app-main">
    <router-view v-slot="{ Component, route }">
      <transition name="fade-transform" mode="out-in">
        <keep-alive :max="20">
          <component :is="Component" :key="route.path" />
        </keep-alive>
      </transition>
    </router-view>
  </section>
</template>

<script setup></script>

<style lang="scss" scoped>
.app-main {
  position: relative;
  flex: 1;
  min-height: 0;
  overflow: auto;
  background-color: #f5f7fa;
}
</style>
