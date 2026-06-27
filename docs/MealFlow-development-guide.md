# MealFlow 从零开发指南

这份文档不是普通 README，而是一份带学习路线的开发手册。它的目标是让你从 0 开始，最终开发出一个业务完整、技术完整、能用于求职展示和面试追问的微服务外卖项目。

本轮目标已经调整为：

```text
完全重写 MealFlow，不复用 CQWM 和 hm-dianping 的代码。
旧项目只作为业务启发和反例来源，不作为工程基础。
从第一天开始按微服务项目建设，而不是先写单体再拆分。
```

这样做的原因很直接：如果最终项目要展示微服务能力，服务边界、数据所有权、事件一致性、部署拓扑、网关鉴权和可观测性都应该从工程第一天就进入设计。先写单体再拆分，虽然能降低早期启动成本，但会把很多单体时代的隐式依赖、跨模块直接调用、共享事务和共享表习惯带进后续拆分，最后反而会产生新的债务。

## 1. 这份指南怎么用

建议按三层阅读。

第一层，看方向：

```text
MealFlow-development-guide.md
MealFlow-microservice-architecture.md
```

你先理解项目为什么这么设计、要做成什么样、技术亮点是什么。

第二层，看专题：

```text
MealFlow-api-events.md
MealFlow-consistency-data-model.md
MealFlow-queue-design.md
MealFlow-status-codes.md
MealFlow-ddl.sql
```

写代码时不要凭记忆。涉及接口，看 API 文档；涉及状态码，看状态码文档；涉及表结构，看 DDL；涉及排队和产能，看排队专题；涉及幂等和一致性，看一致性专题。

第三层，看验收：

```text
MealFlow-migration-test-plan.md
MealFlow-review-fixes.md
```

其中 `MealFlow-migration-test-plan.md` 原本服务于“从旧 CQWM 演进”的路线。现在既然决定完全重写，它只保留测试和验收思路作为参考，不再作为主开发路径。主开发路径以本文为准。

## 2. 项目最终形态

项目名：

```text
MealFlow
```

一句话定位：

```text
MealFlow 是一个面向午晚高峰和营销活动的微服务外卖交易平台，重点解决高峰排队、商户产能、秒杀领券、订单状态一致性和履约协同问题。
```

它不是：

```text
普通外卖 CRUD
两个教学项目的代码拼接
只展示 Redis 或 MQ 用法的练习项目
```

它应该能完整演示：

- 用户登录、浏览商户、加购、提交订单。
- 商户产能不足时进入业务排队。
- 排队状态可查询、可取消、可超时、可恢复。
- 秒杀券可高并发领取，不超卖、不重复入券包。
- 用户支付后订单进入履约。
- 商户接单、出餐、骑手取餐、送达。
- 订单、支付、库存、优惠券、排队票据都有幂等和补偿。
- 管理端能配置商户、商品、优惠券、活动和产能。
- 系统能展示关键监控指标和故障恢复能力。

## 3. 为什么从一开始就写微服务

这个项目的核心不是“能跑”，而是“边界清楚”。从一开始写微服务有几个好处。

第一，服务数据所有权一开始就能立住。

```text
queue-service 只能写 queue_ticket、capacity_token
order-service 只能写 order、order_item、order_status_log
promotion-service 只能写 voucher、user_voucher、voucher_claim、voucher_lock
payment-service 只能写 payment_order、refund_order
```

如果先写单体，很容易写出“order-service 直接更新 queue_ticket”这种方便但错误的逻辑。后面再拆时，你不是在拆服务，而是在拆隐式事务和隐式依赖。

第二，接口契约一开始就能稳定。

微服务不是把代码分目录，而是通过 API 和事件协作。越早定义：

```text
OpenFeign 内部接口
RocketMQ 领域事件
requestId 幂等键
eventKey 幂等键
错误码
状态码
```

越能避免后期接口反复改，前后端和测试也更容易并行。

第三，一致性问题一开始就暴露。

外卖项目真正难的不是 Controller，而是：

```text
支付成功事件重复怎么办
MQ 发送成功但 Outbox 标记失败怎么办
Redis 秒杀扣成功但 DB 没写怎么办
用户 READY 后取消怎么办
订单创建成功但回写 ticket 失败怎么办
```

这些问题只有在微服务语境下才会自然出现。从第一天按微服务做，反而能让你真正学到项目的价值。

第四，求职叙事更干净。

你可以明确说：

```text
我没有把两个教学项目拼起来，而是重新抽象业务，把高峰外卖交易拆成订单、排队、履约、营销、支付等领域，并用本地事务、Outbox、幂等消费和补偿任务保证最终一致。
```

这比“先做单体再拆”更像一个主动设计过的项目。

## 4. 学习路线

开发前不需要把所有知识都学完，但每个阶段要学对应的东西。

### 4.1 第一阶段：Spring Boot 和工程基础

