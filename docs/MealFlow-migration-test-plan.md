# MealFlow 迁移与测试计划

## 0. 当前路线说明

本文件最初服务于“从现有 CQWM 单体逐步演进到 MealFlow”的路线。现在项目目标已调整为：

```text
完全重写 MealFlow，不复用 CQWM 和 hm-dianping 代码。
从第一天开始按微服务工程建设。
```

因此，当前主开发路线以 `MealFlow-development-guide.md` 为准。本文保留的价值是：

- 提供阶段验收思路。
- 提供测试场景清单。
- 提供故障注入和演示脚本设计。
- 作为“如果从旧系统迁移时怎么做”的备选路线。

不要再把本文中的“模块化单体改造”作为当前项目的必经步骤。

## 1. 目标

当前仓库来自 CQWM 单体和 hm-dianping 学习项目，不能直接“大搬家”成微服务。如果未来必须在旧系统上做渐进迁移，备选路线是：

```text
先模块化单体
再抽基础设施
再拆核心服务
最后补监控、压测和故障演示
```

这样可以保证旧系统迁移时每一步都有可运行系统，而不是一次性拆碎后长期不可用。当前完全重写路线不走这条路径。

## 2. 迁移阶段

### M1：文档与模型冻结

交付：

- `MealFlow-microservice-architecture.md`
- `MealFlow-queue-design.md`
- `MealFlow-consistency-data-model.md`
- `MealFlow-api-events.md`
- `MealFlow-ddl.sql`
- `MealFlow-status-codes.md`

验收：

- 表结构、事件名、状态枚举在文档间一致。
- 订单、排队、履约、支付、优惠券的数据所有权明确。
- 每个可释放资源都有记录表。

### M2：旧系统备选 - CQWM 模块化单体改造

目标：

在当前 Spring Boot 单体内先按领域包拆分：

```text
com.sky.mealflow.order
com.sky.mealflow.queue
com.sky.mealflow.promotion
com.sky.mealflow.catalog
com.sky.mealflow.fulfillment
com.sky.mealflow.infra
```

交付：

- 订单状态机组件。
- requestId 幂等组件。
- Outbox 表和扫描任务。
- consumer_record 幂等组件。
- 基础 DDL 脚本。

验收：

- 原 CQWM 登录、菜品、套餐、订单基础接口仍可用。
- 新增表可以初始化。
- `mvn -q -DskipTests compile` 通过。

### M3：秒杀券闭环

交付：

- 秒杀券活动配置。
- Redis 库存预热。
- Lua 一人一券校验。
- `voucher_claim` 流水。
- `VoucherClaimAcceptedEvent`。
- 用户券包异步入账。
- Redis/DB 对账补偿。

验收：

- 单用户重复抢同一券只成功一次。
- 并发抢券不超卖。
- Lua 成功但 DB 写入失败可补偿。
- Outbox 重发不会重复入券包。

### M4：高峰排队闭环

交付：

- `queue_ticket`。
- `capacity_token`。
- Redis ZSet 等待队列。
- 动态产能计算。
- WAITING/READY/PROCESSING 补偿任务。
- `MerchantCapacityReleaseRequestedEvent`。
- `QueueTicketReadyEvent`。

验收：

- 商户无产能时返回 ticket。
- ticket 可查询、可取消、可超时。
- PROCESSING 卡死可恢复。
- capacity_token 重复释放不会多放行。
- ZPOPMIN 弹到已取消 ticket 时继续扫描下一个有效 WAITING ticket。
- 直接下单场景下孤儿 capacity_token 可被补偿任务回填 order_id 或释放。
- 商户接单不释放产能，出餐完成释放产能。

### M5：库存和优惠券资源锁定

交付：

- `stock_reservation`。
- `voucher_lock`。
- 取消、支付超时、创建失败释放资源。
- 支付成功确认资源。

验收：

- 下单入队时库存可预占。
- 用户取消 ticket 后库存和券释放。
- 同一 requestId 重试返回同一 reservation/lock。
- 同一用户同 SKU 活跃预占不会重复扣库存。

### M6：拆分微服务

目标服务：

```text
meal-gateway
meal-auth-user
meal-catalog
meal-order
meal-queue
meal-promotion
meal-payment
meal-fulfillment
meal-notify
```

交付：

- Nacos 注册发现。
- Gateway 路由和鉴权。
- OpenFeign 内部调用。
- RocketMQ 事件通信。
- 每个服务独立数据库或逻辑 schema。

验收：

- 用户提交订单可跨服务跑通。
- 秒杀领券可跨服务跑通。
- 商户出餐释放产能可跨服务跑通。
- 任意消费者重复消费不产生脏数据。

## 3. 测试计划

### 3.1 单元测试

- 订单状态机合法流转。
- 非法状态流转拒绝。
- dynamicLimit 计算。
- estimatedWaitSeconds 计算。
- eventKey 生成全局唯一。
- requestId 重复请求返回快照。

### 3.2 集成测试

- Outbox 扫描任务抢占 `locked_until`。
- consumer_record SUCCESS 去重。
- consumer_record FAILED/TIMEOUT 通过 `locked_by + locked_until` 抢占租约后可重试，两个实例并发时只有一个处理。
- stock_reservation 确认、释放、过期。
- voucher_lock 确认、释放、过期。
- capacity_token CAS 释放。

### 3.3 并发测试

秒杀：

```text
1000 并发抢 100 张券
期望：user_voucher 不超过 100，Redis 库存不为负，同用户不重复
```

排队：

```text
商户 dynamicLimit = 5
100 用户同时提交订单
期望：最多 5 个 HELD capacity_token，其余进入 WAITING
```

释放：

```text
同一个 capacity_token 重复发送 10 次 MerchantCapacityReleaseRequestedEvent
期望：只释放一次，只放行一个 ticket
```

### 3.4 故障注入

- Redis waiting ZSet 删除后，从 MySQL 重建。
- Outbox 发送 MQ 成功但标记 SENT 失败，确认消费者按 eventKey 去重。
- QueueTicket READY 事件丢失，READY 扫描补发。
- QueueTicket PROCESSING 后 order-service 宕机，PROCESSING 扫描恢复。
- Lua 成功但 voucher_claim 未写入，Redis 对账补偿。
- 支付成功事件重复投递，订单只流转一次。

### 3.5 演示脚本

求职演示建议准备 5 条主线：

1. 正常下单到支付。
2. 商户高峰排队，等待页展示预计时间。
3. 商户出餐释放产能，下一个 ticket 自动转订单。
4. 秒杀券高并发抢券。
5. 故障补偿演示：重复 MQ、Redis 重建、PROCESSING 恢复。

## 4. 每阶段提交建议

```text
提交 1：新增 MealFlow 核心 DDL 和枚举
提交 2：新增幂等、Outbox、consumer_record 基础设施
提交 3：新增订单状态机
提交 4：新增秒杀券领取闭环
提交 5：新增 QueueTicket 和 capacity_token
提交 6：接入库存预占和优惠券锁定
提交 7：拆出微服务骨架
提交 8：补压测、监控和故障演示
```

每个提交都必须至少跑：

```text
mvn -q -DskipTests compile
```

涉及核心链路的提交还要补接口级手工验证或集成测试。
