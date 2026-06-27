# MealFlow 高峰履约型外卖平台微服务设计说明书

## 1. 设计定位

MealFlow 不是一个普通外卖 CRUD 项目，也不是 Redis 技术点练习项目。它的定位是：

> 面向奶茶、快餐、轻餐连锁在午晚高峰、节日营销、爆品活动中的瞬时订单洪峰，构建支持点餐、排队、商户产能调度、优惠券秒杀、订单履约、骑手配送、营销增长和运营分析的微服务平台。

项目核心能力不是“能下单”，而是：

- 用户在高峰期仍能得到明确反馈。
- 商户不会被瞬时订单压垮。
- 排队状态可查询、可取消、可超时、可恢复。
- 订单、支付、优惠券、配送状态流转具备幂等和一致性保障。
- 技术组件围绕业务模型服务，而不是堆砌技术名词。

## 1.1 文档拆分

本文件是总览设计，回答“为什么这样拆、核心模型是什么、技术取舍是什么”。开发时不要只看本文，还要看专题文档：

```text
docs/README.md
  设计文档入口索引，说明阅读顺序和单一事实源。

docs/MealFlow-queue-design.md
  高峰排队、QueueTicket、capacity_token、等待时间、取消超时、产能释放。

docs/MealFlow-consistency-data-model.md
  Outbox、consumer_record、requestId 幂等、库存预占、优惠券锁定、订单状态机。

docs/MealFlow-api-events.md
  服务接口、内部调用、事件清单、event_key 规则、开发顺序。

docs/MealFlow-review-fixes.md
  本轮架构评审提出问题的逐条修正对照表。

docs/MealFlow-ddl.sql
  核心表结构草案，开发建表时以此文件为第一参考。

docs/MealFlow-status-codes.md
  所有 TINYINT 状态码映射，SQL、枚举、补偿任务和测试用例必须保持一致。

docs/MealFlow-migration-test-plan.md
  从现有 CQWM 演进到 MealFlow 的迁移路线、迭代拆分和测试验收计划。
```

设计原则：

- 总览文档用于架构评审。
- 专题文档用于开发落地。
- 表结构以 `MealFlow-ddl.sql` 和对应专题文档为准，总览中的 DDL 片段必须与其保持一致。
- 状态码以 `MealFlow-status-codes.md` 为准，文档和代码不得自行定义另一套数值。
- 任何“可释放资源”都必须有事实记录，例如 `capacity_token`、`stock_reservation`、`voucher_lock`。
- MQ 只传递事件，不保存用户可见业务状态。

## 2. 纠正旧设计中的关键问题

### 2.1 RocketMQ 不能作为业务排队队列

错误设计：

```text
用户请求超过阈值 -> 直接发送到 RocketMQ -> 消费者慢慢消费 -> 创建订单
```

问题：

- MQ 只能保证消息传递，不能表达业务排队状态。
- 用户无法准确查询自己排第几。
- 无法取消排队。
- 无法做超时释放。
- 无法做会员优先级、插队、降级处理。
- 无法计算预计等待时间。
- MQ 消息被消费不代表该业务票据仍有效。
- MQ 积压量不等于业务排队人数。

正确设计：

```text
Redis ZSet + MySQL QueueTicket 维护业务排队状态
RocketMQ 只传递产能释放、票据就绪、订单创建等事件
```

也就是说：

- 排队是业务状态，归 Queue 服务维护。
- MQ 是事件通道，只负责通知。
- 消费者收到事件后必须回查业务状态。

### 2.2 排队单和订单不能混淆

高峰下单时，用户提交请求后不一定立即生成正式订单。

应先生成：

```text
QueueTicket 排队凭证
```

当票据获得处理资格后，再生成：

```text
Order 正式订单
```

这样才能支持：

- 排队取消。
- 排队超时。
- 排队优先级调整。
- 排队等待时间估算。
- 排队状态恢复。

### 2.3 必须保存快照

排队期间用户购物车、商品价格、商品状态都可能变化。系统不能在排到时再读取用户当前购物车。

进入排队时必须冻结：

- 购物车快照。
- 商品快照。
- 价格快照。
- 优惠快照。
- 配送费和包装费快照。
- 库存预占信息。

订单创建时只使用这些快照，保证用户等待期间业务语义稳定。

## 3. 微服务边界

建议控制在 9 个核心服务，避免为了“微服务”拆得过碎。

```text
meal-gateway       网关、鉴权、限流、路由、灰度
meal-auth-user     登录、用户、地址、会员、签到
meal-merchant      商户、门店、营业状态、产能配置
meal-catalog       菜品、套餐、类目、库存、商品快照
meal-cart          购物车
meal-order         订单主流程、订单状态机、订单快照
meal-queue         高峰排队、产能令牌、等待时间估算
meal-promotion     优惠券、秒杀券、发券、用户标签
meal-fulfillment   商家接单、出餐、骑手配送
```

可选辅助服务：

```text
meal-payment       支付单、退款、支付回调
meal-notify        WebSocket/SSE、站内信、短信模拟
meal-report        报表、排行、运营分析
meal-search        店铺和商品搜索
```

公共模块：

```text
meal-common        公共 DTO、异常、返回模型、常量
meal-infra         Redis、Redisson、RocketMQ、Outbox、幂等、缓存组件
```

## 4. 领域模型

### 4.1 QueueTicket 排队凭证

`QueueTicket` 是高峰排队域的核心对象。

状态：

```text
WAITING        等待中
READY          已获得处理资格
PROCESSING     正在创建订单
ORDER_CREATED  订单已创建
CANCELLED      用户取消
TIMEOUT        排队超时
FAILED         创建失败
```

MySQL 表建议：

