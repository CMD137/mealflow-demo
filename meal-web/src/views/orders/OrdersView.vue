<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { adminSkusApi } from '@/api/catalog';
import { adminOrdersApi, cancelOrderApi, submitOrderApi } from '@/api/orders';
import { mockPayApi } from '@/api/payments';
import { useAuthStore } from '@/stores/auth';
import type { OrderSkuItem, OrderView, SkuView, SubmitOrderResponse } from '@/types/api';
import { formatMoney, statusType } from '@/utils/format';

const auth = useAuthStore();
const loading = ref(false);
const submitLoading = ref(false);
const dialogVisible = ref(false);
const rows = ref<OrderView[]>([]);
const skus = ref<SkuView[]>([]);
const selected = ref<OrderView | null>(null);
const submitResult = ref<SubmitOrderResponse | null>(null);
const filters = reactive({ merchantId: auth.merchantId, userId: undefined as number | undefined, status: '' });
const orderForm = reactive({
  remark: '',
  userVoucherId: undefined as number | undefined,
  items: [{ skuId: 0, quantity: 1 }] as OrderSkuItem[]
});

const availableSkus = computed(() => skus.value.filter((sku) => sku.status === 'ON_SHELF'));

async function load() {
  if (!filters.merchantId && auth.merchantId) {
    filters.merchantId = auth.merchantId;
  }
  loading.value = true;
  try {
    const [orderData, skuData] = await Promise.all([
      adminOrdersApi({
        merchantId: filters.merchantId,
        userId: filters.userId,
        status: filters.status || undefined
      }),
      adminSkusApi()
    ]);
    rows.value = orderData;
    skus.value = skuData;
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  if (!filters.merchantId) {
    ElMessage.warning('当前登录账号未绑定商家，无法创建订单');
    return;
  }
  submitResult.value = null;
  orderForm.remark = '';
  orderForm.userVoucherId = undefined;
  orderForm.items = [{ skuId: availableSkus.value[0]?.skuId || 0, quantity: 1 }];
  dialogVisible.value = true;
}

function addItem() {
  orderForm.items.push({ skuId: availableSkus.value[0]?.skuId || 0, quantity: 1 });
}

function removeItem(index: number) {
  if (orderForm.items.length > 1) {
    orderForm.items.splice(index, 1);
  }
}

async function submitDemoOrder() {
  const merchantId = filters.merchantId;
  if (!merchantId) {
    ElMessage.warning('当前登录账号未绑定商家，无法创建订单');
    return;
  }
  const items = orderForm.items.filter((item) => item.skuId && item.quantity > 0);
  if (!items.length) {
    ElMessage.warning('请至少选择一个商品');
    return;
  }
  submitLoading.value = true;
  try {
    submitResult.value = await submitOrderApi({
      requestId: `web-${Date.now()}-${Math.random().toString(16).slice(2)}`,
      merchantId,
      items,
      userVoucherId: orderForm.userVoucherId || null,
      remark: orderForm.remark
    });
    if (submitResult.value.mode === 'ORDER_CREATED') {
      ElMessage.success(`订单已创建：${submitResult.value.orderId}`);
      dialogVisible.value = false;
      load();
    } else {
      ElMessage.warning(`订单进入排队：${submitResult.value.ticketNo}`);
      load();
    }
  } finally {
    submitLoading.value = false;
  }
}

async function pay(row: OrderView) {
  await mockPayApi(row.payOrderId);
  ElMessage.success('模拟支付完成');
  load();
}

async function cancel(row: OrderView) {
  await ElMessageBox.confirm(`确认取消订单 ${row.orderId}？`, '取消订单', { type: 'warning' });
  await cancelOrderApi(row.orderId, '商家后台取消');
  ElMessage.success('订单已取消');
  load();
}

onMounted(load);
</script>

<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">订单管理</h2>
        <p class="page-subtitle">查询订单、创建演示订单、处理支付和查看排队关联。</p>
      </div>
      <div class="action-bar">
        <el-button @click="load" :loading="loading">刷新</el-button>
        <el-button type="primary" @click="openCreate">创建演示订单</el-button>
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
      <el-button type="primary" @click="load">查询</el-button>
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
            <el-button text type="success" :disabled="row.status !== 'PENDING_PAYMENT'" @click="pay(row)">模拟支付</el-button>
            <el-button text type="danger" :disabled="row.status === 'CANCELLED' || row.status === 'DELIVERED'" @click="cancel(row)">取消</el-button>
          </template>
        </el-table-column>
      </el-table>
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
      </el-descriptions>
      <el-table v-if="selected?.items?.length" :data="selected.items" style="margin-top: 16px">
        <el-table-column prop="skuName" label="商品" min-width="180" />
        <el-table-column prop="quantity" label="数量" width="80" />
        <el-table-column label="单价" width="120">
          <template #default="{ row }">{{ formatMoney(row.priceCent) }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>

    <el-dialog v-model="dialogVisible" title="创建演示订单" width="700px">
      <el-alert
        title="用于演示完整链路：选择已上架商品，提交订单；容量不足时会进入排队，成单后可在列表中模拟支付。"
        type="info"
        :closable="false"
        show-icon
      />
      <el-form :model="orderForm" label-width="96px" class="order-form">
        <el-form-item label="商品">
          <div class="order-items">
            <div v-for="(item, index) in orderForm.items" :key="index" class="order-item-row">
              <el-select v-model="item.skuId" filterable placeholder="选择 SKU">
                <el-option
                  v-for="sku in availableSkus"
                  :key="sku.skuId"
                  :label="`${sku.name} / ${formatMoney(sku.priceCent)} / 库存 ${sku.stock}`"
                  :value="sku.skuId"
                />
              </el-select>
              <el-input-number v-model="item.quantity" :min="1" :max="99" />
              <el-button :disabled="orderForm.items.length === 1" @click="removeItem(index)">删除</el-button>
            </div>
            <el-button @click="addItem">添加商品</el-button>
          </div>
        </el-form-item>
        <el-form-item label="用户券 ID">
          <el-input-number v-model="orderForm.userVoucherId" :min="1" controls-position="right" placeholder="可选" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="orderForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <el-result
        v-if="submitResult?.mode === 'QUEUED'"
        icon="warning"
        title="订单已进入排队"
        :sub-title="`ticket: ${submitResult.ticketNo}，前方 ${submitResult.aheadCount} 单，预计等待 ${submitResult.estimatedWaitSeconds} 秒`"
      />
      <template #footer>
        <el-button @click="dialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="submitLoading" @click="submitDemoOrder">提交订单</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.order-form {
  margin-top: 16px;
}

.order-items {
  display: flex;
  width: 100%;
  flex-direction: column;
  gap: 10px;
}

.order-item-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 140px 72px;
  gap: 8px;
  width: 100%;
}
</style>
