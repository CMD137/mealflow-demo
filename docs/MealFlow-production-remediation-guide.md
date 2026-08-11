# MealFlow 生产化修复指南

> 版本：1.0  
> 日期：2026-08-11  
> 输入依据：`MealFlow-2027-campus-recruitment-audit.md`、当前源码/表结构、完整构建与 E2E 运行结果  
> 目标：在不盲目增加中间件的前提下，把当前“单实例可运行的微服务原型”改造成安全、可重试、可扩容、可观测、可灰度的生产基线。

本文不是架构愿望清单。每一项都给出当前问题、目标状态、具体落点、迁移顺序、失败处理和验收标准。示例 SQL 是目标模型草案，落地时应转成各服务自己的 Flyway migration，并先在测试环境用现有数据验证。

## 1. 修复总原则

1. **先正确，再快。** 认证绕过、资源越权、错单、超发和孤儿资源没有关闭前，不做性能宣传和复杂扩容。
2. **数据库事实，缓存派生。** 订单、库存、券、排队票、产能 token、消息投递都必须有可对账的事实记录；Redis 只做快速判断、排序和限流。
3. **至少一次 + 业务幂等，不追求“恰好一次”。** 网络和消息系统都会重复；稳定业务键、唯一约束和可重入状态机才是兜底。
4. **本地事务只承诺本地数据。** 跨 HTTP/MQ 的流程使用显式 Saga、步骤状态、超时恢复和反向补偿，不把多个远程调用包进长事务。
5. **身份不可由客户端声明。** 外部身份只由网关验证，内部调用必须带可验证的服务身份；业务服务缺少可信主体时立即拒绝。
6. **变更必须可灰度、可回滚、可观测。** 所有表结构采用 expand-contract，先兼容后切换；新旧路径通过 feature flag 控制；上线前定义指标和回滚阈值。
7. **优先使用已有 MySQL、Redis、RocketMQ。** 当前不引入 Seata、Kubernetes、Elasticsearch 或“全局分布式锁”。

## 2. 目标架构与责任边界

### 2.1 请求入口

```text
Internet
  -> Ingress / Load Balancer
  -> meal-gateway（唯一公开业务入口）
       -> 验证用户 Access Token
       -> 精确路由授权
       -> 生成短时内部身份令牌（audience=目标服务）
       -> 各业务服务

业务服务之间
  -> 服务身份令牌 + 稳定 requestId + traceparent
  -> 超时、有限重试、熔断/隔离
```

- 宿主机/公网只开放前端、网关和受控运维入口；8101～8110 等业务端口只在 Docker/Kubernetes 内部网络可达。
- 网关负责认证和粗粒度权限，业务服务负责资源归属与状态机授权，两者不可互相替代。
- `/internal/**` 只允许服务主体，不允许普通用户 Token；服务令牌必须校验 `iss`、`aud`、`exp`、`scope`。

### 2.2 数据责任

| 事实 | 唯一所有者 | 其他服务如何使用 |
|---|---|---|
| 用户、会话、商家员工角色 | auth-user | 只通过身份声明或授权查询 |
| SKU、可售库存、库存预占 | catalog | 通过幂等库存命令，不直接查表 |
| 订单、下单 Saga、支付/履约视图 | order | 其他服务通过事件/内部接口更新 |
| 排队票、产能 token、商家实时占用 | queue | Redis 仅保存排名和派生计数 |
| 券总量、发放记录、使用锁 | promotion | Redis 仅用于预检查/热点保护 |
| 支付单、支付回调事实 | payment | PaymentPaid 事件通知订单 |
| 履约操作 | fulfillment | 订单状态仍由 order 所有 |
| 通知消息和投递 | notify | 消费领域事件生成通知 |

短期可以继续共用一个 MySQL 实例，但每个服务使用独立 schema 和最小权限账号；禁止跨 schema SQL。物理拆库应由容量、故障域和团队自治驱动，不作为本轮先决条件。

## 3. 实施阶段与发布闸门

| 阶段 | 范围 | 进入下一阶段的硬条件 |
|---|---|---|
| Phase 0：止血 | 精确路由、真实验证码、去默认身份、资源归属、关闭直连端口、修排队转单 | 安全矩阵全绿；匿名/跨用户/跨商家攻击全部失败；转单业务字段一致 |
| Phase 1：数据护栏 | Flyway、数据库 ID、唯一键、持久幂等、HTTP 超时 | 重启和双实例下重复请求只产生一份业务结果 |
| Phase 2：交易闭环 | 下单 Saga、支付确认流程、取消/退款、过期补偿 | 任意一步超时/宕机后最终完成或完整补偿，无长期孤儿资源 |
| Phase 3：Redis/队列 | 券库存事实、原子补偿、队列 CAS/租约、Redis 可重建 | Redis 清空或双 worker 下不超发、不重复转单 |
| Phase 4：MQ 与运营 | Outbox 退避/死信、消费者步骤化、通知启用、告警 | 重复/乱序/毒消息可观测、可恢复且不重复产生业务副作用 |
| Phase 5：性能与交付 | 分页、批量、索引、CI、压测、灰度和回滚演练 | SLO 和业务不变量同时满足，才允许写性能数据 |

严禁将 Phase 2～4 以一个“大爆炸版本”同时上线。每个 Phase 应拆成数据库兼容变更、双写/影子验证、流量切换和清理四步。

## 4. P0 安全修复

### 4.1 手机验证码登录

#### 当前问题

`AuthUserService.login` 只按手机号查询/注册并发 Token，完全没有读取 `LoginRequest.code`。这不是弱校验，而是认证绕过。

#### 目标流程

1. `POST /auth/codes`：验证手机号格式和人机校验，按 IP、手机号、设备三维限流。
2. 生成 6 位随机码，只保存 `HMAC(serverSecret, scene + phone + code)`；Redis key 为 `auth:otp:login:{phone}`，TTL 5 分钟。
3. 同一手机号发送冷却 60 秒，每小时/每天有上限；返回统一响应，避免枚举手机号是否存在。
4. `POST /auth/login` 使用 Lua 或 Redis `GETDEL` 原子消费验证码；错误次数达到 5 次即废弃该验证码。
5. 验证成功后才允许查找或创建用户；创建用户依赖 `uk_user_account_phone` 处理并发注册。
6. 短时间大量失败、IP/设备异常和商家管理员登录触发审计事件。

验证码日志、异常和 tracing 中只能记录掩码手机号，不得记录 code。开发环境如需固定验证码，必须放在 `dev` profile，并在 `prod` 启动时检测并拒绝 `fixed-code` 配置。

