# MealFlow 操作链路联调手册

更新时间：2026-07-13

本文按功能域整理 MealFlow 的完整操作逻辑，便于逐项联调测试。默认后端网关为 `http://localhost:8080`，管理后台为 `http://localhost:5173`，用户端 H5 为 `http://localhost:5174`。

## 0. 联调准备

### 0.1 启动顺序

1. 启动后端和基础设施：

```powershell
docker compose up -d --build
```

2. 启动前端：

```powershell
.\start-frontend.cmd
```

3. 验证基础服务：

```text
GET /ping
GET /orders/ping
GET /catalog/ping
GET /queue/ping
GET /vouchers/ping
GET /payments/ping
GET /fulfillment/ping
```

### 0.2 演示账号

管理后台：

```text
手机号：13800000000
验证码/密码：demo 或 123456
角色：MERCHANT_ADMIN
商户：10
```

用户端：

```text
用户一：13800000001
用户二：13800000002
验证码/密码：任意值或 123456
```

### 0.3 鉴权规则

1. 前端登录调用 `POST /auth/login`。
2. 后端返回 `token`、角色、商户、权限等信息。
3. 前端后续请求携带：

```text
Authorization: Bearer <token>
```

4. Gateway 校验 token 后向下游注入可信身份：

```text
X-User-Id
X-Role
X-Merchant-Id
```

联调接口时优先走 `Authorization`，不要手写内部身份头，除非明确测试内部服务。

## 1. 用户端登录链路

前端入口：`http://localhost:5174/login`

### 操作逻辑

1. 用户输入手机号和验证码。
2. 用户点击登录。
3. 前端调用 `POST /auth/login`。
4. 前端保存 token 和登录信息。
5. 前端跳转到原 redirect 地址或 `/`。
6. 进入受保护页面时，若本地有 token 但没有用户信息，调用 `GET /users/me` 补全用户资料。
7. token 失效或 `GET /users/me` 失败时，清理登录态并回到 `/login`。

### 接口顺序

```text
POST /auth/login
GET /users/me
```

### 验证点

- 登录后本地有 token。
- `/`、`/cart`、`/orders` 等受保护页面可打开。
- 坏 token 会回到 `/login`，不会白屏。

## 2. 管理后台登录和权限菜单链路

前端入口：`http://localhost:5173/login`

### 操作逻辑

1. 管理员输入手机号和验证码。
2. 前端调用 `POST /auth/login`。
3. 登录成功后保存 token。
4. 跳转 `/dashboard`。
5. 路由守卫调用 `GET /users/me` 补全用户、角色和商户信息。
6. 后台布局加载菜单和权限。
7. 用户访问需要权限的页面时，前端检查 `to.meta.permission`。
8. 无权限时跳转 `/forbidden`。

### 接口顺序

```text
POST /auth/login
GET /users/me
GET /auth/admin/menus
```

### 验证点

- `roleCode` 为 `MERCHANT_ADMIN`。
- 左侧菜单能显示。
- `/dashboard` 正常。
- 未知后台路径会回 `/dashboard`。
- `/login/consumer` 会回 `/login`，不会白屏。

## 3. 用户端首页和商家浏览链路

前端入口：`http://localhost:5174/`

### 操作逻辑

1. 用户进入首页。
2. 前端加载商家列表。
3. 用户点击商家。
4. 前端进入 `/merchant/:merchantId`。
5. 加载商家详情、商品分类和 SKU。
6. 前端只展示可售商品和分类。

### 接口顺序

```text
GET /merchants
GET /merchants/{merchantId}
GET /catalog/merchants/{merchantId}/categories
GET /catalog/merchants/{merchantId}/skus
```

### 验证点

- 商家列表不为空。
- 商家详情营业状态展示正确。
- SKU 有价格、库存、上下架状态。
- 下架商品不会用于正常下单。

## 4. 用户端购物车链路

前端入口：

- 商家页：`/merchant/:merchantId`
- 购物车页：`/cart`

### 操作逻辑