你需要掌握：

- Maven 多模块项目。
- Spring Boot 自动配置。
- Controller、Service、Mapper 分层。
- 全局异常处理。
- 参数校验。
- 统一返回对象。
- 日志和 traceId。

交付物：

```text
meal-parent
meal-common
meal-infra
一个最小可启动的业务服务
```

验收：

```text
GET /actuator/health 返回 UP
GET /ping 返回 pong
mvn clean compile 通过
```

### 4.2 第二阶段：微服务基础

你需要掌握：

- Nacos 注册发现。
- Spring Cloud Gateway 路由。
- OpenFeign 内部调用。
- 服务间错误处理。
- 网关统一鉴权。
- 服务内部接口和用户接口隔离。

交付物：

```text
meal-gateway
meal-auth-user
meal-merchant
meal-catalog
```

验收：

```text
前端只访问 gateway
gateway 能转发到后端服务
内部接口不直接暴露给用户
```

### 4.3 第三阶段：数据库和缓存

你需要掌握：

- MySQL 表设计。
- MyBatis 或 MyBatis-Plus。
- 乐观锁 version。
- 唯一索引兜底幂等。
- Redis String、Hash、Set、ZSet、BitMap。
- Redisson 分布式锁。

交付物：

```text
用户、商户、商品、购物车基础模型
Redis 缓存组件
分布式锁组件
```

验收：

```text
商品列表可缓存
重复请求不会产生重复数据
状态修改有条件更新
```

### 4.4 第四阶段：订单和状态机

你需要掌握：

- 订单快照。
- 订单状态机。
- 支付状态和订单状态分离。
- requestId 幂等。
- 订单状态日志。
- DB CAS 条件更新。

交付物：

```text
meal-order
idempotent_request
order
order_item
order_status_log
```

验收：

```text
重复提交订单返回同一结果
非法状态流转被拒绝
支付成功只能推进一次
取消订单不会重复释放资源
```

### 4.5 第五阶段：Outbox 和 MQ

你需要掌握：

- RocketMQ 基本模型。
- 本地事务消息的局限。
- Outbox Pattern。
- consumer_record 消费幂等。
- 事件版本和 eventKey。
- 重试、租约、补偿。

交付物：

```text
local_event
consumer_record
OutboxScanner
EventPublisher
EventConsumerTemplate
```

验收：

```text
业务事务和 local_event 同时提交
Outbox 扫描可重试
重复消息不会重复执行业务
FAILED/TIMEOUT/PROCESSING 超时可抢占重试
```

### 4.6 第六阶段：优惠券和秒杀

你需要掌握：

- Redis Lua 原子操作。
- 活动库存预热。
- 一人一券。
- Redis 和 MySQL 双写风险。
- voucher_claim 领取流水。
- 失败补偿。

交付物：

```text
meal-promotion
voucher
seckill_voucher
user_voucher
voucher_claim
秒杀 Lua 脚本
```

验收：

```text
1000 并发抢 100 张券，最终只发 100 张
同一用户不会重复领取
Redis 成功但 DB 失败能补偿
Outbox 重发不会重复入券包
```

### 4.7 第七阶段：高峰排队和产能

你需要掌握：

- QueueTicket 业务排队模型。
- Redis ZSet 排序。
- capacity_token 产能令牌。
- 动态产能计算。
- READY/PROCESSING 补偿。
- ZPOPMIN 弹出脏 ticket 的循环处理。

交付物：

```text
meal-queue
queue_ticket
capacity_token
queue_metric
Redis waiting ZSet
排队补偿任务
产能释放消费者
```

验收：

```text
商户无产能时用户进入排队
排队可查询、可取消、可超时
产能释放后只放行有效 WAITING ticket
READY 事件丢失能补发
PROCESSING 卡死能恢复
直接下单孤儿 capacity_token 能释放或回填
```

### 4.8 第八阶段：履约、通知和监控

你需要掌握：

- 商户接单、拒单、出餐。
- 骑手分配、取餐、送达。
- SSE 或 WebSocket。
- Prometheus 指标。
- Grafana 看板。
- 压测脚本。

交付物：

```text
meal-fulfillment
meal-notify
履约事件
订单进度通知
监控指标
压测脚本
```

验收：

```text
订单能从支付后走到完成
商户接单不释放产能，出餐完成才释放产能
用户能收到排队和订单状态变化
监控能看到队列长度、等待时间、MQ 重试和订单流转失败
```

## 5. 推荐工程目录

全新项目建议放在工作区根目录：

```text
MealFlow/
  pom.xml
  README.md
  docker-compose.yml
  docs/
  scripts/
  sql/
  meal-common/
  meal-infra/
  meal-gateway/
  meal-auth-user/
  meal-merchant/
  meal-catalog/
  meal-cart/
  meal-order/
  meal-queue/
  meal-promotion/
  meal-payment/
  meal-fulfillment/
  meal-notify/
```