```sql
CREATE TABLE queue_ticket (
  id BIGINT PRIMARY KEY,
  ticket_no VARCHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  cart_snapshot JSON NOT NULL,
  price_snapshot JSON NOT NULL,
  submit_snapshot JSON NOT NULL,
  status TINYINT NOT NULL,
  priority INT NOT NULL DEFAULT 0,
  queue_score BIGINT NOT NULL,
  ahead_count_snapshot INT NOT NULL DEFAULT 0,
  estimated_wait_seconds INT NOT NULL DEFAULT 0,
  order_id BIGINT DEFAULT NULL,
  ready_time DATETIME DEFAULT NULL,
  processing_time DATETIME DEFAULT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  last_error VARCHAR(512) DEFAULT NULL,
  expire_time DATETIME NOT NULL,
  version INT NOT NULL DEFAULT 0,
  create_time DATETIME NOT NULL,
  update_time DATETIME NOT NULL,
  UNIQUE KEY uk_ticket_no(ticket_no),
  KEY idx_merchant_status_score (merchant_id, status, queue_score),
  KEY idx_status_ready_time(status, ready_time),
  KEY idx_status_processing_time(status, processing_time),
  KEY idx_user_create_time (user_id, create_time)
);
```

Redis 热数据：

```text
queue:merchant:{merchantId}:waiting       ZSet，等待队列
queue:ticket:{ticketId}                   Hash，票据摘要
capacity:merchant:{merchantId}:inflight   当前处理中数量，派生热数据，不是最终事实
capacity:merchant:{merchantId}:limit      当前动态产能上限
queue:merchant:{merchantId}:metrics       排队人数、平均耗时、释放速率
```

`capacity_token` 才是商户处理资格的事实记录，Redis `inflight` 只用于快速判断。详细生命周期见 `MealFlow-queue-design.md`。

### 4.2 Order 订单

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

订单状态表达交易和履约业务阶段，支付通道状态单独放在 `payment_order.status` 中。订单不再设置独立 `PAID` 状态，避免订单状态和支付状态重复表达“已支付”。支付成功后由 `PaymentSuccessEvent` 驱动订单从 `PENDING_PAYMENT` 流转到 `WAIT_MERCHANT_ACCEPT`。

订单必须保存快照：

```text
merchant_id
dish_id
dish_name
dish_image
sku_name
origin_price
actual_price
quantity
discount_amount
coupon_id
package_fee
delivery_fee
```

### 4.3 VoucherClaim 优惠券领取

秒杀券领取状态：

```text
ACCEPTED       已通过 Redis 资格校验
CLAIMED        已入用户券包
DUPLICATE      重复领取
FAILED         创建失败
COMPENSATING   补偿中
COMPENSATED    已补偿
```

MySQL 唯一索引：

```text
uk_user_voucher(user_id, voucher_id)
```

用于兜底一人一券。

### 4.4 Payment 支付单

支付单状态：

```text
UNPAID
PAYING
PAID
CLOSED
REFUNDING
REFUNDED
```

支付回调不直接修改订单状态，而是发布 `PaymentSuccessEvent`，由订单服务消费后流转订单。

## 5. 高峰排队详细设计

### 5.1 下单入口流程

```text
用户提交订单
-> order-service 校验地址、商品、优惠券、金额
-> 生成购物车快照、价格快照、优惠快照
-> queue-service 申请商户处理资格
```

如果商户有产能：

```text
queue-service 创建 capacity_token
-> Redis inflight 派生计数 + 1
-> 返回 READY
-> order-service 创建订单
```

如果商户无产能：

```text
创建 QueueTicket
-> 写 MySQL
-> 写 Redis ZSet
-> 返回 ticketId
-> 前端进入等待页
```

### 5.2 产能释放流程

商户接单不释放厨房产能。接单只表示商户确认订单，真正占用产能的制作过程才刚开始。

释放厨房产能的时机：

- 商户拒单。
- 用户取消。
- 支付超时。
- 出餐完成。
- 订单异常关闭。

```text
fulfillment-service 或 order-service 产生 MerchantCapacityReleaseRequestedEvent
-> queue-service 消费事件
-> CAS 释放 capacity_token
-> 从 Redis ZSet 弹出下一个有效 QueueTicket
-> 回查 MySQL 确认状态仍是 WAITING
-> 更新为 READY
-> 发布 QueueTicketReadyEvent
-> order-service 消费事件
-> 基于快照创建订单
```

### 5.3 用户取消排队

```text
用户请求取消 ticket
-> queue-service 按 ticketId 加锁
-> 校验状态是 WAITING/READY/PROCESSING 中可取消阶段
-> MySQL 状态改为 CANCELLED
-> Redis ZSet 移除 ticketId
-> 释放 capacity_token
-> 释放预占库存和优惠券
-> 发布 QueueTicketCancelledEvent
```

### 5.4 排队超时

定时任务扫描：

```text
WHERE status = WAITING AND expire_time < now()
```

处理：

```text
状态改为 TIMEOUT
-> Redis ZSet 移除
-> 释放库存和优惠券
-> 通知用户
```

### 5.5 优先级调整

ZSet score 设计：

```text
score = createTimeMillis - priorityWeightMillis
```

例如：

```text
普通用户 priorityWeightMillis = 0
会员用户 priorityWeightMillis = 30_000
补偿用户 priorityWeightMillis = 60_000
```

这样优先级越高，score 越小，越靠前。

为了避免同毫秒同优先级下顺序不稳定，ZSet member 使用趋势递增的 `ticketNo`。同时 `priorityWeightMillis` 必须有上限，例如最多提前 2 分钟，并引入等待老化机制，避免普通用户长期饥饿。

### 5.6 等待时间估算

基础公式：

```text
estimatedWaitSeconds = ceil(aheadCount / dynamicLimit) * avgPrepareSeconds
```

示例：

```text
aheadCount = 25
dynamicLimit = 5
avgPrepareSeconds = 180

estimatedWaitSeconds = ceil(25 / 5) * 180 = 900 秒
```

真实指标版：

```text
releaseRate = recentCompletedCount / recentWindowSeconds
estimatedWaitSeconds = aheadCount / max(releaseRate, minReleaseRate)
```

