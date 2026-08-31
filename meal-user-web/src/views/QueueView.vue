<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue';
import AppShell from '@/components/AppShell.vue';
import { errorMessage } from '@/api/http';
import { activeQueueTicketApi, cancelQueueTicketApi, queueTicketHistoryApi } from '@/api/queue';
import { formatWait } from '@/utils/format';
import type { QueueTicketView } from '@/types/api';

const ticket = ref<QueueTicketView | null>(null);
const history = ref<QueueTicketView[]>([]);
const loading = ref(false);
const cancelling = ref(false);
const message = ref('');
const messageType = ref<'error' | 'success'>('error');
let refreshTimer: ReturnType<typeof setInterval> | undefined;

async function load(silent = false) {
  if (!silent) loading.value = true;
  try {
    const [activeTicket, ticketHistory] = await Promise.all([activeQueueTicketApi(), queueTicketHistoryApi()]);
    ticket.value = activeTicket;
    history.value = ticketHistory.filter((item) => item.ticketId !== activeTicket?.ticketId);
    message.value = '';
  } catch (error) {
    messageType.value = 'error';
    message.value = errorMessage(error, '排队状态加载失败，请稍后重试');
  } finally {
    loading.value = false;
  }
}

async function cancel() {
  if (!ticket.value) return;
  cancelling.value = true;
  message.value = '';
  try {
    await cancelQueueTicketApi(ticket.value.ticketId);
    await load();
    messageType.value = 'success';
    message.value = '排队已取消。';
  } catch (error) {
    messageType.value = 'error';
    message.value = errorMessage(error, '取消排队失败，请稍后重试');
  } finally {
    cancelling.value = false;
  }
}

function ticketStatusText(status: string) {
  return ({ WAITING: '排队中', READY: '已轮到您', PROCESSING: '正在为您创建订单', ORDER_CREATED: '已生成订单', TIMEOUT: '已超时', CANCELLED: '已取消' } as Record<string, string>)[status] || status;
}

onMounted(() => {
  void load();
  refreshTimer = setInterval(() => void load(true), 5000);
});

onUnmounted(() => {
  if (refreshTimer) clearInterval(refreshTimer);
});
</script>

<template>
  <AppShell title="排队进度" subtitle="每 5 秒自动刷新一次" :show-nav="false">
    <p v-if="message" :class="messageType === 'error' ? 'inline-error' : 'success-message'">{{ message }}</p>
    <section v-if="ticket" class="card queue-card">
      <div class="queue-head">
        <strong>排队号 {{ ticket.ticketNo }}</strong>
        <span class="status-pill primary">{{ ticketStatusText(ticket.status) }}</span>
      </div>
      <p v-if="ticket.status === 'WAITING'">前方还有 {{ ticket.aheadCount }} 单，预计等待 {{ formatWait(ticket.estimatedWaitSeconds) }}。</p>
      <p v-else-if="ticket.status === 'READY'">已轮到您，正在为您创建订单，请不要重复提交。</p>
      <p v-else-if="ticket.status === 'PROCESSING'">正在为您创建订单，请稍候。</p>
      <p v-else-if="ticket.status === 'ORDER_CREATED'">订单已创建，可前往订单详情完成支付。</p>
      <p v-else>该排队票当前不可继续使用。</p>
      <div class="actions">
        <button v-if="ticket.canCancel" class="danger-button" :disabled="cancelling" @click="cancel">
          {{ cancelling ? '正在取消…' : '取消排队' }}
        </button>
        <RouterLink v-if="ticket.orderId" class="primary-button" :to="`/orders/${ticket.orderId}`">查看订单</RouterLink>
        <button class="ghost-button" :disabled="loading" @click="load()">刷新状态</button>
      </div>
    </section>
    <div v-else-if="!loading && history.length === 0" class="empty">当前没有进行中的排队</div>
    <section v-if="history.length" class="history-section">
      <h3>最近排队记录</h3>
      <article v-for="item in history" :key="item.ticketId" class="card history-item">
        <div class="queue-head">
          <strong>排队号 {{ item.ticketNo }}</strong>
          <span class="status-pill">{{ ticketStatusText(item.status) }}</span>
        </div>
        <p v-if="item.status === 'TIMEOUT'">该排队票已超时，系统已释放本次排队容量。</p>
        <p v-else-if="item.status === 'CANCELLED'">该排队票已取消。</p>
        <p v-else-if="item.status === 'ORDER_CREATED'">已由该排队票生成订单。</p>
        <p v-else>状态：{{ ticketStatusText(item.status) }}</p>
        <RouterLink v-if="item.orderId" class="ghost-button" :to="`/orders/${item.orderId}`">查看订单</RouterLink>
      </article>
    </section>
  </AppShell>
</template>

<style scoped>
.queue-card { padding: 16px; }
.queue-head, .actions { display: flex; align-items: center; gap: 10px; }
.queue-head { justify-content: space-between; }
.queue-card p { color: #64748b; line-height: 1.7; }
.actions { flex-wrap: wrap; }
.success-message { color: #15803d; }
.history-section { display: grid; gap: 12px; margin-top: 18px; }
.history-section h3 { margin: 0; font-size: 16px; }
.history-item { padding: 14px 16px; }
.history-item p { color: #64748b; margin: 10px 0; }
</style>