1. 用户在商家页选择 SKU。
2. 点击加购。
3. 前端调用新增购物车项接口。
4. 用户进入购物车。
5. 前端查询购物车列表。
6. 用户可修改数量、勾选、删除、清空。
7. 结算时只提交选中项。

### 接口顺序

```text
POST /cart/items
GET /cart
PUT /cart/items/{cartItemId}
PUT /cart/items/{cartItemId}/selected
DELETE /cart/items/{cartItemId}
DELETE /cart
```

### 验证点

- 加购后购物车数量变化。
- 修改数量后金额变化。
- 取消选中后不参与结算。
- 删除和清空后列表刷新。
- 不同用户购物车互不影响。

## 5. 用户端优惠券领取和券包链路

前端入口：`http://localhost:5174/vouchers`

### 操作逻辑

1. 用户进入优惠券页。
2. 前端加载可领取优惠券列表。
3. 用户点击领取秒杀券。
4. 后端执行资格校验。
5. Docker 环境下 Redis Lua 原子扣减库存并写入一人一券集合。
6. 后端落 MyBatis 领取事实和用户券包。
7. 用户刷新券包。

### 接口顺序

```text
GET /vouchers
POST /vouchers/{voucherId}/seckill
GET /vouchers/wallet
```

### 状态变化

```text
voucher stock: 可领取库存减少
voucher_claim: CLAIMED
user_voucher: AVAILABLE
```

### 验证点

- 同一用户重复领取同一张券应被拒绝或返回幂等结果。
- 库存不足时不能继续领取。
- 领取后 `GET /vouchers/wallet` 能看到 `AVAILABLE` 券。

## 6. 用户端签到链路

前端入口：`http://localhost:5174/sign`

### 操作逻辑

1. 用户进入签到页。
2. 前端查询当月签到信息。
3. 用户手动点击签到。
4. 后端使用 Redis Bitmap 或持久化数据记录当天签到。
5. 前端刷新连续签到、今日状态和月历状态。

### 接口顺序

```text
GET /users/sign
POST /users/sign
GET /users/sign
```

### 验证点

- 签到必须由用户手动触发。
- 当天重复签到不应重复增加。
- 今日状态从未签到变为已签到。

## 7. 用户端结算和立即成单链路

前端入口：

- `/cart`
- `/checkout`
- `/order-result`

### 前置条件

- 用户已登录。
- 购物车有已选中的商品。
- 商户产能未满。
- SKU 上架且库存充足。
- 如使用优惠券，券状态为 `AVAILABLE`。

### 操作逻辑

1. 用户从购物车进入结算页。
2. 前端加载选中购物车项和可用券。
3. 用户确认地址、商品和优惠券。
4. 点击提交订单。
5. 前端调用 `POST /orders/submit`，必须传 `requestId`。
6. `order` 服务校验幂等。
7. `order` 服务请求 `catalog` 生成商品快照并预占库存。
8. `order` 服务请求 `promotion` 锁定优惠券。
9. `order` 服务请求 `queue` 申请商户产能。
10. 若产能可用，`queue` 返回 `READY` 和 `capacityTokenId`。
11. `order` 创建订单，状态为 `PENDING_PAYMENT`。
12. `order` 请求 `payment` 创建支付单。
13. `order` 绑定产能 token 到订单。
14. 前端进入下单结果页。

### 接口顺序

用户侧显式接口：

```text
GET /cart
GET /vouchers/wallet
POST /orders/submit
```

服务内部编排：

```text
POST /catalog/internal/stocks/snapshots/{merchantId}
POST /catalog/internal/stocks/reserve
POST /vouchers/internal/lock
POST /queue/internal/capacity/apply
POST /payments/internal/create
POST /queue/internal/capacity/{capacityTokenId}/bind-order
```

### 成功返回

```text
mode = ORDER_CREATED
orderId 不为空
payOrderId 不为空
status = PENDING_PAYMENT
```

### 验证点