#### 会话方案

生产基线建议使用：

- 15 分钟 RS256/ES256 Access Token；业务服务或网关本地验签。
- 30 天随机 Refresh Token，只在 auth-user 保存 SHA-256/HMAC hash；每次刷新轮换，旧 token 立即失效。
- Access Token 至少包含 `sub`、`sid`、`role`、`merchantId`、`iat`、`exp`、`jti`、`permissionVersion`，不包含手机号等 PII。
- 登出、密码/角色变更、员工禁用时撤销 session；Access Token 最多承受 15 分钟窗口，高风险账号用 Redis `revoked:sid` 即时拒绝。
- 签名私钥放 Secret Manager/KMS，不写仓库；支持 `kid` 和双公钥轮换。

如果暂时保留当前 opaque token，也必须改为存 token hash、增加 session/refresh 轮换和吊销；但网关每请求回调 auth 服务会成为延迟与可用性瓶颈，只适合作为 Phase 0 过渡。

#### 验收

- 任意错误/复用/过期验证码登录失败；同一验证码只能成功一次。
- 10 个并发注册请求最终只有一个用户。
- 角色禁用后，新请求在定义的吊销窗口内失败。
- 日志和数据库中搜索不到明文验证码、Access Token、Refresh Token。

### 4.2 网关路由与内部接口

公共接口必须从前缀判断改为精确的“HTTP 方法 + 路由模板”白名单。例如只允许：

| 方法 | 公共路径 | 备注 |
|---|---|---|
| GET | `/catalog/merchants/{merchantId}/skus` | 只返回上架商品 |
| GET | `/catalog/merchants/{merchantId}/categories` | 只返回可见类目 |
| GET | `/catalog/images/{objectKey}` | 服务端固定 MIME、下载头和 CSP |
| POST | `/auth/codes`、`/auth/login` | 独立限流 |
| GET | `/ping` | 不泄露依赖细节 |

`/catalog/admin/**`、`/catalog/internal/**` 必须在公共规则之前匹配并拒绝匿名。不要使用 `path.startsWith("/catalog/")` 这类会随新增接口自动放大的规则。

外部可写状态的接口应收缩：

- 删除或仅在 `dev` 暴露 `/payments/{id}/mock-pay`。
- `/orders/{id}/pay-success` 改为内部事件处理，不允许顾客直接调用。
- `merchant-accept/meal-ready/picked-up/delivered` 只由 fulfillment 内部接口推进，外部商家操作先进入 fulfillment，再由它以服务身份调用 order。
- `/internal/events/dispatch`、恢复/重放、预占列表等运维接口放独立 management port 或 admin 网络，不经过普通用户网关。

### 4.3 可信身份传播

当前业务服务信任 `X-User-Id` 且缺失时使用默认用户。修复要求：

1. 删除所有 `mealflow.demo.default-user-id` 生产兜底。
2. 网关验证外部 Access Token 后，签发 30～60 秒内部 JWT，`aud` 指向单个下游服务。
3. 内部 JWT 包含用户主体或服务主体，两者不可混用：用户主体有 `sub/merchantId/permissions`；服务主体有 `client_id/scopes`。
4. 业务服务用 Spring Security Filter 验证签名、时间、audience 和 scope，再构造不可由请求参数覆盖的 `CurrentPrincipal`。
5. 身份头只作为网关内部的派生数据；即使保留，也必须由已验证令牌覆盖，绝不能在缺令牌时信任。
6. 服务间调用沿用用户上下文时同时携带服务身份和 actor；审计日志记录“谁代表谁调用”。

### 4.4 资源级授权矩阵

| 资源/动作 | CUSTOMER | MERCHANT_ADMIN/STAFF | 内部服务 |
|---|---|---|---|
| 订单列表/详情 | `order.user_id == sub` | `order.merchant_id == merchantId` 且有读权限 | 按 scope |
| 取消订单 | 本人且状态允许 | 本商家且有取消权限 | order-saga scope |
| 支付单查询 | `payment.user_id == sub` 或经 order 归属验证 | 默认禁止 | order/payment scope |
| 商家配置 | 禁止 | 路径 merchantId 必须等于主体 merchantId | 管理 scope |
| 履约操作 | 禁止 | 订单 merchantId 匹配且有对应动作权限 | fulfillment scope |
| 队列票 | 本人只读/取消 | 本商家查看 | order/fulfillment scope |
| 通知 | 只读自己的 | 只读自己的；运营另设权限 | notify scope |
| 库存预占/Outbox/消费记录 | 禁止 | 只通过受控运维端 | service/admin scope |

资源归属检查放在 Service 层，查询 SQL 直接带主体条件，例如 `WHERE id=? AND user_id=?`，避免“先查任意对象，再忘记判断”。找不到或无权访问敏感对象时统一返回 404，减少 ID 枚举；操作型越权记安全审计日志。

### 4.5 网络与配置止血

- Compose/生产编排删除业务服务的宿主机 `ports`，改用内部 `expose`；Nacos、Redis、RocketMQ、MySQL、Prometheus、Grafana 不对公网开放。
- Redis 开启 ACL/TLS（若基础设施支持），每类服务使用最小权限用户和 key 前缀；Nacos 开认证；Grafana 使用外部 Secret。
- MySQL 禁止业务服务使用 root；每个 schema 一个读写账号，迁移账号与运行账号分离。
- Actuator 业务端口只暴露 `/health/liveness`、`/health/readiness`；Prometheus 走内网抓取，其余端点需要运维认证。
- 上传文件校验魔数、允许扩展名、最大尺寸，使用随机 object key；对象存储设置 `Content-Disposition`、`X-Content-Type-Options: nosniff` 和私有 bucket/签名 URL。

## 5. 数据库迁移、ID 与唯一约束

### 5.1 引入 Flyway

每个服务维护自己的 `db/migration`：

```text
V1__baseline.sql
V2__add_idempotency_and_business_keys.sql
V3__add_saga_columns_nullable.sql
V4__backfill_saga_data.sql
V5__enforce_not_null_and_unique.sql
```

- 禁用生产环境 `spring.sql.init.mode=always` 和所有 `PostConstruct ALTER TABLE`。
- migration 使用独立 DDL 账号，由发布任务先执行；应用运行账号没有 CREATE/ALTER/DROP 权限。
- 大表变更采用 expand-contract：先加 nullable 列/索引，双写并回填，校验完成后切读，再加 NOT NULL/删除旧列。
- Flyway 失败时禁止应用新版本启动；不要在应用启动时自动“修复” checksum。

### 5.2 删除 `MAX(id) + AtomicLong`