说明：

- `docs/` 放当前设计文档副本，后续所有工程变更同步更新文档。
- `scripts/` 放启动脚本、压测脚本、故障演示脚本。
- `sql/` 放初始化 SQL、测试数据、迁移脚本。
- `meal-common` 只放无基础设施依赖的公共模型。
- `meal-infra` 放 Redis、MQ、Outbox、幂等、锁、缓存等基础组件。
- 每个业务服务独立启动、独立配置、独立数据库 schema。

## 6. 模块职责

### 6.1 meal-common

放真正通用且稳定的东西：

```text
Result
ErrorCode
BizException
PageResult
BaseEntity
TraceContext
枚举基础接口
```

不要把业务 Entity 全放进 common。否则 common 会变成共享数据库模型，微服务边界会被破坏。

### 6.2 meal-infra

放跨服务基础设施：

```text
RedisKeyBuilder
DistributedLockTemplate
IdempotentTemplate
OutboxEventPublisher
ConsumerRecordTemplate
MqMessageEnvelope
JsonUtils
TimeUtils
```

注意：`meal-infra` 可以提供组件和抽象，但不要替业务服务决定业务状态。

### 6.3 meal-gateway

负责：

```text
路由
鉴权
限流
traceId
黑名单
统一跨域
内部接口隔离
```

Gateway 不写业务表，不做复杂业务判断。

### 6.4 meal-auth-user

负责：

```text
登录注册
用户资料
地址管理
会员等级
签到
用户标签基础数据
```

签到奖励可以通过事件交给 promotion-service 发券。

### 6.5 meal-merchant

负责：

```text
商户信息
门店营业状态
商户员工
基础产能配置
手动降载配置
```

它拥有 `capacity_config`，但不拥有实时 `capacity_token`。

### 6.6 meal-catalog

负责：

```text
类目
菜品
套餐
SKU
商品库存
商品快照
库存预占
```

如果库存预占实现放在 catalog-service，那么 `stock_reservation` 归 catalog-service。

### 6.7 meal-cart

负责：

```text
购物车增删改查
购物车选中状态
购物车临时价格展示
```

购物车不是订单事实。下单时 order-service 必须生成订单快照。

### 6.8 meal-order

负责：

```text
订单提交入口
订单快照
订单状态机
订单取消
订单查询
订单状态日志
```

它不直接改 queue_ticket，不直接扣秒杀库存，不直接改支付单最终状态。

### 6.9 meal-queue

负责：

```text
QueueTicket
capacity_token
动态产能
Redis waiting ZSet
等待时间估算
排队取消和超时
排队补偿
```

这是项目亮点服务之一。它的核心思想是：

```text
排队是业务状态，不是 MQ 积压。
MySQL 是事实，Redis 是热索引，RocketMQ 是通知通道。
```

### 6.10 meal-promotion

负责：

```text
优惠券模板
秒杀活动
用户券包
优惠券锁定
voucher_claim
用户标签发券
```

秒杀是项目另一个亮点。它必须同时回答：

```text
如何不超卖
如何一人一券
Redis 成功 DB 失败怎么办
MQ 重复消费怎么办
用户重复点击怎么办
```

### 6.11 meal-payment

负责：

```text
支付单
模拟支付
支付回调
退款单
退款回调
支付对账
```

支付成功不直接调用 order-service 的 HTTP 回告接口，推荐发布 `PaymentSuccessEvent`，order-service 消费后推进订单状态。

### 6.12 meal-fulfillment

负责：

```text
商户接单
商户拒单
出餐完成
骑手分配
骑手取餐
骑手送达
履约单
配送单
```

商户接单不释放产能，出餐完成才释放厨房产能。

### 6.13 meal-notify

负责：

```text
WebSocket/SSE
站内信
短信模拟
排队状态推送
订单状态推送
```

通知是最终一致，不承载核心事实。

## 7. 基础设施启动顺序

本地开发用 Docker Compose。

建议先启动：

```text
MySQL 8
Redis 7
Nacos
RocketMQ NameServer
RocketMQ Broker
Sentinel Dashboard
Prometheus
Grafana
```

服务启动顺序：

```text
meal-gateway
meal-auth-user
meal-merchant
meal-catalog
meal-cart
meal-promotion
meal-queue
meal-order
meal-payment
meal-fulfillment
meal-notify
```

早期不必一次启动所有服务。每个阶段只启动相关服务，但工程结构从一开始就保留完整微服务形态。

## 8. 开发总路线

推荐按 10 个里程碑推进，每个里程碑都要有提交、测试和可演示结果。

### D0：创建工程骨架

目标：

```text
建立 Maven 多模块微服务工程。
所有服务能最小启动。
公共返回、异常、日志、traceId 就位。
```

提交建议：

```text
初始化 MealFlow 微服务工程骨架
```

不要在 D0 写业务。D0 只解决项目能不能组织起来。

