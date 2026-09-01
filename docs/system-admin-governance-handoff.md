# MealFlow 系统管理员治理升级：新对话实施说明

> 这是一份给全新开发对话的实施任务书。先完整阅读，再实施；不要把文中的历史审计结论当作当前代码事实。遇到与本文不一致的代码，应以当前分支代码为准并说明差异。

## 1. 任务目标

把当前只管理“平台券”的 `PLATFORM_ADMIN` 升级为真正的 **系统管理员**（`SYSTEM_ADMIN`）。

系统管理员负责平台治理与只读排障；商户管理员负责自己商家的经营；普通用户只管理自己的数据。必须保持最小权限原则，不能把系统管理员实现成可以绕过一切业务约束的“万能角色”。

本次 v1 必须交付：

1. 角色从 `PLATFORM_ADMIN` 迁移为 `SYSTEM_ADMIN`，并安全处理存量会话。
2. 保留并扩展系统管理员的“平台券”能力；不能看到、编辑或创建任意商家券。
3. 新增系统管理员专属的商家治理、用户治理、全局订单只读能力及管理端页面。
4. 保持商户数据隔离：商家管理员仍只能操作自己商家；普通用户不能访问任何治理接口。
5. 提供已有数据库的迁移脚本、自动化验证、浏览器 UI 验收及截图证据。

## 2. 开始前的真实基线

- 工作目录：`C:\Users\29924\Desktop\developtest\mealflow`
- 当前工作分支：`system-admin-governance`
- 起点：`master` 已快进合入截至 `4b478bc` 的全部修复；不要回退、重置或覆盖其提交。
- 工作区存在用户所有的未跟踪目录 `ui-test-evidence/`。不要删除、移动、暂存或提交它。
- 当前本地 Docker 已运行，RocketMQ Nameserver、Broker 和 promotion 服务健康；不要为了本任务破坏性重置数据库或整套 Compose。
- 当前 demo 平台账号：手机号 `13800000006`，用户 ID `106`，成员资格存放在 `platform_admin`。项目登录仍走验证码流程，不要为管理员新增明文密码。

现有实现位置：

| 事实 | 位置 |
|---|---|
| 平台身份表及角色、权限种子 | `meal-auth-user/src/main/resources/schema.sql`、`data.sql` |
| 登录、token 校验、菜单/角色/员工接口 | `meal-auth-user/src/main/java/com/mealflow/authuser/AuthUserService.java`、`AuthUserController.java`、`mapper/AuthUserMapper.java` |
| 平台管理员历史兼容迁移 | `scripts/migrations/20260904-platform-voucher-scope.sql` |
| 平台券与商家券范围隔离 | `meal-promotion/src/main/java/com/mealflow/promotion/PromotionService.java`、`PromotionController.java` |
| 商户营业状态和产能接口 | `meal-merchant/src/main/java/com/mealflow/merchant/` |
| 管理端路由、菜单、请求层 | `meal-web/src/router/`、`meal-web/src/layouts/AdminLayout.vue`、`meal-web/src/api/` |

## 3. 已有约束，绝不能破坏

### 3.1 身份空间

- `platform_admin` 是独立成员资格表，平台人员没有 `merchant_id`，也不能伪装成 `merchant_employee`。
- `merchant_employee` 只属于一个商家，商户角色包括 `MERCHANT_ADMIN`、`STORE_STAFF` 等。
- `auth_token` 持久化 `role_code` 和 `merchant_id`；改名角色后，旧 token 不能继续被当成新高权限身份使用。

### 3.2 优惠券范围

- 券的范围已有 `PLATFORM` 与 `MERCHANT`。
- `SYSTEM_ADMIN` 只能管理 `PLATFORM` 券；商户管理员只能管理自己 `merchant_id` 的 `MERCHANT` 券。
- 不得为了方便让系统管理员编辑商家券；商家券的经营权仍属于对应商家。
- 普通用户领取和使用券的接口、钱包、结算锁券语义不能改变。

### 3.3 安全原则

- 前端菜单隐藏不是权限控制。所有新增/现有治理接口必须在网关和服务端都按已存在的可信身份机制校验。
- 不接受客户端伪造的 `X-User-Id`、`X-Merchant-Id`、`X-Role-Code` 作为授权依据；沿用当前网关注入与服务端 `RequestIdentity` 模式。
- 不把商户接口的 `merchantId` 参数简单改成可为空来实现系统管理员访问；这会使原有商户隔离失效。应增加明确的系统治理端点，或在服务端做严格的角色分支。
- 系统管理员不能直接代替顾客付款、退款、修改订单商品/金额、编辑商家商品库存或推进履约状态。

## 4. v1 权限模型

