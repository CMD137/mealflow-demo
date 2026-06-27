# MealFlow 一致性与数据模型专题设计

## 1. 本文解决什么问题

本文用于开发落地，回答：

- 哪些数据必须落表。
- 哪些操作需要幂等。
- 哪些资源需要锁定记录。
- Outbox 如何避免重复发送。
- 消费者如何避免重复消费。
- 订单状态如何合法流转。

核心原则：

```text
交易事实靠数据库。
高并发资格校验靠 Redis。
异步解耦靠 MQ。
最终正确性靠幂等键、状态机、CAS 和补偿任务。
```

## 2. 服务数据所有权

一个表只能由一个服务直接写。

| 表 | 所属服务 | 其他服务如何访问 |
| --- | --- | --- |
| queue_ticket | queue-service | API 或 QueueTicket 事件 |
| capacity_token | queue-service | API 或产能事件 |
| orders | order-service | API 或订单事件 |
| order_item | order-service | API 或订单事件 |
| payment_order | payment-service | API 或支付事件 |
| stock_reservation | catalog-service | API 或库存事件 |
| user_voucher | promotion-service | API 或优惠券事件 |
| voucher_lock | promotion-service | API 或优惠券事件 |
| voucher_claim | promotion-service | API 或优惠券事件 |
| idempotent_request | 各服务本地库 | 只由本服务幂等组件写 |
| local_event | 各服务本地库 | 只由本服务 Outbox 任务写 |
| consumer_record | 各服务本地库 | 只由本服务消费者写 |

示例：

- order-service 不直接写 `queue_ticket`。
- queue-service 不直接写 `orders`。
- fulfillment-service 不直接改 `orders.status`，而是发布履约事件。
- order-service 消费履约事件后按状态机推进订单。

## 3. Outbox 事件表

业务事务中不能直接发 MQ。正确做法是：

```text
本地事务：
  更新业务表
  插入 local_event
  提交事务

Outbox 后台任务：
  抢占待发送事件
  发送 RocketMQ
  标记 SENT
  失败则重试
```

表结构：

```sql
CREATE TABLE local_event (
  event_id BIGINT PRIMARY KEY,
  event_key VARCHAR(128) NOT NULL,
  aggregate_type VARCHAR(64) NOT NULL,
  aggregate_id BIGINT NOT NULL,
  event_type VARCHAR(128) NOT NULL,
  event_version INT NOT NULL DEFAULT 1,
  payload JSON NOT NULL,
  status TINYINT NOT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_time DATETIME DEFAULT NULL,
  locked_by VARCHAR(128) DEFAULT NULL,
  locked_until DATETIME DEFAULT NULL,
  last_error VARCHAR(512) DEFAULT NULL,
  sent_time DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_event_key(event_key),
  KEY idx_status_next_retry_time(status, next_retry_time),
  KEY idx_locked_until(locked_until)
);
```

状态：

```text
NEW
SENDING
SENT
FAILED
DEAD
```

抢占发送租约：

```sql
UPDATE local_event
SET status = 1,
    locked_by = ?,
    locked_until = DATE_ADD(NOW(), INTERVAL 30 SECOND),
    update_time = NOW()
WHERE event_id = ?
  AND status IN (0, 3)
  AND (locked_until IS NULL OR locked_until < NOW());
```

只有更新成功的实例才能发送 MQ。

event_key 示例：

```text
queue:QueueTicketReady:{ticketId}:{version}
order:OrderCreated:{orderId}:1
order:OrderCreatedFromTicket:{ticketId}:1
payment:PaymentSuccess:{paymentOrderId}:1
order:OrderCancelled:{orderId}:{version}
fulfillment:MealReady:{fulfillmentOrderId}:{version}
promotion:VoucherClaimAccepted:{voucherId}:{userId}:1
```

为什么需要 `event_key`：

如果 local_event 已经发到 MQ，但更新 SENT 失败，下一轮扫描会再次发送。新的 MQ messageId 可能不同，但业务 `event_key` 必须相同。

`event_key` 必须全局唯一，不只是生产服务内唯一。推荐前缀包含生产服务名和事件类型，格式为 `{producerService}:{eventType}:{businessKey}:{version}`，避免不同服务碰巧生成同样的业务键后被 `consumer_record` 误判为重复事件。

