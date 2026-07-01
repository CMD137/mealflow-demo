<script setup lang="ts">
import { onMounted, ref } from 'vue';
import AppShell from '@/components/AppShell.vue';
import { claimVoucherApi, vouchersApi, walletApi } from '@/api/promotion';
import { formatMoney } from '@/utils/format';
import type { UserVoucherView, VoucherView } from '@/types/api';

const loading = ref(false);
const vouchers = ref<VoucherView[]>([]);
const wallet = ref<UserVoucherView[]>([]);

async function load() {
  loading.value = true;
  try {
    const [voucherData, walletData] = await Promise.all([vouchersApi(), walletApi()]);
    vouchers.value = voucherData;
    wallet.value = walletData;
  } finally {
    loading.value = false;
  }
}

async function claim(voucher: VoucherView) {
  const result = await claimVoucherApi(voucher.voucherId);
  window.alert(result.message || '领取请求已提交');
  await load();
}

function ownedCount(voucherId: number) {
  return wallet.value.filter((item) => item.voucherId === voucherId).length;
}

onMounted(load);
</script>

<template>
  <AppShell title="优惠券" subtitle="领取并在结算时使用">
    <section v-for="voucher in vouchers" :key="voucher.voucherId" class="voucher-card card">
      <div>
        <h2>{{ voucher.name }}</h2>
        <p>{{ voucher.type }} · 库存 {{ voucher.stock }} · 已有 {{ ownedCount(voucher.voucherId) }} 张</p>
      </div>
      <strong>{{ formatMoney(voucher.discountCent) }}</strong>
      <button class="primary-button" :disabled="voucher.status !== 'ACTIVE' || voucher.stock <= 0" @click="claim(voucher)">
        领取
      </button>
    </section>
    <div v-if="!loading && !vouchers.length" class="empty">暂无优惠券</div>
  </AppShell>
</template>

<style scoped>
.voucher-card {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 10px;
  align-items: center;
  margin-bottom: 10px;
  padding: 15px;
}

.voucher-card h2 {
  margin: 0 0 6px;
  font-size: 16px;
}

.voucher-card p {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.voucher-card strong {
  color: #dc2626;
  font-size: 21px;
}

.voucher-card button {
  grid-column: 1 / -1;
}
</style>