角色代码统一使用 `SYSTEM_ADMIN`；界面中文显示“系统管理员”。不再保留 `PLATFORM_ADMIN` 作为可登录或可授权角色。

| 能力 | `SYSTEM_ADMIN` | `MERCHANT_ADMIN` | `STORE_STAFF` | `CUSTOMER` |
|---|---:|---:|---:|---:|
| 平台券 CRUD | 是，仅 `PLATFORM` | 否 | 否 | 否 |
| 本商家券 CRUD | 否 | 是，仅本商家 | 否 | 否 |
| 商家目录/状态 | 全局查看；可设 `OPEN`、`CLOSED`、`SUSPENDED` | 仅自己的营业开关；不得自行设/解除 `SUSPENDED` | 否 | 否 |
| 用户目录/账号状态 | 全局分页查看；可启用/禁用普通用户 | 否 | 否 | 仅自己资料 |
| 全局订单 | 跨商家只读分页、筛选、详情 | 仅本商家订单 | 仅当前既有范围 | 仅自己的订单 |
| 商家员工 | v1 不管理、不改角色 | 仅自己的员工 | 否 | 否 |
| 商品、库存、履约 | v1 不直接操作 | 仅本商家 | 当前既有范围 | 否 |
| 付款、退款、资金动作 | 不提供直接执行入口 | 当前既有流程 | 否 | 当前既有流程 |

建议新增权限码（命名可按现有风格微调，但必须职责清晰）：

- `PLATFORM_VOUCHER_MANAGE`：保留，归属 `SYSTEM_ADMIN`。
- `SYSTEM_MERCHANT_READ`
- `SYSTEM_MERCHANT_STATUS_WRITE`
- `SYSTEM_USER_READ`
- `SYSTEM_USER_STATUS_WRITE`
- `SYSTEM_ORDER_READ`

不要在 v1 引入模糊的 `SYSTEM_ALL`、`ADMIN_ALL` 或按 URL 前缀一键放行的权限。

## 5. 后端实施方案

### 5.1 角色迁移与会话安全（必须先完成）

1. 将 `AuthUserService` 中的平台角色常量改为 `SYSTEM_ADMIN`；登录和 `currentUser` 校验仍通过 `platform_admin.status = ACTIVE` 确认成员资格，并返回 `merchantId = null`。
2. 将 `PromotionService` 的平台角色判断改为 `SYSTEM_ADMIN`，但保持券范围规则完全不变。
3. 更新网关角色/权限映射、管理端登录态类型、菜单过滤和所有后端硬编码角色判断。全仓库搜索 `PLATFORM_ADMIN`，逐项判断并清零生产代码引用；历史迁移和历史说明可以保留为“旧版本来源”，但不能作为运行时配置。
4. 新增兼容迁移，例如 `scripts/migrations/20260905-system-admin-governance.sql`：
   - 插入 `SYSTEM_ADMIN` 内置角色及 v1 权限；
   - 为其写入 `role_permission`；
   - 撤销 `auth_token.role_code = 'PLATFORM_ADMIN'` 的未过期 token，强制历史平台管理员重新登录，防止旧 token 在角色切换期间出现歧义；
   - 删除旧 `PLATFORM_ADMIN` 的权限和角色记录，或以明确、可重复执行的方式将其迁走；
   - 保留 `platform_admin` 的成员数据，不把平台人员迁入 `merchant_employee`；
   - 每一步幂等，注释清楚执行前提和预期影响。
5. 同步更新 `schema.sql`、`data.sql`、迁移 README。新库启动后的最终种子状态只能含 `SYSTEM_ADMIN`，平台账号显示名改为“System Admin/系统管理员”。

不要通过 `UPDATE auth_token SET role_code='SYSTEM_ADMIN'` 让旧高权限 token 静默延续；本次是权限扩展，安全策略应是撤销并重新签发。

### 5.2 商家治理

新增系统管理员专属 API，推荐使用明确前缀，避免改弱已有商家自助接口：

- `GET /merchants/system`：全局分页商家目录；支持名称/状态筛选，返回必要运营字段，不返回密钥或不应公开的内部配置。
- `PUT /merchants/system/{merchantId}/business-status`：仅 `SYSTEM_ADMIN`；可设 `OPEN`、`CLOSED`、`SUSPENDED`，必须校验商家存在并记录更新时间。

同时收紧原有 `POST /merchants/{merchantId}/business-status`：商家自助只能设 `OPEN`、`CLOSED`，不得把自己从 `SUSPENDED` 恢复，也不得自行设为 `SUSPENDED`。系统管理员使用新端点，不复用商家身份端点。

