<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '@/stores/auth';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const form = reactive({
  phone: '13800000000',
  code: 'demo'
});

async function submit() {
  loading.value = true;
  try {
    await auth.login(form);
    ElMessage.success('登录成功');
    router.push((route.query.redirect as string) || '/dashboard');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-panel">
      <div class="login-copy">
        <h1>MealFlow 管理后台</h1>
        <p>面向商家运营、履约处理和队列运维的统一工作台。</p>
      </div>
      <el-form class="login-form" :model="form" label-position="top" @submit.prevent="submit">
        <el-form-item label="手机号">
          <el-input v-model="form.phone" autocomplete="username" />
        </el-form-item>
        <el-form-item label="验证码">
          <el-input v-model="form.code" autocomplete="one-time-code" />
        </el-form-item>
        <el-button type="primary" size="large" :loading="loading" @click="submit">登录</el-button>
        <p class="hint">默认演示账号：13800000000，验证码任意。</p>
      </el-form>
    </section>
  </main>
</template>

<style scoped>
.login-page {
  display: grid;
  width: 100%;
  height: 100%;
  place-items: center;
  background: #eef2f7;
}

.login-panel {
  display: grid;
  grid-template-columns: 1fr 360px;
  width: 820px;
  min-height: 420px;
  overflow: hidden;
  border: 1px solid #d9e0ea;
  border-radius: 8px;
  background: #ffffff;
}

.login-copy {
  padding: 56px;
  background: #1f2937;
  color: #ffffff;
}

.login-copy h1 {
  margin: 0;
  font-size: 30px;
  letter-spacing: 0;
}

.login-copy p {
  margin-top: 18px;
  color: #d1d5db;
  line-height: 1.7;
}

.login-form {
  display: flex;
  flex-direction: column;
  justify-content: center;
  padding: 44px;
}

.hint {
  margin: 14px 0 0;
  color: #667085;
  font-size: 13px;
}
</style>