### D1：基础设施和规范

目标：

```text
统一错误码
统一状态码枚举
统一 requestId 规范
统一 eventKey 规范
统一日志格式
```

交付：

```text
meal-common 的 Result、BizException、ErrorCode
meal-infra 的 IdempotentTemplate、Outbox 基础接口
状态码枚举与 MealFlow-status-codes.md 对齐
```

提交建议：

```text
新增公共响应异常和幂等基础组件
```

### D2：用户、商户、商品基础链路

目标：

```text
让系统具备最小外卖业务语境。
```

交付：

```text
用户登录
地址管理
商户列表
门店营业状态
商品类目
SKU 和库存
购物车
```

验收：

```text
用户能登录
能浏览商户和商品
能加购物车
```

提交建议：

```text
实现用户商户商品和购物车基础链路
```

### D3：订单提交和状态机

目标：

```text
先跑通无排队、无优惠券的普通下单。
```

交付：

```text
POST /orders/submit
GET /orders/{orderId}
POST /orders/{orderId}/cancel
订单快照
订单状态机
idempotent_request
```

验收：

```text
重复 requestId 返回同一订单
非法状态流转失败
订单取消只执行一次
```

提交建议：

```text
实现订单提交幂等和状态机
```

### D4：Outbox 和消费幂等

目标：

```text
让跨服务事件可恢复、可重试、可去重。
```

交付：

```text
local_event
consumer_record
OutboxScanner
RocketMQ Publisher
ConsumerRecordTemplate
```

验收：

```text
同一 eventKey 重发不会重复消费
Outbox 多实例不会重复抢同一事件
消费者 PROCESSING 超时可被重试实例抢占
```

提交建议：

```text
新增 Outbox 可靠事件和消费幂等
```

### D5：支付链路

目标：

```text
用模拟支付跑通订单从待支付到待商户接单。
```

交付：

```text
payment_order
POST /orders/{orderId}/pay
PaymentSuccessEvent
PaymentClosedEvent
支付超时任务
```

验收：

```text
支付成功事件重复时订单只流转一次
支付超时关闭订单并释放资源
```

提交建议：

```text
实现模拟支付和支付事件驱动订单流转
```

### D6：优惠券普通领取和锁定

目标：

```text
先做非秒杀优惠券，把下单用券模型立住。
```

交付：

```text
voucher
user_voucher
voucher_lock
普通券领取
下单锁券
支付确认核销
取消释放券
```

验收：

```text
同一 requestId 锁券返回同一 voucherLockId
订单取消后券恢复可用
支付成功后券变 USED
重复释放不会多次改变状态
```

提交建议：

```text
实现优惠券领取锁定确认和释放
```

### D7：秒杀券

目标：

```text
把 Redis 高并发资格校验和 MySQL 最终入账做成闭环。
```

交付：

```text
seckill_voucher
voucher_claim
Redis Lua
库存预热
VoucherClaimAcceptedEvent
领取补偿任务
```

验收：

```text
不超卖
一人一券
重复请求不会重复入账
Lua 成功 DB 失败可补偿
```

提交建议：

```text
实现秒杀券领取和补偿闭环
```

### D8：库存预占

目标：

```text
让订单提交、取消、支付超时和支付成功都能正确处理商品库存。
```

交付：

```text
stock_reservation
库存预占接口
库存确认接口
库存释放接口
过期释放任务
```

验收：

```text
重复 requestId 不重复预占
释放 reservation 和回补 available_stock 在同一事务
支付成功后 reservation 变 CONFIRMED
```

提交建议：

```text
实现商品库存预占确认和释放
```

### D9：高峰排队

目标：

```text
实现项目最核心的高峰履约能力。
```

交付：

```text
queue_ticket
capacity_token
Redis waiting ZSet
capacity apply
ticket query
ticket cancel
QueueTicketReadyEvent
OrderCreatedFromTicketEvent
MerchantCapacityReleaseRequestedEvent
READY/PROCESSING 补偿任务
直接下单孤儿 token 补偿任务
Redis 队列重建任务
```

验收：

```text
商户有产能时直接创建订单
商户无产能时返回 ticket
用户能看到 aheadCount 和 estimatedWaitSeconds
用户能取消 WAITING/READY/PROCESSING 中尚未成单的 ticket
出餐完成释放产能后放行下一个有效 ticket
ZPOPMIN 弹到已取消 ticket 时继续找下一个
PROCESSING 卡死能恢复
```

提交建议：

```text
实现高峰排队和商户产能令牌
```

### D10：履约、通知、监控和演示

目标：

```text
让项目具备完整业务闭环和求职展示能力。
```

交付：

```text
fulfillment_order
delivery_order
商户接单/拒单/出餐
骑手分配/取餐/送达
订单状态推送
排队状态推送
Prometheus 指标
Grafana 看板
压测脚本
故障演示脚本
```

