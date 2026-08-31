<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import AppShell from '@/components/AppShell.vue';
import { errorMessage } from '@/api/http';
import { checkoutApi, paymentApi } from '@/api/payments';
import { formatWait } from '@/utils/format';
import type { SubmitOrderResponse } from '@/types/api';

const result = computed<SubmitOrderResponse | null>(() => {
  const raw = sessionStorage.getItem('mealflow.lastOrderResult');
  return raw ? JSON.parse(raw) as SubmitOrderResponse : null;
});
const route = useRoute();
const router = useRouter();
const payError = ref('');
const paying = ref(false);
const returnMessage = ref('');
const confirmingReturn = ref(false);

function returnedPayOrderId() {
  const outTradeNo = String(route.query.out_trade_no || '');
  if (/^MF\d+$/.test(outTradeNo)) return Number(outTradeNo.substring(2));
  return result.value?.payOrderId || null;
}

async function pay() {
  if (!result.value?.payOrderId) return;
  paying.value = true;
  payError.value = '';
  try {
    const checkout = await checkoutApi(result.value.payOrderId);
    window.location.assign(checkout.checkoutUrl);
  } catch (error) {
    payError.value = errorMessage(error, '无法发起支付，请稍后重试');
  } finally {
    paying.value = false;
  }
}

async function confirmReturnedPayment() {
  const payOrderId = returnedPayOrderId();
  if (!payOrderId) return;
  confirmingReturn.value = true;
  returnMessage.value = '正在确认支付宝支付结果…';
  try {
    const payment = await paymentApi(payOrderId);
    if (payment.status === 'PAID') {
      returnMessage.value = '支付已确认，正在打开订单详情…';
      await router.replace({ path: `/orders/${payment.orderId}`, query: { payment: 'success' } });
      return;
    }
    returnMessage.value = '支付宝已返回，但支付结果尚未确认。请稍后刷新订单状态。';
  } catch (error) {
    returnMessage.value = errorMessage(error, '无法确认支付结果，请在订单详情中稍后刷新。');
  } finally {
    confirmingReturn.value = false;
  }
}

onMounted(() => {
  if (route.query.source === 'alipay' || route.query.out_trade_no) {
    void confirmReturnedPayment();
  }
});
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
      <p v-if="returnMessage" class="return-message">{{ returnMessage }}</p>
      <p v-if="payError" class="inline-error">{{ payError }}</p>
      <button v-if="result.payOrderId" class="primary-button" :disabled="paying" @click="pay">
        {{ paying ? '正在跳转支付宝…' : '前往支付宝支付' }}
      </button>
      <button v-if="returnMessage && !confirmingReturn" class="ghost-button" @click="confirmReturnedPayment">刷新支付状态</button>
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

.return-message {
  color: #1d4ed8 !important;
}
</style>
