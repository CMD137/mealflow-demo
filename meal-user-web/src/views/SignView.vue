<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import AppShell from '@/components/AppShell.vue';
import { signInApi, signInfoApi } from '@/api/sign';
import type { SignInView } from '@/types/api';

const loading = ref(false);
const signing = ref(false);
const info = ref<SignInView | null>(null);
const message = ref('');

const signedDates = computed(() => new Set(info.value?.monthSignDates || []));
const days = computed(() => {
  const now = new Date();
  const year = now.getFullYear();
  const month = now.getMonth();
  const total = new Date(year, month + 1, 0).getDate();
  return Array.from({ length: total }, (_, index) => {
    const day = index + 1;
    const value = `${year}-${String(month + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
    return { day, value, signed: signedDates.value.has(value), today: day === now.getDate() };
  });
});

async function load() {
  loading.value = true;
  try {
    info.value = await signInfoApi();
  } finally {
    loading.value = false;
  }
}

async function sign() {
  signing.value = true;
  message.value = '';
  try {
    const result = await signInApi();
    info.value = result;
    message.value = result.todayRewardPoints > 0
      ? `签到成功，获得 ${result.todayRewardPoints} 积分`
      : '今天已经签到过了';
  } finally {
    signing.value = false;
  }
}

onMounted(load);
</script>

<template>
  <AppShell title="每日签到" subtitle="连续签到领取积分，后续可兑换优惠券">
    <section class="sign-hero card">
      <div>
        <span>连续签到</span>
        <strong>{{ info?.continuousDays || 0 }} 天</strong>
      </div>
      <div>
        <span>累计积分</span>
        <strong>{{ info?.totalPoints || 0 }}</strong>
      </div>
    </section>

    <button class="primary-button sign-button" :disabled="signing || info?.signedToday" @click="sign">
      {{ info?.signedToday ? '今日已签到' : signing ? '签到中...' : '立即签到' }}
    </button>

    <p v-if="message" class="notice">{{ message }}</p>

    <section class="calendar card">
      <div class="calendar-head">
        <h2>本月签到</h2>
        <span>{{ info?.totalDays || 0 }} 次累计</span>
      </div>
      <div class="calendar-grid">
        <span
          v-for="item in days"
          :key="item.value"
          :class="{ signed: item.signed, today: item.today }"
        >
          {{ item.day }}
        </span>
      </div>
    </section>

    <div v-if="loading" class="empty">正在加载签到记录</div>
  </AppShell>
</template>

<style scoped>
.sign-hero {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
  padding: 18px;
  background: linear-gradient(135deg, #0f766e, #2563eb);
  color: #ffffff;
}

.sign-hero span {
  color: rgba(255, 255, 255, 0.78);
  font-size: 13px;
}

.sign-hero strong {
  display: block;
  margin-top: 6px;
  font-size: 28px;
}

.sign-button {
  width: 100%;
  margin: 14px 0 8px;
}

.notice {
  border-radius: 8px;
  background: #ecfdf5;
  color: #047857;
  padding: 10px 12px;
  font-weight: 700;
}

.calendar {
  margin-top: 14px;
  padding: 15px;
}

.calendar-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.calendar-head h2 {
  margin: 0;
  font-size: 16px;
}

.calendar-head span {
  color: #64748b;
  font-size: 13px;
}

.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8px;
}

.calendar-grid span {
  display: grid;
  aspect-ratio: 1;
  place-items: center;
  border-radius: 8px;
  background: #f1f5f9;
  color: #64748b;
  font-weight: 700;
}

.calendar-grid span.signed {
  background: #dcfce7;
  color: #15803d;
}

.calendar-grid span.today {
  outline: 2px solid #2563eb;
}
</style>
