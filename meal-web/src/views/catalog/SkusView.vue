<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { adminCategoriesApi, adminSkusApi, saveSkuApi, updateSkuStatusApi, updateSkuStockApi, uploadImageApi } from '@/api/catalog';
import type { CategoryView, SkuView } from '@/types/api';
import { formatMoney, statusType } from '@/utils/format';

const loading = ref(false);
const dialogVisible = ref(false);
const rows = ref<SkuView[]>([]);
const categories = ref<CategoryView[]>([]);
const filters = reactive({ categoryId: '', status: '' });
const form = reactive({
  skuId: 0,
  categoryId: 0,
  name: '',
  description: '',
  imageUrl: '',
  priceCent: 100,
  stock: 0,
  status: 'ON_SHELF'
});

const filteredRows = computed(() =>
  rows.value.filter((row) => {
    const categoryMatched = !filters.categoryId || row.categoryId === Number(filters.categoryId);
    const statusMatched = !filters.status || row.status === filters.status;
    return categoryMatched && statusMatched;
  })
);

async function load() {
  loading.value = true;
  try {
    const [categoryData, skuData] = await Promise.all([adminCategoriesApi(), adminSkusApi()]);
    categories.value = categoryData;
    rows.value = skuData;
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  Object.assign(form, { skuId: 0, categoryId: categories.value[0]?.categoryId || 0, name: '', description: '', imageUrl: '', priceCent: 100, stock: 0, status: 'ON_SHELF' });
  dialogVisible.value = true;
}

function openEdit(row: SkuView) {
  Object.assign(form, row);
  dialogVisible.value = true;
}

async function save() {
  await saveSkuApi(
    {
      categoryId: form.categoryId,
      name: form.name,
      description: form.description,
      imageUrl: form.imageUrl,
      priceCent: form.priceCent,
      stock: form.stock,
      status: form.status
    },
    form.skuId || undefined
  );
  ElMessage.success('商品已保存');
  dialogVisible.value = false;
  load();
}

async function toggleStatus(row: SkuView) {
  await updateSkuStatusApi(row.skuId, row.status === 'ON_SHELF' ? 'OFF_SHELF' : 'ON_SHELF');
  ElMessage.success('商品状态已更新');
  load();
}

async function changeStock(row: SkuView, stock: number) {
  await updateSkuStockApi(row.skuId, stock);
  ElMessage.success('库存已更新');
  load();
}

async function upload(file: { raw: File }) {
  const uploaded = await uploadImageApi(file.raw);
  form.imageUrl = uploaded.url;
  ElMessage.success('图片已上传');
}

onMounted(load);
</script>

<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">商品管理</h2>
        <p class="page-subtitle">维护 SKU、图片、库存和上下架状态。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增商品</el-button>
    </div>

    <div class="toolbar">
      <div class="filter-row">
        <el-select v-model="filters.categoryId" clearable placeholder="类目" style="width: 180px">
          <el-option v-for="category in categories" :key="category.categoryId" :label="category.name" :value="String(category.categoryId)" />
        </el-select>
        <el-select v-model="filters.status" clearable placeholder="状态" style="width: 160px">
          <el-option label="上架" value="ON_SHELF" />
          <el-option label="下架" value="OFF_SHELF" />
        </el-select>
      </div>
      <el-button :loading="loading" @click="load">刷新</el-button>
    </div>

    <div class="content-panel">
      <el-table v-loading="loading" :data="filteredRows" row-key="skuId">
        <el-table-column prop="skuId" label="ID" width="90" />
        <el-table-column label="图片" width="96">
          <template #default="{ row }">
            <el-image v-if="row.imageUrl" :src="row.imageUrl" fit="cover" style="width: 52px; height: 52px; border-radius: 6px" />
            <span v-else class="status-line">未上传</span>
          </template>
        </el-table-column>
        <el-table-column prop="name" label="商品名称" min-width="180" />
        <el-table-column prop="categoryName" label="类目" width="140" />
        <el-table-column label="价格" width="110">
          <template #default="{ row }"><span class="money">{{ formatMoney(row.priceCent) }}</span></template>
        </el-table-column>
        <el-table-column label="库存" width="150">
          <template #default="{ row }">
            <div class="stock-cell">
              <span>{{ Number(row.stock ?? 0) }}</span>
              <el-input-number :model-value="Number(row.stock ?? 0)" :min="0" size="small" @change="(value: number | undefined) => changeStock(row, Number(value || 0))" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" @click="openEdit(row)">编辑</el-button>
            <el-button text :type="row.status === 'ON_SHELF' ? 'warning' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'ON_SHELF' ? '下架' : '上架' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.skuId ? '编辑商品' : '新增商品'" width="620px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="类目">
          <el-select v-model="form.categoryId" style="width: 100%">
            <el-option v-for="category in categories" :key="category.categoryId" :label="category.name" :value="category.categoryId" />
          </el-select>
        </el-form-item>
        <el-form-item label="名称"><el-input v-model="form.name" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="图片">
          <div class="upload-line">
            <el-input v-model="form.imageUrl" />
            <el-upload :show-file-list="false" :auto-upload="false" accept="image/*" :on-change="upload">
              <el-button>上传</el-button>
            </el-upload>
          </div>
        </el-form-item>
        <el-form-item label="价格(分)"><el-input-number v-model="form.priceCent" :min="1" /></el-form-item>
        <el-form-item label="库存"><el-input-number v-model="form.stock" :min="0" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="form.status">
            <el-option label="上架" value="ON_SHELF" />
            <el-option label="下架" value="OFF_SHELF" />
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
.upload-line {
  display: grid;
  grid-template-columns: 1fr auto;
  gap: 8px;
  width: 100%;
}

.stock-cell {
  display: grid;
  grid-template-columns: 32px 1fr;
  align-items: center;
  gap: 8px;
}
</style>
