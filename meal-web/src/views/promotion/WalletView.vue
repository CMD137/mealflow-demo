<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { walletApi } from '@/api/promotion';

const loading = ref(false);
const rows = ref<unknown[]>([]);

async function load() {
  loading.value = true;
  try {
    rows.value = await walletApi();
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
        <h2 class="page-title">用户券包</h2>
        <p class="page-subtitle">查看当前登录用户的可用券，用于演示领券到下单的闭环。</p>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>
    <div class="content-panel">
      <el-table v-loading="loading" :data="rows">
        <el-table-column label="券包记录">
          <template #default="{ row }"><pre>{{ row }}</pre></template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>
