<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute } from 'vue-router';
import AppShell from '@/components/AppShell.vue';
import { cancelOrderApi, orderApi } from '@/api/orders';
import { mockPayApi, paymentApi } from '@/api/payments';
import { formatMoney, orderStatusText, statusClass } from '@/utils/format';
import type { OrderView, PaymentView } from '@/types/api';

const route = useRoute();
const orderId = Number(route.params.orderId);
const loading = ref(false);
const order = ref<OrderView | null>(null);
const payment = ref<PaymentView | null>(null);

async function load() {
  loading.value = true;
  try {
    order.value = await orderApi(orderId);
    if (order.value?.payOrderId) {
      payment.value = await paymentApi(order.value.payOrderId).catch(() => null);
    }
  } finally {
    loading.value = false;
  }
}

async function pay() {
  if (!order.value?.payOrderId) return;
  await mockPayApi(order.value.payOrderId);
  await load();
}

async function cancel() {
  if (!order.value) return;
  await cancelOrderApi(order.value.orderId, '用户端取消订单');
  await load();
}

onMounted(load);
</script>

<template>
  <AppShell title="订单详情" subtitle="支付、排队与履约进度" :show-nav="false">
    <section v-if="order" class="card detail">
      <div class="detail-head">
        <h2>订单 {{ order.orderId }}</h2>
        <span class="status-pill" :class="statusClass(order.status)">{{ orderStatusText(order.status) }}</span>
      </div>

      <div class="timeline">
        <span :class="{ active: order.status !== 'CANCELLED' }">提交</span>
        <span :class="{ active: ['WAIT_MERCHANT_ACCEPT', 'MERCHANT_ACCEPTED', 'COOKING', 'WAIT_RIDER_PICKUP', 'DELIVERING', 'COMPLETED'].includes(order.status) }">支付</span>
        <span :class="{ active: ['MERCHANT_ACCEPTED', 'COOKING', 'WAIT_RIDER_PICKUP', 'DELIVERING', 'COMPLETED'].includes(order.status) }">制作</span>
        <span :class="{ active: ['WAIT_RIDER_PICKUP', 'DELIVERING', 'COMPLETED'].includes(order.status) }">取餐</span>
        <span :class="{ active: ['COMPLETED', 'DELIVERED'].includes(order.status) }">完成</span>
      </div>

      <div class="items">
        <div v-for="item in order.items" :key="item.skuId" class="line">
          <span>{{ item.skuName }} × {{ item.quantity }}</span>
          <strong>{{ formatMoney(item.priceCent * item.quantity) }}</strong>
        </div>
      </div>

      <div class="summary">
        <span>支付单 {{ order.payOrderId }}</span>
        <span v-if="order.queueTicketId">排队票 {{ order.queueTicketId }}</span>
        <strong>{{ formatMoney(order.amountCent) }}</strong>
      </div>

      <div class="actions">
        <button v-if="order.status === 'PENDING_PAYMENT'" class="primary-button" @click="pay">模拟支付</button>
        <button v-if="!['CANCELLED', 'COMPLETED', 'DELIVERED'].includes(order.status)" class="danger-button" @click="cancel">取消订单</button>
        <RouterLink class="ghost-button" to="/orders">返回订单</RouterLink>
      </div>

      <p v-if="payment" class="muted">支付状态：{{ payment.status }}</p>
    </section>
    <div v-else-if="!loading" class="empty">订单不存在</div>
  </AppShell>
</template>

<style scoped>
.detail {
  padding: 16px;
}

.detail-head,
.line,
.summary,
.actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}

.detail h2 {
  margin: 0;
}

.timeline {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 6px;
  margin: 18px 0;
}

.timeline span {
  border-radius: 999px;
  background: #e5e7eb;
  color: #64748b;
  padding: 7px 0;
  text-align: center;
  font-size: 12px;
  font-weight: 700;
}

.timeline span.active {
  background: #dcfce7;
  color: #15803d;
}

.items {
  border-top: 1px solid #edf0f5;
  border-bottom: 1px solid #edf0f5;
  padding: 8px 0;
}

.line {
  padding: 8px 0;
}

.summary {
  align-items: flex-end;
  margin: 14px 0;
  color: #64748b;
  font-size: 13px;
}

.summary strong {
  color: #111827;
  font-size: 20px;
}

.actions {
  justify-content: flex-start;
  flex-wrap: wrap;
}
</style>