- 重复提交同一个 `requestId` 不应生成多笔订单。
- 库存预占记录存在。
- 优惠券锁定记录存在。
- capacity token 为 `HELD`。
- 订单列表能查到新订单。

## 8. 用户端高峰排队链路

前端入口：

- `/checkout`
- `/order-result`
- `/orders`

### 前置条件

管理后台将商户 10 的排队上限设置为 1，且已有订单占用产能。

### 操作逻辑

1. 用户提交订单。
2. `order` 仍先做幂等和商品校验。
3. `queue` 申请产能时发现商户产能满。
4. `queue` 创建等待 ticket，状态为 `WAITING`。
5. `order` 返回排队结果，不创建正式订单。
6. 前端显示排队号、前方人数和预计等待时间。

### 接口顺序

```text
POST /orders/submit
GET /queue/tickets/{ticketId}
GET /orders
```

### 返回结果

```text
mode = QUEUED
ticketId 不为空
ticketNo 不为空
aheadCount >= 0
waitSeconds >= 0
```

### 验证点

- 排队返回时没有 `orderId` 和 `payOrderId`。
- ticket 初始状态通常为 `WAITING`。
- 管理后台排队页能看到该 ticket。

## 9. 用户端模拟支付链路

前端入口：

- `/order-result`
- `/orders/:orderId`

### 前置条件

- 订单已经创建。
- 订单状态为 `PENDING_PAYMENT`。
- 支付单状态为 `UNPAID`。

### 操作逻辑

1. 用户点击模拟支付。
2. 前端调用 `POST /payments/{payOrderId}/mock-pay`。
3. `payment` 将支付单状态改为 `PAID`。
4. `payment` 写入本地 Outbox 事件 `PaymentPaid`。
5. 事件 dispatch 到 RocketMQ 或手动 dispatch。
6. `order` 消费 `PaymentPaid`。
7. `order` 将订单推进到 `WAIT_MERCHANT_ACCEPT`。
8. `order` 确认库存和优惠券。
9. `notify` 消费订单事件并生成通知。

### 接口顺序

用户侧：

```text
POST /payments/{payOrderId}/mock-pay
GET /payments/{payOrderId}
GET /orders/{orderId}
```

内部和运维：

```text
GET /payments/internal/events
POST /payments/internal/events/dispatch
POST /orders/internal/consumer-records/recover
```

### 状态变化

```text
payment: UNPAID -> PAID
order: PENDING_PAYMENT -> WAIT_MERCHANT_ACCEPT
stock reservation: RESERVED -> CONFIRMED
voucher lock: LOCKED -> CONFIRMED
```

### 验证点

- 支付后订单最终进入 `WAIT_MERCHANT_ACCEPT`。
- 如果短时间未推进，可在后台事件运维中 dispatch payment 事件。
- 通知中心能看到相关通知。

## 10. 用户端订单查询和取消链路

前端入口：

- `/orders`
- `/orders/:orderId`

### 查询逻辑

1. 用户进入订单列表。
2. 前端调用 `GET /orders`。
3. 点击订单进入详情。
4. 前端调用 `GET /orders/{orderId}`。

### 取消逻辑

1. 用户在可取消状态点击取消。
2. 前端调用 `POST /orders/{orderId}/cancel`。
3. `order` 校验订单状态。
4. 可取消状态包括 `PENDING_PAYMENT`、`WAIT_MERCHANT_ACCEPT`。
5. 后端释放库存、优惠券和产能。
6. 订单状态变为 `CANCELLED`。

### 接口顺序

```text
GET /orders
GET /orders/{orderId}
POST /orders/{orderId}/cancel
```

### 验证点

- 已出餐或已完成订单不能取消。
- 取消后库存预占释放。
- 优惠券锁释放。
- 产能 token 释放。

## 11. 管理后台工作台链路

前端入口：`http://localhost:5173/dashboard`

### 操作逻辑

1. 管理员登录后台。
2. 工作台加载订单统计。
3. 工作台加载排队指标。
4. 展示订单总数、待接单、制作中、配送中、已完成、已取消、营业额和队列指标。