商家查询应分页，`pageSize` 限制沿用项目约定（上限 100）。补充 Mapper 索引/查询只在真实需要时添加，并为已有库提供迁移。

### 5.3 用户治理

在 `meal-auth-user` 新增只供 `SYSTEM_ADMIN` 使用的用户目录与状态接口，例如：

- `GET /auth/system/users?page=&pageSize=&phone=&status=`：分页查询用户；只返回 ID、手机号、昵称、状态、创建时间、身份摘要，绝不返回 token、验证码或敏感认证材料。
- `PUT /auth/system/users/{userId}/status`：只允许 `NORMAL` 与 `DISABLED`；禁用后立即撤销该用户全部 token。

安全细节：

- 系统管理员不能禁用自己，避免把唯一治理入口锁死。
- 禁用/启用操作必须验证目标存在。
- 禁用商户账号时，用户状态检查和既有员工状态语义应保持一致；不要顺手修改商家员工角色或商家营业状态。
- 若现有 Mapper 已有 token 撤销能力则复用；没有则新增 `revokeTokensByUserId` 的条件更新，并测试现有 token 随即失效。

### 5.4 全局订单只读

不要放宽已有 `GET /orders/admin` 的商户过滤。新增明确端点，例如：

- `GET /orders/system?page=&pageSize=&status=&merchantId=&userId=&from=&to=`
- `GET /orders/system/{orderId}`

要求：

- 仅 `SYSTEM_ADMIN`；仅查询，不新增任何状态转换接口。
- 与商户后台订单 DTO 尽可能复用，但详情需明确是否包含地址/电话。v1 建议返回必要履约信息；若包含地址和联系电话，必须限定系统管理员且在页面标注“运营排障用途”。
- 商户管理员访问返回 403；普通用户访问返回 403；商户原有 `/orders/admin` 的本商家隔离回归不得受影响。
- 按现有后台分页样式实现，避免一次加载全部订单。

### 5.5 菜单、权限和网关

1. 将新权限种子写入 `role_permission`；若 `menu_permission` 是当前菜单来源，为系统页添加对应菜单项和唯一权限码。
2. 确保登录响应中的 `permissions`/`menus` 与新角色相符；管理员 UI 只渲染拥有的菜单。
3. 更新网关路由授权：新增系统端点必须仅接受 `SYSTEM_ADMIN`。已有商户、顾客、internal 路径规则不能因通配符顺序变化而放宽。
4. 每个服务仍应验证资源级范围：商家接口校验自身 merchantId，系统端点校验系统角色。不要只依赖 UI 或网关。

## 6. 管理端 UI 方案

目标是增加系统治理入口，而不是把商家后台改造成另一个产品。

### 6.1 路由与导航

新增系统管理员可见的独立页面/分组，建议：

- `/admin/system/merchants`：商家治理
- `/admin/system/users`：用户治理
- `/admin/system/orders`：全局订单
- 现有券页在系统管理员登录时显示“平台券管理”，在商家登录时显示“本店优惠券管理”。

若当前菜单不支持多层级，使用三个清晰的顶级菜单即可。系统管理员不应看到“商家设置、商品管理、履约工作台”等商家专属页面；商家也不应看到系统治理菜单。

### 6.2 页面行为

**商家治理**：分页表格、名称/状态筛选、状态修改确认框；`SUSPENDED` 使用醒目风险提示，明确会阻止用户继续下单。不得在此页编辑产能、商品、员工。

**用户治理**：分页表格、手机号/状态筛选、启用/禁用确认；禁用操作显示“将立即使该账号所有已登录会话失效”。当前系统管理员本人对应按钮禁用。

**全局订单**：分页、状态/商家/用户/时间筛选、只读详情抽屉或详情页；不显示“接单、出餐、取消、支付成功”等操作按钮。

**平台券管理**：保留现有创建、编辑、停用能力；文案写明“平台券”，且列表只显示 `PLATFORM` 范围。商家券不出现。

所有 API 错误统一使用现有中文错误呈现，不展示 Axios 原始英文。路由守卫在 401 时清理会话并回登录页；403 则显示无权限，不应跳转到错误的商家页面。

## 7. 明确不做的内容

以下不属于本次 v1，避免“系统管理员”概念无限膨胀：

- 不创建财务管理员、风控管理员、客服主管等新角色。
- 不做系统管理员直接退款、付款、调账、改价、改库存、接单、出餐。
- 不让系统管理员管理商家员工角色；这会改变商家组织边界，需独立需求与审计设计。
- 不新增复杂审计系统、权限编辑器或自定义角色编辑器。现有内置角色是只读的，应保持。
- 不改变券的领取、锁定、核销、Pending/MQ 补偿逻辑。
- 不删除或重置数据库、Docker 卷、历史截图证据。