验收：

```text
能完整演示下单、排队、支付、接单、出餐、配送、完成
能演示秒杀并发
能演示 MQ 重复消费、Redis 重建、PROCESSING 恢复
```

提交建议：

```text
实现履约通知监控和演示脚本
```

## 9. 第一周怎么开始

第一周不要急着写秒杀和排队。真正该做的是把工程骨架、规范和最小业务语境立起来。

### 第 1 天：创建工程

任务：

```text
创建 MealFlow 根目录
创建 parent pom
创建 meal-common、meal-infra、meal-gateway
创建 3 个最小业务服务：auth-user、merchant、catalog
配置 Java 17 或 Java 8
配置 Spring Boot 和 Spring Cloud 版本
```

建议版本：

```text
Java 17
Spring Boot 2.7.x 或 3.x
Spring Cloud 2021.x 或 2022.x
Spring Cloud Alibaba 与 Spring Cloud 版本匹配
```

如果你更重视学习资料和兼容性，选 Spring Boot 2.7.x。  
如果你更重视新版本和长期维护，选 Spring Boot 3.x，但要接受 Jakarta 包名变化。

### 第 2 天：统一基础模型

任务：

```text
Result
ErrorCode
BizException
GlobalExceptionHandler
PageQuery
PageResult
RequestContext
TraceIdFilter
```

验收：

```text
所有服务错误返回格式一致
每个请求日志都有 traceId
```

### 第 3 天：基础设施容器

任务：

```text
docker-compose.yml
MySQL
Redis
Nacos
RocketMQ
```

验收：

```text
本地能一键启动基础设施
服务能注册到 Nacos
```

### 第 4 天：网关和鉴权

任务：

```text
gateway 路由
JWT 解析
用户上下文透传
内部接口拦截
```

验收：

```text
未登录不能访问用户接口
登录后 gateway 能透传 userId
/internal/** 不能从外部直接访问
```

### 第 5 天：用户、商户、商品最小模型

任务：

```text
用户手机号登录
商户列表
商品类目
SKU
购物车加购
```

验收：

```text
用户能登录后浏览商品并加购
```

第一周结束时，你还没有写高并发，但你已经拥有一个能继续生长的微服务骨架。这比一开始冲进复杂业务更稳。

## 10. 每个功能怎么写

每开发一个功能，都按同一个顺序。

### 10.1 先写业务用例

示例：取消订单。

先写清楚：

```text
谁发起：用户
入口：POST /orders/{orderId}/cancel
前置条件：订单属于当前用户
允许状态：PENDING_PAYMENT、WAIT_MERCHANT_ACCEPT
副作用：关闭支付单、释放库存、释放优惠券、释放 capacity_token
幂等：重复取消返回同一结果
事件：OrderCancelledEvent
```

### 10.2 再写表和状态

确认：

```text
涉及哪些表
每张表归哪个服务
状态码在哪里定义
是否需要 version
是否需要唯一索引
是否需要 requestId
```

### 10.3 再写 API 和事件

确认：

```text
用户接口还是内部接口
请求体是否有 requestId
响应体是否有明确状态
会发什么事件
eventKey 是什么
谁消费
消费失败怎么补偿
```

### 10.4 最后写代码

代码顺序：

```text
Entity/DO
Mapper
Service
Controller
Outbox event
Consumer
Compensation job
Test
```

不要先写 Controller。Controller 很容易让你陷入“接口能调通就算完成”的错觉。

## 11. 代码规范

### 11.1 包结构

每个服务建议：

```text
com.mealflow.order
  api
  application
  domain
  infrastructure
  interfaces
```

解释：

```text
api             给其他服务引用的 Feign client、DTO、事件定义
application     应用服务，编排领域逻辑和外部调用
domain          领域模型、状态机、领域规则
infrastructure  Mapper、Redis、MQ、外部客户端实现
interfaces      Controller、请求响应模型
```

如果你觉得 DDD 分层太重，可以先用：

```text
controller
service
mapper
model
event
job
```

但要保留服务边界，不要跨服务直接引用 Entity。

### 11.2 DTO 和 Entity

原则：

```text
Controller 不直接返回 Entity
Feign 不传 Entity
MQ 不传 Entity
DB 表字段变化不直接污染外部契约
```

建议命名：

```text
SubmitOrderRequest
SubmitOrderResponse
OrderDetailResponse
OrderCreatedEvent
OrderDO
OrderItemDO
```

### 11.3 状态流转

状态流转不要散落在 if else 里。

建议：

```text
OrderStatusMachine
QueueTicketStatusMachine
VoucherLockStatusMachine
CapacityTokenStatusMachine
```

每个状态机至少提供：

```text
boolean canTransit(from, event, to)
Status transit(from, event)
```

DB 更新必须使用条件：

```sql
UPDATE orders
SET status = ?, version = version + 1
WHERE id = ?
  AND status = ?
  AND version = ?;
```

