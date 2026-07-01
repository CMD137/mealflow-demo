<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { dispatchLocalEventsApi, localEventsApi, type EventDomain } from '@/api/ops';
import type { LocalEventView } from '@/types/api';
import { statusType } from '@/utils/format';

const loading = ref(false);
const activeDomain = ref<EventDomain>('orders');
const rows = ref<LocalEventView[]>([]);

const domainOptions: Array<{ label: string; value: EventDomain }> = [
  { label: '订单事件', value: 'orders' },
  { label: '支付事件', value: 'payments' },
  { label: '履约事件', value: 'fulfillment' }
];

async function load() {
  loading.value = true;
  try {
    rows.value = await localEventsApi(activeDomain.value);
  } finally {
    loading.value = false;
  }
}

async function dispatch() {
  const count = await dispatchLocalEventsApi(activeDomain.value);
  ElMessage.success(`已派发 ${count} 条事件`);
  load();
}

onMounted(load);
</script>

<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">事件运维</h2>
        <p class="page-subtitle">查看订单、支付、履约 Outbox 事件，并手动派发待处理事件。</p>
      </div>
      <div class="action-bar">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button type="primary" @click="dispatch">派发待处理事件</el-button>
      </div>
    </div>

    <div class="toolbar">
      <div class="filter-row">
        <el-segmented v-model="activeDomain" :options="domainOptions" @change="load" />
      </div>
      <span class="status-line">当前共 {{ rows.length }} 条事件</span>
    </div>

    <div class="content-panel">
      <el-table v-loading="loading" :data="rows" row-key="id">
        <el-table-column prop="id" label="ID" width="90" />
        <el-table-column prop="eventType" label="事件类型" width="160" />
        <el-table-column prop="aggregateType" label="聚合" width="130" />
        <el-table-column prop="aggregateId" label="聚合 ID" width="120" />
        <el-table-column prop="eventKey" label="事件键" min-width="260" show-overflow-tooltip />
        <el-table-column label="状态" width="120">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="retryCount" label="重试" width="90" />
        <el-table-column prop="lastError" label="错误" min-width="180" show-overflow-tooltip />
        <el-table-column prop="updateTime" label="更新时间" width="180" />
        <el-table-column type="expand">
          <template #default="{ row }">
            <pre class="payload">{{ row.payloadJson }}</pre>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>

<style scoped>
.payload {
  max-height: 240px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  border-radius: 6px;
  background: #f8fafc;
  color: #334155;
  white-space: pre-wrap;
}
</style>