本项目现阶段最稳妥的方案是：

- 各服务内部主键改为 MySQL `BIGINT AUTO_INCREMENT`，Mapper insert 后读取 generated key。
- 对外暴露独立、不可枚举的业务号：`order_no`、`pay_order_no`、`ticket_no` 使用 ULID/UUIDv7 字符串并加唯一索引。
- 事件 `event_key` 由业务类型 + 聚合业务号 + 版本构造，不使用数据库自增 ID 作为幂等语义。

已有主键可在测试环境验证后改为 AUTO_INCREMENT，并设置下一个值大于现有 MAX。若未来真正跨库分片，再引入有 worker-id 租约和时钟回拨方案的 Snowflake；现在不需要为未发生的规模承担复杂度。

### 5.3 必需唯一键

| 表 | 建议唯一约束 | 目的 |
|---|---|---|
| `customer_order` | `uk_order_submit(user_id, submit_request_id)` | 同一用户重复提交只生成一单 |
| `customer_order` | `uk_order_queue_ticket(queue_ticket_id)` | 一张票最多一单；NULL 可重复 |
| `customer_order` | `uk_order_no(order_no)` | 对外业务号唯一 |
| `payment_order` | `uk_payment_order_scene(order_id, pay_scene)` | 同一订单同一支付场景只一张有效单 |
| `payment_order` | `uk_pay_request(user_id, request_id)`、`uk_pay_no(pay_order_no)` | 支付创建重试幂等 |
| `queue_ticket` | `uk_ticket_request(user_id, merchant_id, request_id)`、`uk_ticket_no(ticket_no)` | 排队申请幂等 |
| `capacity_token` | `uk_token_request(merchant_id, request_id)`、`uk_token_ticket(ticket_id)` | 同一票/请求最多一个 token |
| `stock_reservation` | `uk_stock_reserve(request_id, sku_id)` | 同一库存命令幂等 |
| `voucher_claim` | `uk_claim_request(request_id)`、`uk_claim_user_voucher(user_id,voucher_id)` | 一人一券与请求幂等 |
| `voucher_lock` | `uk_voucher_lock_order(order_id, user_voucher_id)` | 重复锁券不产生新锁 |
| `local_event` | `uk_event_key(event_key)` | 重复业务处理不产生重复事件 |

加唯一键前必须执行重复数据扫描并形成处理清单。不得直接删除重复行：先根据订单、支付和事件关系判定主记录，冲突记录迁入归档表，保留审计和恢复能力。

## 6. 生产级持久幂等

### 6.1 目标表

每个需要幂等的服务在自己的 schema 建表，不建立跨服务共享“全局幂等库”：

```sql
CREATE TABLE idempotency_record (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  namespace VARCHAR(64) NOT NULL,
  owner_type VARCHAR(32) NOT NULL,
  owner_id VARCHAR(64) NOT NULL,
  idempotency_key VARCHAR(128) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  status VARCHAR(24) NOT NULL,
  response_json JSON NULL,
  error_code VARCHAR(64) NULL,
  lease_owner VARCHAR(64) NULL,
  lease_until TIMESTAMP(3) NULL,
  attempt_count INT NOT NULL DEFAULT 0,
  expire_time TIMESTAMP(3) NOT NULL,
  create_time TIMESTAMP(3) NOT NULL,
  update_time TIMESTAMP(3) NOT NULL,
  UNIQUE KEY uk_idem_owner_key
    (namespace, owner_type, owner_id, idempotency_key),
  INDEX idx_idem_recover (status, lease_until),
  INDEX idx_idem_expire (expire_time)
);
```

状态建议：`PROCESSING`、`SUCCEEDED`、`RETRYABLE_FAILED`、`FINAL_FAILED`。失败是否允许复用同一 key 必须按接口定义，不可统一“删 Map 后再做”。

### 6.2 执行算法

1. 客户端为一次业务意图生成 Idempotency-Key；服务端将规范化请求体做 SHA-256。
2. 尝试插入 PROCESSING 记录和租约；唯一键冲突后读取已有记录。
3. request hash 不同：返回 `409 IDEMPOTENCY_KEY_REUSED`，绝不能返回旧请求结果。
4. SUCCEEDED：返回保存的业务结果；FINAL_FAILED：返回同一最终错误。
5. PROCESSING 且租约有效：返回 202/409 + `Retry-After`，不能递归等待。
6. 租约过期或 RETRYABLE_FAILED：CAS 更新 `lease_owner/lease_until/attempt_count` 后重新执行。
7. 核心业务结果和幂等成功记录应在同一本地事务提交；大响应只保存必要结果 ID/状态。
8. 定时清理过期成功记录；金融/审计业务保留期按合规要求确定，不盲目 24 小时删除。

### 6.3 接口分类

- **天然幂等**：按 ID 的 GET、状态已经是目标状态的确认操作。
- **需业务键**：下单、创建支付、库存预占、领券、申请排队、通知创建。
- **不能自动重试**：没有稳定业务键的第三方支付/短信调用；必须先生成本地调用记录，再按记录重试。
- **补偿同样幂等**：释放库存、释放券、释放 token、关闭支付单都使用原 reservation/lock/token/pay ID，并做条件状态更新。

移除 `IdempotentTemplate` 前先双跑：新持久记录为主，旧 Map 仅作为进程内快速返回；观察一周无不一致后删除 Map。

## 7. HTTP 客户端与远程调用规范

将各模块的裸 `new RestTemplate()` 替换为集中配置的 `RestClient` 或 WebClient。对当前同步 MVC 服务，推荐 `RestClient + Apache HttpClient 5`，避免为了非阻塞而混用编程模型。

### 7.1 起始参数

| 参数 | 建议初值 | 说明 |
|---|---:|---|
| DNS/connect timeout | 300～500 ms | 同机房内部调用起始值，按基线调整 |
| response timeout | 普通查询 1 s；写命令 2 s | 不覆盖整个 Saga 的最终完成时间 |
| connection request timeout | 200 ms | 连接池耗尽快速失败 |
| total/per-route connections | 200 / 50 | 必须结合线程池、实例数和下游容量计算 |
| retry | 最多 2 次，100/300 ms + jitter | 只对网络错误、5xx 和明确幂等命令 |

原则：

- POST 只有携带稳定 requestId 且下游实现持久幂等时才可重试。
- 400/401/403/404/409 业务错误不重试；429/503 遵守 `Retry-After`。
- 超时结果记为 `UNKNOWN`，由 Saga 查询同 requestId 的结果；不能直接当作“远端失败”。
- 为每个下游设置独立连接池/并发隔离，防止 catalog 卡死耗尽 order 的全部线程。
- 可使用 Resilience4j 熔断和 bulkhead，但熔断只保护资源，不负责业务补偿。
- 统一传播 `traceparent`、`X-Request-Id`、服务身份和 Idempotency-Key。