### 11.4 幂等

写接口必须回答：

```text
幂等键是什么
重复请求返回什么
第一次执行中再次请求怎么办
执行失败后重试怎么办
幂等记录多久过期
```

用户请求通常用：

```text
requestId
```

事件消费通常用：

```text
eventId/eventKey + consumerGroup
```

业务唯一性通常用：

```text
唯一索引
```

这三层不是互相替代，而是互相兜底。

## 12. 数据库开发规则

### 12.1 每个表必须有归属服务

写表前先问：

```text
这张表谁拥有？
谁能写？
其他服务怎么读？
其他服务怎么感知变化？
```

禁止：

```text
order-service 直接写 queue_ticket
payment-service 直接写 order.status
fulfillment-service 直接写 payment_order
promotion-service 直接写 order_item
```

### 12.2 每个可释放资源必须有记录表

例如：

```text
capacity_token       商户处理资格
stock_reservation    商品库存预占
voucher_lock         优惠券锁定
```

不要只在 Redis 或主表字段上做加减，否则取消、超时、重复释放和审计都会变困难。

### 12.3 状态字段必须有状态码文档

所有 `TINYINT status` 都要在 `MealFlow-status-codes.md` 中定义：

```text
数值
名称
含义
终态还是非终态
允许流转
```

写 SQL 时不要硬猜数字。

### 12.4 唯一索引用于业务兜底

典型唯一索引：

```text
order.queue_ticket_id
user_voucher(user_id, voucher_id)
voucher_lock(request_id, user_voucher_id)
stock_reservation(request_id, sku_id)
consumer_record(event_key, consumer_group)
local_event(event_key)
idempotent_request(user_id, request_id, biz_type)
```

唯一索引不是性能优化，而是正确性保障。

## 13. Redis 使用规则

Redis 在 MealFlow 中有四类用途。

第一类：热缓存。

```text
商户营业状态
商品列表
秒杀活动配置
```

第二类：原子资格判断。

```text
秒杀券 Lua
一人一券 Set
活动库存 String
```

第三类：业务热索引。

```text
queue:merchant:{merchantId}:waiting
capacity:merchant:{merchantId}:inflight
capacity:merchant:{merchantId}:limit
```

第四类：分布式协调。

```text
Redisson lock
限流计数器
短时互斥 key
```

关键原则：

```text
Redis 可以提升性能，但核心事实要能从 MySQL 恢复。
```

例外是秒杀 Lua 的资格瞬间判断，但它也必须通过 `voucher_claim` 或 Redis Stream 建立可补偿流水。

## 14. MQ 使用规则

RocketMQ 在项目中只做事件通知，不做业务状态。

事件命名：

```text
PaymentSuccessEvent
OrderCreatedFromTicketEvent
QueueTicketReadyEvent
MerchantCapacityReleaseRequestedEvent
VoucherClaimAcceptedEvent
MealReadyEvent
```

eventKey 格式：

```text
{producerService}:{eventType}:{businessKey}:{version}
```

示例：

```text
order:OrderCreatedFromTicket:ticket-10001:1
payment:PaymentSuccess:payment-90001:1
fulfillment:MerchantCapacityReleaseRequested:token-30001:MEAL_READY:1
```

消费者永远不信任消息本身。收到消息后必须：

```text
查 consumer_record
查业务表
校验状态
CAS 更新
写本服务 local_event
```

## 15. 高峰排队怎么实现

排队流程分三种情况。

### 15.1 有产能，直接下单

```text
order-service 调 queue-service apply
queue-service 创建 capacity_token
Redis inflight + 1
返回 READY + capacityTokenId
order-service 创建订单
order-service 写 OrderCreatedEvent
queue-service 回填 capacity_token.order_id
```

如果 order-service 在创建订单前崩溃：

```text
queue-service 补偿扫描 HELD、ticket_id IS NULL、order_id IS NULL、超过阈值的 capacity_token
按 requestId 查询订单
有订单则回填 order_id
无订单则释放 token 并 inflight - 1
```

### 15.2 无产能，进入排队

```text
queue-service 创建 QueueTicket
写 MySQL
写 Redis ZSet
返回 QUEUED + ticketId
```

用户看到：

```text
前方人数
预计等待时间
是否可取消
过期时间
```

### 15.3 产能释放，放行下一个

```text
fulfillment-service 出餐完成
发布 MerchantCapacityReleaseRequestedEvent
queue-service CAS 释放 capacity_token
Redis inflight - 1
ZPOPMIN waiting ZSet
回查 MySQL ticket
如果不是 WAITING，继续 ZPOPMIN
如果是 WAITING，CAS 更新 READY
发布 QueueTicketReadyEvent
```

注意：ZPOPMIN 不可逆，所以必须循环处理脏 ticket，不能弹一个无效就停止。

## 16. 秒杀券怎么实现

推荐校招实现版：

