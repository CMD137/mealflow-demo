import { http } from './http';
import type { SeckillVoucherResponse, UserVoucherView, VoucherView } from '@/types/api';

export function vouchersApi(merchantId?: number) {
  return http.get<unknown, VoucherView[]>('/vouchers', { params: merchantId ? { merchantId } : undefined });
}

export function walletApi(merchantId?: number) {
  return http.get<unknown, UserVoucherView[]>('/vouchers/wallet', { params: merchantId ? { merchantId } : undefined });
}

export function claimVoucherApi(voucherId: number, merchantId?: number) {
  return http.post<unknown, SeckillVoucherResponse>(`/vouchers/${voucherId}/seckill`, {
    requestId: `h5-voucher-${Date.now()}-${Math.random().toString(16).slice(2)}`
  }, { params: merchantId ? { merchantId } : undefined });
}

export function claimStatusApi(voucherId: number) {
  return http.get<unknown, SeckillVoucherResponse>(`/vouchers/${voucherId}/claims/me`);
}