### 接口顺序

```text
GET /orders/admin/statistics?merchantId=10
GET /queue/merchants/10/metrics
```

### 验证点

- 新建订单后统计变化。
- 排队上限调整后队列指标变化。

## 12. 管理后台商户资料和营业状态链路

前端入口：

- `/merchant/profile`
- `/merchant/capacity`

### 商户资料逻辑

1. 页面加载商户列表或当前商户详情。
2. 显示商户名称、营业状态、容量配置。
3. 修改营业状态。

### 产能配置逻辑

1. 管理员进入产能配置页。
2. 设置 `baseCapacity` 和 `manualFactor`。
3. 后端更新商户容量。
4. 如需演示排队，设置队列 limit 为 1。

### 接口顺序

```text
GET /merchants
GET /merchants/{merchantId}
POST /merchants/{merchantId}/business-status
POST /merchants/{merchantId}/capacity
POST /queue/merchants/{merchantId}/limit
GET /queue/merchants/{merchantId}/metrics
```

### 验证点

- 营业状态变化后用户端商户页同步显示。
- 产能 limit 变小后第二单可进入排队。

## 13. 管理后台商品分类链路

前端入口：`/catalog/categories`

### 操作逻辑

1. 加载分类列表。
2. 新增分类。
3. 编辑分类名称、排序、状态。
4. 用户端商家页重新加载分类。

### 接口顺序

```text
GET /catalog/admin/categories
POST /catalog/admin/categories
PUT /catalog/admin/categories/{categoryId}
GET /catalog/merchants/{merchantId}/categories
```

### 验证点

- 新增分类后台可见。
- 启用分类用户端可见。
- 停用分类用户端不应作为正常可选分类展示。

## 14. 管理后台 SKU 管理和图片上传链路

前端入口：`/catalog/skus`

### 操作逻辑

1. 后台加载 SKU 列表。
2. 上传商品图片。
3. 新增 SKU，填写分类、名称、价格、库存、图片和状态。
4. 修改 SKU。
5. 上架或下架 SKU。
6. 修改库存。
7. 用户端商家页刷新商品。

### 接口顺序

```text
GET /catalog/admin/skus
POST /catalog/admin/images
POST /catalog/admin/skus
PUT /catalog/admin/skus/{skuId}
PUT /catalog/admin/skus/{skuId}/status
PUT /catalog/admin/skus/{skuId}/stock
GET /catalog/merchants/{merchantId}/skus
GET /catalog/images/{objectKey}
```

### 验证点

- 图片上传成功返回 objectKey/url。
- OSS 未配置时应走本地上传兜底。
- 上架 SKU 在用户端可见。
- 下架 SKU 下单应被拒绝。
- 库存不足下单应失败。

## 15. 管理后台订单管理链路

前端入口：`/orders`

### 查询逻辑

1. 进入订单管理页。
2. 按商户、用户、状态筛选。
3. 查看订单详情。

### 模拟下单逻辑

1. 后台可以构造用户订单请求。
2. 调用订单提交接口。
3. 根据返回结果展示立即成单或排队。

### 取消和支付逻辑

1. 对可取消订单调用取消。
2. 对待支付订单调用模拟支付。
3. 刷新订单状态。

### 接口顺序

```text
GET /orders/admin
GET /orders/admin/statistics
GET /orders/{orderId}
POST /orders/submit
POST /orders/{orderId}/cancel
POST /payments/{payOrderId}/mock-pay
GET /payments/{payOrderId}
```

### 验证点

- 筛选条件生效。
- 模拟下单成功后订单列表出现新订单。
- 模拟支付后状态从 `PENDING_PAYMENT` 推进。
- 取消后状态为 `CANCELLED`。

## 16. 管理后台履约链路

前端入口：`/fulfillment`

### 前置条件

订单状态为 `WAIT_MERCHANT_ACCEPT`。

### 操作逻辑

