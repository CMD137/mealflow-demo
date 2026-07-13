import { http } from './http';
import type { VoucherView } from '@/types/api';

export function vouchersApi() {
  return http.get<unknown, VoucherView[]>('/vouchers/admin');
}

export function saveVoucherApi(
  payload: { name: string; type: string; discountCent: number; stock: number; status: string },
  voucherId?: number
) {
  const seckillPayload = { ...payload, type: 'SECKILL' };
  if (voucherId) {
    return http.put<unknown, VoucherView>(`/vouchers/admin/${voucherId}`, seckillPayload);
  }
  return http.post<unknown, VoucherView>('/vouchers/admin', seckillPayload);
}

export function walletApi() {
  return http.get<unknown, unknown[]>('/vouchers/wallet');
}
