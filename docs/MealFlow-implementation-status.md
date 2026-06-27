# MealFlow 实现状态

本文记录当前代码与最终微服务目标的差距，避免把阶段性 MVP 误认为完整生产系统。

## 当前已完成

- `meal-common`：统一响应、异常、业务状态码枚举。
- `meal-infra`：本地 ID、requestId 幂等、Outbox/consumer 语义的基础组件。
- `meal-app`：单进程兼容 demo，保留完整下单、库存预占、优惠券锁定、排队、支付、履约闭环。
- `meal-gateway`：独立 Spring Cloud Gateway，端口 `8080`，按业务前缀路由到各服务。
- 独立业务服务：
  - `meal-auth-user`，端口 `8101`，实现模拟登录、用户资料和地址查询。
  - `meal-merchant`，端口 `8102`，实现商户列表、商户详情和产能配置。
  - `meal-catalog`，端口 `8103`，实现商品列表、库存快照、库存预占、确认和释放接口；SKU 和库存预占已接入 MyBatis Mapper 事实源，默认 H2，Docker 环境接 MySQL。
  - `meal-cart`，端口 `8104`，实现购物车添加、修改、删除和查询。
  - `meal-order`，端口 `8105`，通过 HTTP 编排 catalog、promotion、queue、payment 完成提交订单主链路；订单主表、订单项快照和资源引用已接入 MyBatis Mapper 事实源。
  - `meal-queue`，端口 `8106`，实现产能申请、排队票据、产能释放、READY ticket 放行、订单创建回告；票据和产能 token 已接入 MyBatis Mapper 事实源，启动时可重建 WAITING 队列索引。
  - `meal-promotion`，端口 `8107`，实现秒杀领券、券包查询、优惠券锁定、确认和释放接口；券、用户券、领取流水和锁券流水已接入 MyBatis Mapper 事实源。
  - `meal-payment`，端口 `8108`，实现支付单创建、模拟支付、关闭和查询接口；支付单已接入 MyBatis Mapper 事实源。
  - `meal-fulfillment`，端口 `8109`，通过 HTTP 推进订单履约状态，出餐完成会释放 queue 产能并触发排队转订单。
  - `meal-notify`，端口 `8110`，实现站内消息推送和用户消息查询。
- 每个独立服务都有 `/ping` 和 gateway 前缀 ping，例如 `/orders/ping`、`/queue/ping`。
- `docker-compose.yml` 已包含 Nacos、MySQL、Redis、RocketMQ、gateway 和所有业务服务；执行前需要先 `mvn -q -DskipTests package` 生成各服务 jar。
- 新增 catalog、queue、payment 的 Spring Boot 持久化烟测，覆盖 H2 schema 初始化和关键状态流转；当前 `mvn -q test` 通过。

## 当前尚未完成

- auth-user、merchant、cart、notify 已有最小业务接口，但尚未接真实认证、权限、员工体系、SSE/WebSocket 等完整能力。
- fulfillment、notify 仍以本地内存状态为主，尚未全部迁移到 MySQL 表级事实源。
- Outbox、consumer_record 当前仍是内存实现，尚未接真实 RocketMQ 投递、消费重试和补偿扫描。
- Redis ZSet、券库存、产能计数等仍未接真实 Redis；queue 当前使用 MySQL 事实源加本地运行时优先队列。
- Nacos Discovery 已预留配置，Docker 环境开启，但尚未补自动化 e2e 验证注册发现。
- Prometheus/Grafana、压测脚本、故障注入脚本尚未完成。

## 下一阶段实施顺序

1. 将 fulfillment、notify 的核心状态迁移到 MySQL。
2. 将 payment 和 fulfillment 事件改成 Outbox + RocketMQ 语义。
3. 接入 Redis ZSet/库存/产能派生计数，替换本地运行时计数。
4. 增加跨服务 e2e 测试、压测脚本和故障注入脚本。
5. 将 `meal-app` 降级为仅用于本地演示的兼容模块，最终移除或改成 e2e demo client。
