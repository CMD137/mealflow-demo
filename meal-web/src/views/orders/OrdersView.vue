<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessageBox } from 'element-plus';
import { adminOrdersApi, cancelOrderApi } from '@/api/orders';
import type { OrderView } from '@/types/api';
import { formatMoney, statusType } from '@/utils/format';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();
const loading = ref(false);
const rows = ref<OrderView[]>([]);
const selected = ref<OrderView | null>(null);
const filters = reactive({ merchantId: auth.merchantId, userId: undefined as number | undefined, status: '' });

async function load() {
  loading.value = true;
  try {
    rows.value = await adminOrdersApi({
      merchantId: filters.merchantId,
      userId: filters.userId,
      status: filters.status || undefined
    });
  } finally {
    loading.value = false;
  }
}

async function cancel(row: OrderView) {
  await ElMessageBox.confirm(`确认取消订单 ${row.orderId}？`, '取消订单', { type: 'warning' });
  await cancelOrderApi(row.orderId, '商家后台取消');
  load();
}

onMounted(load);
</script>

<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">订单管理</h2>
        <p class="page-subtitle">按商家、用户和状态查询订单，并查看订单详情和排队关联。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="toolbar">
      <div class="filter-row">
        <el-input-number v-model="filters.merchantId" :min="1" controls-position="right" />
        <el-input-number v-model="filters.userId" :min="1" controls-position="right" placeholder="用户 ID" />
        <el-select v-model="filters.status" clearable placeholder="订单状态" style="width: 180px">
          <el-option label="待支付" value="PENDING_PAYMENT" />
          <el-option label="已支付" value="PAID" />
          <el-option label="已接单" value="ACCEPTED" />
          <el-option label="已出餐" value="READY" />
          <el-option label="已送达" value="DELIVERED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
      </div>
      <el-button type="primary" @click="load">查询</el-button>
    </div>

    <div class="content-panel">
      <el-table v-loading="loading" :data="rows" row-key="orderId">
        <el-table-column prop="orderId" label="订单号" width="120" />
        <el-table-column prop="userId" label="用户" width="100" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="应付金额" width="130">
          <template #default="{ row }"><span class="money">{{ formatMoney(row.payableAmountCent) }}</span></template>
        </el-table-column>
        <el-table-column prop="queueTicketId" label="排队 ticket" width="140" />
        <el-table-column prop="payOrderId" label="支付单" width="120" />
        <el-table-column prop="createTime" label="创建时间" min-width="180" />
        <el-table-column label="操作" width="190" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="selected = row">详情</el-button>
            <el-button text type="danger" :disabled="row.status === 'CANCELLED' || row.status === 'DELIVERED'" @click="cancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-drawer v-model="selected" title="订单详情" size="420px">
      <el-descriptions v-if="selected" :column="1" border>
        <el-descriptions-item label="订单号">{{ selected.orderId }}</el-descriptions-item>
        <el-descriptions-item label="商家">{{ selected.merchantId }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ selected.userId }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ selected.status }}</el-descriptions-item>
        <el-descriptions-item label="总金额">{{ formatMoney(selected.totalAmountCent) }}</el-descriptions-item>
        <el-descriptions-item label="应付金额">{{ formatMoney(selected.payableAmountCent) }}</el-descriptions-item>
        <el-descriptions-item label="排队 ticket">{{ selected.queueTicketId || '-' }}</el-descriptions-item>
      </el-descriptions>
    </el-drawer>
  </section>
</template>
