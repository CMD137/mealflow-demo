<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { orderStatisticsApi } from '@/api/orders';
import { queueMetricsApi } from '@/api/queue';
import { useAuthStore } from '@/stores/auth';
import { formatMoney } from '@/utils/format';

const auth = useAuthStore();
const loading = ref(false);
const stats = reactive({
  totalOrders: 0,
  waitingAcceptCount: 0,
  acceptedCount: 0,
  deliveringCount: 0,
  completedCount: 0,
  cancelledCount: 0,
  turnoverCent: 0,
  queue: {} as Record<string, unknown>
});

async function load() {
  const merchantId = auth.merchantId;
  if (!merchantId) {
    ElMessage.warning('当前登录账号未绑定商家，无法加载工作台');
    return;
  }
  loading.value = true;
  try {
    const [orderStats, queueStats] = await Promise.all([
      orderStatisticsApi({ merchantId }),
      queueMetricsApi(merchantId)
    ]);
    stats.totalOrders = orderStats.totalCount;
    stats.waitingAcceptCount = orderStats.waitingAcceptCount;
    stats.acceptedCount = orderStats.acceptedCount;
    stats.deliveringCount = orderStats.deliveringCount;
    stats.completedCount = orderStats.completedCount;
    stats.cancelledCount = orderStats.cancelledCount;
    stats.turnoverCent = orderStats.turnoverCent;
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
      <div class="metric"><span>待接单</span><strong>{{ stats.waitingAcceptCount }}</strong></div>
      <div class="metric"><span>制作中</span><strong>{{ stats.acceptedCount }}</strong></div>
      <div class="metric"><span>配送中</span><strong>{{ stats.deliveringCount }}</strong></div>
      <div class="metric"><span>已完成</span><strong>{{ stats.completedCount }}</strong></div>
      <div class="metric"><span>已取消</span><strong>{{ stats.cancelledCount }}</strong></div>
      <div class="metric"><span>营业额</span><strong>{{ formatMoney(stats.turnoverCent) }}</strong></div>
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
