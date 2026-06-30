# MealFlow Vue3 前端设计与开发规范

本文定义 MealFlow 前端的实现边界、页面结构、交互规范和后端接口映射。后续前端开发应先满足本文描述的业务使用逻辑，再考虑视觉细节。

## 定位

- 前端使用 Vue 3 构建，作为 MealFlow 后端微服务的后台管理与商家工作台。
- 页面采用经典后台布局：左侧菜单、顶部栏、右侧主内容区域。
- 不做营销首页，不做由卡片堆叠而成的接口测试控制台。
- 每个页面都应围绕真实业务任务组织：查询、筛选、新增、编辑、状态流转、上传、确认操作和结果反馈。
- 前端第一阶段只做后台/商家/运维视图，不做正式用户点餐移动端。

## 技术栈

- Vue 3
- Vite
- TypeScript
- Vue Router
- Pinia
- Element Plus
- Axios

工程目录建议为 `meal-web/`，与后端同仓库管理，便于联调 Docker、网关地址和接口文档。

## 运行目标

默认对接网关：

```text
http://localhost:8080
```

开发环境变量：

```properties
VITE_API_BASE_URL=http://localhost:8080
```

请求统一走 `VITE_API_BASE_URL`。除登录、公开商品浏览和图片访问外，其余请求应携带 `Authorization: Bearer {token}`。

## 布局规范

页面结构固定为：

```text
App
├── LoginView
└── AdminLayout
    ├── Sidebar
    ├── Topbar
    └── RouterView
```

左侧菜单：

- 固定宽度，默认 220px。
- 使用图标 + 文本菜单项。
- 支持按后端返回的 `menus` 或 `permissions` 控制可见菜单。
- 菜单按业务域分组，不按接口模块机械排列。

顶部栏：

- 显示当前登录用户、角色、商家 ID。
- 提供退出登录。
- 可显示当前页面标题和简短状态信息。

右侧主内容：

- 使用页面标题、筛选区、主表格/表单、抽屉或弹窗。
- 表格页应有明确筛选、刷新、空状态、加载状态和错误提示。
- 操作按钮放在表格行尾或页面右上角。
- 避免把每个接口做成一个孤立卡片。

## 视觉规范

- 整体使用浅色后台风格，主色保持克制。
- 页面内容以表格、表单、步骤状态、标签和弹窗为主。
- 卡片只用于统计摘要或少量信息分组，不用于堆砌页面。
- 表单字段应有明确标签、校验和提交反馈。
- 状态字段使用标签展示，例如 `ON_SHELF`、`PENDING_PAYMENT`、`READY`。
- 危险操作使用确认弹窗，例如取消订单、禁用员工、关闭支付单。

## 路由与菜单

第一阶段路由如下：

| 路由 | 菜单 | 目标 |
| --- | --- | --- |
| `/login` | 无 | 登录并保存 token、用户、权限 |
| `/dashboard` | 工作台 | 查看订单、队列、券、通知的概览 |
| `/merchant/profile` | 商家管理 | 查看商家列表和当前商家基础信息 |
| `/merchant/capacity` | 产能配置 | 设置商家产能上限和营业状态 |
| `/catalog/categories` | 商品类目 | 类目列表、新增、编辑、启停 |
| `/catalog/skus` | 商品管理 | SKU 列表、新增、编辑、上下架、库存、图片上传 |
| `/orders` | 订单管理 | 条件查询订单、查看详情、取消、状态跟踪 |
| `/fulfillment` | 履约工作台 | 接单、出餐、取餐、送达 |
| `/queue` | 排队与产能 | 查看 ticket、产能 token、商家队列指标 |
| `/promotion/vouchers` | 优惠券管理 | 券列表、新增、编辑、启用/禁用 |
| `/promotion/wallet` | 用户券包 | 查询当前用户券包，便于演示领券到下单 |
| `/employees` | 员工管理 | 员工列表、新增、角色调整、启停 |
| `/roles` | 角色权限 | 角色列表、新增角色、菜单权限选择 |
| `/notify/messages` | 通知中心 | 消息列表、投递记录、SSE 状态 |
| `/ops/events` | 事件运维 | Outbox、consumer record、重放/恢复入口 |

第一阶段必须完成的核心页面：

