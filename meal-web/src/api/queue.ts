import { http } from './http';
import type { CapacityTokenView, QueueTicketView } from '@/types/api';

export function queueMetricsApi(merchantId: number) {
  return http.get<unknown, Record<string, unknown>>(`/queue/merchants/${merchantId}/metrics`);
}

export function setQueueLimitApi(merchantId: number, limit: number) {
  return http.post<unknown, unknown>(`/queue/merchants/${merchantId}/limit`, { limit });
}

export function queueTicketsApi() {
  return http.get<unknown, QueueTicketView[]>('/queue/internal/tickets');
}

export function capacityTokensApi() {
  return http.get<unknown, CapacityTokenView[]>('/queue/internal/capacity/tokens');
}