其中 `releaseRate` 的单位是单/秒，`aheadCount / releaseRate` 已经是秒，不能再乘 `avgPrepareSeconds`。

可加入修正因子：

- 最近 5 分钟订单完成速度。
- P90 出餐时间。
- 骑手供给系数。
- 商户手动降载系数。

## 6. 商户动态产能模型

商户不是固定阈值，而是动态产能。

输入：

```text
base_capacity          商户基础同时处理数
manual_factor          商户手动降载系数
avg_prepare_seconds    平均出餐时间
pending_order_count    待处理订单数
rider_supply_factor    骑手供给系数
activity_factor        活动高峰系数
```

简化公式：

```text
dynamicLimit = baseCapacity
             * merchantHealthFactor
             * riderSupplyFactor
             * manualFactor
             * activityFactor
```

工程实现应取整并设置边界：

```text
dynamicLimit = max(1, floor(
  baseCapacity
  * merchantHealthFactor
  * riderSupplyFactor
  * manualFactor
  * activityFactor
))
```

`pending_order_count` 不直接乘入公式，而是用于计算 `merchantHealthFactor`。例如待制作订单越多、P90 出餐越慢，`merchantHealthFactor` 越低。

示例：

```text
baseCapacity = 20
merchantHealthFactor = 0.7
riderSupplyFactor = 0.8
manualFactor = 0.5
activityFactor = 1.0

dynamicLimit = max(1, floor(20 * 0.7 * 0.8 * 0.5 * 1.0)) = 5
```

这比固定 Redis 阈值更贴近真实业务。

## 7. 事件驱动与一致性

### 7.1 不直接在事务里发 MQ

使用 Outbox Pattern。

业务事务中：

```text
更新业务表
-> 插入 local_event
-> 提交事务
```

后台任务：

```text
扫描 NEW 事件
-> 投递 RocketMQ
-> 成功后标记 SENT
-> 失败则按 retry_count 延迟重试
```

事件表：

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
  KEY idx_status_next_retry_time (status, next_retry_time),
  KEY idx_locked_until(locked_until)
);
```

Outbox 扫描任务必须先用 `locked_by + locked_until` 抢占发送租约，避免多实例同时投递同一条 NEW 事件。消费者幂等也必须使用 `event_id` 或 `event_key`，不能只依赖 MQ 自己的 `messageId`。

### 7.2 消费幂等

消费者记录表：

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
  UNIQUE KEY uk_event_consumer (event_id, consumer_group),
  UNIQUE KEY uk_event_key_consumer (event_key, consumer_group),
  KEY idx_consumer_locked_until(consumer_group, status, locked_until)
);
```

消费者处理原则：

- 先查幂等记录。
- 再加业务锁。
- 再回查业务状态。
- 状态不匹配时直接忽略或补偿。

原因：同一条 Outbox 事件可能因为“MQ 发送成功但 local_event 标记 SENT 失败”而再次发送，此时 MQ `messageId` 可能不同，但业务 `event_id/event_key` 必须相同。

## 8. 秒杀优惠券设计

Redis Key：

```text
voucher:stock:{voucherId}
voucher:user:{voucherId}
voucher:claim:flow:{flowId}
```

Lua 原子校验：

```text
校验活动时间
校验库存 > 0
校验用户未领取
库存 - 1
用户加入领取集合
记录领取流水
返回 flowId
```

异步流程：

```text
返回 accepted
-> 写 voucher_claim flow
-> 写 VoucherClaimAcceptedEvent
-> promotion-service 异步落库
-> MySQL 唯一索引兜底
-> 失败则补偿 Redis 库存和用户集合
```

注意：Outbox 只能保证“数据库本地事务 + 消息”一致，不能天然保证“Redis Lua + 数据库 Outbox”一致。因此秒杀资格必须采用以下两种闭环之一：

- 方案 A：Lua 扣库存后写 Redis Stream，消费者从 Stream 落库，Stream 是 Redis 侧资格流水。
- 方案 B：Lua 成功后请求线程同步写 `voucher_claim` 和 `local_event`，二者在 MySQL 本地事务中提交。

本项目推荐校招实现版使用方案 B，并补偿扫描 Redis 领取集合和 `voucher_claim`，处理“Lua 成功但 DB/Outbox 未写入”的资格悬挂。

异常补偿：

- Redis 成功，DB 失败：补偿库存和用户领取集合。
- MQ 投递失败：Outbox 重试。
- MQ 重复消费：消费幂等表 + MySQL 唯一索引。
- 用户重复请求：Lua 中 Set 校验。

## 9. 订单状态机与 Redisson 锁

所有订单状态修改统一通过：

```text
lock:order:{orderId}
```

流程：

```text
获取 Redisson 锁
-> 查询当前订单
-> 校验合法状态流转
-> DB 条件更新 status/version
-> 写 local_event
-> 提交事务
-> 释放锁
```

Redisson 锁只用于降低并发竞争，不作为唯一正确性保障。订单状态最终以数据库 CAS 和乐观锁为准：

```sql
UPDATE orders
SET status = ?, version = version + 1
WHERE id = ?
  AND status = ?
  AND version = ?;
```

典型场景：

- 支付回调重复通知。
- 用户重复取消。
- 商家重复接单。
- 骑手重复送达。
- MQ 重复消费。
- 定时任务和人工操作并发。

## 10. 缓存治理

不同数据不同策略：

| 数据 | 策略 |
| --- | --- |
| 商户营业状态 | 短 TTL + 主动更新 |
| 商户产能配置 | Redis 缓存 + 配置变更删除 |
| 菜品列表 | 逻辑过期 + 异步重建 |
| 热门商品 | Caffeine + Redis 二级缓存 |
| 用户基础信息 | 普通 TTL |
| 不存在商品/商户 | 空值缓存 |
| 秒杀活动配置 | 预热 + 本地缓存 |

