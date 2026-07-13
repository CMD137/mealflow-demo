# MealFlow 全链路联调测试报告

测试日期：2026-07-13  
测试环境：本地 Docker Compose + 管理后台 `http://localhost:5173` + 用户端 H5 `http://localhost:5174` + 网关 `http://localhost:8080`  
执行方式：Codex 应用内浏览器页面验证为主，项目主链路 smoke 脚本补充校验内部接口与事件流。

## 1. 测试结论

本次按 `MealFlow-operation-flows.md` 的推荐顺序完成主链路联调。核心闭环通过：

1. 用户端登录、商家浏览、SKU 展示、加购、结算、提交订单。
2. 高峰限流下第二单进入排队。
3. 第一单模拟支付后，通过支付 Outbox 派发推动订单进入待接单。
4. 后台履约接单、出餐后释放产能。
5. 排队 ticket 自动转为正式订单。
6. 后台工作台、履约、队列、券、商品、权限、事件页可打开并展示数据。
7. Prometheus 监控 targets 显示 11/11 服务 UP。

主链路脚本最终结果：

```text
smoke test passed: firstOrder=10039, queuedTicket=10027, convertedOrder=10040
```

## 2. 关键验证记录

| 序号 | 链路 | 结果 | 证据 |
| --- | --- | --- | --- |
| 0 | 基础服务探活 | 通过 | smoke 覆盖 `/ping`、`/orders/ping`、`/queue/ping`、`/catalog/ping`、`/vouchers/ping`、`/payments/ping`、`/fulfillment/ping` |
| 1 | 用户端登录 | 通过 | `13800000001` 登录后进入首页，显示 `Queue User A` |
| 2 | 后台登录与权限菜单 | 通过 | `13800000000` 登录后进入 `/dashboard`，角色为 `MERCHANT_ADMIN`，左侧菜单可见 |
| 3 | 商家与 SKU 浏览 | 通过 | 用户端商家 10 展示 `Signature Beef Rice`、`Grilled Chicken Rice`、`Iced Lemon Tea`，包含价格和库存 |
| 4 | 购物车与结算 | 通过 | 商家页加购后进入 `/checkout`，结算页显示 `Signature Beef Rice × 1` 和可选优惠券 |
| 5 | 用户端排队结果 | 通过 | 页面提交订单后进入 `/order-result`，显示 `已进入排队`、`QT10026` |
| 6 | 秒杀券与券包 | 通过 | smoke 成功领取券；用户端 `/vouchers` 显示 `Lunch Peak Seckill Coupon` 和 `AVAILABLE` 券包 |
| 7 | 签到 | 通过 | 点击签到后按钮变为 `今日已签到`，连续签到变为 `1 天`，积分变为 `6` |
| 8 | 即时成单与排队 | 通过 | smoke 第一单 `10039` 成单，第二单 ticket `10027` 进入排队并最终转单 |
| 9 | 模拟支付与事件派发 | 通过 | smoke 完成 `mock-pay` 和 payment events dispatch，订单推进到待接单 |
| 10 | 后台履约 | 通过 | smoke 对订单 `10039` 执行接单、出餐；后台 `/fulfillment` 显示 `10039 WAIT_RIDER_PICKUP` |
| 11 | ticket 转订单 | 通过 | ticket `10027` 转为订单 `10040`，后台履约页显示 `10040 PENDING_PAYMENT` 且关联 ticket `10027` |
| 12 | 队列与产能 | 通过 | 后台 `/queue` 可查看 ticket 列表；工作台显示 `limit 1`、`held 1` |
| 13 | 商品后台 | 通过 | `/catalog/categories` 展示 `Rice Bowls`、`Drinks`；`/catalog/skus` 展示 3 个上架 SKU |
| 14 | 权限后台 | 通过 | `/employees` 展示演示员工；`/roles` 展示 `CUSTOMER`、`MERCHANT_ADMIN`、`STORE_STAFF` |
| 15 | 事件运维 | 通过 | `/ops/events` 可打开，显示订单/支付/履约事件，事件总数 82 |
| 16 | 用户通知 | 通过 | 用户端 `/messages` 可查看订单创建、支付、接单、出餐通知 |
| 17 | 监控 | 部分通过 | Prometheus `/targets` 显示 `mealflow-services (11/11 up)`；Grafana 入口可打开但未登录验证 dashboard |

## 3. 发现问题

| 优先级 | 问题 | 现象 | 建议 |
| --- | --- | --- | --- |
| P1 | 用户端订单列表商品名为空 | `/orders` 中订单项显示 `undefined×1`，但订单金额、商家、状态正常 | 检查订单列表 DTO 与前端字段映射，重点看商品快照名称字段 |
| P1 | 后台新增优惠券保存失败 | `/promotion/vouchers` 点击新增券后，类型字段是文本框且默认值为 `AMOUNT_OFF`；填写名称后直接保存会报 `Request failed with status code 500` | 当前业务只有秒杀券领取链路，`NORMAL` 缺少对应的普通券发放、领取和使用闭环，未来也不再考虑普通券类型设计；建议删除或隐藏类型字段，系统只保留 `SECKILL` 一种券类型，并移除 `NORMAL`/`AMOUNT_OFF` 等无效类型入口；后端也应将业务参数错误返回为可读的 400/业务错误而不是 500 |
| P2 | 后台订单管理页列表为空 | `/orders` 页面显示 `No Data`，但 `/fulfillment` 可看到订单 `10039/10040` 等数据 | 检查后台订单管理查询默认参数、merchantId 传参或响应结构适配 |
| P2 | 后台 SKU 库存列为空 | `/catalog/skus` 中 3 个 SKU 的库存列未显示；用户端商家页能显示库存 | 检查后台 SKU 列表字段名与库存字段映射 |
| P3 | 后台通知中心无消息 | `/notify/messages` 显示 `No Data`，用户端 `/messages` 有订单通知 | 确认后台通知页是否按当前管理员用户过滤；若预期展示全量，应调整查询范围 |
| P3 | Grafana 面板未完成验证 | `http://localhost:3000` 可打开登录页，但本次未登录验证 `MealFlow Overview` 面板 | 补充 Grafana 演示账号或免登录只读访问后复测 |

## 4. 本次生成的数据

- 页面手动提交产生排队 ticket：`QT10026`
- smoke 第一单：`10039`
- smoke 排队 ticket：`10027`
- smoke 转化订单：`10040`

## 5. 结论

MealFlow 当前核心业务闭环可联调通过：登录、浏览、加购、下单、排队、支付事件、履约释放产能、ticket 转订单均已验证。建议优先修复用户端订单列表商品名、后台新增优惠券保存失败和后台订单管理列表问题；其中新增优惠券问题会阻断后台运营新增券流程。优惠券模型建议收敛为单一秒杀券类型，不再保留或规划 `NORMAL` 普通券设计，避免出现没有业务闭环的类型选项。