1. 登录
2. 商品类目
3. 商品管理
4. 订单管理
5. 履约工作台
6. 排队与产能
7. 优惠券管理
8. 员工/角色权限

通知中心和事件运维可作为第二批页面，但菜单结构应预留。

## 后端接口映射

### 登录与权限

| 页面动作 | 方法 | 接口 |
| --- | --- | --- |
| 登录 | `POST` | `/auth/login` |
| 当前用户 | `GET` | `/users/me` |
| 菜单列表 | `GET` | `/auth/admin/menus` |
| 角色列表 | `GET` | `/auth/admin/roles` |
| 新增角色 | `POST` | `/auth/admin/roles` |
| 员工列表 | `GET` | `/auth/admin/employees` |
| 新增员工 | `POST` | `/auth/admin/employees` |
| 调整员工角色 | `PUT` | `/auth/admin/employees/{employeeId}/role` |
| 调整员工状态 | `PUT` | `/auth/admin/employees/{employeeId}/status` |

### 商家与产能

| 页面动作 | 方法 | 接口 |
| --- | --- | --- |
| 商家列表 | `GET` | `/merchants` |
| 商家详情 | `GET` | `/merchants/{merchantId}` |
| 设置容量 | `POST` | `/merchants/{merchantId}/capacity` |
| 设置营业状态 | `POST` | `/merchants/{merchantId}/business-status` |
| 队列指标 | `GET` | `/queue/merchants/{merchantId}/metrics` |
| 设置排队上限 | `POST` | `/queue/merchants/{merchantId}/limit` |

### 商品

| 页面动作 | 方法 | 接口 |
| --- | --- | --- |
| 用户端类目 | `GET` | `/catalog/merchants/{merchantId}/categories` |
| 用户端 SKU | `GET` | `/catalog/merchants/{merchantId}/skus` |
| 后台类目 | `GET` | `/catalog/admin/categories` |
| 新增类目 | `POST` | `/catalog/admin/categories` |
| 编辑类目 | `PUT` | `/catalog/admin/categories/{categoryId}` |
| 后台 SKU | `GET` | `/catalog/admin/skus` |
| 上传图片 | `POST` | `/catalog/admin/images` |
| 新增 SKU | `POST` | `/catalog/admin/skus` |
| 编辑 SKU | `PUT` | `/catalog/admin/skus/{skuId}` |
| 上下架 | `PUT` | `/catalog/admin/skus/{skuId}/status` |
| 设置库存 | `PUT` | `/catalog/admin/skus/{skuId}/stock` |
| 图片访问 | `GET` | `/catalog/images/{objectKey}` |

商品管理页应提供：

- 类目筛选。
- 上架状态筛选。
- 图片上传并回填 URL。
- 新增/编辑 SKU 表单。
- 上下架按钮。
- 库存快捷编辑。

### 订单与履约

| 页面动作 | 方法 | 接口 |
| --- | --- | --- |
| 提交订单演示 | `POST` | `/orders/submit` |
| 订单详情 | `GET` | `/orders/{orderId}` |
| 我的订单 | `GET` | `/orders` |
| 后台订单查询 | `GET` | `/orders/admin` |
| 订单统计 | `GET` | `/orders/admin/statistics` |
| 取消订单 | `POST` | `/orders/{orderId}/cancel` |
| 商家接单 | `POST` | `/fulfillment/orders/{orderId}/accept` |
| 出餐完成 | `POST` | `/fulfillment/orders/{orderId}/meal-ready` |
| 取餐 | `POST` | `/fulfillment/orders/{orderId}/picked-up` |
| 送达 | `POST` | `/fulfillment/orders/{orderId}/delivered` |
| 履约记录 | `GET` | `/fulfillment/orders/internal/operations` |

订单管理页应以状态机为核心：

```text
SUBMITTED -> PENDING_PAYMENT -> PAID -> ACCEPTED -> READY -> PICKED_UP -> DELIVERED
```

若后端返回排队结果，页面应显示 ticket ID，并允许跳转到排队页查看状态。

### 排队

| 页面动作 | 方法 | 接口 |
| --- | --- | --- |
| ticket 详情 | `GET` | `/queue/tickets/{ticketId}` |
| 取消 ticket | `POST` | `/queue/tickets/{ticketId}/cancel` |
| ticket 列表 | `GET` | `/queue/internal/tickets` |
| capacity token 列表 | `GET` | `/queue/internal/capacity/tokens` |