### 7.2 契约

内部错误响应至少包含：`code`、`message`、`retryable`、`traceId`、`details`。调用方只按稳定 error code 做决策，不解析 message。为关键命令建立契约测试，确保新旧版本在灰度期间兼容。

## 8. 下单 Saga：替换长事务同步编排

### 8.1 目标数据模型

```sql
CREATE TABLE order_submit_saga (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  request_id VARCHAR(128) NOT NULL,
  request_hash CHAR(64) NOT NULL,
  user_id BIGINT NOT NULL,
  merchant_id BIGINT NOT NULL,
  source VARCHAR(24) NOT NULL,
  state VARCHAR(32) NOT NULL,
  stock_reservation_ref VARCHAR(128) NULL,
  voucher_lock_ref VARCHAR(128) NULL,
  capacity_token_id BIGINT NULL,
  queue_ticket_id BIGINT NULL,
  order_id BIGINT NULL,
  payment_order_id BIGINT NULL,
  retry_count INT NOT NULL DEFAULT 0,
  next_retry_time TIMESTAMP(3) NULL,
  lease_owner VARCHAR(64) NULL,
  lease_until TIMESTAMP(3) NULL,
  last_error_code VARCHAR(64) NULL,
  last_error_message VARCHAR(512) NULL,
  expire_time TIMESTAMP(3) NOT NULL,
  version INT NOT NULL DEFAULT 0,
  create_time TIMESTAMP(3) NOT NULL,
  update_time TIMESTAMP(3) NOT NULL,
  UNIQUE KEY uk_order_saga_request (user_id, request_id),
  INDEX idx_order_saga_recover (state, next_retry_time),
  INDEX idx_order_saga_lease (lease_until)
);
```

`source` 为 `DIRECT` 或 `QUEUE`; `request_hash` 覆盖 merchant、商品/数量、voucher、地址和价格版本。步骤引用保存下游返回的稳定业务 ID，禁止补偿时重新猜测资源。

### 8.2 正向状态机

```text
INIT
 -> STOCK_RESERVED
 -> VOUCHER_LOCKED（无券可跳过，但状态仍明确）
 -> CAPACITY_READY ---------> ORDER_CREATED
 -> QUEUED -> QUEUE_READY --> ORDER_CREATED
 -> PAYMENT_CREATING -> PAYMENT_CREATED
 -> COMPLETED
```

关键调整：**先在 order 本地创建订单，再创建支付单。**

1. 每一步调用下游都使用 `saga.request_id + stepName` 作为幂等键。
2. 下游成功后，在一个很短的本地事务中记录返回引用和下一状态。
3. 产能 READY 后先插入 `customer_order(PAYMENT_INITIALIZING)`，提交后再调用 payment。
4. Payment 创建超时不删除订单；保持 `PAYMENT_INITIALIZING`，恢复 worker 用相同 requestId 查询/重试。
5. 创建成功后更新 `pay_order_id` 和 `PENDING_PAYMENT`。用户可轮询订单状态，不要求一个 HTTP 请求完成所有步骤。
6. 同步体验可在入口等待有限时间（如 1～2 秒）；未完成返回 `202 ACCEPTED + requestId`，后台继续推进。

不要让 worker 持有数据库事务跨远程调用。Worker 用租约 claim 一条 Saga，读取状态，释放事务后调用下游，再用 `WHERE id=? AND state=? AND version=?` 更新。

### 8.3 反向补偿

| 已完成到 | 补偿顺序 | 最终状态 |
|---|---|---|
| STOCK_RESERVED | release stock | CANCELLED/FAILED |
| VOUCHER_LOCKED | release voucher → release stock | CANCELLED/FAILED |
| CAPACITY_READY | release token → release voucher → release stock | CANCELLED/FAILED |
| PAYMENT_CREATED 未支付 | close payment → release token → release voucher → release stock | CANCELLED/FAILED |
| 已支付 | 进入退款 Saga；退款成功后再释放业务资源 | REFUNDED/CANCELLED |

补偿不是简单 catch 块：每一步都有 `COMPENSATING_*` 状态、独立幂等键、重试次数和最终人工处理状态。补偿遇到 `NOT_FOUND` 时只有在下游契约明确“从未创建/已释放”才视为成功。

### 8.4 支付确认

将“支付状态”和“订单履约状态”拆开表达：

- PaymentPaid 到达后先以 event key 去重，将 `payment_status=PAID` 落库。
- 建立 `order_paid_process` 或复用 Saga 步骤：确认库存、确认券、写 OrderPaid Outbox。
- 每一步可重入；全部完成后订单进入 `PAID/WAITING_ACCEPT`。
- 如果库存/券确认持续失败，订单进入 `PAID_RECONCILE_REQUIRED`，触发高优告警，人工选择修复资源或退款；绝不能丢弃已到账事实。
- 重复、迟到的 PaymentPaid 对已取消订单触发退款流程，而不是静默忽略或重新开单。

### 8.5 取消

取消请求先做身份和状态检查，再将订单改为 `CANCEL_REQUESTED`，由 Saga 逐步关闭支付、释放 token/券/库存。只有所有必要动作完成才标 `CANCELLED`。前端展示“取消处理中”，避免为了同步响应把远程调用塞回本地事务。

## 9. 排队与转单修复

### 9.1 立即修复错单

删除 `createOrder(0L, 10L, ...)`。内部转单接口不再接受或硬编码用户/商家：

1. fulfillment/order 只提交 `ticketId`；`capacityTokenId` 由 queue 查询或同时校验。
2. queue 用条件更新把票从 `READY` claim 为 `PROCESSING`，返回包含 `userId`、`merchantId`、`sagaRequestId`、`snapshotHash` 的服务端快照。
3. order 按 `sagaRequestId` 读取原 `order_submit_saga`，对比 user、merchant、ticket、token、snapshotHash。
4. `customer_order.queue_ticket_id` 唯一键保证一张票最多一单。
5. order 创建成功后幂等通知 queue；失败则释放 claim 租约，恢复到 READY 或进入人工处理。

队列快照不能成为价格和身份的最终事实；它只用于展示/校验，真正下单数据来自最初的 Saga 和 catalog 预占引用。

### 9.2 产能原子占用

新增或扩展商家容量事实表：

