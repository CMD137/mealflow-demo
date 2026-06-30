<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { menusApi, rolesApi, saveRoleApi } from '@/api/auth';
import type { MenuView, RoleView } from '@/types/api';

const loading = ref(false);
const dialogVisible = ref(false);
const rows = ref<RoleView[]>([]);
const menus = ref<MenuView[]>([]);
const form = reactive({ roleCode: '', roleName: '', description: '', permissions: [] as string[] });

async function load() {
  loading.value = true;
  try {
    [rows.value, menus.value] = await Promise.all([rolesApi(), menusApi()]);
  } finally {
    loading.value = false;
  }
}

function openCreate() {
  Object.assign(form, { roleCode: '', roleName: '', description: '', permissions: [] });
  dialogVisible.value = true;
}

async function save() {
  await saveRoleApi(form);
  ElMessage.success('角色已保存');
  dialogVisible.value = false;
  load();
}

onMounted(load);
</script>

<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">角色权限</h2>
        <p class="page-subtitle">维护角色和菜单权限，登录后菜单会按权限过滤。</p>
      </div>
      <el-button type="primary" @click="openCreate">新增角色</el-button>
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

    <el-dialog v-model="dialogVisible" title="新增角色" width="560px">
      <el-form :model="form" label-width="96px">
        <el-form-item label="角色编码"><el-input v-model="form.roleCode" /></el-form-item>
        <el-form-item label="角色名称"><el-input v-model="form.roleName" /></el-form-item>
        <el-form-item label="说明"><el-input v-model="form.description" /></el-form-item>
        <el-form-item label="权限">
          <el-checkbox-group v-model="form.permissions">
            <el-checkbox v-for="menu in menus" :key="menu.id" :label="menu.permissionCode">
              {{ menu.menuName }} / {{ menu.permissionCode }}
            </el-checkbox>
          </el-checkbox-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </section>
</template>
