# MealFlow 实现状态

本文记录当前代码与最终微服务目标的差距，避免把阶段性 MVP 误认为完整生产系统。

## 当前已完成

- `meal-common`：统一响应、异常、业务状态码枚举。
- `meal-infra`：本地 ID、requestId 幂等、Outbox/consumer 语义的基础组件。
- `meal-gateway`：独立 Spring Cloud Gateway，端口 `8080`，按业务前缀路由到各服务。
- 独立业务服务均已拆为 Spring Boot 模块：`meal-auth-user`、`meal-merchant`、`meal-catalog`、`meal-cart`、`meal-order`、`meal-queue`、`meal-promotion`、`meal-payment`、`meal-fulfillment`、`meal-notify`。
- `auth-user`、`merchant`、`catalog`、`cart`、`order`、`queue`、`promotion`、`payment`、`notify` 的核心业务事实已接入 MyBatis Mapper；Docker 环境使用 MySQL，本地测试默认 H2。
- `meal-order` 通过 HTTP 编排 `catalog`、`promotion`、`queue`、`payment` 完成提交订单主链路。
- `meal-fulfillment` 通过 HTTP 推进订单履约状态，出餐完成会释放 `queue` 产能并触发排队 ticket 转订单。
- `meal-queue` 的票据、产能 token、商户产能配置已持久化；启动时可从 MySQL 重建 WAITING 队列运行时索引。
- `docker-compose.yml` 已包含 Nacos、MySQL、Redis、RocketMQ、gateway 和所有业务服务；MySQL 使用 healthcheck，业务服务等待 MySQL healthy 后启动。
- 新增 `scripts/e2e-smoke.ps1`，覆盖 gateway ping、种子商品检查、产能限流、第一单成单、第二单排队、支付成功、履约出餐、产能释放后排队 ticket 自动转单。
- 当前验证通过：`mvn -q test`、`mvn -q -DskipTests compile`、`docker compose config`、`scripts/e2e-smoke.ps1`。

## 当前尚未完成

- `auth-user`、`merchant`、`cart`、`notify` 已有最小业务接口，但尚未接真实认证、权限、员工体系、SSE/WebSocket 等完整能力。
- `fulfillment` 主要负责跨服务状态推进，尚未持久化独立履约操作日志。
- Outbox、consumer_record 当前仍是内存实现，尚未接真实 RocketMQ 投递、消费重试和补偿扫描。
- Redis ZSet、券库存、产能派生计数等仍未接真实 Redis；queue 当前使用 MySQL 事实源加本地运行时优先队列。
- Prometheus/Grafana、压测脚本、故障注入脚本尚未完成。

## 下一阶段实施顺序

1. 将 order、payment、fulfillment 的关键事件改成 Outbox + RocketMQ 语义。
2. 接入 Redis ZSet/库存/产能派生计数，并保留 MySQL 事实源和重建能力。
3. 为 fulfillment 增加独立履约操作日志表与 MyBatis Mapper。
4. 增加压测脚本、故障注入脚本和可观测性配置。
5. 将 `meal-app` 降级为仅用于本地演示的兼容模块，最终移除或改成 e2e demo client。