## 4. Consumer 幂等表

消费者不能只用 MQ `messageId` 去重。

表结构：

```sql
CREATE TABLE consumer_record (
  id BIGINT PRIMARY KEY,
  event_id BIGINT NOT NULL,
  event_key VARCHAR(128) NOT NULL,
  consumer_group VARCHAR(128) NOT NULL,
  status TINYINT NOT NULL,
  locked_by VARCHAR(128) DEFAULT NULL,
  locked_until DATETIME DEFAULT NULL,
  error_msg VARCHAR(512) DEFAULT NULL,
  consume_time DATETIME DEFAULT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_event_consumer(event_id, consumer_group),
  UNIQUE KEY uk_event_key_consumer(event_key, consumer_group),
  KEY idx_consumer_locked_until(consumer_group, status, locked_until)
);
```

消费流程：

```text
解析 event_id/event_key
-> 插入 consumer_record PROCESSING，并写 locked_by/locked_until
-> 插入失败时读取已有记录
-> 已 SUCCESS 则直接返回成功
-> PROCESSING 且未超时则返回成功，等待原消费者完成
-> FAILED/TIMEOUT 或 PROCESSING 超时则抢占消费租约
-> 回查业务状态
-> 执行业务状态机/CAS
-> 标记 consumer_record SUCCESS
```

即使同一业务事件被不同 MQ messageId 投递，也能通过 `event_key` 去重。

FAILED 不能永久阻断补偿。READY 超时补发 `QueueTicketReadyEvent` 时，如果第一次消费已经写入 FAILED，第二次消费必须允许基于同一 `event_key` 重新抢占处理：

```sql
UPDATE consumer_record
SET status = 0,
    locked_by = ?,
    locked_until = DATE_ADD(NOW(), INTERVAL 30 SECOND),
    error_msg = NULL,
    update_time = NOW()
WHERE event_key = ?
  AND consumer_group = ?
  AND status IN (2, 3)
  AND (locked_until IS NULL OR locked_until < NOW());
```

PROCESSING 超时抢占单独处理：

```sql
UPDATE consumer_record
SET locked_by = ?,
    locked_until = DATE_ADD(NOW(), INTERVAL 30 SECOND),
    update_time = NOW()
WHERE event_key = ?
  AND consumer_group = ?
  AND status = 0
  AND locked_until < NOW();
```

PROCESSING 超时不修改状态值，只刷新消费租约。抢占成功后，新的消费者必须重新回查业务状态再处理；如果原消费者只是卡住但业务已经完成，业务状态机会让本次处理成为幂等空操作。

这才是消费侧真正的租约抢占。两个补偿任务实例同时重试时，只有一个能在 `locked_until` 过期窗口内抢到处理权；另一个要么更新 0 行，要么读取到未过期租约后放弃本轮处理。

`consumer_record.status` 数值以 `MealFlow-status-codes.md` 为准：

```text
0 PROCESSING
1 SUCCESS
2 FAILED
3 TIMEOUT
```

## 5. RequestId 幂等

用户提交订单、支付、取消等接口必须支持重复请求。

表结构：

```sql
CREATE TABLE idempotent_request (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  request_id VARCHAR(128) NOT NULL,
  biz_type VARCHAR(64) NOT NULL,
  biz_id BIGINT DEFAULT NULL,
  status TINYINT NOT NULL,
  response_snapshot JSON DEFAULT NULL,
  expire_time DATETIME NOT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_user_request_biz(user_id, request_id, biz_type),
  KEY idx_status_expire(status, expire_time)
);
```

状态：

```text
PROCESSING
SUCCESS
FAILED
EXPIRED
```

提交订单流程：

```text
第一次请求：
  插入 idempotent_request PROCESSING
  执行业务
  保存 response_snapshot
  标记 SUCCESS

重复请求：
  如果 SUCCESS，直接返回 response_snapshot
  如果 PROCESSING，返回处理中或短暂等待后查询
  如果 FAILED，可按业务规则允许重试或返回失败
```

`requestId` 解决用户重复点击和网络重试，不能替代订单表业务唯一键。

