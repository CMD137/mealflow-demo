<script setup lang="ts">
import { computed } from 'vue';
import AppShell from '@/components/AppShell.vue';
import { mockPayApi } from '@/api/payments';
import { formatWait } from '@/utils/format';
import type { SubmitOrderResponse } from '@/types/api';

const result = computed<SubmitOrderResponse | null>(() => {
  const raw = sessionStorage.getItem('mealflow.lastOrderResult');
  return raw ? JSON.parse(raw) as SubmitOrderResponse : null;
});

async function pay() {
  if (!result.value?.payOrderId) return;
  await mockPayApi(result.value.payOrderId);
  window.alert('支付完成');
}
</script>

<template>
  <AppShell title="下单结果" subtitle="查看成单或排队状态" :show-nav="false">
    <section v-if="result" class="result-card card">
      <div class="result-icon">{{ result.mode === 'ORDER_CREATED' ? '✓' : '…' }}</div>
      <h2>{{ result.mode === 'ORDER_CREATED' ? '订单已创建' : '已进入排队' }}</h2>
      <p v-if="result.mode === 'ORDER_CREATED'">
        订单 {{ result.orderId }} 已生成，请完成支付。
      </p>
      <p v-else>
        排队号 {{ result.ticketNo }}，前方 {{ result.aheadCount }} 单，预计等待 {{ formatWait(result.estimatedWaitSeconds) }}。
      </p>
      <button v-if="result.payOrderId" class="primary-button" @click="pay">模拟支付</button>
      <RouterLink v-if="result.orderId" class="ghost-button" :to="`/orders/${result.orderId}`">查看订单</RouterLink>
      <RouterLink class="ghost-button" to="/orders">订单列表</RouterLink>
    </section>
    <section v-else class="empty">暂无下单结果</section>
  </AppShell>
</template>

<style scoped>
.result-card {
  display: grid;
  gap: 12px;
  justify-items: center;
  padding: 28px 18px;
  text-align: center;
}

.result-icon {
  display: grid;
  width: 68px;
  height: 68px;
  place-items: center;
  border-radius: 50%;
  background: #dcfce7;
  color: #15803d;
  font-size: 34px;
  font-weight: 900;
}

.result-card h2 {
  margin: 0;
}

.result-card p {
  margin: 0;
  color: #64748b;
  line-height: 1.7;
}
</style>
