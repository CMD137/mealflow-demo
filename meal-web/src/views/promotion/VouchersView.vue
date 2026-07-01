<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { saveVoucherApi, vouchersApi } from '@/api/promotion';
import type { VoucherView } from '@/types/api';
import { formatMoney, statusType } from '@/utils/format';

const loading = ref(false);
const dialogVisible = ref(false);
const rows = ref<VoucherView[]>([]);
const form = reactive({ voucherId: 0, name: '', type: 'AMOUNT_OFF', discountCent: 100, stock: 0, status: 'ACTIVE' });

async function load() {
  loading.value = true;
  try {
    rows.value = await vouchersApi();
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  Object.assign(form, { voucherId: 0, name: '', type: 'AMOUNT_OFF', discountCent: 100, stock: 0, status: 'ACTIVE' });
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
        <h2 class="page-title">优惠券管理</h2>
        <p class="page-subtitle">管理券的库存、状态和优惠金额，支撑领券与下单抵扣演示。</p>
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
    </div>

    <el-dialog v-model="dialogVisible" :title="form.voucherId ? '编辑优惠券' : '新增优惠券'" width="520px">
      <el-form :model="form" label-width="110px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="类型"><el-input v-model="form.type" /></el-form-item>
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
