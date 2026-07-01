<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { ElMessage } from 'element-plus';
import {
  notifyConsumerRecordsApi,
  notifyDeliveriesApi,
  notifyMessagesApi,
  recoverNotifyConsumerRecordsApi,
  replayNotifyConsumerRecordApi
} from '@/api/notify';
import { useAuthStore } from '@/stores/auth';
import type { ConsumerRecordView, DeliveryView, MessageView } from '@/types/api';
import { statusType } from '@/utils/format';

const auth = useAuthStore();
const loading = ref(false);
const activeTab = ref('messages');
const messages = ref<MessageView[]>([]);
const deliveries = ref<DeliveryView[]>([]);
const records = ref<ConsumerRecordView[]>([]);

async function load() {
  loading.value = true;
  try {
    const [messageData, deliveryData] = await Promise.all([
      notifyMessagesApi(),
      notifyDeliveriesApi()
    ]);
    messages.value = messageData;
    deliveries.value = deliveryData;
    records.value = auth.hasPermission('INTERNAL_OPERATE') ? await notifyConsumerRecordsApi() : [];
  } finally {
    loading.value = false;
  }
}

async function recover() {
  if (!auth.hasPermission('INTERNAL_OPERATE')) {
    return;
  }
  const count = await recoverNotifyConsumerRecordsApi();
  ElMessage.success(`已恢复 ${count} 条超时消费记录`);
  load();
}

async function replay(row: ConsumerRecordView) {
  if (!auth.hasPermission('INTERNAL_OPERATE')) {
    return;
  }
  await replayNotifyConsumerRecordApi(row.eventKey, row.consumerGroup);
  ElMessage.success('消费记录已重放');
  load();
}

onMounted(load);
</script>

<template>
  <section class="page">
    <div class="page-header">
      <div>
        <h2 class="page-title">通知中心</h2>
        <p class="page-subtitle">查看站内通知、投递记录和通知消费幂等记录。</p>
      </div>
      <div class="action-bar">
        <el-button :loading="loading" @click="load">刷新</el-button>
        <el-button v-if="auth.hasPermission('INTERNAL_OPERATE')" type="primary" @click="recover">恢复超时消费</el-button>
      </div>
    </div>

    <div class="content-panel">
      <el-tabs v-model="activeTab">
        <el-tab-pane label="消息列表" name="messages">
          <el-table v-loading="loading" :data="messages" row-key="messageId">
            <el-table-column prop="messageId" label="消息 ID" width="110" />
            <el-table-column prop="bizType" label="业务类型" width="150" />
            <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
            <el-table-column prop="createTime" label="创建时间" width="180" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane label="投递记录" name="deliveries">
          <el-table v-loading="loading" :data="deliveries" row-key="deliveryId">
            <el-table-column prop="deliveryId" label="投递 ID" width="110" />
            <el-table-column prop="messageId" label="消息 ID" width="110" />
            <el-table-column prop="channel" label="渠道" width="120" />
            <el-table-column prop="target" label="目标" width="160" />
            <el-table-column label="状态" width="120">
              <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="content" label="内容" min-width="240" show-overflow-tooltip />
            <el-table-column prop="createTime" label="创建时间" width="180" />
          </el-table>
        </el-tab-pane>

        <el-tab-pane v-if="auth.hasPermission('INTERNAL_OPERATE')" label="消费记录" name="records">
          <el-table v-loading="loading" :data="records" row-key="id">
            <el-table-column prop="id" label="ID" width="90" />
            <el-table-column prop="eventType" label="事件类型" width="150" />
            <el-table-column prop="consumerGroup" label="消费组" width="220" show-overflow-tooltip />
            <el-table-column prop="eventKey" label="事件键" min-width="260" show-overflow-tooltip />
            <el-table-column label="状态" width="120">
              <template #default="{ row }"><el-tag :type="statusType(row.status)">{{ row.status }}</el-tag></template>
            </el-table-column>
            <el-table-column prop="lastError" label="错误" min-width="180" show-overflow-tooltip />
            <el-table-column label="操作" width="110" fixed="right">
              <template #default="{ row }">
                <el-button text type="primary" @click="replay(row)">重放</el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>
      </el-tabs>
    </div>
  </section>
</template>
