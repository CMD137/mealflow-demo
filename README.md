# MealFlow

MealFlow 是一个面向外卖点餐、午晚高峰排队和商家履约的微服务项目。当前仓库已经包含后端微服务、管理后台和用户端 H5，不再只是 legacy demo。

## 当前状态

- 后端微服务主线已完成核心闭环。
- 管理后台已实现，目录为 `meal-web`，默认端口 `5173`。
- 用户端移动 H5 已实现，目录为 `meal-user-web`，默认端口 `5174`。
- Docker Compose 已覆盖 MySQL、Redis、RocketMQ、Nacos、Prometheus、Grafana、网关和所有业务服务。
- 最新验收记录见 [docs/MealFlow-delivery-checklist.md](docs/MealFlow-delivery-checklist.md)。

## 已覆盖业务

- 用户登录、地址、通知消息。
- 商家浏览、商品分类、SKU 浏览、库存和上下架。
- 购物车增删改查、选中状态和清空。
- 优惠券秒杀领取、券包、锁定、核销和释放。
- 下单、幂等提交、排队票据、产能令牌。
- 模拟支付、支付事件消费、订单状态推进。
- 商家接单、出餐、取餐、送达。
- 出餐释放产能后自动将等待中的 ticket 转为正式订单。
- 后台员工、角色、权限菜单。
- Outbox、consumer record、事件重放、通知投递记录。
- Prometheus 指标和 Grafana 基础面板。

## 后端启动

Docker 已启动时，先确认服务状态：

```powershell
docker compose ps
```

如需重新构建并启动：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dmaven.repo.local=.m2repo' -q -DskipTests package
docker compose up -d --build
```

后端网关：

```text
http://localhost:8080
```

基础检查：

```text
GET http://localhost:8080/ping
GET http://localhost:8080/orders/ping
GET http://localhost:8080/catalog/ping
```

## 前端启动

推荐使用仓库根目录的一键脚本。它会强制重启管理后台和用户端，清理 Vite 缓存并重新优化依赖：

```powershell
.\start-frontend.cmd
```

访问地址：

```text
管理后台：http://localhost:5173/
用户端 H5：http://localhost:5174/
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

## 验收命令

后端编译：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dmaven.repo.local=.m2repo' -q -DskipTests compile
```

管理后台构建：

```powershell
npm.cmd --prefix meal-web run build
```

用户端构建：

```powershell
npm.cmd --prefix meal-user-web run build
```

后端端到端 smoke：

```powershell
powershell.exe -ExecutionPolicy Bypass -File scripts/e2e-smoke.ps1 -BaseUrl http://localhost:8080
```

该脚本覆盖：服务 ping、登录、秒杀券、产能限流、第一单成单、第二单排队、模拟支付、支付事件消费、商家接单出餐、排队 ticket 转订单。

## 重要文档

- [docs/MealFlow-implementation-status.md](docs/MealFlow-implementation-status.md)：后端实现状态。
- [docs/MealFlow-frontend-integration-status.md](docs/MealFlow-frontend-integration-status.md)：前端集成状态。
- [docs/MealFlow-delivery-checklist.md](docs/MealFlow-delivery-checklist.md)：最终交付验收清单。
- [docs/MealFlow-operation-flows.md](docs/MealFlow-operation-flows.md)：按功能拆分的完整操作链路联调手册。
- [docs/MealFlow-storage-config.md](docs/MealFlow-storage-config.md)：上传与存储配置。
- [docs/MealFlow-api-events.md](docs/MealFlow-api-events.md)：API 和事件契约。

## 环境和密钥

- `.env.example` 和 `**/.env.example` 可以提交到 Git。
- `.env`、`.env.*`、`.env.local` 不应提交。
- OSS AccessKey、Secret 等真实密钥只放在本地环境变量或 `.env.local`。
- 上传目录 `uploads/` 已被 Git 忽略。

## 后续可选增强

当前项目主体已经可运行、可演示、可验收。后续更适合作为增强项继续推进：

- Playwright 浏览器自动化 e2e。
- 用户端地址簿编辑、订单取消、再来一单、支付状态轮询。
- 管理后台大表分页、筛选条件持久化和更细粒度表单校验。
- 更完整的移动端截图验收和演示录屏。
- 更细的 Grafana 面板和故障注入断言。
