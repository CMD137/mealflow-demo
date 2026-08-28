import { http } from './http';

function newRequestId(prefix: string) {
  return `${prefix}-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export function acceptOrderApi(orderId: number) {
  return http.post<unknown, unknown>(`/fulfillment/orders/${orderId}/accept`, { requestId: newRequestId('accept') });
}

export function mealReadyApi(orderId: number) {
  return http.post<unknown, unknown>(`/fulfillment/orders/${orderId}/meal-ready`, { requestId: newRequestId('meal-ready') });
}

export function pickedUpApi(orderId: number) {
  return http.post<unknown, unknown>(`/fulfillment/orders/${orderId}/picked-up`, { requestId: newRequestId('picked-up') });
}

export function deliveredApi(orderId: number) {
  return http.post<unknown, unknown>(`/fulfillment/orders/${orderId}/delivered`, { requestId: newRequestId('delivered') });
}

export function fulfillmentOperationsApi() {
  return http.get<unknown, unknown[]>('/fulfillment/orders/internal/operations');
}
