import { http } from '@/api/http';
import type { MerchantView, OrderView, PageResult, SystemUserView } from '@/types/api';

export function systemMerchantsApi(params: { page?: number; pageSize?: number; name?: string; status?: string }) {
  return http.get<unknown, PageResult<MerchantView>>('/merchants/system', { params });
}

export function updateSystemMerchantStatusApi(merchantId: number, businessStatus: string) {
  return http.put<unknown, MerchantView>(`/merchants/system/${merchantId}/business-status`, { businessStatus });
}

export function systemUsersApi(params: { page?: number; pageSize?: number; phone?: string; status?: string }) {
  return http.get<unknown, PageResult<SystemUserView>>('/auth/system/users', { params });
}

export function updateSystemUserStatusApi(userId: number, status: string) {
  return http.put<unknown, SystemUserView>(`/auth/system/users/${userId}/status`, { status });
}

export function systemOrdersApi(params: {
  page?: number; pageSize?: number; merchantId?: number; userId?: number; status?: string; from?: string; to?: string;
}) {
  return http.get<unknown, PageResult<OrderView>>('/orders/system', { params });
}
