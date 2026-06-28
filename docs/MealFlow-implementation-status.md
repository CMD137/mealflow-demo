# MealFlow 实现状态

本文记录当前代码与最终微服务目标的差距，避免把阶段性 MVP 误认为完整生产系统。

## 当前已完成

- `meal-common`：统一响应、异常、业务状态码枚举。
- `meal-infra`：本地 ID、requestId 幂等、Outbox/consumer 语义的基础组件。
- `meal-gateway`：独立 Spring Cloud Gateway，端口 `8080`，按业务前缀路由到各服务。
- 独立业务服务均已拆为 Spring Boot 模块：`meal-auth-user`、`meal-merchant`、`meal-catalog`、`meal-cart`、`meal-order`、`meal-queue`、`meal-promotion`、`meal-payment`、`meal-fulfillment`、`meal-notify`。
- `auth-user`、`merchant`、`catalog`、`cart`、`order`、`queue`、`promotion`、`payment`、`fulfillment`、`notify` 的核心业务事实已接入 MyBatis Mapper；Docker 环境使用 MySQL，本地测试默认 H2。
- 各持久化服务启动时会通过 MyBatis 查询对应业务表最大 ID，推进本地 ID 生成器，避免 Docker/MySQL 持久数据下服务重启后主键回绕。
- `meal-order` 通过 HTTP 编排 `catalog`、`promotion`、`queue`、`payment` 完成提交订单主链路。
- `meal-fulfillment` 通过 HTTP 推进订单履约状态，出餐完成会释放 `queue` 产能并触发排队 ticket 转订单。
- `meal-fulfillment` 已新增独立履约操作日志表、MyBatis Mapper 和查询接口，可记录商户接单、出餐、取餐、送达等动作。
- `meal-order`、`meal-payment`、`meal-fulfillment` 已新增服务私有本地事件表和 MyBatis Mapper；订单创建、订单支付成功、支付成功、履约接单/出餐/取餐/送达等关键动作会在本地事务内写入 `NEW` 事件，并支持内部 dispatch、定时扫描和 RocketMQ 发布将事件推进为 `SENDING`/`SENT`/`FAILED`。
- `meal-order` 已新增服务私有 `order_consumer_record` 表和 MyBatis Mapper，可消费 `meal-payment` 发布的 `PaymentPaid` RocketMQ 事件，并按 `eventKey + consumerGroup` 幂等推进订单为待商户接单。
- `meal-notify` 已新增 `consumer_record` MyBatis 持久化消费记录和 RocketMQ 领域事件消费者，默认订阅订单事件并按 `eventKey + consumerGroup` 去重落用户通知。
- `meal-promotion` 秒杀领券已支持可配置资格校验模式；本地测试默认 MyBatis/MySQL 路径，Docker 环境使用 Redis Lua 原子扣减 `voucher:stock:{voucherId}` 并写入 `voucher:user:{voucherId}` 一人一券集合，Lua 成功后再落 MyBatis `voucher_claim` 和 `user_voucher` 事实记录，并提供定时/手动 Redis-MyBatis 领取资格对账补偿。
- `meal-queue` 的票据、产能 token、商户产能配置已持久化；启动时可以从 MySQL 重建 WAITING 队列运行时索引和商户产能 inflight 派生计数，Docker 环境可使用 Redis ZSet 作为等待队列热索引、使用 `capacity:merchant:{merchantId}:inflight` 作为产能热计数，本地测试默认使用内存实现。
- `docker-compose.yml` 已包含 Nacos、MySQL、Redis、RocketMQ、gateway 和所有业务服务；MySQL 使用 healthcheck，业务服务等待 MySQL healthy 后启动。
- `scripts/e2e-smoke.ps1` 覆盖 gateway ping、种子商品检查、秒杀券领取、产能限流、第一单成单、第二单排队、支付成功事件异步消费、履约出餐、产能释放后排队 ticket 自动转单。
- 当前验证通过：`mvn -q test`、`mvn -q -DskipTests compile`、`docker compose config`、`scripts/e2e-smoke.ps1`。

## 当前尚未完成

- `auth-user`、`merchant`、`cart`、`notify` 已有最小业务接口，但尚未接真实认证、权限、员工体系、SSE/WebSocket 等完整能力。
- Outbox 已开始落地到 order/payment/fulfillment 的 MySQL 本地事件表，并具备手动 dispatch、定时扫描、状态回写和可配置 RocketMQ 发布器；payment 到 order 的真实 MQ 消费已接入 consumer_record，notify 已开始落地 consumer_record 持久化，持久化消费模板已支持 PROCESSING 超时抢占重试，真实 RocketMQ 消费者已支持配置最大重消费次数并交由 RocketMQ DLQ 兜底。尚未完成所有消费者的 consumer_record 接入和补偿扫描。
- Redis waiting ZSet 和产能 inflight 派生计数已在 `meal-queue` 接入并保留 MySQL 事实源重建/补偿能力；券库存 Redis Lua 和基础领取资格对账补偿已在 `meal-promotion` 接入。更完整的失败重试/死信处理仍需继续补齐。
- Prometheus/Grafana、压测脚本、故障注入脚本尚未完成。

## 下一阶段实施顺序

1. 将 consumer_record 推广到后续真实 MQ 消费者，并补齐消费重试和补偿扫描。
2. 补齐秒杀失败重试/死信处理等剩余 Redis/MQ 异常兜底能力。
3. 增加压测脚本、故障注入脚本和可观测性配置。
4. 将 `meal-app` 降级为仅用于本地演示的兼容模块，最终移除或改成 e2e demo client。