## 6. 库存预占模型

排队期间如果不预占库存，用户等到后可能发现商品售罄，体验很差。预占库存需要有可追踪记录。

表结构：

```sql
CREATE TABLE stock_reservation (
  id BIGINT PRIMARY KEY,
  request_id VARCHAR(128) NOT NULL,
  user_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  sku_id BIGINT NOT NULL,
  ticket_id BIGINT DEFAULT NULL,
  order_id BIGINT DEFAULT NULL,
  quantity INT NOT NULL,
  status TINYINT NOT NULL,
  expire_time DATETIME NOT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_request_sku(request_id, sku_id),
  UNIQUE KEY uk_ticket_sku(ticket_id, sku_id),
  UNIQUE KEY uk_order_sku(order_id, sku_id),
  KEY idx_order_id(order_id),
  KEY idx_user_merchant_sku_status(user_id, merchant_id, sku_id, status),
  KEY idx_status_expire(status, expire_time)
);
```

状态：

```text
RESERVED
CONFIRMED
RELEASED
EXPIRED
```

流程：

```text
提交订单/入队时：
  对 userId + merchantId + skuId 加短时分布式锁
  查询是否存在同用户同商户同 SKU 的 RESERVED 记录
  如果存在且属于同 requestId，返回原 reservation
  如果存在且属于已取消/超时 ticket，先释放旧 reservation
  如果存在且仍有效，拒绝重复预占或提示用户先处理上一单
  校验可售库存
  创建 stock_reservation RESERVED
  available_stock - quantity

订单创建后：
  绑定 order_id

支付成功后：
  RESERVED -> CONFIRMED

取消/超时/失败：
  RESERVED -> RELEASED
  available_stock + quantity
```

释放必须在 catalog-service 本地事务中完成，不能把 reservation 状态修改和 SKU 库存回补拆成两个无事务操作。

事务示例：

```sql
START TRANSACTION;

UPDATE stock_reservation
SET status = 2,
    update_time = NOW()
WHERE id = ?
  AND status = 1;

-- 只有上一条 affected_rows = 1 才允许回补库存
UPDATE sku
SET available_stock = available_stock + ?,
    update_time = NOW()
WHERE id = ?;

COMMIT;
```

如果 `stock_reservation` CAS 成功但 `sku` 回补失败，整个事务必须回滚，reservation 仍保持 RESERVED，后续补偿任务可以再次释放。只有事务整体提交成功，才认为库存释放完成。

为什么不能只靠 `uk_ticket_sku`：

同一用户取消旧 ticket 后立刻重新提交，可能在旧 reservation 释放和新 reservation 创建之间出现竞态。`uk_ticket_sku` 只能防止同一 ticket 重复预占，不能防止同一用户同一 SKU 在多个活跃 ticket 上重复预占。因此需要 `request_id` 幂等、用户 SKU 短锁、活跃 reservation 查询三层保护。

`uk_order_sku(order_id, sku_id)` 只保护订单创建后的重复绑定，不保护 ticket 阶段。因为 MySQL 唯一索引允许多行 `NULL`，当 `order_id = NULL` 时，多个 `(NULL, same_sku_id)` 不会冲突。ticket 阶段的保护依赖：

- `uk_request_sku(request_id, sku_id)`：同一请求重试不会重复预占。
- `uk_ticket_sku(ticket_id, sku_id)`：同一 ticket 不会重复预占同一 SKU。
- `idx_user_merchant_sku_status` + 短时分布式锁：同一用户同一商户同一 SKU 不能同时存在多个有效 RESERVED。

## 7. 优惠券锁定模型

只在 `user_voucher` 上改状态不够，需要锁定记录做审计和幂等释放。

表结构：

```sql
CREATE TABLE voucher_lock (
  id BIGINT PRIMARY KEY,
  request_id VARCHAR(128) NOT NULL,
  user_id BIGINT NOT NULL,
  user_voucher_id BIGINT NOT NULL,
  ticket_id BIGINT DEFAULT NULL,
  order_id BIGINT DEFAULT NULL,
  status TINYINT NOT NULL,
  lock_expire_time DATETIME NOT NULL,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_request_voucher(request_id, user_voucher_id),
  UNIQUE KEY uk_ticket_voucher(ticket_id, user_voucher_id),
  UNIQUE KEY uk_order_voucher(order_id, user_voucher_id),
  KEY idx_ticket_id(ticket_id),
  KEY idx_user_voucher(user_voucher_id),
  KEY idx_status_expire(status, lock_expire_time)
);
```