```sql
CREATE TABLE merchant_capacity (
  merchant_id BIGINT PRIMARY KEY,
  limit_value INT NOT NULL,
  inflight_count INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,
  update_time TIMESTAMP(3) NOT NULL,
  CHECK (limit_value >= 0),
  CHECK (inflight_count >= 0)
);
```

申请时在 queue 本地事务执行：

```sql
UPDATE merchant_capacity
SET inflight_count = inflight_count + 1,
    version = version + 1,
    update_time = NOW(3)
WHERE merchant_id = ?
  AND inflight_count < limit_value;
```

affected rows 为 1 才创建 HELD token；为 0 则进入排队。释放时先 CAS `capacity_token HELD -> RELEASED/EXPIRED`，成功后同事务 `inflight_count - 1`。重复释放因为 token 状态不匹配，不会重复减计数。

Redis inflight key 只从数据库投影，用于监控/快速展示，不能决定是否接单。定时对账校验 `merchant_capacity.inflight_count == count(capacity_token where HELD)`，不一致立即修复派生值并告警。

### 9.3 多 worker 推进

生产推荐以 MySQL 状态迁移作为派发事实，Redis ZSet 仅用于排名：

```sql
SELECT id
FROM queue_ticket
WHERE merchant_id = ?
  AND status = 'WAITING'
  AND expire_time > NOW(3)
ORDER BY score, id
LIMIT 1
FOR UPDATE SKIP LOCKED;
```

同一事务尝试占用容量并将票改为 READY/CLAIMED。若必须从 Redis 弹出，使用 `ZPOPMIN`/Lua，并仍以数据库 `WAITING -> CLAIMED` 的 affected rows 作为唯一成功判断；数据库失败时把成员幂等放回，且对账 worker 能从 DB 重建。

为票增加 `claim_owner`、`claim_until`、`attempt_count`。Worker 崩溃后，租约恢复任务将无订单的过期 CLAIMED/PROCESSING 票重新入队；已有订单的票直接确认完成。禁止 `ZRANGE + ZREM` 分步竞争。

### 9.4 过期与补偿

- 按 `(status, expire_time)` 索引分批 `FOR UPDATE SKIP LOCKED` 扫描，每批 100～500 条。
- WAITING 过期后写 `QueueTicketExpired` Outbox，由 order Saga 释放库存和券；queue 不直接跨域补偿。
- HELD token 过期后 CAS 释放容量并发 `CapacityTokenExpired`；若已绑订单，必须先查订单状态，禁止误释放正在履约的容量。
- 补偿事件重复消费安全；超过阈值进入人工队列。
- Redis 重建不用 `KEYS`：以 `waiting:v2:{merchant}` 新前缀批量写入，完成后切换 active version，旧前缀异步 SCAN 删除。

### 9.5 验收不变量

- `HELD token 数 == merchant_capacity.inflight_count <= limit_value`。
- 同一 queue ticket 最多一个 order、最多一个 active token。
- 双 queue worker 并发 1,000 次不会重复推进同一票。
- Worker 在 claim 后、创建订单前被 kill，租约到期后能恢复。
- Redis `FLUSHDB` 后从 MySQL 重建，顺序和票状态不发生业务性改变。

## 10. 商品库存预占修复

保留当前条件扣减 SQL，但补齐预占生命周期：

### 10.1 数据模型

`stock_reservation` 至少包含 `reservation_no`、`request_id`、`request_hash`、`order_saga_id`、`user_id`、`merchant_id`、`sku_id`、`quantity`、`status`、`expire_time`、`version`。唯一键覆盖 `(request_id, sku_id)`；若同一个请求出现重复 SKU，入服务前先按 SKU 合并数量并排序，避免同一唯一键对应多个行意图。

### 10.2 原子命令

- reserve：校验 SKU 上架与 merchant，在同一 SQL 中条件扣库存；插入预占与扣减在同一事务。
- confirm：`RESERVED -> CONFIRMED` 条件更新；重复确认直接返回现状。
- release：`RESERVED -> RELEASED/EXPIRED` 成功后才加回库存；重复释放不加库存。
- 所有命令校验 request hash；相同 requestId 不同数量返回 409。

状态和库存更新必须处于 catalog 自己的本地事务。管理员改库存不再直接覆盖 `stock`：使用“设置目标总量”的命令，校验目标量不能小于已售/已预占，并记录库存流水和操作人。

### 10.3 过期任务与对账

- 扫描 `(status='RESERVED', expire_time < now)`，分批 claim 后按条件释放。
- 订单 Saga 每次续期/进入排队时明确续约预占，续约有最大期限；不能无限占库存。
- 定时校验 `可售库存 + RESERVED + CONFIRMED/已售` 与库存流水总量；出现负数或差额立即停止该 SKU 售卖并告警。
- 过期与确认并发时由状态 CAS 决胜；只有一个动作能影响库存。

## 11. 秒杀券与 Redis 一致性修复

### 11.1 推荐的当前规模方案：MySQL 强事实，Redis 快速拒绝

把 `voucher.stock` 拆成或语义化为 `total_stock`、`issued_count`，发券最终以 MySQL 为准：

```sql
UPDATE voucher
SET issued_count = issued_count + 1,
    update_time = NOW(3)
WHERE id = ?
  AND status = 'ACTIVE'
  AND start_time <= NOW(3)
  AND end_time > NOW(3)
  AND issued_count < total_stock;
```

在同一事务插入 `voucher_claim(request_id,user_id,voucher_id,status=SUCCESS)` 和 `user_voucher`。`(user_id,voucher_id)`、`request_id` 唯一键处理一人一券与重试。唯一冲突导致整个事务回滚，`issued_count` 不会多加。

Redis Lua 只做活动开关、估算剩余和用户 Bloom/Set 预检查，以降低无效流量；即使 Redis 丢失或数据偏大，MySQL 条件更新仍保证不超发。数据库成功后通过 Outbox 更新 Redis 派生剩余，不要求请求线程同时修改两个资源。

这一方案比“Redis 先成功、后台落库”峰值低，但与当前没有可信万级流量的实际更匹配，也最容易证明正确。只有基准显示数据库成为瓶颈时，才评估分桶库存/排队异步确认；届时接口应返回 PROCESSING，而不是在事实落库前告诉用户领取成功。

### 11.2 Redis key 与重建

- key 加活动版本：`voucher:{voucherId}:{version}:remaining`、`:claimed-users`。
- TTL 设置到活动结束 + 对账保留期，避免永久 key。
- 重建剩余量使用 `total_stock - issued_count`，绝不能读取原始总量直接覆盖。
- 使用单飞锁/版本 CAS 防并发重建；写新版本完成后切换 active version。
- 大集合对账使用 SSCAN/分页数据库，不使用 SMEMBERS 一次加载全部用户。

