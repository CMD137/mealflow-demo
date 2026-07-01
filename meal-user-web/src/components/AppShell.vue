<script setup lang="ts">
import BottomNav from './BottomNav.vue';

defineProps<{
  title?: string;
  subtitle?: string;
  showNav?: boolean;
}>();
</script>

<template>
  <div class="phone-shell">
    <header v-if="title" class="app-header">
      <div>
        <h1>{{ title }}</h1>
        <p v-if="subtitle">{{ subtitle }}</p>
      </div>
      <slot name="header-extra" />
    </header>
    <main class="app-main" :class="{ 'with-nav': showNav !== false }">
      <slot />
    </main>
    <BottomNav v-if="showNav !== false" />
  </div>
</template>
