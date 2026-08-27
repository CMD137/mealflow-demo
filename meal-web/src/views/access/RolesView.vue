<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { rolesApi } from '@/api/auth';
import type { RoleView } from '@/types/api';

const loading = ref(false);
const rows = ref<RoleView[]>([]);

async function load() {
  loading.value = true;
  try {
    rows.value = await rolesApi();
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
        <h2 class="page-title">角色权限</h2>
        <p class="page-subtitle">项目使用固定的预置角色；菜单会按登录权限过滤。</p>
      </div>
    </div>

    <div class="content-panel">
      <el-table v-loading="loading" :data="rows" row-key="roleCode">
        <el-table-column prop="roleCode" label="角色编码" width="180" />
        <el-table-column prop="roleName" label="角色名称" width="180" />
        <el-table-column prop="description" label="说明" min-width="220" />
        <el-table-column label="内置" width="100">
          <template #default="{ row }"><el-tag :type="row.builtin ? 'info' : 'success'">{{ row.builtin ? '是' : '否' }}</el-tag></template>
        </el-table-column>
      </el-table>
    </div>
  </section>
</template>
