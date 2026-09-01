<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { systemMerchantsApi, updateSystemMerchantStatusApi } from '@/api/system';
import { errorMessage } from '@/api/http';
import type { MerchantView } from '@/types/api';

const loading = ref(false);
const rows = ref<MerchantView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const filters = reactive({ name: '', status: '' });

async function load() {
  loading.value = true;
  try {
    const result = await systemMerchantsApi({ page: page.value, pageSize: pageSize.value, name: filters.name || undefined,
      status: filters.status || undefined });
    rows.value = result.items;
    total.value = result.total;
  } catch (error) {
    ElMessage.error(errorMessage(error, '商家目录加载失败'));
  } finally {
    loading.value = false;
  }
}

function query() { page.value = 1; load(); }
function changePage(value: number) { page.value = value; load(); }

async function setStatus(row: MerchantView, businessStatus: string) {
  const suspending = businessStatus === 'SUSPENDED';
  try {
    await ElMessageBox.confirm(suspending
      ? `确认挂起“${row.name}”？挂起后顾客无法继续向该商家下单。`
      : `确认将“${row.name}”设为 ${businessStatus === 'OPEN' ? '营业' : '休息'}？`,
    suspending ? '高风险操作' : '确认状态变更', { type: suspending ? 'warning' : 'info' });
    await updateSystemMerchantStatusApi(row.merchantId, businessStatus);
    ElMessage.success('商家状态已更新');
    await load();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error, '状态更新失败'));
  }
}

onMounted(load);
</script>

<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">商家治理</h2>
        <p class="page-subtitle">全局查看商家并管理营业状态；不在此编辑商品、员工或产能。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <div class="toolbar">
      <div class="filter-row">
        <el-input v-model="filters.name" clearable placeholder="商家名称" @keyup.enter="query" style="width: 220px" />
        <el-select v-model="filters.status" clearable placeholder="营业状态" style="width: 150px">
          <el-option label="营业中" value="OPEN" /><el-option label="已打烊" value="CLOSED" />
          <el-option label="已挂起" value="SUSPENDED" />
        </el-select>
      </div>
      <el-button type="primary" @click="query">查询</el-button>
    </div>
    <div class="content-panel">
      <el-table v-loading="loading" :data="rows" row-key="merchantId">
        <el-table-column prop="merchantId" label="商家 ID" width="100" />
        <el-table-column prop="name" label="商家名称" min-width="180" />
        <el-table-column label="营业状态" width="130">
          <template #default="{ row }"><el-tag :type="row.businessStatus === 'SUSPENDED' ? 'danger' : row.businessStatus === 'OPEN' ? 'success' : 'info'">{{ row.businessStatus }}</el-tag></template>
        </el-table-column>
        <el-table-column prop="baseCapacity" label="基础产能" width="110" />
        <el-table-column prop="manualFactor" label="产能系数" width="110" />
        <el-table-column label="治理操作" width="250" fixed="right">
          <template #default="{ row }">
            <el-button text type="success" :disabled="row.businessStatus === 'OPEN'" @click="setStatus(row, 'OPEN')">营业</el-button>
            <el-button text :disabled="row.businessStatus === 'CLOSED'" @click="setStatus(row, 'CLOSED')">打烊</el-button>
            <el-button text type="danger" :disabled="row.businessStatus === 'SUSPENDED'" @click="setStatus(row, 'SUSPENDED')">挂起</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="pagination-bar"><el-pagination layout="total, prev, pager, next" :total="total" :current-page="page" :page-size="pageSize" @current-change="changePage" /></div>
    </div>
  </section>
</template>

<style scoped>
.pagination-bar { display: flex; justify-content: flex-end; padding-top: 14px; }
</style>
