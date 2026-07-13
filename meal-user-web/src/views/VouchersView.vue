<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import AppShell from '@/components/AppShell.vue';
import { claimVoucherApi, vouchersApi, walletApi } from '@/api/promotion';
import { formatMoney } from '@/utils/format';
import type { UserVoucherView, VoucherView } from '@/types/api';

const loading = ref(false);
const claimingId = ref<number | null>(null);
const vouchers = ref<VoucherView[]>([]);
const wallet = ref<UserVoucherView[]>([]);
const message = ref('');

const availableWallet = computed(() => wallet.value.filter((item) => item.status === 'AVAILABLE'));
const claimableCount = computed(() => vouchers.value.filter((voucher) => canClaim(voucher)).length);

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
  claimingId.value = voucher.voucherId;
  message.value = '';
  try {
    const result = await claimVoucherApi(voucher.voucherId);
    if (result.status === 'CLAIMED') {
      message.value = `抢券成功，已放入券包`;
    } else if (result.status === 'DUPLICATE') {
      message.value = `你已经抢过这张券了`;
    } else {
      message.value = `来晚了，券已抢完`;
    }
    await load();
  } finally {
    claimingId.value = null;
  }
}

function ownedCount(voucherId: number) {
  return wallet.value.filter((item) => item.voucherId === voucherId).length;
}

function voucherMeta(voucherId: number) {
  return vouchers.value.find((voucher) => voucher.voucherId === voucherId);
}

function canClaim(voucher: VoucherView) {
  return voucher.status === 'ACTIVE' && voucher.stock > 0;
}

function claimButtonText(voucher: VoucherView) {
  if (claimingId.value === voucher.voucherId) {
    return '抢券中...';
  }
  if (voucher.status !== 'ACTIVE') {
    return '已暂停';
  }
  if (voucher.stock <= 0) {
    return '已抢完';
  }
  return '立即抢券';
}

onMounted(load);
</script>

<template>
  <AppShell title="抢券中心" subtitle="限量券先到先得，结算时自动可选">
    <section class="campaign card">
      <div>
        <span>今日活动</span>
        <strong>{{ claimableCount }} 张券可抢</strong>
      </div>
      <RouterLink to="/checkout" class="ghost-button">去结算</RouterLink>
    </section>

    <p v-if="message" class="notice">{{ message }}</p>

    <div class="section-title">
      <h2>限时抢券</h2>
      <button class="link refresh" @click="load">刷新</button>
    </div>

    <section v-for="voucher in vouchers" :key="voucher.voucherId" class="voucher-card card">
      <div class="voucher-main">
        <div>
          <h2>{{ voucher.name }}</h2>
          <p>秒杀券 · 库存 {{ voucher.stock }} · 已有 {{ ownedCount(voucher.voucherId) }} 张</p>
        </div>
        <strong>{{ formatMoney(voucher.discountCent) }}</strong>
      </div>
      <button
        class="primary-button"
        :disabled="claimingId === voucher.voucherId || !canClaim(voucher)"
        @click="claim(voucher)"
      >
        {{ claimButtonText(voucher) }}
      </button>
    </section>

    <div v-if="!loading && !vouchers.length" class="empty">暂无可抢优惠券</div>

    <div class="section-title wallet-title">
      <h2>我的券包</h2>
      <span>{{ availableWallet.length }} 张可用</span>
    </div>

    <section v-for="item in wallet" :key="item.userVoucherId" class="wallet-card card">
      <div>
        <strong>{{ voucherMeta(item.voucherId)?.name || `优惠券 ${item.voucherId}` }}</strong>
        <p>券号 {{ item.userVoucherId }} · {{ item.status }}</p>
      </div>
      <span>{{ formatMoney(voucherMeta(item.voucherId)?.discountCent || 0) }}</span>
    </section>
  </AppShell>
</template>

<style scoped>
.campaign {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px;
  background: #111827;
  color: #ffffff;
}

.campaign span {
  display: block;
  color: #cbd5e1;
  font-size: 12px;
}

.campaign strong {
  display: block;
  margin-top: 4px;
  font-size: 20px;
}

.notice {
  border-radius: 8px;
  background: #ecfdf5;
  color: #047857;
  padding: 10px 12px;
  font-weight: 700;
}

.refresh {
  background: transparent;
}

.voucher-card,
.wallet-card {
  margin-bottom: 10px;
  padding: 15px;
}

.voucher-main,
.wallet-card {
  display: flex;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.voucher-card h2 {
  margin: 0 0 6px;
  font-size: 16px;
}

.voucher-card p,
.wallet-card p,
.wallet-title span {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.voucher-main > strong,
.wallet-card > span {
  color: #dc2626;
  font-size: 21px;
  font-weight: 900;
}

.voucher-card button {
  width: 100%;
  margin-top: 12px;
}

.wallet-title {
  margin-top: 22px;
}
</style>