## 8. 数据迁移与发布顺序

1. 完成代码、测试和生产包构建。
2. 审查新迁移脚本，确认只影响角色/权限/token，不删用户、商家、订单、券或历史业务数据。
3. 对已有本地数据库先备份；执行新的迁移脚本一次。新库不需要单独执行迁移，`schema.sql`/`data.sql` 已是最终状态。
4. 重建受影响服务，至少 `meal-auth-user`、gateway、`meal-promotion`、`meal-merchant`、`meal-order` 和管理端；不要仅点击旧容器 Start。修改 Compose/环境/挂载包后使用 `docker compose up -d --force-recreate <service...>`。
5. 历史平台管理员 token 被撤销是预期行为；用 `13800000006` 重新登录验证返回 `SYSTEM_ADMIN`。
6. 完成 API 和浏览器验收后才提交。不要把 `.env` 中的敏感凭据、浏览器 profile 或 `ui-test-evidence/` 纳入 Git。

## 9. 必须补充的自动化测试

至少覆盖下列情形，并在相关模块现有测试风格中实现：

1. 平台账号重新登录后角色为 `SYSTEM_ADMIN`，`merchantId` 为空，拥有准确权限集合。
2. 旧 `PLATFORM_ADMIN` token 在迁移/角色切换后被拒绝；重新登录可用。
3. `SYSTEM_ADMIN` 能创建、编辑、禁用平台券；不能读取或编辑商家 A/B 的券。
4. 商家 A/B 各自只能管理自己的商家券；不能看到平台券或其他商家券。
5. 系统管理员可分页读取商家、用户、全局订单；商户/顾客访问系统端点为 403。
6. 商家管理员只能改自己的营业状态且不能解除 `SUSPENDED`；系统管理员能执行挂起/恢复。
7. 禁用普通用户后其已签发 token 立即不可用；系统管理员不能禁用自己。
8. 商户原有订单列表仍只返回自己的订单；顾客仍只读取自己的订单。
9. 网关授权回归：伪造角色/商家请求头不获得权限，`/internal/**` 仍拒绝外部身份。

运行受影响后端模块测试、全量或相关 Maven 测试、两套前端生产构建、`docker compose config --quiet`。若修改了 SQL，至少用新库和已有库迁移路径各验证一次。

## 10. 必须完成的浏览器 UI 验收与证据

使用四个彼此隔离的浏览器会话/用户数据目录，不复用 Cookie：

1. 系统管理员（`13800000006`）：登录后看见系统治理与平台券；创建/编辑一张测试平台券；查看商家、用户和全局订单；挂起并恢复一个测试商家；禁用/恢复一个专用测试用户。
2. 商家 A：只看到本商家功能和自己的商家券；访问系统治理路由/API 被拒绝；不能查看商家 B 数据。
3. 商家 B：与商家 A 交叉验证，不能看到/编辑商家 A 的券、订单或员工。
4. 顾客：不能进入管理端系统页面；被禁用时旧会话失效；恢复后重新登录正常；正常顾客的券包/下单不受影响。

截图应清楚显示浏览器地址、当前登录身份、关键表格/结果与成功或拒绝提示。将截图放在新的、带日期的 `ui-test-evidence/` 子目录；该目录仅作证据，不提交 Git。

不要为验收而使用真实不可逆退款或破坏真实演示数据。测试建立的数据应使用可识别前缀，并在能安全恢复的情况下恢复状态。

## 11. 提交与最终报告

建议按可审查的逻辑提交，不把所有改动堆成一个提交：

1. `refactor(auth): promote platform admin to system admin`
2. `feat(system): add merchant user and order governance APIs`
3. `feat(admin-web): add system governance pages`
4. 如需要，单独的迁移/文档提交。

每个提交前执行 `git diff --check`。不要提交用户未跟踪截图或凭据。最终报告必须说明：

- 新角色、权限与数据迁移具体做了什么；
- 哪些高风险能力被刻意排除；
- 已运行的测试、构建、Compose 校验和浏览器验收；
- 迁移是否已在本地旧库执行；
- 提交哈希；
- Git 工作区是否干净（将用户截图目录单独说明）。

## 12. 完成标准

只有同时满足以下条件才算完成：

- 项目中运行时不再把 `PLATFORM_ADMIN` 作为有效角色；
- `SYSTEM_ADMIN` 有明确、最小化且后端可验证的治理权限；
- 平台券/商家券隔离、商家隔离、顾客隔离均保持；
- 新治理页面可用，且非系统管理员无法通过直接 URL 或 API 绕过；
- 历史 token 的升级行为安全且已测试；
- 迁移、测试、构建、UI 证据和 Git 提交全部完成。
