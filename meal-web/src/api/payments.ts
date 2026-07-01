import { http } from './http';
import type { PaymentView } from '@/types/api';

export function mockPayApi(payOrderId: number) {
  return http.post<unknown, PaymentView>(`/payments/${payOrderId}/mock-pay`, {});
}

export function paymentApi(payOrderId: number) {
  return http.get<unknown, PaymentView>(`/payments/${payOrderId}`);
}
