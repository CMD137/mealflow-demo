import { http } from './http';
import type { MerchantView } from '@/types/api';

export function merchantsApi() {
  return http.get<unknown, MerchantView[]>('/merchants');
}

export function merchantApi(merchantId: number) {
  return http.get<unknown, MerchantView>(`/merchants/${merchantId}`);
}

export function updateCapacityApi(merchantId: number, baseCapacity: number, manualFactor: number) {
  return http.post<unknown, MerchantView>(`/merchants/${merchantId}/capacity`, { baseCapacity, manualFactor });
}

export function updateBusinessStatusApi(merchantId: number, businessStatus: string) {
  return http.post<unknown, MerchantView>(`/merchants/${merchantId}/business-status`, { businessStatus });
}
