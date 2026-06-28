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
- `meal-order`、`meal-payment`、`meal-fulfillment` 已新增服务私有本地事件表和 MyBatis Mapper；订单创建、订单支付成功、支付成功、履约接单/出餐/取餐/送达等关键动作会在本地事务内写入 `NEW` 事件，并支持内部 dispatch 和定时扫描将事件推进为 `SENDING`/`SENT`/`FAILED`。
- `meal-notify` 已新增 `consumer_record` MyBatis 持久化消费记录，支持按 `eventKey + consumerGroup` 去重消费通知事件。
- `meal-queue` 的票据、产能 token、商户产能配置已持久化；启动时可以从 MySQL 重建 WAITING 队列运行时索引，Docker 环境可使用 Redis ZSet 作为商户等待队列热索引，本地测试默认使用内存实现。
- `docker-compose.yml` 已包含 Nacos、MySQL、Redis、RocketMQ、gateway 和所有业务服务；MySQL 使用 healthcheck，业务服务等待 MySQL healthy 后启动。
- `scripts/e2e-smoke.ps1` 覆盖 gateway ping、种子商品检查、产能限流、第一单成单、第二单排队、支付成功、履约出餐、产能释放后排队 ticket 自动转单。
- 当前验证通过：`mvn -q test`、`mvn -q -DskipTests compile`、`docker compose config`、`scripts/e2e-smoke.ps1`。

## 当前尚未完成

- `auth-user`、`merchant`、`cart`、`notify` 已有最小业务接口，但尚未接真实认证、权限、员工体系、SSE/WebSocket 等完整能力。
- Outbox 已开始落地到 order/payment/fulfillment 的 MySQL 本地事件表，并具备手动 dispatch、定时扫描、状态回写和可配置 RocketMQ 发布器；notify 已开始落地 consumer_record 持久化。尚未完成所有消费者的 consumer_record 接入、消费重试和补偿扫描。
- Redis waiting ZSet 已在 `meal-queue` 接入并保留 MySQL 事实源重建能力；券库存、产能派生计数等仍未接真实 Redis。
- Prometheus/Grafana、压测脚本、故障注入脚本尚未完成。

## 下一阶段实施顺序

1. 将 consumer_record 推广到后续真实 MQ 消费者，并补齐消费重试和补偿扫描。
2. 接入券库存、产能派生计数等剩余 Redis 能力，并补齐 Redis/MySQL 对账补偿。
3. 增加压测脚本、故障注入脚本和可观测性配置。
4. 将 `meal-app` 降级为仅用于本地演示的兼容模块，最终移除或改成 e2e demo client。