### 11.3 补偿

如果仍保留 Redis permit，补偿必须用一个 Lua 原子完成“验证 claimId 未补偿 → 恢复库存 → 移除用户标记 → 写 compensated marker”，marker TTL 至活动对账完成。补偿任务以 claimId 唯一，不以 `(user,voucher)` 模糊推断。

### 11.4 验收

- 初始 100 张券，1,000 个并发用户最多 100 个 SUCCESS，`issued_count=成功 user_voucher 数`。
- 同一用户 100 次并发只成功一次。
- 在 Redis 扣减前后、DB 事务前后分别 kill 服务，最终都不超发且可对账。
- Redis 清空、重建、活动库存后台调整后不出现二次放量。
- 活动开始前和结束后全部拒绝。

## 12. RocketMQ、Outbox 与消费恢复

### 12.1 Outbox 表与抢占

在现有 `local_event` 增加：`retry_count`、`max_retries`、`next_retry_time`、`lease_owner`、`lease_until`、`broker_message_id`、`sent_time`、`last_error_code`、`payload_hash`；索引 `(status,next_retry_time,id)`，`event_key` 唯一。

投递器流程：

1. 事务中用 `FOR UPDATE SKIP LOCKED` 或条件 UPDATE claim NEW/FAILED 且到期的事件，写 30 秒租约。
2. 事务提交后发送 RocketMQ，设置 keys、tags、eventType、schemaVersion、traceparent。
3. 只有 `SendStatus.SEND_OK` 才 CAS 标记 SENT 并保存 broker message ID。
4. 失败按 `min(base * 2^retry, 30m) + jitter` 计算下一次；例如 5s、15s、1m、5m、15m、30m。
5. 超过 12 次或遇到不可重试错误进入 DEAD，停止自动轰炸并告警；修复数据后由受控运维接口重放。
6. SENT 前宕机会重复发送，消费者幂等承担正常重复语义。

Outbox 清理按业务审计周期归档，不能无限增长；分表只在容量数据证明需要后考虑。

### 12.2 消费记录

扩展 `consumer_record`：`event_type`、`schema_version`、`payload_hash`、`attempt_count`、`lease_owner`、`lease_until`、`next_retry_time`。收到相同 eventKey 但 payload hash/type 不同必须告警并拒绝，不能当成普通重复。

消费者处理顺序：

1. 插入/claim 消费记录。
2. 执行业务本地事务与本地 Outbox。
3. 同一事务标 SUCCESS。
4. 异常保存结构化 error code；可重试异常交给 broker/应用退避，不可重试进入人工队列。

批量消息中单条失败不应让已成功消息产生昂贵副作用；可缩小 batch，或逐条使用持久幂等记录。RocketMQ broker DLQ 与应用 DEAD 记录都要有告警和 runbook。

### 12.3 业务处理可重入

PaymentPaid 消费不能用“一条 consumer SUCCESS”掩盖内部多个远程步骤。将确认库存、确认券、推进订单、写 OrderPaid 事件分别记录步骤；重试从未完成步骤继续。事件乱序时：

- 未来状态事件先到，保存为 WAITING_DEPENDENCY 并短期重试。
- 已到达更高状态的重复旧事件直接幂等成功。
- 非法状态组合进入人工对账，不强行推进。

### 12.4 通知消费者上线

先在测试环境启用 `NOTIFY_EVENT_CONSUMER_ENABLED`，为每个事件类型写契约和重复消费测试；生产先以影子模式消费并只写审计记录，对比预期通知数量，再开启真实投递。SSE 在线推送失败不影响通知事实落库，客户端重连后按 message ID 补拉。

## 13. 签到、积分与其他 Redis 数据

签到 Bitmap 可以保留，但积分不能只存在 Redis：

- 新建 `points_ledger(id,user_id,biz_type,biz_key,delta,balance_after,create_time)`，唯一键 `(user_id,biz_type,biz_key)`；签到 `biz_key=yyyy-MM-dd`。
- MySQL 事务插入流水并更新用户积分余额；重复签到因唯一键不重复加分。
- Bitmap 是派生查询缓存，事务提交后通过 Outbox/Lua 设置；缓存丢失可从当月签到流水重建。
- 若先操作 Redis，必须用 Lua 原子执行 `GETBIT/SETBIT`，但仍只能返回“签到请求已接受”，最终权益以流水为准。
- 月历一次读取整段位图并本地解析，避免 31 次网络往返。
- 月 key TTL 至少覆盖业务查询/申诉周期；积分流水按合规要求长期保留。

所有 Redis fallback 都要按业务性质选择：排行榜/展示可 fail-open 或降级为无排名；库存、产能、权益等强约束必须退回数据库或 fail-closed，不能让各 JVM 用本地副本继续写。

## 14. 统一异常、日志、Tracing 与指标

### 14.1 API 错误契约

在 `meal-common/meal-infra` 提供统一 `@RestControllerAdvice`：

```json
{
  "success": false,
  "code": "ORDER_STATE_CONFLICT",
  "message": "order state does not allow this operation",
  "retryable": false,
  "traceId": "...",
  "details": {}
}
```

建议映射：参数错误 400、未认证 401、无权限 403、资源不存在 404、幂等/状态冲突 409、限流 429、下游暂不可用 503。内部调用和前端都按 code 处理；message 可本地化，不作为控制流。

### 14.2 Tracing 与日志

- 使用 Micrometer Tracing + OpenTelemetry，传播标准 `traceparent`；export 到 Tempo/Jaeger/Zipkin 任一现有平台。
- 结构化 JSON 日志包含 timestamp、service、env、traceId、spanId、requestId、actorType、actorId、merchantId、orderNo、eventKey、errorCode。
- Token、验证码、手机号、地址、支付凭据不入日志；手机号只留后四位，异常堆栈也做字段脱敏。
- 每个 Saga 步骤、补偿、Outbox 投递和人工重放写审计事件，记录旧状态、新状态、操作主体和原因。

### 14.3 必需业务指标

