# MealFlow 实现状态

本文记录当前代码与最终微服务目标的差距，避免把阶段性 MVP 误认为完整生产系统。

## 当前已完成

- `meal-common`：统一响应、异常、业务状态码枚举。
- `meal-infra`：本地 ID、requestId 幂等、Outbox/consumer 语义的基础组件。
- `meal-gateway`：独立 Spring Cloud Gateway，端口 `8080`，按业务前缀路由到各服务；已接入 Bearer token 鉴权过滤器，可调用 `auth-user` 校验 token 并向下游注入可信 `X-User-Id`、`X-Role`、`X-Merchant-Id`。
- 独立业务服务均已拆为 Spring Boot 模块：`meal-auth-user`、`meal-merchant`、`meal-catalog`、`meal-cart`、`meal-order`、`meal-queue`、`meal-promotion`、`meal-payment`、`meal-fulfillment`、`meal-notify`。
- `auth-user`、`merchant`、`catalog`、`cart`、`order`、`queue`、`promotion`、`payment`、`fulfillment`、`notify` 的核心业务事实已接入 MyBatis Mapper；Docker 环境使用 MySQL，本地测试默认 H2。
- `meal-auth-user` 已新增 MyBatis 持久化 token、角色权限、菜单权限和商户员工表；登录后签发 `mf-*` token，内部校验接口返回用户、角色、商户、权限和可见菜单，网关按权限保护用户、商户、商品管理、履约和内部运维类接口。
- `meal-auth-user` 已补齐用户地址 MyBatis CRUD，支持地址新增、修改、删除、查询和默认地址设置。
- `meal-auth-user` 已补齐商家后台员工、角色和菜单权限管理，支持角色权限维护、员工新增/复用用户账号、员工角色调整、员工启停用；员工停用后旧 token 会在校验时失去商户身份。
- 各持久化服务启动时会通过 MyBatis 查询对应业务表最大 ID，推进本地 ID 生成器，避免 Docker/MySQL 持久数据下服务重启后主键回绕。
- `meal-order` 通过 HTTP 编排 `catalog`、`promotion`、`queue`、`payment` 完成提交订单主链路，并提供商家后台订单条件查询和订单状态统计接口。
- `meal-fulfillment` 通过 HTTP 推进订单履约状态，出餐完成会释放 `queue` 产能并触发排队 ticket 转订单。
- `meal-fulfillment` 已新增独立履约操作日志表、MyBatis Mapper 和查询接口，可记录商户接单、出餐、取餐、送达等动作。
- `meal-order`、`meal-payment`、`meal-fulfillment` 已新增服务私有本地事件表和 MyBatis Mapper；订单创建、订单支付成功、支付成功、履约接单/出餐/取餐/送达等关键动作会在本地事务内写入 `NEW` 事件，并支持内部 dispatch、定时扫描、`SENDING` 超时回收和 RocketMQ 发布将事件推进为 `SENDING`/`SENT`/`FAILED`。
- `meal-order` 已新增服务私有 `order_consumer_record` 表和 MyBatis Mapper，可消费 `meal-payment` 发布的 `PaymentPaid` RocketMQ 事件，并按 `eventKey + consumerGroup` 幂等推进订单为待商户接单；同时支持定时/手动扫描过期 `PROCESSING` 消费记录并标记 `TIMEOUT`，避免租约卡死阻塞后续重投。
- `meal-notify` 已新增 `consumer_record` MyBatis 持久化消费记录和 RocketMQ 领域事件消费者，默认订阅订单事件并按 `eventKey + consumerGroup` 去重落用户通知；同时提供 `/notify/messages/stream` SSE 实时通知流，消息事实仍以 MyBatis `notify_message` 为准，并支持消费记录超时补偿扫描。
- `meal-order` 和 `meal-notify` 的 consumer_record 已保存事件类型与 payload，失败或超时记录可通过内部 replay 接口按本地记录重放，重放仍走同一套幂等消费模板和业务状态机。
- `meal-notify` 已支持通知模板和多渠道投递事实表，模板消息可同时生成站内信与 `SMS_MOCK` 投递记录，便于演示短信模拟和后续真实渠道扩展。
- `meal-promotion` 秒杀领券已支持可配置资格校验模式；本地测试默认 MyBatis/MySQL 路径，Docker 环境使用 Redis Lua 原子扣减 `voucher:stock:{voucherId}` 并写入 `voucher:user:{voucherId}` 一人一券集合，Lua 成功后再落 MyBatis `voucher_claim` 和 `user_voucher` 事实记录，并提供定时/手动 Redis-MyBatis 领取资格对账补偿。
- `meal-promotion` 已补齐营销券后台管理能力，支持券列表、新建券、修改券名称/类型/折扣/库存/状态；禁用券会阻止用户继续领取。
- `meal-promotion` 已新增 Redis 秒杀领取修复任务表 `voucher_claim_retry`，对账发现 Redis 已接受但 MyBatis 事实缺失时会记录待修复任务，支持手动/定时重试，成功后标记 `REPAIRED`，超过最大重试次数进入 `DEAD`，便于故障演示和运维兜底。
- `meal-queue` 的票据、产能 token、商户产能配置已持久化；启动时可以从 MySQL 重建 WAITING 队列运行时索引和商户产能 inflight 派生计数，Docker 环境可使用 Redis ZSet 作为等待队列热索引、使用 `capacity:merchant:{merchantId}:inflight` 作为产能热计数，本地测试默认使用内存实现。
- `meal-catalog` 已支持用户端商家类目和上架 SKU 浏览；商家后台类目和 SKU 商品维护包含类目新增/修改、SKU 新增/修改、上下架、库存设置；下单快照会拒绝已下架 SKU。
- `meal-merchant` 已支持商户容量配置和营业状态更新；`meal-cart` 已支持购物车增删改查、选中状态切换和整车清空。
- `docker-compose.yml` 已包含 Nacos、MySQL、Redis、RocketMQ、gateway、所有业务服务、Prometheus 和 Grafana；MySQL 使用 healthcheck，业务服务等待 MySQL healthy 后启动。
- 所有业务服务和网关已暴露 `/actuator/prometheus`，Prometheus 默认抓取各服务指标，Grafana 自动 provision Prometheus 数据源和 `MealFlow Overview` 基础面板。
- `meal-order`、`meal-payment`、`meal-fulfillment` 已暴露 Outbox 状态指标 `mealflow_outbox_events`，`meal-order`、`meal-notify` 已暴露 consumer_record 状态指标 `mealflow_consumer_records`，`meal-promotion` 已暴露秒杀领取修复任务指标 `mealflow_voucher_claim_retries`；Grafana 面板已展示 Outbox 积压、消费记录异常和券修复任务状态。
- `scripts/e2e-smoke.ps1` 覆盖 gateway ping、登录取 token、种子商品检查、秒杀券领取、产能限流、第一单成单、第二单排队、支付成功事件异步消费、履约出餐、产能释放后排队 ticket 自动转单。
- `scripts/load-seckill.ps1`、`scripts/load-peak-orders.ps1` 和 `scripts/fault-demo.ps1` 已覆盖秒杀并发、高峰下单、鉴权拒绝、Redis 热索引重建和 capacity token 重复释放幂等演示。
- `meal-app` 已从默认 Maven reactor 移出，仅保留在 `legacy-demo` profile 下作为本地内存版演示模块，避免与当前微服务主线混淆。
- 最近一次后端主线全量验证通过：`mvn -q test`、`mvn -q -DskipTests package`、`docker compose config`、`docker compose up -d --build`、`scripts/e2e-smoke.ps1`。
- 本轮新增管理端能力已验证通过：`mvn -q -pl meal-auth-user -am test`、`mvn -q -pl meal-catalog -am test`、`mvn -q -pl meal-order -am test`、`mvn -q -pl meal-promotion -am test`、`mvn -q test`、`mvn -q -DskipTests package`。
- Docker 复验进展：已修复旧 MySQL 表结构下 `data.sql` 先于启动迁移执行导致的 catalog/auth-user/promotion 初始化兼容问题；`catalog`、`auth-user`、`promotion`、`gateway`、`payment`、`fulfillment` 的 ping 和相关模块测试已通过。完整 `scripts/e2e-smoke.ps1` 当前卡在 `meal-queue` 管理接口，已用干净依赖重新打包 `meal-queue`，待 Docker 提权恢复后重建 `meal-queue` 容器并复跑 smoke。

