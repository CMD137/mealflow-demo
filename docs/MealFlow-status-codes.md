# MealFlow 状态码定义

本文定义所有 `TINYINT status` 的数值映射。开发、DDL、补偿 SQL、测试用例必须以本文为准。

## 1. QueueTicketStatus

适用表：`queue_ticket.status`

| 数值 | 枚举 | 含义 |
| --- | --- | --- |
| 1 | WAITING | 等待中 |
| 2 | READY | 已获得处理资格 |
| 3 | PROCESSING | 正在创建订单 |
| 4 | ORDER_CREATED | 订单已创建 |
| 5 | CANCELLED | 已取消 |
| 6 | TIMEOUT | 排队超时 |
| 7 | FAILED | 创建失败 |

## 2. CapacityTokenStatus

适用表：`capacity_token.status`

| 数值 | 枚举 | 含义 |
| --- | --- | --- |
| 1 | HELD | 正在占用商户产能 |
| 2 | RELEASED | 已释放 |
| 3 | EXPIRED | 已过期释放 |

## 3. LocalEventStatus

适用表：`local_event.status`

| 数值 | 枚举 | 含义 |
| --- | --- | --- |
| 0 | NEW | 待发送 |
| 1 | SENDING | 发送中 |
| 2 | SENT | 已发送 |
| 3 | FAILED | 发送失败，可重试 |
| 4 | DEAD | 死信，不再自动重试 |

## 4. ConsumerRecordStatus

适用表：`consumer_record.status`

| 数值 | 枚举 | 含义 |
| --- | --- | --- |
| 0 | PROCESSING | 消费中 |
| 1 | SUCCESS | 消费成功 |
| 2 | FAILED | 消费失败，可重试 |
| 3 | TIMEOUT | 消费超时，可重试 |

## 5. IdempotentRequestStatus

适用表：`idempotent_request.status`

| 数值 | 枚举 | 含义 |
| --- | --- | --- |
| 0 | PROCESSING | 请求处理中 |
| 1 | SUCCESS | 已成功，可返回响应快照 |
| 2 | FAILED | 处理失败 |
| 3 | EXPIRED | 幂等记录过期 |

## 6. StockReservationStatus

适用表：`stock_reservation.status`

| 数值 | 枚举 | 含义 |
| --- | --- | --- |
| 1 | RESERVED | 已预占 |
| 2 | RELEASED | 已释放 |
| 3 | CONFIRMED | 已确认扣减 |
| 4 | EXPIRED | 已过期 |

说明：释放 SQL 使用 `RESERVED(1) -> RELEASED(2)`；支付成功确认使用 `RESERVED(1) -> CONFIRMED(3)`。

## 7. VoucherLockStatus

适用表：`voucher_lock.status`

| 数值 | 枚举 | 含义 |
| --- | --- | --- |
| 1 | LOCKED | 已锁定 |
| 2 | RELEASED | 已释放 |
| 3 | CONFIRMED | 已确认使用 |
| 4 | EXPIRED | 锁定过期 |

## 8. VoucherClaimStatus

适用表：`voucher_claim.status`

| 数值 | 枚举 | 含义 |
| --- | --- | --- |
| 1 | ACCEPTED | 已通过 Redis 资格校验 |
| 2 | CLAIMED | 已入用户券包 |
| 3 | DUPLICATE | 重复领取 |
| 4 | FAILED | 落库失败 |
| 5 | COMPENSATING | 补偿中 |
| 6 | COMPENSATED | 已补偿 |

## 9. OrderStatus

适用表：`orders.status`

| 数值 | 枚举 | 含义 |
| --- | --- | --- |
| 1 | PENDING_PAYMENT | 待支付 |
| 2 | WAIT_MERCHANT_ACCEPT | 待商户接单 |
| 3 | MERCHANT_ACCEPTED | 商户已接单 |
| 4 | COOKING | 制作中 |
| 5 | WAIT_RIDER_PICKUP | 待骑手取餐 |
| 6 | DELIVERING | 配送中 |
| 7 | COMPLETED | 已完成 |
| 8 | CANCELLED | 已取消 |
| 9 | AFTER_SALE | 售后中 |

## 10. PaymentStatus

适用表：`payment_order.status`

| 数值 | 枚举 | 含义 |
| --- | --- | --- |
| 0 | UNPAID | 未支付 |
| 1 | PAYING | 支付中 |
| 2 | PAID | 已支付 |
| 3 | CLOSED | 已关闭 |
| 4 | REFUNDING | 退款中 |
| 5 | REFUNDED | 已退款 |

## 11. RefundStatus

适用字段：`orders.refund_status` 或售后单退款状态字段

| 数值 | 枚举 | 含义 |
| --- | --- | --- |
| 0 | NONE | 无退款 |
| 1 | REFUND_REQUESTED | 已请求退款 |
| 2 | REFUNDING | 退款中 |
| 3 | REFUNDED | 已退款 |
| 4 | REFUND_FAILED | 退款失败 |
