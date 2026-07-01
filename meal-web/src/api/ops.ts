import { http } from './http';
import type { LocalEventView } from '@/types/api';

export type EventDomain = 'orders' | 'payments' | 'fulfillment';

const endpoints: Record<EventDomain, { list: string; dispatch: string }> = {
  orders: { list: '/orders/internal/events', dispatch: '/orders/internal/events/dispatch' },
  payments: { list: '/payments/internal/events', dispatch: '/payments/internal/events/dispatch' },
  fulfillment: { list: '/fulfillment/orders/internal/events', dispatch: '/fulfillment/orders/internal/events/dispatch' }
};

export function localEventsApi(domain: EventDomain) {
  return http.get<unknown, LocalEventView[]>(endpoints[domain].list);
}

export function dispatchLocalEventsApi(domain: EventDomain) {
  return http.post<unknown, number>(endpoints[domain].dispatch, {});
}
