<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { saveVoucherApi, vouchersApi } from '@/api/promotion';
import { useAuthStore } from '@/stores/auth';
import type { VoucherView } from '@/types/api';
import { formatMoney, statusType } from '@/utils/format';

const loading = ref(false);
const dialogVisible = ref(false);
const rows = ref<VoucherView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const auth = useAuthStore();
const form = reactive({ voucherId: 0, name: '', type: 'SECKILL', discountCent: 100, stock: 0, status: 'ACTIVE' });

async function load() {
  loading.value = true;
  try {
    const data = await vouchersApi({ page: page.value, pageSize: pageSize.value });
    rows.value = data.items;
    total.value = data.total;
  } finally {
    loading.value = false;
  }
}

function handlePageChange(nextPage: number) {
  page.value = nextPage;
  load();
}

function openCreate() {
  Object.assign(form, { voucherId: 0, name: '', type: 'SECKILL', discountCent: 100, stock: 0, status: 'ACTIVE' });
  dialogVisible.value = true;
}

function openEdit(row: VoucherView) {
  Object.assign(form, row);
  dialogVisible.value = true;
}

async function save() {
  await saveVoucherApi(
    {
      name: form.name,
      type: form.type,
      discountCent: form.discountCent,
      stock: form.stock,
      status: form.status
    },
    form.voucherId || undefined
  );
  ElMessage.success('优惠券已保存');
  dialogVisible.value = false;
  load();
}

onMounted(load);
</script>

<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">{{ auth.roleCode === 'SYSTEM_ADMIN' ? '平台券管理' : '本店优惠券管理' }}</h2>
        <p class="page-subtitle">{{ auth.roleCode === 'SYSTEM_ADMIN' ? '仅管理 PLATFORM 范围的平台券；商家券保持由所属商家独立经营。' : '仅管理当前商家的 MERCHANT 范围优惠券。' }}</p>
      </div>
      <el-button type="primary" @click="openCreate">新增券</el-button>
    </div>

    <div class="content-panel">
      <el-table v-loading="loading" :data="rows" row-key="voucherId">
        <el-table-column prop="voucherId" label="ID" width="100" />
        <el-table-column prop="name" label="券名称" min-width="180" />
        <el-table-column prop="type" label="类型" width="140" />
        <el-table-column label="优惠" width="130">
          <template #default="{ row }"><span class="money">{{ formatMoney(row.discountCent) }}</span></template>
        </el-table-column>
        <el-table-column prop="stock" label="库存" width="100" />
        <el-table-column label="状态" width="130">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="120" fixed="right">
          <template #default="{ row }"><el-button text type="primary" @click="openEdit(row)">编辑</el-button></template>
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

    <el-dialog v-model="dialogVisible" :title="form.voucherId ? '编辑优惠券' : '新增优惠券'" width="520px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型"><el-tag type="success">秒杀券</el-tag></el-form-item>
        <el-form-item label="优惠金额(分)"><el-input-number v-model="form.discountCent" :min="1" /></el-form-item>
        <el-form-item label="库存"><el-input-number v-model="form.stock" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="禁用" value="DISABLED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.pagination-bar {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}
</style>
