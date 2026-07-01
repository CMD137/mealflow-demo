<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppShell from '@/components/AppShell.vue';
import { walletApi, vouchersApi } from '@/api/promotion';
import { submitOrderApi } from '@/api/orders';
import { useCartStore } from '@/stores/cart';
import { buildSkuMapByCart } from '@/utils/catalog';
import { formatMoney } from '@/utils/format';
import type { UserVoucherView, VoucherView } from '@/types/api';

const router = useRouter();
const cart = useCartStore();
const loading = ref(false);
const submitting = ref(false);
const vouchers = ref<VoucherView[]>([]);
const wallet = ref<UserVoucherView[]>([]);
const form = reactive({
  userVoucherId: undefined as number | undefined,
  remark: ''
});

const selected = computed(() => cart.selectedItems);
const discountCent = computed(() => {
  const userVoucher = wallet.value.find((item) => item.userVoucherId === form.userVoucherId);
  const voucher = vouchers.value.find((item) => item.voucherId === userVoucher?.voucherId);
  return voucher?.discountCent || 0;
});
const payableCent = computed(() => Math.max(0, cart.selectedAmountCent - discountCent.value));

async function load() {
  loading.value = true;
  try {
    await cart.load();
    cart.skuMap = await buildSkuMapByCart(cart.items);
    const [voucherData, walletData] = await Promise.all([vouchersApi(), walletApi()]);
    vouchers.value = voucherData;
    wallet.value = walletData.filter((item) => item.status === 'UNUSED');
  } finally {
    loading.value = false;
  }
}

async function submit() {
  if (!selected.value.length) {
    window.alert('请先选择要结算的商品');
    return;
  }
  const merchantId = selected.value[0].merchantId;
  submitting.value = true;
  try {
    const result = await submitOrderApi({
      requestId: `h5-order-${Date.now()}-${Math.random().toString(16).slice(2)}`,
      merchantId,
      items: selected.value.map((item) => ({ skuId: item.skuId, quantity: item.quantity })),
      userVoucherId: form.userVoucherId || null,
      remark: form.remark
    });
    await Promise.all(selected.value.map((item) => cart.remove(item.cartItemId)));
    sessionStorage.setItem('mealflow.lastOrderResult', JSON.stringify(result));
    router.push('/order-result');
  } finally {
    submitting.value = false;
  }
}

function voucherName(item: UserVoucherView) {
  const voucher = vouchers.value.find((voucherItem) => voucherItem.voucherId === item.voucherId);
  return voucher ? `${voucher.name} - ${formatMoney(voucher.discountCent)}` : `用户券 ${item.userVoucherId}`;
}

onMounted(load);
</script>

<template>
  <AppShell title="确认订单" subtitle="选择优惠券并提交订单" :show-nav="false">
    <section class="card block">
      <h2>商品明细</h2>
      <div v-for="item in selected" :key="item.cartItemId" class="line">
        <span>{{ cart.skuMap[item.skuId]?.name || `商品 ${item.skuId}` }} × {{ item.quantity }}</span>
        <strong>{{ formatMoney((cart.skuMap[item.skuId]?.priceCent || 0) * item.quantity) }}</strong>
      </div>
      <div v-if="!loading && !selected.length" class="empty small-empty">暂无选中商品</div>
    </section>

    <section class="card block">
      <h2>优惠券</h2>
      <select v-model.number="form.userVoucherId">
        <option :value="undefined">不使用优惠券</option>
        <option v-for="item in wallet" :key="item.userVoucherId" :value="item.userVoucherId">
          {{ voucherName(item) }}
        </option>
      </select>
    </section>

    <section class="card block">
      <h2>备注</h2>
      <textarea v-model="form.remark" placeholder="口味、餐具、配送备注" />
    </section>

    <div class="submit-bar">
      <div>
        <span>优惠 {{ formatMoney(discountCent) }}</span>
        <strong>{{ formatMoney(payableCent) }}</strong>
      </div>
      <button class="primary-button" :disabled="submitting || !selected.length" @click="submit">
        {{ submitting ? '提交中...' : '提交订单' }}
      </button>
    </div>
  </AppShell>
</template>

<style scoped>
.block {
  margin-bottom: 12px;
  padding: 14px;
}

.block h2 {
  margin: 0 0 12px;
  font-size: 16px;
}

.line {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 0;
  border-bottom: 1px solid #f1f5f9;
}

.line:last-child {
  border-bottom: 0;
}

select,
textarea {
  width: 100%;
  border: 1px solid #dbe2ea;
  border-radius: 8px;
  padding: 11px 12px;
}

textarea {
  min-height: 88px;
  resize: vertical;
}

.small-empty {
  min-height: 80px;
}

.submit-bar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  display: flex;
  max-width: 430px;
  margin: 0 auto;
  align-items: center;
  justify-content: space-between;
  border-top: 1px solid #e5e7eb;
  background: #ffffff;
  padding: 10px 14px calc(10px + env(safe-area-inset-bottom));
}

.submit-bar span {
  display: block;
  color: #6b7280;
  font-size: 12px;
}
</style>
