# MealFlow 交付验收清单

更新时间：2026-07-13

## 结论

当前项目已经达到“主体功能完成，可本地运行、演示和验收”的状态。

已完成范围：

- 后端微服务主链路。
- 管理后台 `meal-web`。
- 用户端移动 H5 `meal-user-web`。
- Docker Compose 运行环境。
- 前端一键重启脚本。
- 端到端 smoke 验收脚本。
- 按功能拆分的操作链路联调手册：`docs/MealFlow-operation-flows.md`。

仍建议作为增强继续推进的内容：

- 浏览器自动化 e2e。
- 用户端更多便捷交互，例如地址簿编辑、订单取消、再来一单。
- 管理后台大表分页和筛选条件持久化。
- 更完整的生产级告警和故障注入断言。

## 运行入口

后端网关：

```text
http://localhost:8080
```

管理后台：

```text
http://localhost:5173/
```

用户端 H5：

```text
http://localhost:5174/
```

Grafana：

```text
http://localhost:3000/
```

Prometheus：

```text
http://localhost:9090/
```

## 启动方式

后端 Docker：

```powershell
docker compose up -d --build
```

前端：

```powershell
.\start-frontend.cmd
```

停止前端：

```powershell
.\start-frontend.cmd -Stop
```

## 演示账号

管理后台：

```text
手机号：13800000000
验证码：demo 或任意值
```

用户端：

```text
手机号：13800000001
验证码：任意值
```

## 已验证命令

### Docker 状态

命令：

```powershell
docker compose ps
```

结果：

- MySQL healthy。
- Redis、RocketMQ、Nacos 已运行。
- Gateway 已运行。
- auth-user、merchant、catalog、cart、order、queue、promotion、payment、fulfillment、notify 均已运行。
- Prometheus、Grafana 已运行。

### 后端编译

命令：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dmaven.repo.local=.m2repo' -q -DskipTests compile
```

结果：通过。

### 管理后台构建

命令：

```powershell
npm.cmd --prefix meal-web run build
```

结果：通过。

备注：存在 Rollup chunk size 和第三方 PURE 注释提示，不影响构建产物。

### 用户端 H5 构建

命令：

```powershell
npm.cmd --prefix meal-user-web run build
```

结果：通过。

### 后端 e2e smoke

命令：

```powershell
powershell.exe -ExecutionPolicy Bypass -File scripts/e2e-smoke.ps1 -BaseUrl http://localhost:8080
```

结果：通过。

本次输出：

```text
smoke test passed: firstOrder=10037, queuedTicket=10025, convertedOrder=10038
```

覆盖链路：

- Gateway 和核心服务 ping。
- 种子商品检查。
- 管理员和用户登录。
- 秒杀券领取和券包查询。
- 商户产能限制。
- 第一单立即创建。
- 第二单进入排队。
- 模拟支付。
- 支付事件消费。
- 商家接单和出餐。
- 产能释放后排队 ticket 转正式订单。

## 前端白屏修复验收

已处理问题：

- 登录公开页鉴权初始化顺序问题。
- Vue 应用早于路由准备完成时挂载的问题。
- 管理后台 Element Plus 依赖预构建缓存缺失问题。
- 用户端 Vue Router 开发版 devtools 空节点崩溃问题。
- 未知路由没有兜底导致的空白页问题。
- 前端脚本不会强制重启和清缓存导致的旧服务复现问题。

已验证：

- `http://localhost:5173/dashboard` 正常。
- `http://localhost:5173/login/consumer` 会重定向到 `http://localhost:5173/login`，不再白屏。
- `http://localhost:5174/` 会进入用户端登录页或登录后首页，不再白屏。

## 功能覆盖

### 用户端

- 登录。
- 首页商家浏览。
- 商家商品浏览。
- 购物车。
- 优惠券领取和券包。
- 签到。
- 结算和下单。
- 排队提示。
- 模拟支付。
- 订单列表和订单详情。
- 通知消息。
- 我的页面。

### 管理后台

- 登录。
- 工作台。
- 商户资料。
- 产能配置。
- 商品分类。
- SKU 管理。
- 订单管理。
- 履约工作台。
- 排队和产能。
- 优惠券管理。
- 用户券包。
- 员工管理。
- 角色权限。
- 通知中心。
- 事件运维。

### 后端

- Gateway 鉴权和路由。
- MyBatis 持久化。
- Redis 秒杀和队列热索引。
- RocketMQ outbox 和消费幂等。
- MySQL 事实表。
- Prometheus 指标。
- Grafana 面板。

## 提交记录

最近关键提交：

```text
97472a2 fix(frontend): 修复前端白屏与启动缓存问题
```

提交作者：

```text
CMD137 <2992456841@qq.com>
```
