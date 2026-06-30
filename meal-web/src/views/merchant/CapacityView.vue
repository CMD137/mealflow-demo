<script setup lang="ts">
import { reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { updateBusinessStatusApi, updateCapacityApi } from '@/api/merchant';
import { setQueueLimitApi } from '@/api/queue';
import { useAuthStore } from '@/stores/auth';

const auth = useAuthStore();
const loading = ref(false);
const form = reactive({
  merchantId: auth.merchantId,
  capacityLimit: 1,
  queueLimit: 20,
  businessStatus: 'OPEN'
});

async function save() {
  loading.value = true;
  try {
    await updateCapacityApi(form.merchantId, form.capacityLimit);
    await updateBusinessStatusApi(form.merchantId, form.businessStatus);
    await setQueueLimitApi(form.merchantId, form.queueLimit);
    ElMessage.success('产能配置已保存');
  } finally {
    loading.value = false;
  }
}
</script>

<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">产能配置</h2>
        <p class="page-subtitle">调整商家并发接单容量、排队上限和营业状态。</p>
      </div>
    </div>
    <div class="content-panel">
      <el-form :model="form" label-width="120px" style="max-width: 520px">
        <el-form-item label="商家 ID"><el-input-number v-model="form.merchantId" :min="1" /></el-form-item>
        <el-form-item label="产能上限"><el-input-number v-model="form.capacityLimit" :min="1" /></el-form-item>
        <el-form-item label="排队上限"><el-input-number v-model="form.queueLimit" :min="0" /></el-form-item>
        <el-form-item label="营业状态">
          <el-select v-model="form.businessStatus">
            <el-option label="营业中" value="OPEN" />
            <el-option label="休息中" value="CLOSED" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="loading" @click="save">保存配置</el-button>
        </el-form-item>
      </el-form>
    </div>
  </section>
</template>