## 剩余增强方向

- 正式前端尚未实现；后端已提供用户端、商家端和运维端所需的基础 API，后续应补用户点餐端、商家工作台和管理/运维端页面。
- Outbox 已开始落地到 order/payment/fulfillment 的 MySQL 本地事件表，并具备手动 dispatch、定时扫描、状态回写和可配置 RocketMQ 发布器；payment 到 order、domain event 到 notify 的真实 MQ 消费均已接入 consumer_record，持久化消费模板已支持 PROCESSING 超时抢占重试和基于保存 payload 的本地重放，真实 RocketMQ 消费者已支持配置最大重消费次数并交由 RocketMQ DLQ 兜底。
- Redis waiting ZSet 和产能 inflight 派生计数已在 `meal-queue` 接入并保留 MySQL 事实源重建/补偿能力；券库存 Redis Lua、领取资格对账补偿、领取修复重试和死信记录已在 `meal-promotion` 接入。后续可继续扩展更多 Redis/MQ 故障注入断言。
- Prometheus/Grafana、业务积压指标和基础压测/故障脚本已完成；后续可继续扩展更细的队列等待 P90/P99、秒杀失败原因分布和故障注入自动断言。

## 后续实施顺序

1. 默认后端微服务主线已完成闭环；下一步应优先建设独立前端，替代 `meal-app` legacy demo，并把 e2e 从脚本验收扩展到浏览器验收。
