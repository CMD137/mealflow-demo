import { http } from './http';
import type { SeckillVoucherResponse, UserVoucherView, VoucherView } from '@/types/api';

export function vouchersApi() {
  return http.get<unknown, VoucherView[]>('/vouchers/admin');
}

export function walletApi() {
  return http.get<unknown, UserVoucherView[]>('/vouchers/wallet');
}

export function claimVoucherApi(voucherId: number) {
  return http.post<unknown, SeckillVoucherResponse>(`/vouchers/${voucherId}/seckill`, {
    requestId: `h5-voucher-${Date.now()}-${Math.random().toString(16).slice(2)}`
  });
}
