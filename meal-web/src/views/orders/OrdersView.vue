<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { adminOrdersApi, merchantCancelOrderApi } from '@/api/orders';
import { useAuthStore } from '@/stores/auth';
import type { OrderView } from '@/types/api';
import { formatMoney, statusType } from '@/utils/format';
import { errorMessage } from '@/api/http';

const auth = useAuthStore();
const loading = ref(false);
const rows = ref<OrderView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const selected = ref<OrderView | null>(null);
const filters = reactive({ merchantId: auth.merchantId, userId: undefined as number | undefined, status: '' });

async function load() {
  if (!filters.merchantId && auth.merchantId) {
    filters.merchantId = auth.merchantId;
  }
  loading.value = true;
  try {
    const orderData = await adminOrdersApi({
        merchantId: filters.merchantId,
        userId: filters.userId,
        status: filters.status || undefined,
        page: page.value,
        pageSize: pageSize.value
      });
    rows.value = orderData.items;
    total.value = orderData.total;
  } catch (error) {
    ElMessage.error(errorMessage(error, '订单加载失败'));
  } finally {
    loading.value = false;
  }
}

function handlePageChange(nextPage: number) {
  page.value = nextPage;
  load();
}

function handleQuery() {
  page.value = 1;
  load();
}

async function cancel(row: OrderView) {
  try {
    await ElMessageBox.confirm(`确认取消订单 ${row.orderId}？`, '取消订单', { type: 'warning' });
    await merchantCancelOrderApi(row.orderId, '商家后台取消');
    ElMessage.success('订单已取消');
    await load();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(errorMessage(error, '取消订单失败'));
    }
  }
}

onMounted(load);
</script>

<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">订单管理</h2>
        <p class="page-subtitle">查询订单、取消订单和查看排队关联。</p>
      </div>
      <div class="action-bar">
        <el-button @click="load" :loading="loading">刷新</el-button>
      </div>
    </div>

    <div class="toolbar">
      <div class="filter-row">
        <el-input-number v-model="filters.merchantId" :min="1" controls-position="right" />
        <el-input-number v-model="filters.userId" :min="1" controls-position="right" placeholder="用户 ID" />
        <el-select v-model="filters.status" clearable placeholder="订单状态" style="width: 180px">
          <el-option label="待支付" value="PENDING_PAYMENT" />
          <el-option label="待接单" value="WAIT_MERCHANT_ACCEPT" />
          <el-option label="已接单" value="MERCHANT_ACCEPTED" />
          <el-option label="制作中" value="COOKING" />
          <el-option label="待取餐" value="WAIT_RIDER_PICKUP" />
          <el-option label="配送中" value="DELIVERING" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已取消" value="CANCELLED" />
        </el-select>
      </div>
      <el-button type="primary" @click="handleQuery">查询</el-button>
    </div>

    <div class="content-panel">
      <el-table v-loading="loading" :data="rows" row-key="orderId">
        <el-table-column prop="orderId" label="订单号" width="120" />
        <el-table-column prop="userId" label="用户" width="100" />
        <el-table-column label="状态" width="140">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="订单金额" width="130">
          <template #default="{ row }"><span class="money">{{ formatMoney(row.amountCent) }}</span></template>
        </el-table-column>
        <el-table-column prop="queueTicketId" label="排队 ticket" width="140" />
        <el-table-column prop="payOrderId" label="支付单" width="120" />
        <el-table-column label="商品项" min-width="120">
          <template #default="{ row }">{{ row.items?.length || 0 }} 项</template>
        </el-table-column>
        <el-table-column label="操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="selected = row">详情</el-button>
            <el-button text type="danger" :disabled="!['PENDING_PAYMENT', 'WAIT_MERCHANT_ACCEPT'].includes(row.status)" @click="cancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar">
        <el-pagination
          layout="total, prev, pager, next"
          :total="total"
          :current-page="page"
          :page-size="pageSize"
          @current-change="handlePageChange"
        />
      </div>
    </div>

    <el-drawer v-model="selected" title="订单详情" size="460px">
      <el-descriptions v-if="selected" :column="1" border>
        <el-descriptions-item label="订单号">{{ selected.orderId }}</el-descriptions-item>
        <el-descriptions-item label="商家">{{ selected.merchantId }}</el-descriptions-item>
        <el-descriptions-item label="用户">{{ selected.userId }}</el-descriptions-item>
        <el-descriptions-item label="状态">{{ selected.status }}</el-descriptions-item>
        <el-descriptions-item label="订单金额">{{ formatMoney(selected.amountCent) }}</el-descriptions-item>
        <el-descriptions-item label="支付单">{{ selected.payOrderId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="排队 ticket">{{ selected.queueTicketId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="产能 token">{{ selected.capacityTokenId || '-' }}</el-descriptions-item>
        <el-descriptions-item label="配送联系人">{{ selected.contactName || '-' }} {{ selected.contactPhone || '' }}</el-descriptions-item>
        <el-descriptions-item label="配送地址">{{ selected.deliveryAddress || '-' }}</el-descriptions-item>
        <el-descriptions-item label="用户备注">{{ selected.remark || '-' }}</el-descriptions-item>
      </el-descriptions>
      <el-table v-if="selected?.items?.length" :data="selected.items" style="margin-top: 16px">
        <el-table-column prop="skuName" label="商品" min-width="180" />
        <el-table-column prop="quantity" label="数量" width="80" />
        <el-table-column label="单价" width="120">
          <template #default="{ row }">{{ formatMoney(row.priceCent) }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>

  </section>
</template>

<style scoped>
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}

</style>
