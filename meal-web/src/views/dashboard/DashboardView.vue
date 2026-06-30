<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { orderStatisticsApi } from '@/api/orders';
import { queueMetricsApi } from '@/api/queue';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();
const loading = ref(false);
const stats = reactive({
  totalOrders: 0,
  statusCounts: {} as Record<string, number>,
  queue: {} as Record<string, unknown>
});

async function load() {
  loading.value = true;
  try {
    const [orderStats, queueStats] = await Promise.all([
      orderStatisticsApi({ merchantId: auth.merchantId }),
      queueMetricsApi(auth.merchantId)
    ]);
    stats.totalOrders = orderStats.totalCount;
    stats.statusCounts = orderStats.statusCounts || {};
    stats.queue = queueStats || {};
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">工作台</h2>
        <p class="page-subtitle">查看商家订单、状态分布和排队运行情况。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="summary-grid">
      <div class="metric">
        <span>订单总数</span>
        <strong>{{ stats.totalOrders }}</strong>
      </div>
      <div class="metric" v-for="(count, status) in stats.statusCounts" :key="status">
        <span>{{ status }}</span>
        <strong>{{ count }}</strong>
      </div>
    </div>

    <div class="content-panel">
      <h3>队列指标</h3>
      <el-empty v-if="!Object.keys(stats.queue).length && !loading" description="暂无队列指标" />
      <el-descriptions v-else :column="3" border>
        <el-descriptions-item v-for="(value, key) in stats.queue" :key="key" :label="String(key)">
          {{ value }}
        </el-descriptions-item>
      </el-descriptions>
    </div>
  </section>
</template>

<style scoped>
.summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.metric {
  display: flex;
  min-height: 86px;
  flex-direction: column;
  justify-content: center;
  padding: 16px;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  background: #ffffff;
}

.metric span {
  color: #667085;
  font-size: 13px;
}

.metric strong {
  margin-top: 8px;
  font-size: 26px;
}

h3 {
  margin: 0 0 14px;
  font-size: 16px;
}
</style>
