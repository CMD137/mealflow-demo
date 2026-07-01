import { http } from './http';
import type { ConsumerRecordView, DeliveryView, MessageView } from '@/types/api';

export function notifyMessagesApi() {
  return http.get<unknown, MessageView[]>('/notify/messages');
}

export function notifyDeliveriesApi() {
  return http.get<unknown, DeliveryView[]>('/notify/deliveries');
}

export function notifyConsumerRecordsApi() {
  return http.get<unknown, ConsumerRecordView[]>('/notify/internal/consumer-records');
}

export function recoverNotifyConsumerRecordsApi() {
  return http.post<unknown, number>('/notify/internal/consumer-records/recover', {});
}

export function replayNotifyConsumerRecordApi(eventKey: string, consumerGroup: string) {
  return http.post<unknown, MessageView>(
    `/notify/internal/consumer-records/${encodeURIComponent(eventKey)}/groups/${encodeURIComponent(consumerGroup)}/replay`,
    {}
  );
}