状态：

```text
LOCKED
CONFIRMED
RELEASED
EXPIRED
```

用户券状态：

```text
AVAILABLE -> LOCKED -> USED
AVAILABLE -> LOCKED -> RELEASED -> AVAILABLE
AVAILABLE -> LOCKED -> EXPIRED
```

流程：

```text
Try:
  user_voucher AVAILABLE -> LOCKED
  创建 voucher_lock LOCKED

Confirm:
  voucher_lock LOCKED -> CONFIRMED
  user_voucher LOCKED -> USED

Cancel:
  voucher_lock LOCKED -> RELEASED
  user_voucher LOCKED -> AVAILABLE
```

重复释放时，只有 voucher_lock CAS 更新成功才允许恢复 user_voucher。

锁定阶段的接口重试幂等由 `uk_request_voucher(request_id, user_voucher_id)` 兜底；确认和释放阶段由 `requestId + voucherLockId` 共同保证幂等。这样 `voucher_lock` 与 `stock_reservation` 的资源锁定策略保持一致。

## 8. 秒杀券一致性

秒杀券分两层：

- Redis 负责高并发资格校验。
- MySQL 负责用户最终券包事实。

内部流水状态：

```text
ACCEPTED      已通过 Redis 资格校验
CLAIMED       已入用户券包
DUPLICATE     重复领取
FAILED        落库失败
COMPENSATING  补偿中
COMPENSATED   已补偿
```

用户展示状态：

```text
PROCESSING
SUCCESS
FAILED
SOLD_OUT
DUPLICATE
EXPIRED
```

### 8.1 推荐实现：Lua + MySQL claim_flow + Outbox

请求线程：

```text
执行 Lua：
  校验库存
  校验用户未领取
  Redis stock - 1
  Redis user set add

Lua 成功后：
  MySQL 本地事务写 voucher_claim ACCEPTED
  MySQL 本地事务写 local_event VoucherClaimAccepted
  返回 PROCESSING/ACCEPTED
```

异步消费者：

```text
消费 VoucherClaimAccepted
-> 根据 uk_user_voucher(user_id, voucher_id) 创建 user_voucher
-> voucher_claim ACCEPTED -> CLAIMED
```

兜底约束：

```sql
ALTER TABLE user_voucher
ADD UNIQUE KEY uk_user_voucher(user_id, voucher_id);
```

这个唯一索引用于兜底一人一券。

### 8.2 Lua 成功但 DB 未写入

风险：

```text
Redis Lua 成功
服务还没写 voucher_claim/local_event 就宕机
```

补偿：

```text
扫描 Redis voucher:user:{voucherId}
-> 对比 voucher_claim
-> 缺失则补写 voucher_claim ACCEPTED 和 local_event
```

如果无法补写：

```text
Redis stock + 1
Redis user set remove
记录 voucher_claim COMPENSATED
```

### 8.3 Redis Stream 备选方案

Lua 内部：

```text
扣库存
记录用户
XADD voucher:claim:stream
```

消费者从 Stream 落库。这个方案 Redis 侧闭环更好，但需要处理 Stream pending list、ack、重试和死信。校招项目可以作为扩展设计。

## 9. 订单表与 QueueTicket 幂等

订单表必须有 `queue_ticket_id`。

```sql
ALTER TABLE orders
ADD COLUMN queue_ticket_id BIGINT DEFAULT NULL,
ADD UNIQUE KEY uk_queue_ticket_id(queue_ticket_id);
```

创建订单逻辑：

```text
收到 QueueTicketReadyEvent
-> 按 queue_ticket_id 查询订单
-> 如果存在，返回已有 orderId
-> 如果不存在，创建 orders 和 order_item
-> 写 OrderCreatedFromTicketEvent
```

原因：

