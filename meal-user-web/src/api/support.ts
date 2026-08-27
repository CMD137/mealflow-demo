import { readToken, clearToken } from './http';

/** The support agent is routed by the gateway at /api/support/**; the H5 dev proxy strips
 *  the /api prefix for the other APIs, so the support client talks to the gateway directly. */
const SUPPORT_BASE = (import.meta.env.VITE_SUPPORT_BASE_URL as string | undefined) || 'http://localhost:8080/api/support';

export interface SupportPingView {
  service: string;
  tools: ToolDefinition[];
}

export interface ToolDefinition {
  name: string;
  description: string;
  allowedRoles: string[];
  requiredParams: string[];
  mockOnly: boolean;
}

export interface Citation {
  source: string;
  chunkIndex: string;
  score?: number | null;
  content: string;
}

export interface ChatResponse {
  sessionId: string;
  answer: string;
  usedTools: string[];
  traceId: string;
  citations: Citation[];
}

export interface CreateSessionResponse {
  sessionId: string;
  traceId: string;
}

interface Envelope<T> {
  success: boolean;
  code: string;
  message: string;
  data: T;
}

function headers(): Record<string, string> {
  const token = readToken();
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {})
  };
}

async function unwrap<T>(response: Response): Promise<T> {
  if (response.status === 401) {
    clearToken();
    if (window.location.pathname !== '/login') {
      window.location.href = '/login';
    }
    throw new Error('登录已过期');
  }
  const envelope = (await response.json()) as Envelope<T>;
  if (!response.ok || !envelope.success) {
    throw new Error(envelope.message || envelope.code || `请求失败（${response.status}）`);
  }
  return envelope.data;
}

export async function pingSupport(): Promise<SupportPingView> {
  const response = await fetch(`${SUPPORT_BASE}/ping`, { headers: headers() });
  return unwrap<SupportPingView>(response);
}

export async function createSupportSession(channel = 'mealflow-h5'): Promise<CreateSessionResponse> {
  const response = await fetch(`${SUPPORT_BASE}/session`, {
    method: 'POST',
    headers: headers(),
    body: JSON.stringify({ channel })
  });
  return unwrap<CreateSessionResponse>(response);
}

export async function sendSupportMessage(payload: {
  sessionId: string;
  message: string;
}): Promise<ChatResponse> {
  const response = await fetch(`${SUPPORT_BASE}/chat`, {
    method: 'POST',
    headers: headers(),
    body: JSON.stringify(payload)
  });
  return unwrap<ChatResponse>(response);
}

export interface StreamHandlers {
  onStatus?: (status: string) => void;
  onTool?: (tool: string) => void;
  onAnswer?: (delta: string) => void;
  onDone?: (payload: { usedTools: string[]; citations: Citation[]; modelName?: string }) => void;
  onError?: (message: string) => void;
}

/** SSE streaming chat via fetch + ReadableStream (EventSource only supports GET). */
export async function sendSupportMessageStream(
  payload: { sessionId: string; message: string },
  handlers: StreamHandlers
): Promise<void> {
  let response: Response;
  try {
    response = await fetch(`${SUPPORT_BASE}/chat/stream`, {
      method: 'POST',
      headers: headers(),
      body: JSON.stringify(payload)
    });
  } catch {
    handlers.onError?.('无法连接客服服务');
    return;
  }
  if (response.status === 401) {
    clearToken();
    if (window.location.pathname !== '/login') {
      window.location.href = '/login';
    }
    handlers.onError?.('登录已过期');
    return;
  }
  if (!response.ok || !response.body) {
    handlers.onError?.(`客服请求失败（${response.status}）`);
    return;
  }
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let eventName = 'message';

  function dispatch(dataLine: string) {
    const data = dataLine.trim();
    if (!data) {
      return;
    }
    if (eventName === 'status') {
      handlers.onStatus?.(data);
    } else if (eventName === 'tool') {
      handlers.onTool?.(data);
    } else if (eventName === 'answer') {
      handlers.onAnswer?.(data);
    } else if (eventName === 'done') {
      try {
        const parsed = JSON.parse(data) as {
          usedTools: string[];
          citations: Citation[];
          modelName?: string;
        };
        handlers.onDone?.(parsed);
      } catch {
        handlers.onError?.('客服返回数据格式异常');
      }
    } else if (eventName === 'error') {
      handlers.onError?.(data);
    }
  }

  try {
    // eslint-disable-next-line no-constant-condition
    while (true) {
      const { done, value } = await reader.read();
      if (done) {
        break;
      }
      buffer += decoder.decode(value, { stream: true });
      let boundary = buffer.indexOf('\n\n');
      while (boundary >= 0) {
        const block = buffer.slice(0, boundary);
        buffer = buffer.slice(boundary + 2);
        for (const line of block.split('\n')) {
          if (line.startsWith('event:')) {
            eventName = line.slice(6).trim();
          } else if (line.startsWith('data:')) {
            dispatch(line.slice(5));
          }
        }
        boundary = buffer.indexOf('\n\n');
      }
    }
  } catch {
    handlers.onError?.('客服连接中断');
  }
}
