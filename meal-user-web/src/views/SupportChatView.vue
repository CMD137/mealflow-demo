<script setup lang="ts">
import { onMounted, ref } from 'vue';
import AppShell from '@/components/AppShell.vue';
import {
  createSupportSession,
  pingSupport,
  sendSupportMessage,
  sendSupportMessageStream,
  type ChatResponse,
  type Citation
} from '@/api/support';

interface ChatItem {
  role: 'user' | 'assistant';
  content: string;
  tooling?: boolean;
  tools?: string[];
  citations?: Citation[];
  streaming?: boolean;
}

const messages = ref<ChatItem[]>([]);
const input = ref('');
const sending = ref(false);
const sessionId = ref('');
const serviceReady = ref(false);
const toolHint = ref('');
const scrollBox = ref<HTMLElement | null>(null);

function scrollToBottom() {
  requestAnimationFrame(() => {
    if (scrollBox.value) {
      scrollBox.value.scrollTop = scrollBox.value.scrollHeight;
    }
  });
}

async function ensureSession() {
  if (sessionId.value) {
    return;
  }
  const created = await createSupportSession('mealflow-h5');
  sessionId.value = created.sessionId;
}

function pushAssistant(): ChatItem {
  const item: ChatItem = { role: 'assistant', content: '', streaming: true, tools: [] };
  messages.value.push(item);
  return item;
}

async function send() {
  const text = input.value.trim();
  if (!text || sending.value) {
    return;
  }
  input.value = '';
  messages.value.push({ role: 'user', content: text });
  sending.value = true;
  const item = pushAssistant();
  scrollToBottom();
  try {
    await ensureSession();
    await sendSupportMessageStream(
      { sessionId: sessionId.value, message: text },
      {
        onStatus: (status) => {
          item.tooling = status === 'thinking';
          toolHint.value = status === 'thinking' ? '正在思考…' : '';
        },
        onTool: (tool) => {
          item.tools = [...(item.tools ?? []), tool];
          toolHint.value = `正在查询：${tool}`;
        },
        onAnswer: (delta) => {
          item.content += delta;
          scrollToBottom();
        },
        onDone: (payload) => {
          item.streaming = false;
          item.tooling = false;
          item.tools = payload.usedTools;
          item.citations = payload.citations;
          toolHint.value = '';
          scrollToBottom();
        },
        onError: (message) => {
          item.streaming = false;
          item.tooling = false;
          item.content = item.content || message;
          toolHint.value = '';
        }
      }
    );
  } catch {
    item.streaming = false;
    item.content = item.content || '客服服务暂不可用，请稍后重试。';
  } finally {
    sending.value = false;
  }
}

async function sendFallback() {
  const text = input.value.trim();
  if (!text || sending.value) {
    return;
  }
  input.value = '';
  messages.value.push({ role: 'user', content: text });
  sending.value = true;
  const item = pushAssistant();
  try {
    await ensureSession();
    const response = await sendSupportMessage({ sessionId: sessionId.value, message: text });
    item.content = response.answer;
    item.tools = response.usedTools;
    item.citations = response.citations;
  } catch {
    item.content = '客服服务暂不可用，请稍后重试。';
  } finally {
    item.streaming = false;
    sending.value = false;
  }
}

onMounted(async () => {
  try {
    await pingSupport();
    serviceReady.value = true;
  } catch {
    serviceReady.value = false;
  }
  messages.value.push({
    role: 'assistant',
    content: serviceReady.value
      ? '您好，我是 MealFlow 智能客服。可以问我：\n· 我的订单到哪一步了？\n· 我排到第几了？\n· 我的优惠券、秒杀券状态\n· 下单/排队/退款规则'
      : '客服服务暂不可用，请稍后再试。'
  });
});
</script>

<template>
  <AppShell title="在线客服" subtitle="AI 助手 · 7×24">
    <div ref="scrollBox" class="chat-box">
      <div v-for="(item, index) in messages" :key="index" class="message" :class="item.role">
        <div class="bubble">
          <span v-if="item.tooling" class="typing">●</span>
          <p class="text">{{ item.content }}<span v-if="item.streaming && !item.content" class="cursor">▌</span></p>
          <div v-if="item.tools && item.tools.length" class="tools">
            <span v-for="tool in item.tools" :key="tool" class="tool-tag">{{ tool }}</span>
          </div>
          <div v-if="item.citations && item.citations.length" class="citations">
            <details v-for="(citation, ci) in item.citations" :key="ci">
              <summary>参考知识：{{ citation.source }}#{{ citation.chunkIndex }}</summary>
              <p>{{ citation.content }}</p>
            </details>
          </div>
        </div>
      </div>
      <p v-if="toolHint" class="tool-hint">{{ toolHint }}</p>
    </div>

    <div class="composer">
      <textarea v-model="input" rows="2" placeholder="输入您的问题，例如：我排到第几了？" @keydown.enter.exact.prevent="send" />
      <div class="actions">
        <button class="ghost-button" :disabled="sending" @click="sendFallback">非流式兜底</button>
        <button class="primary-button" :disabled="sending || !input.trim()" @click="send">发送</button>
      </div>
    </div>
  </AppShell>
</template>

<style scoped>
.chat-box {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: calc(100vh - 240px);
  overflow-y: auto;
  padding: 4px 2px 12px;
}

.message {
  display: flex;
}

.message.user {
  justify-content: flex-end;
}

.bubble {
  max-width: 82%;
  border-radius: 14px;
  padding: 10px 12px;
  background: #f1f5f9;
}

.message.user .bubble {
  background: #1f2937;
  color: #ffffff;
}

.text {
  margin: 0;
  white-space: pre-wrap;
  word-break: break-word;
}

.cursor,
.typing {
  color: #94a3b8;
  animation: blink 1s steps(2, start) infinite;
}

@keyframes blink {
  to {
    visibility: hidden;
  }
}

.tools {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.tool-tag {
  border-radius: 999px;
  background: #dbeafe;
  color: #1d4ed8;
  padding: 2px 8px;
  font-size: 12px;
}

.citations {
  margin-top: 8px;
}

.citations details {
  border-top: 1px solid #e2e8f0;
  padding: 6px 0;
  font-size: 12px;
  color: #475569;
}

.citations p {
  margin: 6px 0 0;
  color: #64748b;
}

.tool-hint {
  margin: 0;
  color: #64748b;
  font-size: 13px;
}

.composer {
  position: sticky;
  bottom: 0;
  background: #ffffff;
  padding-top: 8px;
}

.composer textarea {
  width: 100%;
  box-sizing: border-box;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  padding: 8px 10px;
  resize: none;
  font: inherit;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 8px;
}

.primary-button,
.ghost-button {
  border: 0;
  border-radius: 999px;
  padding: 8px 18px;
  cursor: pointer;
  font-weight: 700;
}

.primary-button {
  background: #1f2937;
  color: #ffffff;
}

.primary-button:disabled,
.ghost-button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.ghost-button {
  background: #f1f5f9;
  color: #334155;
}
</style>