1. 商家接单。
2. 订单状态进入制作中。
3. 商家出餐。
4. 出餐完成释放产能。
5. 若有等待 ticket，queue 将 ticket 推进为可处理并触发转订单。
6. 骑手取餐。
7. 配送送达。

### 接口顺序

```text
POST /fulfillment/orders/{orderId}/accept
POST /fulfillment/orders/{orderId}/meal-ready
POST /fulfillment/orders/{orderId}/picked-up
POST /fulfillment/orders/{orderId}/delivered
GET /fulfillment/orders/internal/operations
```

### 状态变化

```text
WAIT_MERCHANT_ACCEPT -> MERCHANT_ACCEPTED -> WAIT_RIDER_PICKUP -> RIDER_PICKED_UP -> DELIVERED
```

### 验证点

- 状态必须按顺序推进。
- 出餐完成后 capacity token 释放。
- 有排队 ticket 时会自动转订单。
- 履约操作日志可查。

## 17. 管理后台排队和产能链路

前端入口：`/queue`

### 操作逻辑

1. 查询商户队列指标。
2. 设置商户队列 limit。
3. 查看排队 ticket 列表。
4. 查看 capacity token 列表。
5. 如需重置演示环境，可释放 HELD token。

### 接口顺序

```text
GET /queue/merchants/{merchantId}/metrics
POST /queue/merchants/{merchantId}/limit
GET /queue/internal/tickets
GET /queue/internal/capacity/tokens
POST /queue/internal/capacity/{capacityTokenId}/release
GET /queue/tickets/{ticketId}
POST /queue/tickets/{ticketId}/cancel
```

### 典型测试链路

1. 设置商户 10 的 limit 为 1。
2. 用户一下一单，返回 `ORDER_CREATED`。
3. 用户二下一单，返回 `QUEUED`。
4. 后台查看 ticket 为 `WAITING`。
5. 用户一订单出餐。
6. 后台查看用户二 ticket 变为 `ORDER_CREATED`。

### 验证点

- limit 为 1 时第二单应进入排队。
- token 不应重复释放。
- ticket 取消后状态为 `CANCELLED`。

## 18. 管理后台优惠券管理链路

前端入口：`/promotion/vouchers`

### 操作逻辑

1. 加载优惠券列表。
2. 新建优惠券。
3. 编辑券名称、类型、折扣、库存、状态。
4. 用户端加载可领取券。
5. 用户领取。
6. 后台查看库存和领取结果。

### 接口顺序

```text
GET /vouchers/admin
POST /vouchers/admin
PUT /vouchers/admin/{voucherId}
GET /vouchers
POST /vouchers/{voucherId}/seckill
GET /vouchers/internal/claims
GET /vouchers/internal/claims/retries
POST /vouchers/internal/claims/retries/retry
POST /vouchers/internal/claims/reconcile
```

### 验证点

- 禁用券用户不能继续领取。
- 秒杀券库存扣减准确。
- Redis/MyBatis 不一致时对账任务可记录修复任务。

## 19. 用户券包管理链路

前端入口：

- 用户端 `/vouchers`
- 后台 `/promotion/wallet`

### 操作逻辑

1. 用户领取券。
2. 用户端查询券包。
3. 结算页选择可用券。
4. 下单时锁券。
5. 支付成功确认券。
6. 订单取消释放券。
7. 后台可查看用户券包。

### 接口顺序

```text
GET /vouchers/wallet
POST /vouchers/internal/lock
POST /vouchers/internal/confirm
POST /vouchers/internal/release
GET /vouchers/internal/locks
```

### 状态变化

```text
user_voucher: AVAILABLE -> LOCKED -> USED
取消时：LOCKED -> AVAILABLE
```

### 验证点

- 已使用券不能再次使用。
- 锁券失败时订单不能继续创建。
- 取消订单后券应恢复可用。

## 20. 员工、角色和权限链路

前端入口：

- `/employees`
- `/roles`

### 角色逻辑

1. 加载菜单权限。
2. 加载角色列表。
3. 新建或修改角色。
4. 给角色绑定权限。
5. 员工使用该角色登录。