- MQ 会重复投递。
- Outbox 会重复发送。
- READY 超时任务会补发事件。
- 创建订单成功后，回写 queue_ticket 可能失败。

所以必须用业务唯一键兜底，不能只靠消费者 messageId。

## 10. 订单状态机

订单状态：

```text
PENDING_PAYMENT
WAIT_MERCHANT_ACCEPT
MERCHANT_ACCEPTED
COOKING
WAIT_RIDER_PICKUP
DELIVERING
COMPLETED
CANCELLED
AFTER_SALE
```

支付状态独立：

```text
UNPAID
PAYING
PAID
CLOSED
REFUNDING
REFUNDED
```

退款子状态独立：

```text
NONE
REFUND_REQUESTED
REFUNDING
REFUNDED
REFUND_FAILED
```

合法流转表：

| 当前状态 | 事件 | 目标状态 |
| --- | --- | --- |
| PENDING_PAYMENT | PaymentSuccessEvent | WAIT_MERCHANT_ACCEPT |
| PENDING_PAYMENT | PaymentTimeoutEvent | CANCELLED |
| PENDING_PAYMENT | UserCancelEvent | CANCELLED |
| WAIT_MERCHANT_ACCEPT | MerchantAcceptEvent | MERCHANT_ACCEPTED |
| WAIT_MERCHANT_ACCEPT | MerchantRejectEvent | CANCELLED |
| WAIT_MERCHANT_ACCEPT | UserCancelEvent | CANCELLED |
| MERCHANT_ACCEPTED | StartCookingEvent | COOKING |
| MERCHANT_ACCEPTED | MerchantCancelEvent | CANCELLED |
| COOKING | MealReadyEvent | WAIT_RIDER_PICKUP |
| WAIT_RIDER_PICKUP | DeliveryPickedUpEvent | DELIVERING |
| DELIVERING | DeliveryCompletedEvent | COMPLETED |
| COMPLETED | AfterSaleRequestedEvent | AFTER_SALE |
| AFTER_SALE | AfterSaleApprovedEvent | AFTER_SALE，refund_status = REFUND_REQUESTED |
| AFTER_SALE | RefundStartedEvent | AFTER_SALE，refund_status = REFUNDING |
| AFTER_SALE | RefundSuccessEvent | AFTER_SALE，refund_status = REFUNDED |
| AFTER_SALE | RefundFailedEvent | AFTER_SALE，refund_status = REFUND_FAILED |
| AFTER_SALE | AfterSaleRejectedEvent | COMPLETED，refund_status = NONE |

取消后的退款规则：

| 当前状态 | 事件 | 目标状态 | 副作用 |
| --- | --- | --- | --- |
| WAIT_MERCHANT_ACCEPT | UserCancelEvent 且已支付 | CANCELLED | 写 RefundRequestedEvent，refund_status = REFUND_REQUESTED |
| MERCHANT_ACCEPTED | MerchantCancelEvent 且已支付 | CANCELLED | 写 RefundRequestedEvent，refund_status = REFUND_REQUESTED |
| CANCELLED | RefundStartedEvent | CANCELLED，refund_status = REFUNDING | 无 |
| CANCELLED | RefundSuccessEvent | CANCELLED，refund_status = REFUNDED | 通知用户 |
| CANCELLED | RefundFailedEvent | CANCELLED，refund_status = REFUND_FAILED | 进入人工补偿 |

说明：

- `orders.status` 表示订单业务阶段。
- `payment_order.status` 表示支付单状态。
- `orders.refund_status` 或售后单表状态表示退款进度。
- `CANCELLED` 不代表钱已经退回，只代表订单业务关闭。

状态更新：

```sql
UPDATE orders
SET status = ?,
    version = version + 1,
    update_time = NOW()
WHERE id = ?
  AND status = ?
  AND version = ?;
```

Redisson 锁只降低并发竞争，最终正确性靠 DB CAS 和合法流转表。

## 11. Payment 与 Order 的边界

`payment_order.status` 表示支付通道状态。

`orders.status` 表示订单业务状态。

支付成功流程：

```text
payment-service 收到回调
-> payment_order PAYING -> PAID
-> 写 PaymentSuccessEvent
-> order-service 消费事件
-> orders PENDING_PAYMENT -> WAIT_MERCHANT_ACCEPT
```