| 指标 | 标签建议 | 初始告警条件（需按基线调整） |
|---|---|---|
| `order_saga_total` / `order_saga_age_seconds` | state, step | 非终态最老记录 > 5 分钟；FAILED/人工状态 > 0 |
| `stock_reservation_total` | status, merchant | 过期 RESERVED > 0 持续 5 分钟 |
| `voucher_inventory_gap` | voucherId | 非 0 立即告警 |
| `queue_capacity_gap` | merchantId | DB inflight 与 HELD token 差额非 0 |
| `queue_wait_seconds` | merchantId | P95 超过业务承诺 |
| `outbox_lag_seconds` / `outbox_dead_total` | service,eventType | lag > 60 秒；DEAD > 0 |
| `consumer_processing_age_seconds` | group,eventType | 超过租约阈值 |
| `auth_login_total` | result,reason | 单 IP/手机号失败突增 |
| `authorization_denied_total` | route,role | internal/跨商家拒绝异常增长 |
| HTTP latency/error | service,route,status | 5xx、P95/P99 超过 SLO |

Dashboard 必须能从一张订单号跳到 Saga、库存预占、券锁、token、支付单、事件和消费记录。不要把 userId/merchantId/orderId 作为 Prometheus 高基数 label；这些放日志和 tracing。

## 15. 测试与故障注入验收

### 15.1 测试层次

- 单元测试：状态机、权限规则、请求哈希、退避算法、Lua 返回码。
- Repository 测试：Testcontainers MySQL 验证真实唯一键、锁、隔离级别、SKIP LOCKED 和 Flyway。
- 组件测试：真实 Redis 验证 Lua/重建；真实 RocketMQ 验证重复消息、重投和 DLQ。
- 契约测试：order 与 catalog/promotion/queue/payment 的请求/响应和错误码兼容。
- E2E：外部只经过 gateway，断言完整业务字段和不变量，而非只看 HTTP 200。
- 安全测试：匿名、顾客、本商家、他商家、内部服务、过期/伪造令牌六类主体矩阵。

### 15.2 必测场景

| 场景 | 断言 |
|---|---|
| 100 个相同下单 key 并发 | 1 个 Saga、1 个订单、最多 1 个支付单，响应结果一致 |
| 相同 key 不同商品 | 409，不复用旧订单 |
| 两个 order 实例同时恢复 Saga | 只有一个持有租约并推进 |
| 库存成功后 order 宕机 | 恢复后继续或过期释放，库存账平 |
| payment 收到请求并成功但 order 超时 | 相同 requestId 查询到原支付单，不重复创建 |
| PaymentPaid 重复 100 次 | 订单只推进一次、事件/库存/券副作用一次 |
| 秒杀 1,000 并发抢 100 张 | 成功恰好不超过 100，DB/Redis/用户券账平 |
| Redis 在领券过程中清空 | 不超发；重建后剩余正确 |
| 队列 limit=10，双实例 1,000 并发 | HELD 始终 <=10，其他进入队列 |
| queue worker claim 后被 kill | 租约恢复，一张票最多一单 |
| 排队转单 | order 的 user/merchant/items/amount 与原 Saga 全部一致 |
| 他人订单/支付、他商家履约 | 404/403，数据库无状态变化，产生审计记录 |
| Outbox 发送成功后标 SENT 前宕机 | 发生重复消息但无重复业务副作用 |
| 毒消息 | 达最大次数进入 DEAD，告警且可修复后重放 |

### 15.3 业务不变量查询

将下列校验做成测试断言和只读巡检任务：

- 一张 `queue_ticket` 对应订单数 ≤ 1。
- `merchant_capacity.inflight_count = HELD capacity_token 数`。
- SKU 可售库存不为负；预占状态转换不会重复加回。
- `voucher.issued_count = SUCCESS voucher_claim 数 = user_voucher 数`（考虑明确的撤销状态）。
- 每个 PaymentPaid 的订单支付状态最终为 PAID 或进入明确退款/人工处理状态。
- 每个非终态 Saga 在 SLO 时间内有下一次执行计划，不存在无 owner、无 nextRetry 的悬空记录。

E2E 的 retry 只能用于等待异步完成，不能对所有业务错误无差别重发；每次重试输出最后 error code，以免脚本掩盖真实缺陷。

## 16. 性能修复与容量验证

正确性验收完成后再做性能优化：

1. 管理端/内部列表全部分页，限制 `pageSize <= 100`；深分页使用 `(create_time,id)` 游标。
2. 队列票、商品快照和库存预占改批量 SQL；签到一次读位图。
3. 根据真实 SQL 增加候选复合索引：订单 `(merchant_id,status,create_time,id)`、Outbox `(status,next_retry_time,id)`、过期资源 `(status,expire_time,id)`；必须用 EXPLAIN ANALYZE 和写入代价验证。
4. 商家端前端按路由/组件拆包，按需加载 Element Plus；这是次要项，不阻塞后端上线。
5. 压测使用固定版本、固定数据规模、独立压测机和阶梯流量，报告吞吐、P50/P95/P99、错误率、CPU、GC、连接池、慢 SQL、Redis、RocketMQ lag。
6. 每个流量阶梯结束执行不变量查询；性能提高但账不平视为失败。

建议先定义可验证 SLO，而不是先定 QPS：例如订单请求 99.9% 可接受、同步响应 P95 < 2 秒、99% Saga 在 30 秒内到终态、Outbox P99 lag < 60 秒。具体数字根据第一轮基线和业务承诺调整。

## 17. 生产部署基线

### 17.1 环境与密钥

- `dev/test/staging/prod` 独立配置；prod 启动时主动拒绝 mock pay、固定验证码、默认用户、Nacos auth off、空 Redis 密码等危险配置。
- Secret 通过 Vault/KMS/云 Secret Manager/编排 Secret 注入，不写 Compose、镜像、日志或 Git。
- 镜像固定 digest，使用非 root 用户、只读根文件系统和最小 JRE；依赖及镜像做漏洞扫描。

### 17.2 数据与基础设施

- MySQL 使用托管高可用或主备、自动备份与时间点恢复；上线前实际演练恢复。
- Redis 至少有持久化和副本/哨兵或托管高可用；但业务正确性不依赖 Redis 永不丢数据。
- RocketMQ 使用生产集群和持久化磁盘，明确 Topic/Group/ACL；监控消息堆积、失败与磁盘。
- Compose 开发环境也使用 named volume，避免 `down`/重建无意丢数据；生产不以当前 Compose 作为 HA 方案。

### 17.3 健康与发布

- liveness 只判断进程是否需要重启；readiness 检查必要依赖和迁移版本，不健康实例不接流量。
- 优雅停机：先摘流量，停止领取 Saga/Outbox/消费租约，等待在途短任务结束，再退出。
- 数据库 migration 先于应用；应用版本至少兼容前后一个 schema 版本。
- 先 1%/单实例 canary，再 10%、50%、100%；每级观察错误率、Saga age、库存差额、Outbox lag 和授权拒绝。
- 回滚只回应用，不逆向删除新列；破坏性 contract migration 至少延后一个稳定发布周期。

