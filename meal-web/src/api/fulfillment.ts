import { http } from './http';

export function acceptOrderApi(orderId: number) {
  return http.post<unknown, unknown>(`/fulfillment/orders/${orderId}/accept`, {});
}

export function mealReadyApi(orderId: number) {
  return http.post<unknown, unknown>(`/fulfillment/orders/${orderId}/meal-ready`, {});
}

export function pickedUpApi(orderId: number) {
  return http.post<unknown, unknown>(`/fulfillment/orders/${orderId}/picked-up`, {});
}

export function deliveredApi(orderId: number) {
  return http.post<unknown, unknown>(`/fulfillment/orders/${orderId}/delivered`, {});
}

export function fulfillmentOperationsApi() {
  return http.get<unknown, unknown[]>('/fulfillment/orders/internal/operations');
}
