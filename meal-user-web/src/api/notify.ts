import { http } from './http';
import type { MessageView } from '@/types/api';

export function messagesApi() {
  return http.get<unknown, MessageView[]>('/notify/messages');
}