缓存问题：

- 穿透：空值缓存、BloomFilter。
- 击穿：互斥锁或逻辑过期。
- 雪崩：随机 TTL、分批预热。
- 一致性：更新 DB 后删缓存，必要时延迟双删。

关键事实数据不以缓存作为最终依据：

- 订单。
- 支付。
- 库存流水。
- 发券流水。

## 11. 签到和用户增长

Redis BitMap：

```text
sign:user:{userId}:{yyyyMM}
```

签到流程：

```text
user-service 设置当天 bit
-> 统计连续签到天数
-> 达标后写 SignRewardEvent
-> promotion-service 消费事件
-> 查询用户标签
-> 发放个性化券
-> notify-service 通知用户
```

用户标签：

```text
NEW_USER
PRICE_SENSITIVE
MILK_TEA_LOVER
HIGH_VALUE
CHURN_RISK
```

标签来源：

- 近 30 天消费金额。
- 近 30 天下单次数。
- 偏好品类。
- 优惠券使用率。
- 最近活跃时间。

## 12. 骑手履约设计

配送单状态：

```text
WAIT_ASSIGN
ASSIGNED
ARRIVED_SHOP
PICKED_UP
DELIVERING
DELIVERED
EXCEPTION
CANCELLED
```

履约事件：

```text
MerchantAcceptedEvent
MealReadyEvent
DeliveryAssignedEvent
DeliveryPickedUpEvent
DeliveryCompletedEvent
```

订单完成不应由用户端随意改，而应由配送完成事件或商户自配送完成事件驱动。

## 13. 风控与限流

限流维度：

- 用户维度：防刷单、重复提交。
- 商户维度：保护门店产能。
- 活动维度：保护秒杀活动。
- 接口维度：Gateway + Sentinel。
- 支付回调维度：幂等和签名校验。

下单请求要求携带：

```text
requestId
```

用于防重复点击和重试幂等。

## 14. 可观测性

关键指标：

- 商户当前 inflight。
- 商户等待队列长度。
- 平均等待时间。
- P90/P99 等待时间。
- QueueTicket 超时数量。
- QueueTicket 取消数量。
- 秒杀 Lua 成功率。
- MQ 投递失败数量。
- MQ 消费重试数量。
- 订单状态流转失败数量。

链路追踪：

```text
traceId 贯穿 gateway -> order -> queue -> MQ -> order -> notify
```

日志必须包含：

```text
traceId
userId
merchantId
orderId
ticketId
eventId
```

## 15. 推荐落地路线

### 阶段 1：设计资产

- 完成系统设计说明书。
- 完成核心领域模型。
- 完成高峰排队专题设计。
- 完成状态机设计。
- 完成表结构设计。

### 阶段 2：模块化单体重构

在当前项目中先按包拆领域：

```text
order
queue
promotion
merchant
catalog
user
```

先把业务边界理顺，不急着拆进程。

### 阶段 3：微服务骨架

新建 Spring Cloud 工程：

```text
gateway
auth-user
catalog
order
queue
promotion
notify
```

接入：

- Nacos。
- Gateway。
- OpenFeign。
- Redis。
- RocketMQ。
- Redisson。

### 阶段 4：核心链路

打通：

```text
登录
-> 浏览商品
-> 加购物车
-> 高峰排队
-> 排到后创建订单
-> 支付模拟
-> 商家接单
-> 完成
```

### 阶段 5：技术闭环

- QueueTicket 支持取消、超时、优先级、等待时间估算。
- Outbox 可靠事件。
- 消费幂等。
- Redis Lua 秒杀。
- Redisson 订单状态锁。
- 缓存治理组件。
- Bitmap 签到发券。

### 阶段 6：演示和压测

- JMeter 秒杀压测。
- 高峰下单压测。
- 排队等待页演示。
- MQ 重试演示。
- Redis 队列重建演示。
- Grafana 监控面板。

## 16. 简历表述

推荐表述：

> 设计并实现高峰履约型外卖平台，覆盖点餐、排队、支付、商户接单、骑手配送、秒杀营销、签到增长和运营分析等场景。针对活动高峰爆单问题，抽象 QueueTicket 业务排队模型，使用 Redis ZSet 维护商户维度等待队列，MySQL 持久化排队状态，支持取消、超时、优先级调整和预计等待时间计算；RocketMQ 仅承担产能释放、票据就绪、订单创建等事件通知，实现业务状态与消息传递解耦。基于商户实时产能、平均出餐时长和骑手供给动态释放处理资格。使用 Redis Lua 完成秒杀券库存预扣和一人一单校验，结合异步落库、唯一索引和补偿任务保证最终一致。基于 Redisson 和订单状态机实现订单状态幂等流转，封装缓存治理组件解决穿透、击穿、雪崩问题，并使用 BitMap 实现签到统计和个性化发券。

## 17. 面试追问回答要点

### 为什么 MQ 不适合作为业务排队队列？

因为业务排队需要查询、取消、超时、优先级调整和等待时间估算，这些都要求状态可读、可改、可恢复。MQ 的核心职责是消息传递，不是业务状态维护。

### Redis 宕机后等待队列怎么办？

MySQL `queue_ticket` 是最终事实。Redis 恢复后，可以扫描 `WAITING` 状态票据，按 `queue_score` 重建 ZSet。

### MQ 消息丢了怎么办？

使用 Outbox 事件表。业务事务只写事件表，后台任务负责投递 MQ，失败可重试。

### MQ 重复消费怎么办？

消费者使用 `consumer_record` 或业务唯一键做幂等，并且消费前回查业务状态。

### 用户取消排队时 MQ 已经发了怎么办？

消费者收到 `QueueTicketReadyEvent` 后必须回查票据状态。若状态不是 `READY`，直接忽略。

### 排队期间商品卖完怎么办？

