<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { systemOrdersApi } from '@/api/system';
import { errorMessage } from '@/api/http';
import { formatMoney, statusType } from '@/utils/format';
import type { OrderView } from '@/types/api';

const loading = ref(false); const rows = ref<OrderView[]>([]); const total = ref(0); const page = ref(1); const pageSize = ref(20); const selected = ref<OrderView | null>(null);
const filters = reactive({ merchantId: undefined as number | undefined, userId: undefined as number | undefined, status: '' });
async function load() { loading.value = true; try { const result = await systemOrdersApi({ page: page.value, pageSize: pageSize.value, merchantId: filters.merchantId, userId: filters.userId, status: filters.status || undefined }); rows.value = result.items; total.value = result.total; } catch (error) { ElMessage.error(errorMessage(error, '全局订单加载失败')); } finally { loading.value = false; } }
function query() { page.value = 1; load(); }
function changePage(value: number) { page.value = value; load(); }
onMounted(load);
</script>

<template>
  <section class="page"><div class="page-header"><div><h2 class="page-title">全局订单</h2><p class="page-subtitle">仅用于运营排障的跨商家只读视图；不提供接单、取消、付款或退款操作。</p></div><el-button :loading="loading" @click="load">刷新</el-button></div><div class="toolbar"><div class="filter-row"><el-input-number v-model="filters.merchantId" :min="1" controls-position="right" placeholder="商家 ID" /><el-input-number v-model="filters.userId" :min="1" controls-position="right" placeholder="用户 ID" /><el-select v-model="filters.status" clearable placeholder="订单状态" style="width: 160px"><el-option label="待支付" value="PENDING_PAYMENT" /><el-option label="待接单" value="WAIT_MERCHANT_ACCEPT" /><el-option label="已接单" value="MERCHANT_ACCEPTED" /><el-option label="已完成" value="COMPLETED" /><el-option label="已取消" value="CANCELLED" /></el-select></div><el-button type="primary" @click="query">查询</el-button></div><div class="content-panel"><el-table v-loading="loading" :data="rows" row-key="orderId"><el-table-column prop="orderId" label="订单号" width="120" /><el-table-column prop="merchantId" label="商家" width="100" /><el-table-column prop="userId" label="用户" width="100" /><el-table-column label="状态" width="140"><template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template></el-table-column><el-table-column label="金额" width="120"><template #default="{ row }">{{ formatMoney(row.amountCent) }}</template></el-table-column><el-table-column label="商品项" min-width="120"><template #default="{ row }">{{ row.items?.length || 0 }} 项</template></el-table-column><el-table-column label="操作" width="100" fixed="right"><template #default="{ row }"><el-button text type="primary" @click="selected = row">详情</el-button></template></el-table-column></el-table><div class="pagination-bar"><el-pagination layout="total, prev, pager, next" :total="total" :current-page="page" :page-size="pageSize" @current-change="changePage" /></div></div><el-drawer v-model="selected" title="订单详情（运营排障用途）" size="460px"><el-alert type="info" :closable="false" title="只读：此页面不支持任何订单或资金操作。" style="margin-bottom: 16px" /><el-descriptions v-if="selected" :column="1" border><el-descriptions-item label="订单号">{{ selected.orderId }}</el-descriptions-item><el-descriptions-item label="商家">{{ selected.merchantId }}</el-descriptions-item><el-descriptions-item label="用户">{{ selected.userId }}</el-descriptions-item><el-descriptions-item label="状态">{{ selected.status }}</el-descriptions-item><el-descriptions-item label="订单金额">{{ formatMoney(selected.amountCent) }}</el-descriptions-item><el-descriptions-item label="配送联系人">{{ selected.contactName }} {{ selected.contactPhone }}</el-descriptions-item><el-descriptions-item label="配送地址">{{ selected.deliveryAddress }}</el-descriptions-item></el-descriptions></el-drawer></section>
</template>

<style scoped>
.pagination-bar { display: flex; justify-content: flex-end; padding-top: 14px; }
</style>
