<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import AppShell from '@/components/AppShell.vue';
import { ordersApi } from '@/api/orders';
import { formatMoney, orderStatusText, statusClass } from '@/utils/format';
import type { OrderView } from '@/types/api';

const loading = ref(false);
const active = ref('all');
const orders = ref<OrderView[]>([]);

const tabs = [
  { key: 'all', label: '全部' },
  { key: 'PENDING_PAYMENT', label: '待支付' },
  { key: 'WAIT_MERCHANT_ACCEPT', label: '待接单' },
  { key: 'READY', label: '待取餐' },
  { key: 'COMPLETED', label: '完成' }
];

const visibleOrders = computed(() => {
  if (active.value === 'all') return orders.value;
  return orders.value.filter((order) => order.status === active.value);
});

async function load() {
  loading.value = true;
  try {
    orders.value = (await ordersApi()).sort((a, b) => b.orderId - a.orderId);
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <AppShell title="我的订单" subtitle="查看支付和履约状态">
    <div class="tab-strip">
      <button v-for="tab in tabs" :key="tab.key" :class="{ active: active === tab.key }" @click="active = tab.key">
        {{ tab.label }}
      </button>
    </div>

    <RouterLink v-for="order in visibleOrders" :key="order.orderId" class="order-card card" :to="`/orders/${order.orderId}`">
      <div class="order-head">
        <strong>订单 {{ order.orderId }}</strong>
        <span class="status-pill" :class="statusClass(order.status)">{{ orderStatusText(order.status) }}</span>
      </div>
      <p>{{ order.items.map((item) => `${item.name}×${item.quantity}`).join('、') || '商品明细' }}</p>
      <div class="order-foot">
        <span>商家 {{ order.merchantId }}</span>
        <strong>{{ formatMoney(order.amountCent) }}</strong>
      </div>
    </RouterLink>

    <div v-if="!loading && !visibleOrders.length" class="empty">暂无订单</div>
  </AppShell>
</template>

<style scoped>
.tab-strip {
  display: flex;
  gap: 8px;
  margin: 0 -14px 12px;
  overflow-x: auto;
  padding: 0 14px;
}

.tab-strip button {
  flex: 0 0 auto;
  border-radius: 999px;
  background: #ffffff;
  color: #64748b;
  padding: 8px 13px;
  font-weight: 700;
}

.tab-strip button.active {
  background: #2563eb;
  color: #ffffff;
}

.order-card {
  display: block;
  margin-bottom: 10px;
  padding: 14px;
}

.order-head,
.order-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.order-card p {
  margin: 12px 0;
  color: #64748b;
  font-size: 13px;
  line-height: 1.6;
}

.order-foot span {
  color: #94a3b8;
  font-size: 13px;
}
</style>
