<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { adminCategoriesApi, saveCategoryApi } from '@/api/catalog';
import type { CategoryView } from '@/types/api';
import { statusType } from '@/utils/format';

const loading = ref(false);
const dialogVisible = ref(false);
const rows = ref<CategoryView[]>([]);
const form = reactive({ categoryId: 0, name: '', sortOrder: 10, status: 'ACTIVE' });

async function load() {
  loading.value = true;
  try {
    rows.value = await adminCategoriesApi();
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  Object.assign(form, { categoryId: 0, name: '', sortOrder: 10, status: 'ACTIVE' });
  dialogVisible.value = true;
}

function openEdit(row: CategoryView) {
  Object.assign(form, row);
  dialogVisible.value = true;
}

async function save() {
  await saveCategoryApi({ name: form.name, sortOrder: form.sortOrder, status: form.status }, form.categoryId || undefined);
  ElMessage.success('类目已保存');
  dialogVisible.value = false;
  load();
}

onMounted(load);
</script>

<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">商品类目</h2>
        <p class="page-subtitle">维护商家后台类目，类目会影响 SKU 新增和用户端浏览。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增类目</el-button>
    </div>

    <div class="content-panel">
      <el-table v-loading="loading" :data="rows" row-key="categoryId">
        <el-table-column prop="categoryId" label="ID" width="100" />
        <el-table-column prop="name" label="类目名称" min-width="180" />
        <el-table-column prop="sortOrder" label="排序" width="100" />
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)">{{ row.status }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.categoryId ? '编辑类目' : '新增类目'" width="420px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="启用" value="ACTIVE" />
            <el-option label="停用" value="DISABLED" />
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
