import { http } from './http';
import type { OrderStatisticsView, OrderView, SubmitOrderRequest, SubmitOrderResponse } from '@/types/api';

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
  return http.post<unknown, void>(`/orders/${orderId}/cancel`, { reason });
}

export function submitOrderApi(payload: SubmitOrderRequest) {
  return http.post<unknown, SubmitOrderResponse>('/orders/submit', payload);
}