推荐入队时预占库存并保存快照，取消或超时时释放库存。如果选择排到后扣库存，则需要向用户展示售罄失败，这种体验较差。

### 为什么要有动态产能？

不同商户、不同时间段、不同骑手供给下处理能力不同。固定阈值无法反映真实履约能力，动态产能能更好地控制商户压力和用户等待体验。

## 18. 从原项目到新项目的重构原则

原始 CQWM 和 hm-dianping 都有学习项目特征：

- CQWM 偏后台 CRUD，订单、库存、履约、支付之间的业务约束较弱。
- hm-dianping 偏 Redis 技术练习，很多场景为了展示 Redis 而弱化了真实业务边界。
- 两者直接融合会变成“大单体功能拼接”，不能自然体现微服务设计能力。

因此新项目不应以“把两个项目代码搬到一起”为目标，而应以“重建一个业务完整的外卖履约平台”为目标。

处理原则：

| 原能力 | 新定位 | 处理方式 |
| --- | --- | --- |
| CQWM 员工、商户、菜品、套餐 | 商户和商品基础域 | 保留业务概念，重建服务边界 |
| CQWM 订单 | 订单状态机与履约主链路 | 重构为订单聚合 |
| CQWM 管理后台 | 运营后台 | 保留后台角色，但拆分菜单和权限 |
| hm-dianping 秒杀券 | 营销活动域 | 重建为 promotion-service |
| hm-dianping Redis Lua | 高并发资格校验能力 | 保留思想，不复制实现 |
| hm-dianping 博客、关注、点评 | 非核心内容社区 | 不纳入主线，最多作为扩展模块 |
| hm-dianping 签到 | 用户增长能力 | 纳入 auth-user + promotion 联动 |

新项目的核心叙事：

```text
不是“我用了 Redis 和 MQ”，
而是“我围绕外卖高峰履约问题，抽象了排队、产能、优惠、订单、配送等业务模型，再选择合适技术实现”。
```

## 19. 服务职责与数据所有权

微服务拆分的第一原则是数据所有权清晰。一个表只能归一个服务直接写，其他服务通过 API、事件或只读投影访问。

| 服务 | 负责什么 | 直接拥有的数据 | 不能做什么 |
| --- | --- | --- | --- |
| meal-auth-user | 登录、用户、地址、会员、签到 | user、user_address、member_level、user_sign | 不能直接发券库存扣减 |
| meal-merchant | 商户、门店、营业状态、产能配置 | merchant、store、business_hours、capacity_config | 不能直接创建订单 |
| meal-catalog | 菜品、套餐、SKU、商品库存、商品快照 | category、dish、setmeal、sku、stock、item_snapshot | 不能决定优惠券是否可用 |
| meal-cart | 购物车 | cart_item | 不能保存订单事实 |
| meal-promotion | 优惠券、活动、秒杀、用户券包 | voucher、seckill_voucher、user_voucher、voucher_claim | 不能修改订单状态 |
| meal-queue | 排队票据、等待队列、产能令牌 | queue_ticket、capacity_token、queue_metric | 不能生成正式订单 |
| meal-order | 订单、订单明细、订单状态机 | order、order_item、order_status_log | 不能直接操作秒杀库存 |
| meal-payment | 支付单、退款单、支付回调 | payment_order、refund_order | 不能绕过订单状态机 |
| meal-fulfillment | 商户接单、出餐、配送单、骑手轨迹 | fulfillment_order、delivery_order、rider_task | 不能修改支付状态 |
| meal-notify | 通知、WebSocket、站内信 | notify_message、push_channel | 不能承载核心业务状态 |
| meal-report | 报表、宽表、指标 | report_xxx、olap_xxx | 不能反写交易库 |

跨服务访问原则：

- 查询强一致核心状态：同步 API，例如订单详情查支付状态。
- 传播业务事实：领域事件，例如 `PaymentSuccessEvent`。
- 构建列表和报表：异步投影，例如用户订单列表、商户经营看板。
- 禁止跨库 Join，禁止多个服务直接写同一张表。

`capacity_config` 的归属必须特别说明：

- merchant-service 拥有门店基础产能配置、营业状态、商户手动降载配置。
- queue-service 拥有实时排队、`capacity_token`、`inflight` 派生计数和动态产能计算结果。
- queue-service 可以通过同步 API、配置变更事件或 Redis 缓存读取 merchant-service 的 `capacity_config`，但不能直接写 merchant-service 的配置表。

## 20. 核心服务 API 设计

### 20.1 order-service

用户端：

```text
POST /orders/submit                提交订单或进入排队
GET  /orders/{orderId}             查询订单详情
POST /orders/{orderId}/cancel      取消订单
POST /orders/{orderId}/pay         发起模拟支付
GET  /orders                       查询我的订单
```

内部接口：

```text
POST /internal/orders/from-ticket  根据 QueueTicket 快照创建订单
```

支付成功不暴露 HTTP 回告接口，统一由 payment-service 发布 `PaymentSuccessEvent`，order-service 消费事件后推进订单状态。

order-service 不暴露商户接单、拒单、出餐接口。商户操作属于 fulfillment-service，order-service 只消费履约事件后推进交易订单状态。

### 20.2 queue-service

用户端：

```text
GET  /queue/tickets/{ticketId}     查询排队状态
POST /queue/tickets/{ticketId}/cancel
```

内部接口：

```text
POST /internal/queue/capacity/apply
POST /internal/queue/tickets/{ticketId}/order-created
```

说明：

- 用户不直接调用 `POST /queue/tickets` 创建排队票据；用户提交订单统一走 `POST /orders/submit`。
- `capacity/release`、`ready`、`processing`、`failed` 默认由 queue-service 内部状态机、补偿任务或事件消费者处理，不作为 HTTP 契约暴露。
- 如果后续确实需要运维手工干预接口，应单独放在 `/admin/queue/**` 并补完整请求体、权限和审计。

返回给用户的排队状态应包含：

