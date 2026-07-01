<script setup lang="ts">
const navs = [
  { path: '/', label: '首页', icon: '⌂' },
  { path: '/orders', label: '订单', icon: '☰' },
  { path: '/vouchers', label: '优惠券', icon: '券' },
  { path: '/mine', label: '我的', icon: '人' }
];
</script>

<template>
  <nav class="bottom-nav">
    <RouterLink v-for="item in navs" :key="item.path" :to="item.path" class="bottom-nav-item">
      <span>{{ item.icon }}</span>
      <strong>{{ item.label }}</strong>
    </RouterLink>
  </nav>
</template>
