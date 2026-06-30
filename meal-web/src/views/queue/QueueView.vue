<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { capacityTokensApi, queueTicketsApi } from '@/api/queue';
import type { CapacityTokenView, QueueTicketView } from '@/types/api';
import { statusType } from '@/utils/format';

const loading = ref(false);
const tickets = ref<QueueTicketView[]>([]);
const tokens = ref<CapacityTokenView[]>([]);

async function load() {
  loading.value = true;
  try {
    [tickets.value, tokens.value] = await Promise.all([queueTicketsApi(), capacityTokensApi()]);
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
        <h2 class="page-title">排队与产能</h2>
        <p class="page-subtitle">查看等待 ticket 与产能 token，判断订单是否因高峰进入排队。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="content-panel">
      <h3>排队 ticket</h3>
      <el-table v-loading="loading" :data="tickets" row-key="ticketId">
        <el-table-column prop="ticketId" label="ticket" width="120" />
        <el-table-column prop="merchantId" label="商家" width="100" />
        <el-table-column prop="userId" label="用户" width="100" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="rank" label="排位" width="100" />
        <el-table-column prop="createTime" label="创建时间" min-width="180" />
      </el-table>
    </div>

    <div class="content-panel">
      <h3>产能 token</h3>
      <el-table v-loading="loading" :data="tokens" row-key="capacityTokenId">
        <el-table-column prop="capacityTokenId" label="token" width="120" />
        <el-table-column prop="merchantId" label="商家" width="100" />
        <el-table-column prop="orderId" label="订单" width="120" />
        <el-table-column prop="ticketId" label="ticket" width="120" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" min-width="180" />
      </el-table>
    </div>
  </section>
</template>

<style scoped>
h3 {
  margin: 0 0 14px;
  font-size: 16px;
}
</style>
