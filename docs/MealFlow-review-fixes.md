# MealFlow 设计问题修正对照表

本文逐条回应架构评审中提出的问题，方便后续审阅和开发确认。

## 1. 等待时间公式错误

已修正。

校招实现版：

```text
estimatedWaitSeconds = ceil(aheadCount / dynamicLimit) * avgPrepareSeconds
```

真实指标版：

```text
releaseRate = recentCompletedCount / recentWindowSeconds
estimatedWaitSeconds = aheadCount / max(releaseRate, minReleaseRate)
```

说明：`releaseRate` 是单/秒，`aheadCount / releaseRate` 已经是秒，不能再乘 `avgPrepareSeconds`。

详见 `MealFlow-queue-design.md` 第 6 节。

## 2. 动态产能计算不一致

已修正。

```text
dynamicLimit = max(1, floor(
  baseCapacity
  * merchantHealthFactor
  * riderSupplyFactor
  * manualFactor
  * activityFactor
))
```

`pending_order_count` 不直接进入乘法公式，而是用于计算 `merchantHealthFactor`。

## 3. inflight 泄漏和重复释放

已修正。

新增 `capacity_token` 作为商户产能占用事实，Redis `inflight` 只是派生计数。

释放必须先 CAS 更新 `capacity_token`，只有更新成功才允许 Redis `inflight - 1` 和放行下一个 ticket。

## 4. 商户接单释放产能时机错误

已修正。

商户接单不释放厨房产能。释放时机改为：

- 商户拒单。
- 用户取消。
- 支付超时。
- 出餐完成。
- 订单异常关闭。

## 5. QueueTicket PROCESSING 卡死

已修正。

`queue_ticket` 增加：

```text
ready_time
processing_time
retry_count
last_error
```

补偿任务：

- READY 超过 30 秒且无 order_id，重发 `QueueTicketReadyEvent`。
- PROCESSING 超过 60 秒且无 order_id，查询 order-service，存在订单则补写状态，不存在则退回 READY 或 FAILED。

## 6. QueueTicket 转订单缺少唯一约束

已修正。

订单表要求：

```sql
ALTER TABLE orders
ADD COLUMN queue_ticket_id BIGINT DEFAULT NULL,
ADD UNIQUE KEY uk_queue_ticket_id(queue_ticket_id);
```

创建订单前先按 `queue_ticket_id` 查询已有订单。

## 7. consumer_record 使用 message_id 不够

已修正。

消费者幂等改用：

```text
event_id
event_key
consumer_group
```

而不是 MQ messageId。

## 8. Outbox 缺少 event_key 和发送锁

已修正。

`local_event` 增加：

```text
event_key
event_version
locked_by
locked_until
last_error
sent_time
```

Outbox 扫描任务必须先抢占发送租约。

## 9. order-service 与 fulfillment-service 职责冲突

已修正。

商户接单、拒单、出餐完成、骑手操作统一归 fulfillment-service。

order-service 只维护交易订单状态，消费履约事件后推进订单状态机。

## 10. order-service 直接更新 queue_ticket 违反数据所有权

已修正。

`queue_ticket` 只由 queue-service 写。

order-service 创建订单成功后发布 `OrderCreatedFromTicketEvent`，由 queue-service 消费后更新 ticket 为 `ORDER_CREATED`。

## 11. capacity_config 归属不清

已修正。

- merchant-service 拥有基础产能配置和商户手动降载。
- queue-service 拥有实时排队、`capacity_token`、动态产能计算结果。
- queue-service 通过 API、事件或 Redis 缓存读取配置，不直接写 merchant-service 表。

## 12. Payment 状态与 Order 状态混淆

已修正。

订单状态去掉独立 `PAID`，支付成功后订单从 `PENDING_PAYMENT` 进入 `WAIT_MERCHANT_ACCEPT`。

支付状态保存在 `payment_order.status`。

## 13. 秒杀券 Outbox 边界不清

已修正。

说明了 Redis Lua 与 MySQL Outbox 不是天然事务一致，并给出两种方案：

- Redis Stream 承接秒杀结果。
- Lua 成功后同步写 `voucher_claim + local_event`。

本项目推荐第二种，并增加 Redis 领取集合与 `voucher_claim` 对账补偿。

## 14. 库存预占缺少 reservation 表

已修正。

新增 `stock_reservation`，状态：

```text
RESERVED
CONFIRMED
RELEASED
EXPIRED
```

