import { http } from './http';
import type { OrderView, SubmitOrderRequest, SubmitOrderResponse } from '@/types/api';

export function submitOrderApi(payload: SubmitOrderRequest) {
  return http.post<unknown, SubmitOrderResponse>('/orders/submit', payload);
}

export function ordersApi() {
  return http.get<unknown, OrderView[]>('/orders');
}

export function orderApi(orderId: number) {
  return http.get<unknown, OrderView>(`/orders/${orderId}`);
}

export function cancelOrderApi(orderId: number, reason: string) {
  return http.post<unknown, void>(`/orders/${orderId}/cancel`, {
    requestId: `h5-cancel-${Date.now()}-${Math.random().toString(16).slice(2)}`,
    reason
  });
}
