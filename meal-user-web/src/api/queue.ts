import { http } from './http';
import type { QueueTicketView } from '@/types/api';

export function activeQueueTicketApi() {
  return http.get<unknown, QueueTicketView | null>('/queue/tickets/active');
}

export function queueTicketHistoryApi(limit = 10) {
  return http.get<unknown, QueueTicketView[]>(`/queue/tickets/history?limit=${limit}`);
}

export function queueTicketApi(ticketId: number) {
  return http.get<unknown, QueueTicketView>(`/queue/tickets/${ticketId}`);
}

export function cancelQueueTicketApi(ticketId: number) {
  return http.post<unknown, void>(`/queue/tickets/${ticketId}/cancel`, {});
}
