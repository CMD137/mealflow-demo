import { http } from './http';
import type { PageResult, VoucherView } from '@/types/api';

export function vouchersApi(params?: { page?: number; pageSize?: number }) {
  return http.get<unknown, PageResult<VoucherView>>('/vouchers/admin', { params });
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