排队页应展示：

- 当前商家的等待队列。
- ticket 状态。
- capacity token 状态。
- 可按商家、状态筛选。

### 优惠券

| 页面动作 | 方法 | 接口 |
| --- | --- | --- |
| 秒杀领券 | `POST` | `/vouchers/{voucherId}/seckill` |
| 用户券包 | `GET` | `/vouchers/wallet` |
| 后台券列表 | `GET` | `/vouchers/admin` |
| 新增券 | `POST` | `/vouchers/admin` |
| 编辑券 | `PUT` | `/vouchers/admin/{voucherId}` |
| 领取记录 | `GET` | `/vouchers/internal/claims` |
| 锁定记录 | `GET` | `/vouchers/internal/locks` |

优惠券页面不应只是调用接口按钮，应支持券的生命周期管理：创建、调整库存、启用、禁用、查看领取结果。

### 通知与运维

| 页面动作 | 方法 | 接口 |
| --- | --- | --- |
| 通知消息 | `GET` | `/notify/messages` |
| 投递记录 | `GET` | `/notify/deliveries` |
| SSE 通知流 | `GET` | `/notify/messages/stream` |
| 订单事件 | `GET` | `/orders/internal/events` |
| 派发订单事件 | `POST` | `/orders/internal/events/dispatch` |
| 支付事件 | `GET` | `/payments/internal/events` |
| 派发支付事件 | `POST` | `/payments/internal/events/dispatch` |
| 履约事件 | `GET` | `/fulfillment/orders/internal/events` |
| 派发履约事件 | `POST` | `/fulfillment/orders/internal/events/dispatch` |

运维页面面向演示和排障，必须放在独立菜单下，不混入日常业务页面。

## 交互流程

### 登录流程

1. 用户输入手机号、密码或后端支持的登录字段。
2. 调用 `/auth/login`。
3. 保存 token、用户信息、权限和菜单。
4. 跳转 `/dashboard`。
5. 401 时清空登录态并回到 `/login`。

### 商品维护流程

1. 进入商品管理页，加载类目和 SKU。
2. 新增 SKU 时先选择类目。
3. 可上传图片，上传成功后把返回 URL 写入表单。
4. 保存 SKU。
5. 上架后用户端浏览接口应能看到该 SKU。

### 订单处理流程

1. 订单页通过筛选条件查询订单。
2. 点击订单进入详情抽屉。
3. 根据订单状态显示可用操作。
4. 商家在履约页完成接单、出餐、取餐、送达。
5. 状态变化后刷新订单和履约记录。

### 优惠券管理流程

1. 管理员创建或编辑券。
2. 设置库存、有效期和状态。
3. 用户券包页可演示领券结果。
4. 运维页查看领取记录和锁定记录。

## 前端代码规范

建议目录：

```text
meal-web/
├── src/
│   ├── api/
│   ├── assets/
│   ├── components/
│   ├── layouts/
│   ├── router/
│   ├── stores/
│   ├── styles/
│   ├── types/
│   └── views/
└── vite.config.ts
```

约定：

- `api/` 只放接口封装，不写页面逻辑。
- `views/` 以业务页面为单位组织。
- `stores/auth.ts` 管理 token、用户、权限、菜单。
- `stores/app.ts` 管理布局状态，例如菜单折叠。
- 所有接口响应统一适配后端 `Result<T>`：`success`、`code`、`message`、`data`。
- 表单类型写在 `types/`，不要散落在组件内。
- 页面组件可以偏直接，先保证业务逻辑清楚，不为了抽象而抽象。

## 验收标准

第一阶段前端完成时，应满足：

- 使用 Vue 3，而不是 React 或静态 HTML。
- 左侧菜单 + 顶部栏 + 右侧主内容布局真实可用。
- 登录后能携带 token 调用受保护接口。
- 菜单不是接口列表，而是按业务域组织。
- 商品类目、SKU、图片上传、订单、履约、队列、优惠券、员工角色至少有可操作页面。
- 页面具有加载态、错误态、空状态和操作反馈。
- 能通过真实后端完成“登录 -> 商品维护 -> 下单/排队演示 -> 支付/履约 -> 订单状态查看”的核心链路。
- `npm run build` 通过。