```text
ticketId
status
aheadCount
estimatedWaitSeconds
expireTime
merchantName
canCancel
```

### 20.3 promotion-service

```text
GET  /vouchers                     查询可领取优惠券
POST /vouchers/{id}/claim          领取普通券
POST /vouchers/{id}/seckill        秒杀领取
GET  /users/me/vouchers            我的券包
POST /internal/vouchers/lock       下单锁定优惠券
POST /internal/vouchers/confirm    支付成功确认核销
POST /internal/vouchers/release    订单取消释放优惠券
```

优惠券使用不是简单扣状态，应拆成：

```text
AVAILABLE -> LOCKED -> USED
AVAILABLE -> LOCKED -> RELEASED -> AVAILABLE
```

否则订单超时取消时很难恢复用户券。

### 20.4 fulfillment-service

```text
POST /fulfillment/orders/{id}/accept
POST /fulfillment/orders/{id}/reject
POST /fulfillment/orders/{id}/meal-ready
POST /fulfillment/orders/{id}/assign-rider
POST /fulfillment/orders/{id}/picked-up
POST /fulfillment/orders/{id}/delivered
```

履约服务负责真实世界动作，订单服务负责交易状态，两者通过事件协作。

调用关系：

```text
fulfillment-service 接收商户/骑手操作
-> 更新 fulfillment_order
-> 发布 MerchantAcceptedEvent / MerchantRejectedEvent / MealReadyEvent
-> order-service 消费事件并流转订单状态
```

## 21. 业务流程总览

### 21.1 正常下单流程

```text
用户提交订单
-> gateway 鉴权、限流、生成 traceId
-> order-service 校验 requestId 幂等
-> catalog-service 校验商品与库存
-> promotion-service 试算优惠并锁券
-> merchant-service 查询营业状态和产能
-> queue-service 判断是否需要排队
```

有产能：

```text
queue-service 返回 READY
-> order-service 创建订单
-> payment-service 创建支付单
-> notify-service 通知用户待支付
```

无产能：

```text
queue-service 创建 QueueTicket
-> order-service 返回 QUEUED
-> 用户进入等待页
```

### 21.2 排队转订单流程

```text
商户产能释放
-> fulfillment-service 写出餐/完成状态
-> outbox 投递 MerchantCapacityReleaseRequestedEvent
-> queue-service 消费事件
-> 从 Redis ZSet 选择候选 ticket
-> 回查 MySQL ticket 状态
-> CAS 更新 WAITING -> READY
-> outbox 投递 QueueTicketReadyEvent
-> order-service 消费事件
-> 先按 queue_ticket_id 查询订单
-> 不存在则创建订单
-> 根据 ticket 快照创建订单
-> 写 OrderCreatedFromTicketEvent
-> queue-service 消费事件后将 ticket 标记 ORDER_CREATED 并写 order_id
```

注意：`QueueTicketReadyEvent` 只是通知，不是事实本身。事实仍然是 `queue_ticket.status = READY`。
`queue_ticket` 属于 queue-service，order-service 不允许直接写 queue_ticket 表。

### 21.3 秒杀券领取流程

```text
用户点击抢券
-> gateway 用户限流
-> promotion-service 校验活动基础信息
-> Redis Lua 原子扣减资格库存
-> 写 claim flow
-> 返回 accepted
-> outbox/RocketMQ 异步创建 user_voucher
-> DB 唯一索引兜底一人一券
```

用户看到的状态不应只有“成功/失败”，而应区分：

```text
ACCEPTED     已获得资格，等待入账
CLAIMED      已入券包
DUPLICATE    已领取
SOLD_OUT     已抢完
EXPIRED      活动结束
COMPENSATING 系统补偿中
```

## 22. 一致性边界设计

不是所有一致性都要强一致。强一致用于交易事实，最终一致用于通知和投影。

| 场景 | 一致性要求 | 方案 |
| --- | --- | --- |
| 订单创建和订单明细 | 强一致 | 同库事务 |
| 订单状态流转和状态日志 | 强一致 | 同库事务 + 状态机 |
| 支付成功和订单改为已支付 | 最终一致可接受，但必须可补偿 | 支付事件 + 定时对账 |
| 秒杀资格扣减 | Redis 原子一致 | Lua |
| 秒杀资格落库 | 最终一致 | MQ + 唯一索引 + 补偿 |
| 优惠券锁定和订单提交 | 尽量强一致 | Try-Lock-Confirm/Release |
| 排队票据和 Redis 等待队列 | MySQL 为准，Redis 可重建 | 双写 + 补偿扫描 |
| 通知消息 | 最终一致 | MQ + 重试 |
| 报表数据 | 最终一致 | 事件投影 |

### 22.1 下单优惠券的 TCC 简化模型

Try：

```text
校验券可用
AVAILABLE -> LOCKED
记录 lock_order_id 和 lock_expire_time
```

Confirm：

```text
订单支付成功
LOCKED -> USED
```

Cancel：

```text
订单取消或超时未支付
LOCKED -> AVAILABLE
```

这比“下单时直接把券改成已使用”更符合真实交易。

### 22.2 库存扣减模型

普通下单：

```text
提交订单时预占库存
支付成功后确认占用
支付超时后释放库存
```

秒杀券：

```text
Redis 扣资格库存
DB 异步确认
失败补偿 Redis
```

菜品库存和优惠券库存不要混用一个模型：

- 菜品库存影响履约，需要和订单取消、支付超时联动。
- 优惠券库存影响资格，需要防刷和一人一券。

## 23. 排队系统的正确建模

### 23.1 三类队列要分清

| 类型 | 例子 | 数据结构 | 是否业务事实 |
| --- | --- | --- | --- |
| 业务等待队列 | 商户下单排队 | Redis ZSet + MySQL | 是 |
| 技术消息队列 | 产能释放事件 | RocketMQ | 否 |
| 线程内任务队列 | 本地异步执行 | BlockingQueue | 否 |

