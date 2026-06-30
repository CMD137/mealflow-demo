<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { addEmployeeApi, changeEmployeeRoleApi, changeEmployeeStatusApi, employeesApi, rolesApi } from '@/api/auth';
import type { EmployeeView, RoleView } from '@/types/api';
import { statusType } from '@/utils/format';

const loading = ref(false);
const dialogVisible = ref(false);
const rows = ref<EmployeeView[]>([]);
const roles = ref<RoleView[]>([]);
const form = reactive({ phone: '', nickname: '', roleCode: 'STORE_STAFF' });

async function load() {
  loading.value = true;
  try {
    [rows.value, roles.value] = await Promise.all([employeesApi(), rolesApi()]);
  } finally {
    loading.value = false;
  }
}

async function add() {
  await addEmployeeApi(form);
  ElMessage.success('员工已新增');
  dialogVisible.value = false;
  load();
}

async function changeRole(row: EmployeeView, roleCode: string) {
  await changeEmployeeRoleApi(row.employeeId, roleCode);
  ElMessage.success('角色已更新');
  load();
}

async function toggleStatus(row: EmployeeView) {
  await changeEmployeeStatusApi(row.employeeId, row.status === 'ACTIVE' ? 'DISABLED' : 'ACTIVE');
  ElMessage.success('状态已更新');
  load();
}

onMounted(load);
</script>

<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">员工管理</h2>
        <p class="page-subtitle">维护商家员工账号、角色和启停状态。</p>
      </div>
      <el-button type="primary" @click="dialogVisible = true">新增员工</el-button>
    </div>

    <div class="content-panel">
      <el-table v-loading="loading" :data="rows" row-key="employeeId">
        <el-table-column prop="employeeId" label="员工 ID" width="110" />
        <el-table-column prop="phone" label="手机号" width="150" />
        <el-table-column prop="nickname" label="昵称" min-width="160" />
        <el-table-column label="角色" width="190">
          <template #default="{ row }">
            <el-select :model-value="row.roleCode" size="small" @change="(value: string | number | boolean) => changeRole(row, String(value))">
              <el-option v-for="role in roles" :key="role.roleCode" :label="role.roleName" :value="role.roleCode" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="130">
          <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
        </el-table-column>
        <el-table-column label="操作" width="130" fixed="right">
          <template #default="{ row }">
            <el-button text :type="row.status === 'ACTIVE' ? 'warning' : 'success'" @click="toggleStatus(row)">
              {{ row.status === 'ACTIVE' ? '停用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <el-dialog v-model="dialogVisible" title="新增员工" width="460px">
      <el-form :model="form" label-width="90px">
        <el-form-item label="手机号"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="昵称"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleCode" style="width: 100%">
            <el-option v-for="role in roles" :key="role.roleCode" :label="role.roleName" :value="role.roleCode" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="add">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>