### 员工逻辑

1. 加载员工列表。
2. 新增员工或复用用户账号。
3. 修改员工角色。
4. 启用或停用员工。
5. 停用后旧 token 校验时失去商户身份。

### 接口顺序

```text
GET /auth/admin/menus
GET /auth/admin/roles
POST /auth/admin/roles
GET /auth/admin/employees
POST /auth/admin/employees
PUT /auth/admin/employees/{employeeId}/role
PUT /auth/admin/employees/{employeeId}/status
POST /auth/internal/tokens/validate
```

### 验证点

- 权限变更后对应菜单和页面权限变化。
- 停用员工后不能继续操作商户后台。
- 无权限页面跳 `/forbidden`。

## 21. 通知中心链路

前端入口：

- 用户端 `/messages`
- 后台 `/notify/messages`

### 操作逻辑

1. 订单、支付、履约等业务动作写入领域事件。
2. notify 消费事件。
3. 根据模板生成站内信和投递记录。
4. 用户端查询消息。
5. 后台查看消息、投递记录、消费记录。
6. 失败或超时消费记录可 recover 或 replay。

### 接口顺序

```text
GET /notify/messages
GET /notify/deliveries
GET /notify/internal/consumer-records
POST /notify/internal/consumer-records/recover
POST /notify/internal/consumer-records/{eventKey}/groups/{consumerGroup}/replay
GET /notify/messages/stream
```

内部创建消息：

```text
POST /notify/internal/messages
POST /notify/internal/events/messages
POST /notify/internal/templates/{templateCode}/messages
```

### 验证点

- 支付成功、接单、出餐等动作后能生成通知。
- 消费记录按 `eventKey + consumerGroup` 幂等。
- replay 不应重复产生业务副作用。

## 22. 事件运维和 Outbox 链路

前端入口：`/ops/events`

### 操作逻辑

1. 订单、支付、履约服务在本地事务内写 Outbox 事件。
2. 事件初始为 `NEW`。
3. 定时任务或手动 dispatch 发送事件。
4. 成功后状态变为 `SENT`。
5. 失败后状态变为 `FAILED` 或等待重试。
6. 消费方写入 consumer record，避免重复消费。

### 接口顺序

订单事件：

```text
GET /orders/internal/events
POST /orders/internal/events/dispatch
POST /orders/internal/consumer-records/recover
POST /orders/internal/consumer-records/{eventKey}/groups/{consumerGroup}/replay
```

支付事件：

```text
GET /payments/internal/events
POST /payments/internal/events/dispatch
```

履约事件：

```text
GET /fulfillment/orders/internal/events
POST /fulfillment/orders/internal/events/dispatch
```

通知消费记录：

```text
GET /notify/internal/consumer-records
POST /notify/internal/consumer-records/recover
POST /notify/internal/consumer-records/{eventKey}/groups/{consumerGroup}/replay
```

### 验证点

- 支付后 payment outbox 有 `PaymentPaid`。
- dispatch 后 order 可消费并推进订单。
- consumer record 超时可 recover。
- replay 不应重复推进已完成订单。

## 23. 图片上传和存储配置链路

前端入口：`/catalog/skus`

### 操作逻辑

1. 管理员选择商品图片。
2. 前端使用 multipart 上传。
3. 后端统一走存储服务。
4. 若配置了 OSS，则上传到 OSS。
5. 若未配置 OSS，则落本地 `uploads/`。
6. 返回 objectKey 和 URL。
7. SKU 保存图片地址。

### 接口顺序

```text
POST /catalog/admin/images
GET /catalog/images/{objectKey}
POST /catalog/admin/skus
PUT /catalog/admin/skus/{skuId}
```

### 验证点

- `.env.local` 或运行环境变量保存 OSS key，不进 Git。
- `.env.example` 可提交。
- `uploads/` 不进 Git。
- 图片 URL 在后台和用户端均可访问。

## 24. 监控链路

前端入口：