## 18. 灰度迁移与回滚方案

### 18.1 推荐顺序

1. **只加不改**：Flyway 增加新表、nullable 列、索引和指标。
2. **双写**：旧业务路径继续响应，同时写 idempotency/Saga/新库存字段；失败只告警，不切流量。
3. **影子核对**：新状态机不产生外部副作用，仅计算应有结果，与旧结果比对。
4. **小流量切写**：按用户 hash/商家白名单启用新路径；不能随机让同一请求在新旧路径跳变。
5. **切读**：监控至少一个完整业务周期无差额后，读取新字段/新状态。
6. **停旧写**：保留回读和回滚开关。
7. **清理**：稳定一至两个发布周期后才删除旧 Map、旧列、运行时 DDL 和危险接口。

### 18.2 回滚触发条件

任一条件出现即停止扩流并回滚 feature flag：

- 认证成功率异常下降或 401/403 比基线突增且非攻击流量。
- `voucher_inventory_gap`、`queue_capacity_gap` 非零。
- 一票多单、重复支付单或库存负数出现一例。
- Saga P99 完成时间/失败率超过约定阈值。
- Outbox lag 持续增长、DEAD 事件增加或消费者大面积重投。

回滚后不删除新数据。对已经进入新 Saga 的请求继续由兼容 worker 完成；入口只把新请求切回旧路径。若旧路径本身存在 P0（如任意验证码、错单），不得回滚到不安全实现，而应 fail-closed/暂停相关功能。

## 19. 建议 PR 拆分

| PR | 内容 | 依赖 | 完成定义 |
|---:|---|---|---|
| 1 | 精确公共路由、移除默认身份、关闭直连端口、mock 接口限 dev | 无 | 安全路由测试通过 |
| 2 | OTP、session/token hash/轮换、登录限流 | PR1 | 任意 code/重放失败，安全日志脱敏 |
| 3 | CurrentPrincipal、内部服务令牌、资源归属 SQL | PR1 | 六主体权限矩阵通过 |
| 4 | Flyway、AUTO_INCREMENT/业务号、关键唯一键 | 无，可与 1～3 并行开发但分开发布 | 现有数据校验、迁移/回滚演练通过 |
| 5 | 持久幂等组件和订单/支付接入 | PR4 | 重启/双实例重复请求只一份结果 |
| 6 | 修 queue 转单、容量 CAS、票租约和过期事件 | PR3～5 | 双 worker 与转单不变量通过 |
| 7 | catalog 预占过期/续期/对账 | PR4～5 | 宕机/过期后库存账平 |
| 8 | promotion DB 权威库存、Redis 重建和原子补偿 | PR4～5 | 并发/清 Redis 均不超发 |
| 9 | order Saga、支付确认、取消/退款流程 | PR5～8 | 各步骤故障注入最终闭环 |
| 10 | Outbox 退避/DEAD、消费者步骤化、通知影子启用 | PR4、PR9 | 重复/毒消息可恢复 |
| 11 | 统一错误、OTel、业务指标、告警和 runbook | 可随前述 PR 渐进 | 一单可全链路定位 |
| 12 | CI、Testcontainers、分页/索引、压测报告 | 核心修复完成 | CI 全绿且 SLO/不变量达标 |

每个 PR 只解决一个可验证风险；数据库 migration、业务代码、测试和监控应在同一 PR 内闭环。不要把所有模块格式化或做无关重构混入安全/一致性修复。

## 20. 最终上线清单

### 安全

- [ ] 生产验证码真实校验、一次性消费、限流、日志脱敏。
- [ ] 所有业务端口不公开；internal 接口验证服务身份与 audience。
- [ ] 公共路由精确到方法和模板；admin/internal 无匿名通路。
- [ ] 订单、支付、票、商家、履约、通知全部做资源归属。
- [ ] mock pay、默认用户、固定验证码无法在 prod 启动。
- [ ] Token/数据库/Redis/Nacos/Grafana 密钥由 Secret 管理并完成轮换演练。

### 数据与一致性

- [ ] Flyway 管理所有 schema，无运行时 DDL。
- [ ] 本地 ID 生成器移除，关键业务唯一键上线且无历史冲突。
- [ ] 幂等记录比较请求 hash，重启和多实例有效。
- [ ] 下单/支付/取消 Saga 可恢复、可补偿、可人工处理。
- [ ] 排队转单继承真实用户/商家/商品/金额，一票最多一单。
- [ ] Redis 清空后券、队列、签到均可从事实数据恢复且不增发权益。
- [ ] 所有 RESERVED/HELD/PROCESSING 状态有过期策略、索引和恢复 worker。

### MQ 与运维

- [ ] Outbox 检查 SEND_OK，有退避、租约、最大次数、DEAD 和告警。
- [ ] 消费记录校验 payload hash，业务步骤可重入。
- [ ] 通知消费者经过影子验证后启用。
- [ ] 一张订单能通过 orderNo/traceId 关联全部资源和消息。
- [ ] 业务不变量巡检、Dashboard、告警和人工重放 runbook 可用。

### 测试与发布

- [ ] MySQL/Redis/RocketMQ Testcontainers 和权限矩阵进入 CI。
- [ ] 关键故障点 kill/timeout/重复/Redis 清空测试通过。
- [ ] 压测同时满足 SLO 和业务不变量，无虚构 QPS。
- [ ] 备份恢复、canary、feature flag、优雅停机和回滚完成演练。

## 21. 修复完成的判定

“接口正常返回”不是完成。MealFlow 达到生产基线，需要同时满足：

1. 外部主体不能绕过认证，也不能访问或修改不属于自己的数据。
2. 任意请求重试、实例重启、双实例竞争都不会产生重复订单、支付、券、票或消息副作用。
3. 任一跨服务步骤超时或宕机，流程最终要么成功，要么完整补偿；无法自动处理的记录必须可见、告警、可人工恢复。
4. Redis 和 MQ 丢失/重复语义不会破坏 MySQL 事实和业务不变量。
5. 每次发布可灰度、可回滚，数据库变更前后兼容；问题可通过指标、日志和 trace 定位。
6. 性能结论来自可复现压测，并且压测后账实仍一致。

按实际收益，最先落地 PR1～PR6；它们会消除当前绝大多数 P0。随后用 PR7～PR10建立交易最终一致性，再以 PR11～PR12把“设计正确”变成持续可证明的工程能力。整个过程中不需要新增大型中间件，关键价值来自状态、约束、身份和恢复路径的明确化。
