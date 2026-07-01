import { http } from './http';
import type { VoucherView } from '@/types/api';

export function vouchersApi() {
  return http.get<unknown, VoucherView[]>('/vouchers/admin');
}

export function saveVoucherApi(
  payload: { name: string; type: string; discountCent: number; stock: number; status: string },
  voucherId?: number
) {
  if (voucherId) {
    return http.put<unknown, VoucherView>(`/vouchers/admin/${voucherId}`, payload);
  }
  return http.post<unknown, VoucherView>('/vouchers/admin', payload);
}

export function walletApi() {
  return http.get<unknown, unknown[]>('/vouchers/wallet');
}
