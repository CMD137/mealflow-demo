<script setup lang="ts">
import { onMounted, ref } from 'vue';
import AppShell from '@/components/AppShell.vue';
import { merchantsApi } from '@/api/merchant';
import { messagesApi } from '@/api/notify';
import { useAuthStore } from '@/stores/auth';
import type { MerchantView, MessageView } from '@/types/api';

const auth = useAuthStore();
const loading = ref(false);
const merchants = ref<MerchantView[]>([]);
const messages = ref<MessageView[]>([]);

async function load() {
  loading.value = true;
  try {
    const [merchantData, messageData] = await Promise.all([
      merchantsApi(),
      messagesApi().catch(() => [])
    ]);
    merchants.value = merchantData;
    messages.value = messageData;
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <AppShell title="今天吃什么" :subtitle="`你好，${auth.nickname}`">
    <template #header-extra>
      <RouterLink to="/messages" class="message-badge">{{ messages.length }}</RouterLink>
    </template>

    <section class="search-card">
      <strong>附近可点商家</strong>
      <span>{{ loading ? '正在刷新...' : `${merchants.length} 家营业数据` }}</span>
    </section>

    <div class="section-title">
      <h2>推荐商家</h2>
      <button class="link refresh" @click="load">刷新</button>
    </div>

    <RouterLink
      v-for="merchant in merchants"
      :key="merchant.merchantId"
      class="merchant-card card"
      :to="`/merchant/${merchant.merchantId}`"
    >
      <div class="merchant-logo">{{ merchant.name.slice(0, 1) }}</div>
      <div>
        <h3>{{ merchant.name }}</h3>
        <p>基础产能 {{ merchant.baseCapacity }} · 人工系数 {{ merchant.manualFactor }}</p>
        <span class="status-pill" :class="{ success: merchant.businessStatus === 'OPEN', danger: merchant.businessStatus !== 'OPEN' }">
          {{ merchant.businessStatus === 'OPEN' ? '营业中' : '休息中' }}
        </span>
      </div>
      <span class="go">›</span>
    </RouterLink>

    <div v-if="!loading && !merchants.length" class="empty">暂无可点商家</div>
  </AppShell>
</template>

<style scoped>
.message-badge {
  display: grid;
  min-width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 999px;
  background: #e0ecff;
  color: #2563eb;
  font-weight: 800;
}

.search-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-radius: 14px;
  background: linear-gradient(135deg, #2563eb, #0f766e);
  color: #ffffff;
  padding: 18px;
}

.search-card strong {
  font-size: 18px;
}

.search-card span {
  color: rgba(255, 255, 255, 0.82);
  font-size: 13px;
}

.refresh {
  background: transparent;
}

.merchant-card {
  display: grid;
  grid-template-columns: 58px 1fr 18px;
  gap: 12px;
  align-items: center;
  margin-bottom: 10px;
  padding: 14px;
}

.merchant-logo {
  display: grid;
  width: 58px;
  height: 58px;
  place-items: center;
  border-radius: 12px;
  background: #eef2ff;
  color: #2563eb;
  font-size: 24px;
  font-weight: 900;
}

.merchant-card h3 {
  margin: 0 0 5px;
  font-size: 17px;
}

.merchant-card p {
  margin: 0 0 8px;
  color: #6b7280;
  font-size: 13px;
}

.go {
  color: #94a3b8;
  font-size: 28px;
}
</style>
