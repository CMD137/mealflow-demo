# MealFlow 前端集成状态

本文记录当前前端交付状态，避免继续沿用旧文档中“正式前端尚未实现”的阶段性描述。

## 当前结论

- 后台管理端已实现，目录为 `meal-web`。
- 用户端移动 H5 已实现，目录为 `meal-user-web`。
- 两个前端已在 `codex/full-frontend-integration` 分支完成集成。
- 后端主链路、后台端构建、用户端构建均已完成验收。
- 2026-07-13 已复验：后端编译、两个前端构建、Docker 运行态和 `scripts/e2e-smoke.ps1` 均通过。
- 前端白屏问题已完成专项修复：包括路由准备后挂载、公开页守卫顺序、Vite 依赖预构建、Vue Router devtools 崩溃规避、未知路由兜底和前端强制重启脚本。

## 后台管理端

后台端使用 Vue 3、Vite、TypeScript、Pinia、Vue Router、Element Plus。

已覆盖页面：

- 登录与权限菜单
- 仪表盘
- 商户资料与产能配置
- 商品分类、SKU、库存、上下架、图片上传
- 订单查询、订单统计、取消、模拟下单、排队结果、模拟支付
- 履约接单、出餐、取餐、送达
- 排队票据与产能令牌
- 优惠券管理与用户券包
- 员工、角色、权限
- 通知消息、投递记录、消费记录、重放/恢复
- Outbox 事件与手动派发

## 用户端移动 H5

用户端使用 Vue 3、Vite、TypeScript、Pinia、Vue Router、Axios，设计目标是移动端真实点餐应用，而不是接口测试控制台。

已覆盖页面：

- 登录
- 首页商家浏览
- 商家商品浏览
- 购物车增删改查、选中状态、合计
- 领取和查看优惠券
- 提交订单并使用优惠券
- 高峰期排队提示
- 立即成单后的模拟支付
- 订单列表与订单详情
- 通知消息
- 我的页面

设计规范见 `docs/MealFlow-user-h5-design.md`。

## 环境与密钥

- `meal-web/.env.example` 和 `meal-user-web/.env.example` 可以提交到 Git。
- `.env`、`.env.*` 默认忽略。
- `!**/.env.example` 保证示例环境文件不会被误忽略。
- 本地真实配置、OSS Key、AccessKey、Secret 等只应写入 `.env.local` 或后端运行环境变量，不提交到 Git。
- 上传文件目录 `uploads/` 已忽略。

## 验收记录

前端构建：

```powershell
npm.cmd run build
npm.cmd --prefix meal-user-web run build
```

后端编译：

```powershell
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot'
$env:PATH="$env:JAVA_HOME\bin;$env:PATH"
mvn '-Dmaven.repo.local=.m2repo' -q -DskipTests compile
```

运行态端到端验收：

```powershell
powershell.exe -ExecutionPolicy Bypass -File scripts/e2e-smoke.ps1 -BaseUrl http://localhost:8080
```

最近一次 e2e 覆盖：

- gateway 与核心服务 ping
- 种子商品检查
- 管理员与用户登录
- 秒杀券领取与券包查询
- 商户产能限制
- 第一单立即创建
- 第二单进入排队
- 模拟支付
- 支付事件消费
- 商户接单与出餐
- 产能释放后排队 ticket 转订单

最近一次通过输出：

```text
smoke test passed: firstOrder=10037, queuedTicket=10025, convertedOrder=10038
```

## 后续可选增强

- 移动端浏览器截图验收和交互录屏。
- 前端 e2e 自动化测试，例如 Playwright。
- 用户端地址簿编辑页。
- 用户端订单取消、再来一单和支付状态轮询。
- 后台大表分页、筛选条件持久化和更细粒度的表单校验。
