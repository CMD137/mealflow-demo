<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { systemUsersApi, updateSystemUserStatusApi } from '@/api/system';
import { errorMessage } from '@/api/http';
import { useAuthStore } from '@/stores/auth';
import type { SystemUserView } from '@/types/api';

const auth = useAuthStore();
const loading = ref(false);
const rows = ref<SystemUserView[]>([]);
const total = ref(0);
const page = ref(1);
const pageSize = ref(20);
const filters = reactive({ phone: '', status: '' });
const currentUserId = computed(() => auth.loginInfo?.userId);

async function load() {
  loading.value = true;
  try {
    const result = await systemUsersApi({ page: page.value, pageSize: pageSize.value, phone: filters.phone || undefined,
      status: filters.status || undefined });
    rows.value = result.items;
    total.value = result.total;
  } catch (error) { ElMessage.error(errorMessage(error, '用户目录加载失败')); } finally { loading.value = false; }
}
function query() { page.value = 1; load(); }
function changePage(value: number) { page.value = value; load(); }
async function setStatus(row: SystemUserView, status: string) {
  const disabling = status === 'DISABLED';
  try {
    await ElMessageBox.confirm(disabling ? `确认禁用 ${row.phone}？该账号所有已登录会话将立即失效。` : `确认恢复 ${row.phone}？`,
      disabling ? '禁用用户' : '恢复用户', { type: disabling ? 'warning' : 'info' });
    await updateSystemUserStatusApi(row.userId, status);
    ElMessage.success(disabling ? '用户已禁用，已登录会话已失效' : '用户已恢复');
    await load();
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(errorMessage(error, '用户状态更新失败')); }
}
onMounted(load);
</script>

<template>
  <section class="page">
    <div class="page-header"><div><h2 class="page-title">用户治理</h2><p class="page-subtitle">按需启用或禁用账号；不展示令牌、验证码或其他认证材料。</p></div><el-button :loading="loading" @click="load">刷新</el-button></div>
    <div class="toolbar"><div class="filter-row"><el-input v-model="filters.phone" clearable placeholder="手机号" @keyup.enter="query" style="width: 220px" /><el-select v-model="filters.status" clearable placeholder="账号状态" style="width: 150px"><el-option label="正常" value="NORMAL" /><el-option label="已禁用" value="DISABLED" /></el-select></div><el-button type="primary" @click="query">查询</el-button></div>
    <div class="content-panel"><el-table v-loading="loading" :data="rows" row-key="userId"><el-table-column prop="userId" label="用户 ID" width="100" /><el-table-column prop="phone" label="手机号" width="150" /><el-table-column prop="nickname" label="昵称" min-width="160" /><el-table-column prop="identitySummary" label="身份摘要" width="150" /><el-table-column label="账号状态" width="120"><template #default="{ row }"><el-tag :type="row.status === 'DISABLED' ? 'danger' : 'success'">{{ row.status }}</el-tag></template></el-table-column><el-table-column label="操作" width="150" fixed="right"><template #default="{ row }"><el-button text type="danger" :disabled="row.userId === currentUserId || row.status === 'DISABLED'" @click="setStatus(row, 'DISABLED')">禁用</el-button><el-button text type="success" :disabled="row.status !== 'DISABLED'" @click="setStatus(row, 'NORMAL')">恢复</el-button></template></el-table-column></el-table><div class="pagination-bar"><el-pagination layout="total, prev, pager, next" :total="total" :current-page="page" :page-size="pageSize" @current-change="changePage" /></div></div>
  </section>
</template>

<style scoped>
.pagination-bar { display: flex; justify-content: flex-end; padding-top: 14px; }
</style>
