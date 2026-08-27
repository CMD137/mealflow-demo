<script setup lang="ts">
import { computed } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import BottomNav from './BottomNav.vue';

withDefaults(defineProps<{
  title?: string;
  subtitle?: string;
  showNav?: boolean;
}>(), {
  showNav: true
});

const route = useRoute();
const router = useRouter();
const topLevelPaths = new Set(['/', '/orders', '/vouchers', '/mine']);
const showBack = computed(() => !topLevelPaths.has(route.path));

function back() {
  if (window.history.length > 1) {
    router.back();
    return;
  }
  router.push('/');
}
</script>

<template>
  <div class="phone-shell">
    <header v-if="title" class="app-header">
      <div>
        <h1>{{ title }}</h1>
        <p v-if="subtitle">{{ subtitle }}</p>
      </div>
      <div class="app-header-actions">
        <button v-if="showBack" class="header-back" @click="back">返回</button>
        <slot name="header-extra" />
      </div>
    </header>
    <main class="app-main" :class="{ 'with-nav': showNav !== false }">
      <slot />
    </main>
    <BottomNav v-if="showNav !== false" />
  </div>
</template>