```text
请求进入 promotion-service
校验活动存在和时间
执行 Redis Lua
Lua 成功后同步写 voucher_claim + local_event
返回 PROCESSING
Outbox 投递 VoucherClaimAcceptedEvent
消费者创建 user_voucher
更新 voucher_claim 为 CLAIMED
```

Lua 做：

```text
判断库存
判断用户是否已领
扣 Redis 库存
用户加入领取 Set
返回成功
```

MySQL 做：

```text
voucher_claim 记录资格流水
user_voucher 入券包
唯一索引防重复
补偿任务对账 Redis 和 DB
```

用户展示状态：

```text
PROCESSING    已获得资格，入账中
SUCCESS       已入券包
FAILED        失败
SOLD_OUT      已抢完
DUPLICATE     已领取
EXPIRED       活动结束
```

## 17. 下单链路怎么实现

普通下单完整链路：

```text
gateway 鉴权
order-service 校验 requestId
cart-service 查询购物车
catalog-service 校验商品和库存
promotion-service 试算优惠并锁券
catalog-service 预占库存
queue-service 申请商户产能
有产能则创建订单
无产能则创建 QueueTicket
payment-service 创建支付单
notify-service 通知用户
```

其中任何一步失败，都要释放已经锁住的资源。

建议用 Saga 编排思路，但不引入复杂 Saga 框架：

```text
应用服务按顺序调用
每一步成功后记录资源 ID
后续失败时按相反顺序补偿
关键释放动作通过接口幂等和 CAS 保证可重复调用
```

## 18. 支付和订单怎么协作

支付单状态：

```text
UNPAID
PAYING
PAID
CLOSED
REFUNDING
REFUNDED
```

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

支付成功：

```text
payment-service 更新 payment_order 为 PAID
写 PaymentSuccessEvent
order-service 消费事件
订单 PENDING_PAYMENT -> WAIT_MERCHANT_ACCEPT
catalog-service 确认库存
promotion-service 确认用券
fulfillment-service 创建履约单
```

支付超时：

```text
payment-service 关闭支付单
order-service 取消订单
catalog-service 释放库存
promotion-service 释放券
queue-service 释放 capacity_token
```

## 19. 履约怎么实现

履约不是订单服务的附属接口，单独放在 fulfillment-service。

商户操作：

```text
POST /fulfillment/orders/{id}/accept
POST /fulfillment/orders/{id}/reject
POST /fulfillment/orders/{id}/meal-ready
```

骑手操作：

```text
POST /fulfillment/orders/{id}/assign-rider
POST /fulfillment/orders/{id}/picked-up
POST /fulfillment/orders/{id}/delivered
```

每个写接口都带 requestId。

事件：

```text
MerchantAcceptedEvent
MerchantRejectedEvent
MealReadyEvent
DeliveryAssignedEvent
DeliveryPickedUpEvent
DeliveryCompletedEvent
```

order-service 消费这些事件后推进订单状态。fulfillment-service 不直接写订单表。

## 20. 前端怎么安排

前端不要一开始追求漂亮。先做能测试业务闭环的页面。

阶段 1：接口测试页。

```text
登录
商户列表
商品列表
购物车
提交订单
查看排队票据
模拟支付
商户出餐
秒杀领券
查看券包
```

阶段 2：用户端。

```text
首页
门店页
购物车
提交订单
排队等待页
支付页
订单详情
优惠券中心
秒杀页
我的券包
```

阶段 3：商户端和平台端。

```text
商户订单操作台
出餐看板
产能配置
商品管理
秒杀活动管理
队列监控
补偿任务监控
```

前端要服务于业务演示，不要盖过后端主题。

## 21. 测试策略

### 21.1 单元测试

重点测纯逻辑：

```text
订单状态机
QueueTicket 状态机
等待时间估算
动态产能计算
eventKey 生成
幂等键生成
```

### 21.2 集成测试

重点测数据库和 Redis：

```text
requestId 重复请求
Outbox 租约抢占
consumer_record 重复消费
stock_reservation 释放事务
voucher_lock 释放 CAS
capacity_token 重复释放
```

### 21.3 并发测试

秒杀：

```text
1000 用户抢 100 张券
```

排队：

```text
100 用户同时向 dynamicLimit = 5 的商户下单
```

重复释放：

```text
同一个 capacity_token 重复发送 10 次释放事件
```

### 21.4 故障注入

必须能演示：

```text
Outbox 发送 MQ 成功但标记 SENT 失败
QueueTicket READY 事件丢失
QueueTicket PROCESSING 卡死
Redis waiting ZSet 丢失后重建
Lua 成功但 DB 写入失败
支付成功事件重复
```

这些演示是项目面试最有说服力的部分。

## 22. Git 使用方式

每个提交只做一件事。

推荐提交粒度：

