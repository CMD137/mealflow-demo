import { http } from './http';
import type { OrderStatisticsView, OrderView, PageResult } from '@/types/api';

export function adminOrdersApi(params: {
  merchantId?: number;
  userId?: number;
  status?: string;
  page?: number;
  pageSize?: number;
}) {
  return http.get<unknown, PageResult<OrderView>>('/orders/admin', { params });
}

export function orderStatisticsApi(params: { merchantId?: number }) {
  return http.get<unknown, OrderStatisticsView>('/orders/admin/statistics', { params });
}

export function merchantCancelOrderApi(orderId: number, reason: string) {
  return http.post<unknown, void>(`/orders/admin/${orderId}/cancel`, {
    requestId: `merchant-cancel-${orderId}-${Date.now()}`,
    reason
  });
}
