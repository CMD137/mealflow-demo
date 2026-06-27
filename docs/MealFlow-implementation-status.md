# MealFlow 实现状态

本文记录当前代码与最终微服务目标的差距，避免把阶段性 MVP 误认为完整系统。

## 当前已完成

- `meal-common`：统一响应、异常、状态码枚举。
- `meal-infra`：内存版 ID、requestId 幂等、Outbox 事件、消费幂等基础组件。
- `meal-app`：单进程 legacy demo，已实现下单、库存预占、优惠券锁定、排队、支付、履约闭环。
- `meal-gateway`：独立 Spring Cloud Gateway 模块，端口 `8080`，按业务前缀路由到各服务。
- 独立业务服务骨架：
  - `meal-auth-user`，端口 `8101`，已实现模拟登录、用户资料和地址查询
  - `meal-merchant`，端口 `8102`，已实现商户列表、商户详情和产能配置
  - `meal-catalog`，端口 `8103`，已实现商品列表、库存快照、库存预占、确认和释放接口
  - `meal-cart`，端口 `8104`，已实现购物车添加、修改、删除和查询
  - `meal-order`，端口 `8105`，已通过 HTTP 编排 catalog、promotion、queue、payment 完成提交订单主链路
  - `meal-queue`，端口 `8106`，已实现产能申请、排队票据、产能释放、READY ticket 放行、订单创建回告
  - `meal-promotion`，端口 `8107`，已实现秒杀领券、券包查询、优惠券锁定、确认和释放接口
  - `meal-payment`，端口 `8108`，已实现支付单创建、模拟支付、关闭和查询接口
  - `meal-fulfillment`，端口 `8109`，已通过 HTTP 推进订单履约状态，出餐完成会释放 queue 产能并触发排队转订单
  - `meal-notify`，端口 `8110`，已实现站内消息推送和用户消息查询
- 每个独立服务都有 `/ping` 和 gateway 前缀 ping，例如 `/orders/ping`、`/queue/ping`。

## 当前尚未完成

- auth-user、merchant、cart、notify 已有最小业务接口，但还未接真实认证、权限、员工体系、SSE/WebSocket 等完整能力。
- 各服务还没有真实 MySQL schema 绑定、Mapper、事务和表级数据所有权隔离。
- Outbox、consumer_record 目前是内存实现，还没有真实 RocketMQ 投递和消费重试。
- Redis ZSet、库存、券库存、产能计数还没有接真实 Redis。
- Nacos Discovery 已预留配置，默认关闭；尚未验证真实注册发现。
- Prometheus/Grafana、压测脚本、故障注入脚本尚未完成。

## 下一阶段实施顺序

1. 将 payment 和 fulfillment 事件改成 Outbox + RocketMQ 语义。
2. 接入 MySQL/Redis，替换内存仓库。
3. 增加跨服务 e2e 测试、压测脚本和故障注入脚本。
4. 将 `meal-app` 降级为仅用于本地演示的兼容模块，最终移除或改成 e2e demo client。