## 15. 优惠券锁定缺少记录

已修正。

新增 `voucher_lock`，状态：

```text
LOCKED
CONFIRMED
RELEASED
EXPIRED
```

## 16. requestId 幂等未落表

已修正。

新增 `idempotent_request` 表，用于提交订单、支付、取消等接口幂等。

## 17. QueueTicket READY 后取消语义缺失

已修正。

取消语义按阶段区分：

- WAITING：取消排队。
- READY：取消处理资格。
- PROCESSING：取消处理中资格或转订单取消。
- PENDING_PAYMENT：取消订单。

## 18. 订单状态机缺少合法流转表

已修正。

新增订单合法流转表，明确每个事件只能触发固定状态转换。

## 19. Redisson 锁不能作为唯一正确性保障

已修正。

明确 Redisson 只降低并发竞争，最终正确性靠：

```text
DB 条件更新
version 乐观锁
合法状态流转表
```

## 20. ZSet score 同分问题

已修正。

ZSet member 使用趋势递增 `ticketNo`，同分情况下顺序稳定。

## 21. 优先级导致普通用户饥饿

已修正。

增加：

- `priorityWeight` 上限。
- 等待老化机制。
- 后台人工调整审计。

## 22. 一人一单表述错误

已修正。

`uk_user_voucher(user_id, voucher_id)` 用于兜底一人一券。

## 23. 秒杀状态命名重复

已修正。

内部流水状态：

