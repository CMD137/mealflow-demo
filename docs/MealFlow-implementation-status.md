# MealFlow 实现状态

本文记录当前代码与最终微服务目标的差距，避免把阶段性 MVP 误认为完整生产系统。

## 当前已完成

- `meal-common`：统一响应、异常、业务状态码枚举。
- `meal-infra`：本地 ID、requestId 幂等、Outbox/consumer 语义的基础组件。
- `meal-gateway`：独立 Spring Cloud Gateway，端口 `8080`，按业务前缀路由到各服务；已接入 Bearer token 鉴权过滤器，可调用 `auth-user` 校验 token 并向下游注入可信 `X-User-Id`、`X-Role`、`X-Merchant-Id`。
- 独立业务服务均已拆为 Spring Boot 模块：`meal-auth-user`、`meal-merchant`、`meal-catalog`、`meal-cart`、`meal-order`、`meal-queue`、`meal-promotion`、`meal-payment`、`meal-fulfillment`、`meal-notify`。
- `auth-user`、`merchant`、`catalog`、`cart`、`order`、`queue`、`promotion`、`payment`、`fulfillment`、`notify` 的核心业务事实已接入 MyBatis Mapper；Docker 环境使用 MySQL，本地测试默认 H2。
- `meal-auth-user` 已新增 MyBatis 持久化 token、角色权限和商户员工表；登录后签发 `mf-*` token，内部校验接口返回用户、角色、商户和权限，网关按权限保护用户、商户、履约和内部运维类接口。
- 各持久化服务启动时会通过 MyBatis 查询对应业务表最大 ID，推进本地 ID 生成器，避免 Docker/MySQL 持久数据下服务重启后主键回绕。
- `meal-order` 通过 HTTP 编排 `catalog`、`promotion`、`queue`、`payment` 完成提交订单主链路。
- `meal-fulfillment` 通过 HTTP 推进订单履约状态，出餐完成会释放 `queue` 产能并触发排队 ticket 转订单。
- `meal-fulfillment` 已新增独立履约操作日志表、MyBatis Mapper 和查询接口，可记录商户接单、出餐、取餐、送达等动作。
- `meal-order`、`meal-payment`、`meal-fulfillment` 已新增服务私有本地事件表和 MyBatis Mapper；订单创建、订单支付成功、支付成功、履约接单/出餐/取餐/送达等关键动作会在本地事务内写入 `NEW` 事件，并支持内部 dispatch、定时扫描、`SENDING` 超时回收和 RocketMQ 发布将事件推进为 `SENDING`/`SENT`/`FAILED`。
- `meal-order` 已新增服务私有 `order_consumer_record` 表和 MyBatis Mapper，可消费 `meal-payment` 发布的 `PaymentPaid` RocketMQ 事件，并按 `eventKey + consumerGroup` 幂等推进订单为待商户接单；同时支持定时/手动扫描过期 `PROCESSING` 消费记录并标记 `TIMEOUT`，避免租约卡死阻塞后续重投。
- `meal-notify` 已新增 `consumer_record` MyBatis 持久化消费记录和 RocketMQ 领域事件消费者，默认订阅订单事件并按 `eventKey + consumerGroup` 去重落用户通知；同时提供 `/notify/messages/stream` SSE 实时通知流，消息事实仍以 MyBatis `notify_message` 为准，并支持消费记录超时补偿扫描。
- `meal-order` 和 `meal-notify` 的 consumer_record 已保存事件类型与 payload，失败或超时记录可通过内部 replay 接口按本地记录重放，重放仍走同一套幂等消费模板和业务状态机。
- `meal-notify` 已支持通知模板和多渠道投递事实表，模板消息可同时生成站内信与 `SMS_MOCK` 投递记录，便于演示短信模拟和后续真实渠道扩展。
- `meal-promotion` 秒杀领券已支持可配置资格校验模式；本地测试默认 MyBatis/MySQL 路径，Docker 环境使用 Redis Lua 原子扣减 `voucher:stock:{voucherId}` 并写入 `voucher:user:{voucherId}` 一人一券集合，Lua 成功后再落 MyBatis `voucher_claim` 和 `user_voucher` 事实记录，并提供定时/手动 Redis-MyBatis 领取资格对账补偿。
- `meal-promotion` 已新增 Redis 秒杀领取修复任务表 `voucher_claim_retry`，对账发现 Redis 已接受但 MyBatis 事实缺失时会记录待修复任务，支持手动/定时重试，成功后标记 `REPAIRED`，超过最大重试次数进入 `DEAD`，便于故障演示和运维兜底。
- `meal-queue` 的票据、产能 token、商户产能配置已持久化；启动时可以从 MySQL 重建 WAITING 队列运行时索引和商户产能 inflight 派生计数，Docker 环境可使用 Redis ZSet 作为等待队列热索引、使用 `capacity:merchant:{merchantId}:inflight` 作为产能热计数，本地测试默认使用内存实现。
- `docker-compose.yml` 已包含 Nacos、MySQL、Redis、RocketMQ、gateway、所有业务服务、Prometheus 和 Grafana；MySQL 使用 healthcheck，业务服务等待 MySQL healthy 后启动。
- 所有业务服务和网关已暴露 `/actuator/prometheus`，Prometheus 默认抓取各服务指标，Grafana 自动 provision Prometheus 数据源和 `MealFlow Overview` 基础面板。
- `meal-order`、`meal-payment`、`meal-fulfillment` 已暴露 Outbox 状态指标 `mealflow_outbox_events`，`meal-order`、`meal-notify` 已暴露 consumer_record 状态指标 `mealflow_consumer_records`，`meal-promotion` 已暴露秒杀领取修复任务指标 `mealflow_voucher_claim_retries`；Grafana 面板已展示 Outbox 积压、消费记录异常和券修复任务状态。
- `scripts/e2e-smoke.ps1` 覆盖 gateway ping、登录取 token、种子商品检查、秒杀券领取、产能限流、第一单成单、第二单排队、支付成功事件异步消费、履约出餐、产能释放后排队 ticket 自动转单。
- `scripts/load-seckill.ps1`、`scripts/load-peak-orders.ps1` 和 `scripts/fault-demo.ps1` 已覆盖秒杀并发、高峰下单、鉴权拒绝、Redis 热索引重建和 capacity token 重复释放幂等演示。
- 当前验证通过：`mvn -q test`、`mvn -q -DskipTests compile`、`docker compose config`、`scripts/e2e-smoke.ps1`。

