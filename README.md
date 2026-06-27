# MealFlow

MealFlow 是一个面向午晚高峰履约的外卖交易平台。当前仓库正在从 legacy 单进程 MVP 演进为真实微服务工程：`meal-gateway` 和各业务服务已经拆成独立 Spring Boot 模块，旧的 `meal-app` 保留为本地闭环演示。

## 已实现能力

- Maven 多模块：`meal-common`、`meal-infra`、`meal-gateway`、各业务服务、`meal-app`
- 独立服务骨架：auth-user、merchant、catalog、cart、order、queue、promotion、payment、fulfillment、notify
- Gateway 静态路由：`/orders/**`、`/queue/**`、`/vouchers/**` 等前缀转发到独立服务
- 独立微服务主链路：order 通过 HTTP 编排 catalog、promotion、queue、payment，fulfillment 出餐释放 queue 产能
- 用户、商户、购物车、通知服务已提供最小可用业务接口
- 统一响应、业务异常、traceId、状态码枚举
- requestId 幂等模板、Outbox 事件记录、消费幂等模板
- 商品库存预占、确认、释放
- 优惠券秒杀领取、券包、锁定、确认、释放
- `QueueTicket` 业务排队和 `capacity_token` 产能令牌
- 订单提交、重复提交幂等、排队转订单
- 模拟支付成功后推进订单并确认库存和券
- 商户接单、出餐、取餐、送达
- 出餐完成释放产能，并自动放行等待中的 ticket

## 快速启动

```powershell
mvn -q test
mvn -q -pl meal-app -am -DskipTests package
java -jar meal-app\target\meal-app-0.1.0-SNAPSHOT.jar
```

启动微服务骨架：

```powershell
.\scripts\start-microservices.ps1
```

如果本机 Maven settings 指向不可写目录，可使用仓库内临时 settings：

```powershell
mvn -s .mvn-settings.xml -q test
```

启动后验证：

```text
GET http://localhost:8080/ping
GET http://localhost:8080/orders/ping
GET http://localhost:8090/demo/state
```

## 演示主线

1. `POST /orders/submit` 创建第一个订单，占用商户产能。
2. 再提交订单，在产能满载时返回 `QUEUED` 和 `ticketId`。
3. `POST /payments/{payOrderId}/mock-pay` 模拟支付成功。
4. `POST /fulfillment/orders/{orderId}/accept` 商户接单，此时不释放产能。
5. `POST /fulfillment/orders/{orderId}/meal-ready` 出餐完成，释放产能。
6. 系统自动将等待中的 ticket 推进到 `ORDER_CREATED`，并创建正式订单。

完整请求示例见 [scripts/demo.http](scripts/demo.http)。

## 设计说明

当前分两层推进：

- `meal-app` 是 legacy 单进程闭环演示，端口 `8090`。
- `meal-gateway` 和 `meal-*` 业务模块是真实微服务骨架，gateway 端口 `8080`，业务服务端口 `8101` 到 `8110`。

legacy demo 代码按服务边界分包：

- `catalog` 只管理商品库存和预占
- `promotion` 只管理券、券包和券锁
- `queue` 只管理排队票据和产能令牌
- `order` 负责编排下单和订单状态机
- `payment` 管支付单和支付成功事件
- `fulfillment` 管商户和配送履约动作

后续会把这些包中的业务实现迁移到对应独立服务，接口契约继续对齐 `docs/MealFlow-api-events.md`。当前完成情况和剩余清单见 [docs/MealFlow-implementation-status.md](docs/MealFlow-implementation-status.md)。
