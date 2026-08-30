<script setup lang="ts">
import { reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '@/stores/auth';
import { requestLoginCodeApi } from '@/api/auth';
import { errorMessage } from '@/api/http';

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();
const loading = ref(false);
const error = ref('');
const codeLoading = ref(false);
const form = reactive({
  phone: '13800000001',
  code: '123456'
});

async function requestCode() {
  error.value = '';
  codeLoading.value = true;
  try {
    await requestLoginCodeApi(form.phone);
    error.value = '验证码已发送，请在开发服务日志中查看。';
  } catch (err) {
    error.value = errorMessage(err, '验证码发送失败');
  } finally {
    codeLoading.value = false;
  }
}

async function submit() {
  error.value = '';
  loading.value = true;
  try {
    await auth.login(form);
    router.replace(String(route.query.redirect || '/'));
  } catch (err) {
    error.value = errorMessage(err, '验证码错误、已过期或尚未获取，请先获取验证码');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <div class="login-page">
    <section class="login-hero">
      <div class="brand-mark">M</div>
      <h1>MealFlow</h1>
      <p>点餐、排队、支付和通知都在这里完成。</p>
    </section>

    <form class="login-card" @submit.prevent="submit">
      <div class="field">
        <label>手机号</label>
        <input v-model="form.phone" inputmode="tel" />
      </div>
      <div class="field">
        <label>验证码</label>
        <div class="code-row">
          <input v-model="form.code" />
          <button type="button" class="code-button" :disabled="codeLoading" @click="requestCode">
            {{ codeLoading ? '发送中' : '获取验证码' }}
          </button>
        </div>
      </div>
      <button class="primary-button login-button" :disabled="loading">
        {{ loading ? '登录中...' : '登录' }}
      </button>
      <p class="hint">开发环境验证码会输出到认证服务日志，五分钟内仅能使用一次。</p>
      <p v-if="error" class="error">{{ error }}</p>
    </form>
  </div>
</template>

<style scoped>
.login-page {
  display: grid;
  min-height: 100vh;
  max-width: 430px;
  margin: 0 auto;
  align-content: center;
  gap: 18px;
  background: #eef4fb;
  padding: 26px;
}

.login-hero {
  display: grid;
  gap: 8px;
}

.brand-mark {
  display: grid;
  width: 48px;
  height: 48px;
  place-items: center;
  border-radius: 12px;
  background: #1f2937;
  color: #ffffff;
  font-weight: 900;
}

.login-hero h1 {
  margin: 6px 0 0;
  font-size: 34px;
}

.login-hero p,
.hint {
  margin: 0;
  color: #667085;
  font-size: 14px;
}

.login-card {
  border: 1px solid #dce4ee;
  border-radius: 14px;
  background: #ffffff;
  padding: 18px;
}

.login-button {
  width: 100%;
}

.code-row {
  display: flex;
  gap: 8px;
}

.code-row input {
  min-width: 0;
  flex: 1;
}

.code-button {
  border: 1px solid #1f2937;
  border-radius: 8px;
  background: #ffffff;
  color: #1f2937;
  padding: 0 10px;
  white-space: nowrap;
}

.error {
  margin: 10px 0 0;
  color: #b91c1c;
  font-size: 13px;
}
</style>
