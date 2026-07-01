<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { merchantsApi } from '@/api/merchant';
import type { MerchantView } from '@/types/api';
import { statusType } from '@/utils/format';

const loading = ref(false);
const rows = ref<MerchantView[]>([]);

async function load() {
  loading.value = true;
  try {
    rows.value = await merchantsApi();
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
        <h2 class="page-title">商家资料</h2>
        <p class="page-subtitle">查看系统内商家和营业状态。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <div class="content-panel">
      <el-table v-loading="loading" :data="rows" row-key="merchantId">
        <el-table-column prop="merchantId" label="商家 ID" width="120" />
        <el-table-column prop="name" label="商家名称" min-width="200" />
        <el-table-column prop="baseCapacity" label="基础产能" width="120" />
        <el-table-column prop="manualFactor" label="人工系数" width="120" />
        <el-table-column label="营业状态" width="140">
          <template #default="{ row }"><el-tag :type="statusType(row.businessStatus)">{{ row.businessStatus }}</el-tag></template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>