只有第一类可以对用户承诺“你在排队中”。

### 23.2 Redis 和 MySQL 的职责

Redis：

- 快速计算排队位置。
- 快速弹出下一个候选票据。
- 保存热状态和滑动窗口指标。
- 支持高频查询等待时间。

MySQL：

- 保存最终业务状态。
- 支持审计和恢复。
- 支持取消、超时、失败原因追溯。
- 支持 Redis 故障后的重建。

RocketMQ：

- 通知“产能释放了”。
- 通知“票据可以处理了”。
- 通知“订单创建成功/失败”。
- 通知“排队取消/超时”。

### 23.3 为什么不能只用 Redis

只用 Redis 的问题：

- AOF/RDB 恢复不能替代业务审计。
- 排队取消、失败、补偿原因难追踪。
- 管理后台难以按多条件查询。
- 数据丢失或误删后缺少事实来源。

因此 Redis 是高性能索引，MySQL 是事实账本。

## 24. 失败场景与补偿设计

| 失败点 | 影响 | 补偿方式 |
| --- | --- | --- |
| 写 MySQL ticket 成功，写 Redis ZSet 失败 | 用户有票据但不在热队列 | 定时扫描 WAITING 票据补写 Redis |
| Redis ZSet 有 ticket，MySQL 已取消 | 候选脏数据 | 弹出后回查 MySQL，状态不符则丢弃 |
| QueueTicketReadyEvent 丢失 | 票据 READY 但未创建订单 | 扫描 READY 超时票据重新发事件 |
| order-service 创建订单失败 | 用户排到但未成单 | ticket 标记 FAILED，释放库存和券，通知用户 |
| 支付成功事件重复 | 订单重复流转 | 订单状态机 + consumer_record |
| 商户接单重复点击 | 状态重复修改 | Redisson 锁 + 状态转换表 |
| 秒杀 Redis 成功 DB 失败 | 用户资格悬挂 | claim_flow 补偿任务回滚或重试落库 |
| MQ 积压 | 通知延迟 | 核心状态仍可查询，增加消费者或降级非核心通知 |
| Redis 宕机 | 排队位置不可快速计算 | MySQL 查询兜底，Redis 恢复后重建 |

核心原则：

```text
任何异步消息丢失或重复，都不应该让核心业务状态不可恢复。
```

## 25. 权限与后台体系

角色设计：

```text
PLATFORM_ADMIN      平台管理员
MERCHANT_ADMIN      商户管理员
STORE_MANAGER       门店店长
STORE_STAFF         门店员工
RIDER               骑手
CUSTOMER            用户
OPERATOR            运营人员
```

权限模型：

```text
user -> role -> permission
merchant_user -> merchant_role -> merchant_permission
```

不要把平台员工、商户员工、普通用户混在一张登录语义里。可以共用认证中心，但要区分身份空间：

```text
/admin/**       平台后台
/merchant/**    商户后台
/rider/**       骑手端
/user/**        用户端
/internal/**    服务内部调用
```

内部接口必须通过网关隔离或服务间签名，不能暴露给前端。

## 26. 网关、限流与防刷

Gateway 负责：

- JWT 鉴权。
- 路由转发。
- 用户维度限流。
- 商户维度限流。
- 活动维度限流。
- 黑名单和风控标签拦截。
- 统一 traceId。

秒杀接口限流：

```text
user:{userId}:voucher:{voucherId}       单用户短窗口
ip:{ip}:seckill                         IP 短窗口
voucher:{voucherId}:seckill             活动总入口
```

下单接口限流：

```text
user:{userId}:submit-order              防重复提交
merchant:{merchantId}:submit-order      保护商户
```

风控策略：

- 同一设备多账号抢券。
- 同一 IP 高频请求。
- 非正常时间窗口批量登录。
- requestId 重放。
- 下单后高频取消。

## 27. 数据库拆分建议

求职项目可以先逻辑拆库，工程上逐步演进。

```text
meal_user_db
meal_merchant_db
meal_catalog_db
meal_order_db
meal_queue_db
meal_promotion_db
meal_payment_db
meal_fulfillment_db
meal_report_db
```

订单库分表预留：

```text
order_{yyyyMM}
order_item_{yyyyMM}
order_status_log_{yyyyMM}
```

或按用户 ID 分片：

```text
order_${userId % 16}
```

求职项目不一定要一开始做真实分库分表，但设计上要说明：

- 当前以单库多 schema 或逻辑库模拟。
- 订单表保留分片键。
- 查询接口避免依赖跨月跨分片强 Join。
- 报表走异步宽表。

## 28. 技术选型建议

建议技术栈：

```text
Spring Boot 2.7.x / 3.x
Spring Cloud Alibaba
Nacos
Spring Cloud Gateway
OpenFeign
MyBatis / MyBatis-Plus
MySQL 8
Redis 6/7
Redisson
RocketMQ
Sentinel
Seata 可选，不作为主方案
XXL-JOB 或 Spring Scheduler
Elasticsearch 可选
Prometheus + Grafana
SkyWalking / Micrometer Tracing
Docker Compose
```

为什么 Seata 不作为主方案：

- 外卖交易链路跨服务长流程多，强行分布式事务会放大复杂度。
- 更适合使用本地事务 + Outbox + 状态机 + 补偿。
- 面试时这比“我用了 Seata”更能体现架构判断。

## 29. 部署拓扑

开发环境：

```text
Docker Compose
-> MySQL
-> Redis
-> RocketMQ
-> Nacos
-> Sentinel Dashboard
-> Prometheus/Grafana
-> 各业务服务
```

逻辑拓扑：

```text
Browser/App
  -> Gateway
    -> auth-user
    -> merchant
    -> catalog
    -> cart
    -> order
    -> queue
    -> promotion
    -> payment
    -> fulfillment
    -> notify

业务服务
  -> Redis
  -> MySQL
  -> RocketMQ
  -> Nacos
```