```text
初始化 MealFlow 微服务工程骨架
新增公共响应异常和状态码
新增 Docker Compose 基础设施
新增用户登录和网关鉴权
新增商户商品购物车基础接口
新增订单提交幂等和状态机
新增 Outbox 和消费幂等
新增模拟支付和支付事件
新增优惠券锁定确认释放
新增秒杀券领取闭环
新增库存预占确认释放
新增高峰排队和产能令牌
新增履约服务和订单状态推送
新增监控指标和压测脚本
```

每次提交前至少运行：

```text
mvn -q -DskipTests compile
```

涉及核心链路时还要跑：

```text
对应服务单元测试
接口手工验证
必要的并发脚本
```

提交信息用中文，说明业务价值，而不是只写“update”。

## 23. 每天开发检查清单

开始写代码前：

```text
今天要完成哪个业务用例？
涉及哪些服务？
涉及哪些表？
是否有 requestId？
是否有 eventKey？
失败后怎么补偿？
重复调用会怎样？
```

写代码时：

```text
不跨服务写表
不直接返回 Entity
不信任 MQ 消息本身
不把 Redis 当最终事实
不把状态流转散落在 Controller
```

提交前：

```text
编译通过
状态码和文档一致
DDL 和实体一致
接口和 api-events 一致
关键 SQL 有 CAS 或唯一索引兜底
补偿任务有扫描条件和幂等保护
```

## 24. 面试讲法

项目开场不要说：

```text
我做了一个外卖项目，里面用了 Redis、RocketMQ、微服务。
```

应该说：

```text
我做的是一个高峰履约型外卖平台。核心问题是午晚高峰商户处理能力有限，用户请求不能简单堆到 MQ 里，所以我抽象了 QueueTicket 业务排队模型，用 MySQL 保存排队事实，用 Redis ZSet 做热队列，用 RocketMQ 做事件通知，再通过 capacity_token 控制商户处理资格。
```

讲秒杀时不要说：

```text
我用 Lua 防止超卖。
```

应该说：

```text
秒杀接口用 Lua 做 Redis 侧资格原子校验，但我没有把 Redis 成功当成最终完成，而是引入 voucher_claim 作为领取流水，通过 Outbox 异步入券包，并用 DB 唯一索引和补偿任务处理 Redis 成功但 DB 失败、MQ 重复消费等问题。
```

讲一致性时不要说：

```text
我用了分布式事务。
```

应该说：

```text
我没有优先使用强分布式事务，而是把交易事实限制在各服务本地事务内，通过 Outbox、eventKey、consumer_record、状态机 CAS 和补偿任务保证最终一致。这样更适合外卖这种长流程、多状态、多补偿的业务。
```

## 25. 最小可演示版本

如果时间有限，先做 MVP。

MVP 必须包含：

```text
gateway
auth-user
merchant
catalog
cart
order
queue
promotion
payment
fulfillment
```

MVP 可以简化：

```text
支付用模拟支付
骑手配送用状态模拟
商户和商品数据用后台预置
通知先用轮询或 SSE
报表先只做基础指标
```

MVP 不能省：

```text
requestId 幂等
订单状态机
Outbox
consumer_record
capacity_token
queue_ticket
voucher_claim
stock_reservation
voucher_lock
核心唯一索引
补偿任务
```

因为这些才是项目的技术含金量。

## 26. 最终验收标准

项目做到“可投简历”的标准：

```text
所有服务能通过 Docker Compose 或本地脚本启动
用户能完整下单到完成
高峰下单能进入排队并自动转订单
秒杀券并发不超卖
重复消息和重复请求不会产生脏数据
核心失败场景有补偿任务
有基本监控指标
有接口文档和演示脚本
有清晰 README 和架构图
```

项目做到“能抗面试追问”的标准：

```text
你能讲清楚为什么不用 MQ 直接排队
你能讲清楚 Redis 和 MySQL 谁是事实源
你能讲清楚 eventKey 为什么不能用 MQ messageId 替代
你能讲清楚 capacity_token 为什么需要表
你能讲清楚 READY/PROCESSING 卡死怎么恢复
你能讲清楚秒杀 Redis 成功 DB 失败怎么补偿
你能讲清楚商户接单为什么不释放产能
你能讲清楚优惠券为什么要 LOCKED 状态
你能讲清楚订单状态为什么不用 PAID 作为长期业务状态
```

## 27. 下一步

接下来真正开工时，第一步不是写订单，也不是写秒杀，而是创建全新的 `MealFlow/` 工程骨架。

建议第一轮实施只做：

```text
MealFlow 根目录
parent pom
公共模块
网关模块
3 个最小服务
docker-compose
README
基础 health/ping 接口
```

这一步完成后，再进入用户、商户、商品、购物车和订单。

不要担心一开始慢。一个边界清楚的项目，前 20% 的时间都在铺路；后面业务会越写越顺。这个项目最值钱的不是“代码很多”，而是每一段代码都能解释为什么存在。
