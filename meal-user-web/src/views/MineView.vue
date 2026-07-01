<script setup lang="ts">
import { useRouter } from 'vue-router';
import AppShell from '@/components/AppShell.vue';
import { useAuthStore } from '@/stores/auth';

const router = useRouter();
const auth = useAuthStore();

function logout() {
  auth.logout();
  router.replace('/login');
}
</script>

<template>
  <AppShell title="我的" subtitle="账号与常用入口">
    <section class="profile card">
      <div class="avatar">{{ auth.nickname.slice(0, 1) }}</div>
      <div>
        <h2>{{ auth.nickname }}</h2>
        <p>{{ auth.user?.phone }} · {{ auth.user?.level }}</p>
      </div>
    </section>

    <div class="quick-list card">
      <RouterLink to="/cart">购物车<span>›</span></RouterLink>
      <RouterLink to="/orders">我的订单<span>›</span></RouterLink>
      <RouterLink to="/vouchers">优惠券<span>›</span></RouterLink>
      <RouterLink to="/messages">通知消息<span>›</span></RouterLink>
    </div>

    <button class="danger-button logout" @click="logout">退出登录</button>
  </AppShell>
</template>

<style scoped>
.profile {
  display: flex;
  gap: 12px;
  align-items: center;
  padding: 16px;
}

.avatar {
  display: grid;
  width: 58px;
  height: 58px;
  place-items: center;
  border-radius: 18px;
  background: #1f2937;
  color: #ffffff;
  font-size: 24px;
  font-weight: 900;
}

.profile h2 {
  margin: 0 0 4px;
}

.profile p {
  margin: 0;
  color: #64748b;
}

.quick-list {
  margin-top: 14px;
  overflow: hidden;
}

.quick-list a {
  display: flex;
  justify-content: space-between;
  border-bottom: 1px solid #edf0f5;
  padding: 15px;
  font-weight: 700;
}

.quick-list a:last-child {
  border-bottom: 0;
}

.quick-list span {
  color: #94a3b8;
}

.logout {
  width: 100%;
  margin-top: 16px;
}
</style>
