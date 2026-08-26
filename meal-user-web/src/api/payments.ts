import { http } from './http';
import type { PaymentCheckoutView, PaymentView } from '@/types/api';

export function paymentApi(payOrderId: number) {
  return http.get<unknown, PaymentView>(`/payments/${payOrderId}`);
}

export function checkoutApi(payOrderId: number) {
  return http.post<unknown, PaymentCheckoutView>(`/payments/${payOrderId}/checkout`, {});
}