退款流程：

```text
order-service 审核售后
-> 写 RefundRequestedEvent
-> payment-service 创建 refund_order
-> 退款开始后写 RefundStartedEvent
-> 退款成功后写 RefundSuccessEvent
-> order-service 更新 refund_status
```

不要让 payment-service 直接写 `orders.status`。

## 12. Fulfillment 与 Order 的边界

商户接单、拒单、出餐、骑手取餐、送达都属于 fulfillment-service。

fulfillment-service：

```text
接收商户/骑手操作
更新 fulfillment_order / delivery_order
发布履约事件
```

order-service：

```text
消费履约事件
按订单状态机流转 orders.status
写 order_status_log
```

典型事件：

```text
MerchantAcceptedEvent
MerchantRejectedEvent
MealReadyEvent
DeliveryPickedUpEvent
DeliveryCompletedEvent
```

## 13. 资源锁定统一思想

项目里有三类可释放资源：

| 资源 | 记录表 | 释放条件 |
| --- | --- | --- |
| 商户产能 | capacity_token | 拒单、取消、支付超时、出餐完成、异常关闭 |
| 商品库存 | stock_reservation | 取消、支付超时、创建失败 |
| 用户优惠券 | voucher_lock | 取消、支付超时、创建失败 |

统一规则：

- 资源占用必须落表。
- 释放必须 CAS。
- 只有 CAS 成功才执行副作用。
- 定时任务扫描过期资源。
- 补偿任务只相信状态表，不相信 MQ 消息本身。

## 14. 并发控制策略汇总

不同表的并发保护机制不完全相同，这是有意设计，不是遗漏。

| 对象 | 并发策略 | 原因 |
| --- | --- | --- |
| queue_ticket | version 乐观锁 + 状态条件 | 多状态流转，存在 WAITING/READY/PROCESSING/FAILED 等回退和补偿 |
| orders | version 乐观锁 + 合法状态流转表 | 订单状态复杂，必须防止非法跳转和并发覆盖 |
| local_event | locked_by + locked_until | Outbox 扫描是长任务，多实例需要抢发送租约 |
| consumer_record | locked_by + locked_until | 消费可能失败或超时，需要同一 eventKey 可被补偿重试 |
| capacity_token | request_id 创建幂等 + status 单向 CAS | HELD 创建靠 request_id 去重，释放靠 CAS；直接下单孤儿 token 由补偿任务扫描 |
| stock_reservation | status 单向 CAS + 本地事务 | RESERVED -> RELEASED/CONFIRMED 是单向动作，库存回补必须同事务 |
| voucher_lock | uk_request_voucher 创建幂等 + status 单向 CAS | LOCKED 创建靠唯一键去重，释放靠 CAS |
| idempotent_request | requestId 唯一键 | 防接口重复提交，返回响应快照 |

`capacity_token` 不加 version 的原因：

```text
HELD 只能流转到 RELEASED 或 EXPIRED
RELEASED/EXPIRED 不允许回到 HELD
```

所以释放时使用：

```sql
UPDATE capacity_token
SET status = 2,
    release_reason = ?,
    update_time = NOW()
WHERE id = ?
  AND status = 1;
```

这足以防止重复释放。第二次释放会更新 0 行，不能继续扣 Redis inflight，也不能放行下一个 ticket。

如果未来要支持“产能 token 续租、撤销释放、重新激活”等非单向能力，再给 `capacity_token` 增加 `version`。

## 15. 开发验收清单

- Outbox 多实例扫描不会重复抢占同一事件。
- 同一 event_key 被重复投递，消费者只处理一次。
- requestId 重复提交返回同一业务结果。
- 库存预占能确认、释放、过期。
- 优惠券锁定能确认、释放、过期。
- Lua 成功但 DB 未写入时能补偿。
- QueueTicketReadyEvent 重复投递不会重复创建订单。
- payment-service 不直接写 orders。
- fulfillment-service 不直接写 orders。
- order-service 不直接写 queue_ticket。
- Redisson 锁失效时，DB CAS 仍能保护状态正确。