- Grafana：`http://localhost:3000`
- Prometheus：`http://localhost:9090`

### 操作逻辑

1. 各服务暴露 `/actuator/prometheus`。
2. Prometheus 抓取服务指标。
3. Grafana 自动 provision Prometheus 数据源。
4. 面板展示服务、Outbox、consumer record、券修复任务等指标。

### 验证点

- Prometheus targets 正常。
- Grafana 有 MealFlow Overview 面板。
- 下单、支付、通知、事件 dispatch 后指标变化。

## 25. 一键主链路联调脚本

脚本：

```powershell
powershell.exe -ExecutionPolicy Bypass -File scripts/e2e-smoke.ps1 -BaseUrl http://localhost:8080
```

覆盖顺序：

1. Gateway 和服务 ping。
2. 查询商户 10 种子商品。
3. 管理员和两个用户登录。
4. 用户领取秒杀券。
5. 强制商户 10 产能 limit 为 1。
6. 清理已有 HELD capacity token。
7. 用户一下第一单，立即成单。
8. 用户二下第二单，进入排队。
9. 用户一模拟支付。
10. dispatch payment outbox。
11. 等待 order 消费支付事件。
12. 管理员接单。
13. 管理员出餐。
14. 验证用户二 ticket 转为正式订单。

通过输出示例：

```text
smoke test passed: firstOrder=10037, queuedTicket=10025, convertedOrder=10038
```

## 26. 推荐分模块联调顺序

按依赖关系建议这样测：

1. 基础服务 ping。
2. 登录和鉴权。
3. 商家和商品浏览。
4. 后台分类、SKU、库存、上下架。
5. 用户购物车。
6. 优惠券领取和券包。
7. 签到。
8. 立即成单。
9. 模拟支付和支付事件。
10. 后台履约。
11. 排队和产能。
12. 排队 ticket 转订单。
13. 订单取消和资源释放。
14. 通知消息。
15. Outbox、consumer record、recover、replay。
16. 图片上传和存储。
17. 监控面板。

## 27. 常用状态速查

订单：

```text
PENDING_PAYMENT
WAIT_MERCHANT_ACCEPT
MERCHANT_ACCEPTED
WAIT_RIDER_PICKUP
RIDER_PICKED_UP
DELIVERED
CANCELLED
```

支付：

```text
UNPAID
PAYING
PAID
CLOSED
```

排队 ticket：

```text
WAITING
READY
PROCESSING
ORDER_CREATED
CANCELLED
EXPIRED
```

产能 token：

```text
HELD
RELEASED
```

用户券：

```text
AVAILABLE
LOCKED
USED
EXPIRED
```

Outbox：

```text
NEW
SENDING
SENT
FAILED
```

消费记录：

```text
PROCESSING
SUCCESS
FAILED
TIMEOUT
```

## 28. 常见问题定位

### 页面白屏

1. 先看 URL 是否正确：管理后台 `5173`，用户端 `5174`。
2. 看浏览器控制台是否有模块加载错误。
3. 看 `#app` 是否有子节点。
4. 看是否落入未知路由。
5. 执行 `.\start-frontend.cmd` 强制重启并清 Vite 缓存。

### 下单失败

优先检查：

1. token 是否有效。
2. SKU 是否上架。
3. 库存是否充足。
4. 优惠券是否可用。
5. 商户是否营业。
6. requestId 是否重复但请求体不同。

### 支付后订单不推进

优先检查：

1. `GET /payments/{payOrderId}` 是否为 `PAID`。
2. `GET /payments/internal/events` 是否有 `PaymentPaid`。
3. 执行 `POST /payments/internal/events/dispatch`。
4. 查看 order consumer record。
5. 执行 recover 或 replay。

### 排队不转单

优先检查：

1. 第一单是否占用产能。
2. 第二单是否产生 ticket。
3. 第一单是否完成出餐。
4. capacity token 是否释放。
5. ticket 状态是否从 `WAITING` 变为 `ORDER_CREATED`。
