<script setup lang="ts">
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';

const router = useRouter();
const auth = useAuthStore();

function logout() {
  auth.logout();
  router.push('/login');
}
</script>

<template>
  <section class="page">
    <div class="content-panel forbidden">
      <el-result
        icon="warning"
        title="当前账号没有访问该后台页面的权限"
        sub-title="请使用商家管理员账号登录，或联系管理员调整角色权限。"
      >
        <template #extra>
          <div class="action-bar">
            <el-button @click="logout">重新登录</el-button>
            <el-button type="primary" @click="router.push('/dashboard')">返回工作台</el-button>
          </div>
        </template>
      </el-result>
    </div>
  </section>
</template>

<style scoped>
.forbidden {
  display: grid;
  min-height: 420px;
  place-items: center;
}
</style>
