<script setup lang="ts">
import { onMounted, ref } from 'vue';
import AppShell from '@/components/AppShell.vue';
import { messagesApi } from '@/api/notify';
import type { MessageView } from '@/types/api';

const loading = ref(false);
const messages = ref<MessageView[]>([]);

async function load() {
  loading.value = true;
  try {
    messages.value = await messagesApi();
  } finally {
    loading.value = false;
  }
}

onMounted(load);
</script>

<template>
  <AppShell title="通知消息" subtitle="订单和系统消息">
    <template #header-extra>
      <button class="ghost-button small" @click="load">刷新</button>
    </template>
    <article v-for="message in messages" :key="message.messageId" class="message-card card">
      <span class="status-pill">{{ message.bizType }}</span>
      <p>{{ message.content }}</p>
      <time>{{ message.createTime }}</time>
    </article>
    <div v-if="!loading && !messages.length" class="empty">暂无消息</div>
  </AppShell>
</template>

<style scoped>
.small {
  min-height: 34px;
}

.message-card {
  margin-bottom: 10px;
  padding: 14px;
}

.message-card p {
  margin: 12px 0;
  line-height: 1.6;
}

.message-card time {
  color: #94a3b8;
  font-size: 12px;
}
</style>
