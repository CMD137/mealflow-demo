<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import AppShell from '@/components/AppShell.vue';
import { walletApi, vouchersApi } from '@/api/promotion';
import { addressesApi } from '@/api/auth';
import { submitOrderApi } from '@/api/orders';
import { errorMessage as apiErrorMessage } from '@/api/http';
import { useCartStore } from '@/stores/cart';
import { buildSkuMapByCart } from '@/utils/catalog';
import { formatMoney } from '@/utils/format';
import type { AddressView, UserVoucherView, VoucherView } from '@/types/api';

const router = useRouter();
const cart = useCartStore();
const loading = ref(false);
const submitting = ref(false);
const vouchers = ref<VoucherView[]>([]);
const wallet = ref<UserVoucherView[]>([]);
const addresses = ref<AddressView[]>([]);
const errorMessage = ref('');
const form = reactive({
  userVoucherId: undefined as number | undefined,
  addressId: undefined as number | undefined,
  remark: ''
});

function submitErrorMessage(error: unknown) {
  const code = typeof error === 'object' && error !== null && 'code' in error
    ? String(error.code) : '';
  switch (code) {
    case 'STOCK_NOT_ENOUGH':
      return '商品库存不足，已为你保留购物车，请调整后重试';
    case 'VOUCHER_UNAVAILABLE':
    case 'SOLD_OUT':
      return '优惠券已不可用，请重新选择后提交订单';
    case 'IDEMPOTENT_PROCESSING':
      return '订单正在提交，请勿重复操作';
    default:
      return apiErrorMessage(error, '提交订单失败，请稍后重试');
  }
}

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
    const [voucherData, walletData, addressData] = await Promise.all([vouchersApi(), walletApi(), addressesApi()]);
    vouchers.value = voucherData;
    wallet.value = walletData.filter((item) => item.status === 'AVAILABLE');
    addresses.value = addressData;
    form.addressId = addressData.find((item) => item.defaultAddress)?.addressId || addressData[0]?.addressId;
  } finally {
    loading.value = false;
  }
}

async function submit() {
  errorMessage.value = '';
  if (!selected.value.length) {
    errorMessage.value = '请先选择要结算的商品';
    return;
  }
  if (!form.addressId) {
    errorMessage.value = '请先添加并选择配送地址';
    return;
  }
  const merchantId = selected.value[0].merchantId;
  if (selected.value.some((item) => item.merchantId !== merchantId)) {
    errorMessage.value = '本次结算只能包含同一商户的商品';
    return;
  }
  submitting.value = true;
  try {
    const result = await submitOrderApi({
      requestId: `h5-order-${Date.now()}-${Math.random().toString(16).slice(2)}`,
      merchantId,
      addressId: form.addressId,
      items: selected.value.map((item) => ({ skuId: item.skuId, quantity: item.quantity })),
      userVoucherId: form.userVoucherId || null,
      remark: form.remark
    });
    await Promise.all(selected.value.map((item) => cart.remove(item.cartItemId)));
    sessionStorage.setItem('mealflow.lastOrderResult', JSON.stringify(result));
    router.push('/order-result');
  } catch (error) {
    errorMessage.value = submitErrorMessage(error);
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
    <p v-if="errorMessage" class="inline-error">{{ errorMessage }}</p>
    <section class="card block">
      <h2>商品明细</h2>
      <div v-for="item in selected" :key="item.cartItemId" class="line">
        <span>{{ cart.skuMap[item.skuId]?.name || `商品 ${item.skuId}` }} × {{ item.quantity }}</span>
        <strong>{{ formatMoney((cart.skuMap[item.skuId]?.priceCent || 0) * item.quantity) }}</strong>
      </div>
      <div v-if="!loading && !selected.length" class="empty small-empty">暂无选中商品</div>
    </section>

    <section class="card block">
      <h2>配送地址</h2>
      <RouterLink to="/addresses?return=/checkout" class="address-manage">{{ addresses.length ? '管理地址' : '新增收货地址' }}</RouterLink>
      <select v-model.number="form.addressId">
        <option v-if="!addresses.length" :value="undefined" disabled>暂无地址，请先添加</option>
        <option v-for="address in addresses" :key="address.addressId" :value="address.addressId">
          {{ address.contactName }} {{ address.phone }} · {{ address.detail }}
        </option>
      </select>
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
      <button class="primary-button" :disabled="submitting || !selected.length || !form.addressId" @click="submit">
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

.inline-error {
  margin: 0 0 12px;
  border-radius: 8px;
  background: #fef2f2;
  color: #b91c1c;
  padding: 10px 12px;
  font-size: 14px;
}

.address-manage {
  float: right;
  margin-top: -28px;
  color: #2563eb;
  font-size: 13px;
  font-weight: 700;
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