```text
ACCEPTED
CLAIMED
DUPLICATE
FAILED
COMPENSATING
COMPENSATED
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

## 后续还需要继续细化的内容

本轮修正解决了核心建模问题。下一轮建议继续拆：

- `MealFlow-api-events.md`：已产出，覆盖服务接口、事件 schema、event_key 规则、消费方矩阵。
- `MealFlow-ddl.sql`：已产出，覆盖核心表结构草案。
- `MealFlow-migration-test-plan.md`：已产出，覆盖迁移路线、压测、故障注入和验收。
- 后续可继续补 `MealFlow-service-module-plan.md`，细化 Java 包结构、Maven 模块和 Spring Cloud 服务骨架。

## 第二轮评审修正

### 1. queue_ticket 表结构跨文档不一致

已修正。

总览文档补齐：

```text
UNIQUE KEY uk_ticket_no(ticket_no)
KEY idx_status_ready_time(status, ready_time)
KEY idx_status_processing_time(status, processing_time)
```

同时新增 `MealFlow-ddl.sql` 作为表结构第一参考。

### 2. MerchantCapacityReleaseRequestedEvent 缺失

已修正。

`MealFlow-api-events.md` 事件清单新增：

```text
MerchantCapacityReleaseRequestedEvent
eventKey = {producerService}:MerchantCapacityReleaseRequested:{capacityTokenId}:{reason}:{eventVersion}
```

### 3. Payment 状态枚举不一致

已修正。

统一为：

```text
UNPAID
PAYING
PAID
CLOSED
REFUNDING
REFUNDED
```

### 4. local_event 缺少 idx_locked_until

已修正。

总览文档和 DDL 均包含：

```text
KEY idx_locked_until(locked_until)
```

### 5. PROCESSING 取消时 capacity_token 释放不清

已修正。

明确 `capacity_token` 始终归 queue-service 管理，不会转移给 order-service。PROCESSING 阶段若订单未创建，由 queue-service 释放 token；若订单已创建，则走订单取消链路释放。

### 6. 内部服务接口缺少幂等键

已修正。

以下接口请求体补充 `requestId`：

```text
POST /internal/orders/from-ticket
POST /internal/vouchers/lock
POST /internal/stocks/reserve
```

### 7. consumer_record event_key 全局唯一约束不清

已修正。

明确 `event_key` 必须全局唯一，推荐格式：

```text
{service}:{eventType}:{businessKey}:{version}
```

### 8. stock_reservation 多 ticket 重复预占风险

已修正。

`stock_reservation` 增加 `request_id` 和 `uk_request_sku`，并要求预占时对 `userId + merchantId + skuId` 加短时锁，查询同用户同 SKU 活跃预占。

### 9. 排队老化权重单位错误

已修正。

老化权重改为毫秒单位：

```text
agingWeightMillis = min(waitSeconds * 1000, agingMaxWeightMillis)
```

### 10. READY 补偿重发与 consumer_record FAILED 冲突

已修正。

明确 FAILED 消费记录不能永久阻断补偿，同一 `event_key` 可以在 FAILED/TIMEOUT 状态下被 CAS 抢占重试。

### 11. merchantHealthFactor 缺少公式

已修正。

`MealFlow-queue-design.md` 增加校招实现版：

```text
merchantHealthFactor = min(loadFactor, prepareFactor, exceptionFactor)
```

### 12. DeliveryAssignedEvent 缺失

已修正。

`MealFlow-api-events.md` 增加骑手分配接口和事件清单。

### 13. Redis Key 总览不完整

已修正。

总览文档保留 `queue:ticket:{ticketId}`，并明确 Redis Key 以排队专题文档为准。

### 14. DDL 和迁移计划缺失

已修正。

新增：

```text
MealFlow-ddl.sql
MealFlow-migration-test-plan.md
```

## 第三轮评审修正

### 1. DDL 和迁移计划可发现性不足

已确认当前目录存在：

```text
MealFlow-ddl.sql
MealFlow-migration-test-plan.md
```

同时新增 `docs/README.md` 作为设计文档入口索引，明确阅读顺序和单一事实源。

### 2. consumer_record FAILED 重试不是严格 CAS

已修正。

`consumer_record` 增加：

```text
locked_by
locked_until
idx_consumer_locked_until
```

FAILED/TIMEOUT 重试改为 `locked_by + locked_until` 租约抢占，和 Outbox 发送抢占模型对齐。

### 3. MerchantCapacityReleaseRequestedEvent eventKey 前缀矛盾

已修正。

`eventKey` 格式明确为：

```text
{producerService}:{eventType}:{businessKey}:{version}
```

事件清单拆成：

```text
order:MerchantCapacityReleaseRequested:{capacityTokenId}:{reason}:{eventVersion}
fulfillment:MerchantCapacityReleaseRequested:{capacityTokenId}:{reason}:{eventVersion}
```

### 4. fulfillment-service 写接口缺少 requestId

已修正。

接单、拒单、出餐、分配骑手、取餐、送达接口均补充 `requestId`，并明确同一 `requestId` 重试必须返回同一业务结果。

### 5. stock_reservation 的 uk_order_sku NULL 语义不清

已修正。

明确 `uk_order_sku(order_id, sku_id)` 只保护订单创建后的重复绑定。ticket 阶段因为 `order_id = NULL`，不依赖该唯一键，而依赖：

- `uk_request_sku`
- `uk_ticket_sku`
- 用户 SKU 活跃预占查询
- 短时分布式锁

### 6. capacity_token 回填 order_id 语义不清

已修正。

明确排队转订单后，queue-service 收到 `OrderCreatedFromTicketEvent` 或内部回告后回填 `capacity_token.order_id`。同一 token 同时保留 `ticket_id` 和 `order_id`，分别用于追溯排队来源和订单阶段释放。

### 7. MerchantCapacityReleaseRequestedEvent 旧名称残留

已修正。

总览文档已统一使用 `MerchantCapacityReleaseRequestedEvent`，反向扫描未发现旧名 `MerchantCapacityReleasedEvent`。

### 8. 售后退款事件和状态流转缺失

已修正。

补充：

```text
refund_status:
NONE
REFUND_REQUESTED
REFUNDING
REFUNDED
REFUND_FAILED
```

并补充：

```text
RefundRequestedEvent
RefundStartedEvent
RefundSuccessEvent
RefundFailedEvent
AfterSaleRequestedEvent
AfterSaleApprovedEvent
AfterSaleRejectedEvent
```

## 第四轮评审修正

### 1. queueScore 变量名不一致

已修正。

统一使用毫秒单位变量：

```text
queueScore = createTimeMillis - priorityWeightMillis - agingWeightMillis
```

总览和排队专题均不再混用 `priorityWeight` 与 `priorityWeightMillis`。

### 2. consumer_record PROCESSING 超时缺少抢占 SQL

已修正。

补充 PROCESSING 超时租约抢占：

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

### 3. 取消 QueueTicket 缺少 requestId

已修正。

`POST /queue/tickets/{ticketId}/cancel` 补充：

```json
{
  "requestId": "queue-cancel-80001-001",
  "reason": "用户取消排队"
}
```

### 4. MerchantCapacityReleaseRequestedEvent 的 version 来源不明

已修正。

eventKey 改为：

```text
order:MerchantCapacityReleaseRequested:{capacityTokenId}:{reason}:{eventVersion}
fulfillment:MerchantCapacityReleaseRequested:{capacityTokenId}:{reason}:{eventVersion}
```

其中 `eventVersion` 来自 `local_event.event_version`，当前填 `1`，不是 `capacity_token.version`。

### 5. stock_reservation 释放与库存回补缺少原子性

已修正。

明确 catalog-service 在同一数据库事务内完成：

```text
stock_reservation RESERVED -> RELEASED
sku.available_stock + quantity
```

任一步失败，事务整体回滚。

### 6. confirm/release 内部接口缺少请求体

已修正。

补齐：

```text
POST /internal/vouchers/confirm
POST /internal/vouchers/release
POST /internal/stocks/confirm
POST /internal/stocks/release
```

四个接口的 JSON 请求体，均包含 `requestId` 和业务定位键。

### 7. TINYINT 状态码未定义

已修正。

新增：

```text
MealFlow-status-codes.md
```

覆盖 `QueueTicketStatus`、`CapacityTokenStatus`、`LocalEventStatus`、`ConsumerRecordStatus`、`StockReservationStatus`、`VoucherLockStatus`、`OrderStatus`、`PaymentStatus`、`RefundStatus` 等。

### 8. README 可发现性

当前已确认 `docs/README.md` 存在，并将 `MealFlow-status-codes.md`、`MealFlow-ddl.sql`、`MealFlow-migration-test-plan.md` 纳入阅读顺序和单一事实源说明。

## 第五轮评审修正

### 1. architecture.md 中 queue-service API 与 api-events.md 不一致

已修正。

总览文档删除用户端 `POST /queue/tickets`，明确用户提交订单统一走 `POST /orders/submit`。queue-service 用户端只暴露：

```text
GET /queue/tickets/{ticketId}
POST /queue/tickets/{ticketId}/cancel
```

内部 HTTP 契约只保留：

```text
POST /internal/queue/capacity/apply
POST /internal/queue/tickets/{ticketId}/order-created
```

`capacity/release`、`ready`、`processing`、`failed` 默认由事件消费者、内部状态机或补偿任务处理，不作为 HTTP API 契约暴露。

### 2. M1 交付清单缺少状态码文档

已修正。

`MealFlow-migration-test-plan.md` 的 M1 交付物补充：

```text
MealFlow-status-codes.md
```

### 3. voucher_lock 缺少 request_id

已修正。

`voucher_lock` 增加：

```text
request_id VARCHAR(128) NOT NULL
UNIQUE KEY uk_request_voucher(request_id, user_voucher_id)
```

锁定阶段由 `uk_request_voucher` 兜底重试幂等；确认和释放阶段由 `requestId + voucherLockId` 共同保证幂等。

### 4. capacity_token 不加 version 的理由不清

已修正。

`MealFlow-consistency-data-model.md` 增加“并发控制策略汇总”，说明：

- 多状态聚合用 version。
- 长任务抢占用 `locked_by + locked_until`。
- 单向资源释放用 status CAS。
- `capacity_token` 是 HELD -> RELEASED/EXPIRED 单向流转，不存在 ABA，因此当前不需要 version。

### 5. 并发保护机制缺少统一说明

已修正。

新增并发策略表，覆盖 `queue_ticket`、`orders`、`local_event`、`consumer_record`、`capacity_token`、`stock_reservation`、`voucher_lock`、`idempotent_request`。

## 第六轮评审修正

### 1. ZPOPMIN 弹出无效 ticket 会浪费一次产能释放

已修正。

产能释放流程改为：

```text
循环 ZPOPMIN
-> MySQL CAS: WAITING -> READY
-> CAS 成功发布 QueueTicketReadyEvent
-> CAS 失败说明 ticket 已取消/超时/变化，继续弹下一个
-> 队列为空或达到 maxScanCount 后结束
```

避免“刚弹出候选 ticket，用户已取消”导致本次产能释放被浪费。

### 2. 直接下单 HELD token 缺少补偿

已修正。

`capacity_token` 增加：

```text
request_id VARCHAR(128) DEFAULT NULL
UNIQUE KEY uk_request_id(request_id)
```

直接下单孤儿 token 补偿任务扫描：

```text
status = HELD
ticket_id IS NULL
order_id IS NULL
create_time < NOW() - directTokenTimeout
```

处理方式：

- 如果订单已创建，回填 `order_id`。
- 如果订单未创建且请求超时，释放 `capacity_token`。
- 同步释放同 `request_id` 下的库存预占和优惠券锁定。
