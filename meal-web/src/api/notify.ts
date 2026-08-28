import { http } from './http';
import type { ConsumerRecordView, DeliveryView, MessageView } from '@/types/api';

// 内部运维接口：需要 INTERNAL_OPERATE 权限（仅事件运维角色可访问）
export function notifyMessagesApi() {
  return http.get<unknown, MessageView[]>('/notify/internal/messages');
}

export function notifyDeliveriesApi() {
  return http.get<unknown, DeliveryView[]>('/notify/internal/deliveries');
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

// 用户级接口：普通登录用户（含商家管理员）查看自己的通知与投递
export function myNotifyMessagesApi() {
  return http.get<unknown, MessageView[]>('/notify/messages');
}

export function merchantNotifyMessagesApi() {
  return http.get<unknown, MessageView[]>('/notify/merchant/messages');
}

export function myNotifyDeliveriesApi() {
  return http.get<unknown, DeliveryView[]>('/notify/deliveries');
}
