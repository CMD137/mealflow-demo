import { http } from './http';
import type { MerchantView } from '@/types/api';

export function merchantsApi() {
  return http.get<unknown, MerchantView[]>('/merchants');
}

export function merchantApi(merchantId: number) {
  return http.get<unknown, MerchantView>(`/merchants/${merchantId}`);
}
