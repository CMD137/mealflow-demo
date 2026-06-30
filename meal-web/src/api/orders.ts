import { http } from './http';
import type { OrderStatisticsView, OrderView } from '@/types/api';

export function adminOrdersApi(params: { merchantId?: number; userId?: number; status?: string }) {
  return http.get<unknown, OrderView[]>('/orders/admin', { params });
}

export function orderStatisticsApi(params: { merchantId?: number }) {
  return http.get<unknown, OrderStatisticsView>('/orders/admin/statistics', { params });
}

export function orderDetailApi(orderId: number) {
  return http.get<unknown, OrderView>(`/orders/${orderId}`);
}

export function cancelOrderApi(orderId: number, reason: string) {
  return http.post<unknown, OrderView>(`/orders/${orderId}/cancel`, { reason });
}
