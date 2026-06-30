<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { adminOrdersApi } from '@/api/orders';
import { acceptOrderApi, deliveredApi, mealReadyApi, pickedUpApi } from '@/api/fulfillment';
import { useAuthStore } from '@/stores/auth';
import type { OrderView } from '@/types/api';
import { formatMoney, statusType } from '@/utils/format';

const auth = useAuthStore();
const loading = ref(false);
const rows = ref<OrderView[]>([]);

async function load() {
  loading.value = true;
  try {
    rows.value = await adminOrdersApi({ merchantId: auth.merchantId });
  } finally {
    loading.value = false;
  }
}

async function operate(row: OrderView, action: 'accept' | 'ready' | 'picked' | 'delivered') {
  if (action === 'accept') await acceptOrderApi(row.orderId);
  if (action === 'ready') await mealReadyApi(row.orderId);
  if (action === 'picked') await pickedUpApi(row.orderId);
  if (action === 'delivered') await deliveredApi(row.orderId);
  ElMessage.success('履约状态已推进');
  load();
}

onMounted(load);
</script>

<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">履约工作台</h2>
        <p class="page-subtitle">按订单状态推进接单、出餐、取餐和送达。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="content-panel">
      <el-table v-loading="loading" :data="rows" row-key="orderId">
        <el-table-column prop="orderId" label="订单号" width="120" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="应付金额" width="130">
          <template #default="{ row }"><span class="money">{{ formatMoney(row.payableAmountCent) }}</span></template>
        </el-table-column>
        <el-table-column prop="queueTicketId" label="排队 ticket" width="140" />
        <el-table-column prop="updateTime" label="更新时间" min-width="180" />
        <el-table-column label="可用操作" width="280" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" :disabled="row.status !== 'PAID'" @click="operate(row, 'accept')">接单</el-button>
            <el-button text type="primary" :disabled="row.status !== 'ACCEPTED'" @click="operate(row, 'ready')">出餐</el-button>
            <el-button text type="primary" :disabled="row.status !== 'READY'" @click="operate(row, 'picked')">取餐</el-button>
            <el-button text type="success" :disabled="row.status !== 'PICKED_UP'" @click="operate(row, 'delivered')">送达</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>