生产化可讲述但不必完整实现：

- 网关多实例。
- 服务无状态部署。
- Redis 主从或 Cluster。
- MySQL 主从。
- RocketMQ 多 Broker。
- Prometheus 采集服务和 JVM 指标。

## 30. 前端产品设计

用户端核心页面：

- 首页商户列表。
- 商户菜单页。
- 购物车。
- 提交订单页。
- 高峰排队等待页。
- 支付页。
- 订单详情页。
- 优惠券中心。
- 秒杀活动页。
- 我的券包。
- 签到页。

商户端核心页面：

- 门店状态。
- 当前待处理订单。
- 高峰产能配置。
- 排队队列看板。
- 出餐操作台。
- 商品管理。

平台后台核心页面：

- 商户管理。
- 菜品类目管理。
- 营销活动管理。
- 秒杀券配置。
- 高峰队列监控。
- 订单报表。
- 异常补偿任务。

排队等待页应展示：

```text
前方等待人数
预计等待时间
当前票据状态
是否可取消
商户当前繁忙提示
```

这能把技术设计转化成用户可感知的产品能力。

## 31. 项目实现里程碑

### M1：模块化单体打底

目标：

- 保留现有 CQWM 可运行能力。
- 按领域包重组 order、promotion、queue。
- 完成统一 Result、异常、JWT、Redis、MQ 基础设施。
- 建立核心表结构。

验收：

- 原有登录、菜品、订单接口可用。
- 新增订单状态机。
- 新增优惠券基础 CRUD。

### M2：秒杀营销闭环

目标：

- 秒杀券创建。
- Redis 预热。
- Lua 抢券。
- 异步落库。
- 一人一券。
- 补偿任务。

验收：

- 并发抢券库存不超卖。
- 同一用户不会重复领取。
- DB 失败可补偿或重试。

### M3：高峰排队闭环

目标：

- QueueTicket 建模。
- 商户动态产能。
- Redis ZSet 等待队列。
- 取消、超时、优先级。
- 等待时间估算。

验收：

- 无产能时返回 ticket。
- ticket 可查询、可取消、可超时。
- 产能释放后按规则创建订单。
- Redis 故障后可从 MySQL 重建。

### M4：微服务拆分

目标：

- Gateway + Nacos。
- order、queue、promotion、merchant、catalog 服务独立运行。
- OpenFeign 同步调用。
- RocketMQ 领域事件。
- Outbox 可靠消息。

验收：

- 核心链路跨服务可跑通。
- 服务重启不丢核心状态。
- MQ 重复消费不产生脏数据。

### M5：履约与可观测性

目标：

- 商户接单、出餐、骑手配送。
- WebSocket/SSE 通知。
- Prometheus/Grafana 指标。
- 压测脚本。
- 故障演示脚本。

验收：

- 可以完整演示从下单到完成。
- 可以展示高峰排队指标。
- 可以回答失败恢复问题。

## 32. 面试项目边界说明

面试时要主动说明哪些是真实实现，哪些是设计预留。

建议表达：

```text
当前项目完整实现了用户下单、秒杀领券、高峰排队、订单状态机、异步事件、幂等消费和基础履约链路。
为了控制项目体量，支付接入采用模拟支付，骑手路径采用状态流转模拟；但支付单、回调幂等、退款状态、配送单状态都按真实业务模型预留。
```

不要说：

```text
我用 RocketMQ 做了排队。
我用 Redis 解决了高并发。
我把两个项目融合了一下。
```

应该说：

```text
我把外卖高峰下单抽象为商户产能分配问题，使用 QueueTicket 表达用户排队资格，用 Redis 维护热队列，用 MySQL 保存业务事实，用 RocketMQ 做事件通知，避免把消息队列误用为业务状态容器。
```

## 33. 关键设计自检清单

上线前或面试前按这张表检查。

| 问题 | 合格答案 |
| --- | --- |
| 用户怎么知道自己排第几？ | Redis ZSet 计算，MySQL 保存票据事实 |
| 用户取消排队怎么办？ | ticket 状态改 CANCELLED，Redis 移除，释放资源 |
| ticket READY 后用户取消了怎么办？ | 消费者回查状态，不信任 MQ 消息本身 |
| MQ 消息丢失怎么办？ | Outbox 重试，READY 超时扫描补发 |
| MQ 重复消费怎么办？ | consumer_record + 业务状态机 |
| Redis 宕机怎么办？ | MySQL 是事实源，Redis 可重建 |
| 秒杀超卖怎么办？ | Lua 原子扣库存，DB 唯一索引兜底 |
| 用户重复下单怎么办？ | requestId 幂等表 |
| 优惠券被锁后订单取消怎么办？ | LOCKED 超时释放 |
| 支付成功但订单没改状态怎么办？ | 支付对账 + PaymentSuccessEvent 补偿 |
| 商户忙不过来怎么办？ | 动态产能下降，新订单进入 QueueTicket |
| 这个项目亮点是什么？ | 高峰履约建模、业务排队、最终一致、幂等状态机 |

## 34. 最终项目叙事

最终项目不叫“苍穹外卖融合点评”，而应包装为：

```text
MealFlow：面向高峰履约的微服务外卖交易平台
```

一句话说明：

```text
MealFlow 以外卖交易和商户履约为核心，围绕午晚高峰爆单、活动秒杀、订单状态一致性、商户产能调度和用户增长，设计并实现了一套基于 Spring Cloud、Redis、RocketMQ、MySQL 的微服务系统。
```

三条核心亮点：

1. 高峰排队不是 MQ 堆积，而是 QueueTicket 业务状态模型。
2. 秒杀不是简单扣库存，而是资格校验、异步落库、唯一索引和补偿闭环。
3. 订单不是 CRUD，而是状态机、幂等、Outbox 和履约事件协同。

这套叙事比“整合两个项目功能”更适合作为求职项目，也更经得起面试官追问。