## 当前尚未完成

- `auth-user` 已具备基础 token、角色权限和商户员工能力；`merchant`、`cart` 仍是最小业务接口，尚未补完整后台菜单、细粒度员工管理、地址管理等能力。
- Outbox 已开始落地到 order/payment/fulfillment 的 MySQL 本地事件表，并具备手动 dispatch、定时扫描、状态回写和可配置 RocketMQ 发布器；payment 到 order、domain event 到 notify 的真实 MQ 消费均已接入 consumer_record，持久化消费模板已支持 PROCESSING 超时抢占重试和基于保存 payload 的本地重放，真实 RocketMQ 消费者已支持配置最大重消费次数并交由 RocketMQ DLQ 兜底。
- Redis waiting ZSet 和产能 inflight 派生计数已在 `meal-queue` 接入并保留 MySQL 事实源重建/补偿能力；券库存 Redis Lua、领取资格对账补偿、领取修复重试和死信记录已在 `meal-promotion` 接入。后续可继续扩展更多 Redis/MQ 故障注入断言。
- Prometheus/Grafana、业务积压指标和基础压测/故障脚本已完成；后续可继续扩展更细的队列等待 P90/P99、秒杀失败原因分布和故障注入自动断言。

## 下一阶段实施顺序

1. 将 `meal-app` 降级为仅用于本地演示的兼容模块，最终移除或改成 e2e demo client。
