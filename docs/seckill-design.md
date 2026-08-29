# 秒杀券设计复盘（以当前代码为准）

> 目标读者：Java 校招面试复习。本文描述的是当前 `meal-promotion` 的真实实现，不是目标架构，也不是接口手册。
>
> 核心代码：`PromotionService`、`RedisVoucherSeckillGuard`、`VoucherClaimPendingRecoveryScheduler`、`SeckillClaimRocketMqConsumer`、`VoucherClaimSettlementService`、`VoucherClaimTransactionService`。

## 1. 一句话概括

系统把**高并发入口的预约**放在 Redis Lua 中，把**最终发券和数据库库存扣减**放在 RocketMQ 消费后的 MySQL 事务中。Redis 的 Pending ZSet 记录的不是“尚未落库”，而是“Redis 预约的收尾尚未确认完成”。

这是一条异步闭环：

```text
请求 → Redis 原子预约 → RocketMQ → MySQL 结算 → Redis 收尾
                    ↘ Pending 定时重投 ↗
```

Redis 与 MySQL 之间没有分布式事务。因此代码用三个手段收敛：Redis 预约、数据库唯一约束幂等、以及 Pending 重投/重复消费后的收尾。

## 2. 状态与数据分别代表什么

### 2.1 Redis 秒杀状态（按券隔离）

对券 `voucherId`，当前实现使用以下 key：

| Key | 类型 | 作用 |
| --- | --- | --- |
| `seckill:{voucherId}:stock` | String | Redis 可预约库存 |
| `seckill:{voucherId}:users` | Set | 已在 Redis 成功预约的用户，用于一人一领 |
| `seckill:{voucherId}:pending` | ZSet | Redis 预约尚未完成收尾的用户；score 是下次允许重投的毫秒时间戳 |
| `seckill:state:initialized` | String | 全局连续性 marker，表示 Redis 秒杀状态经过受控初始化 |

这些 key 的实现中**没有设置 TTL**。特别是：成功领取后用户仍留在 `users` Set 中；成功领取仅删除 Pending 成员。

`{voucherId}` 是 Redis Cluster hash tag 风格的 key 写法；当前项目没有引入 Redis Cluster，但同券的 Lua keys 因而有一致的 key 形式。

### 2.2 MySQL 最终事实

| 表 | 当前职责 |
| --- | --- |
| `voucher` | 券配置和最终剩余库存。结算时使用 `UPDATE ... SET stock = stock - 1 WHERE status = 'ACTIVE' AND stock > 0` 条件扣减。 |
| `voucher_claim` | 每次“用户领取某券”的持久化结算记录，状态为 `PROCESSING`、`CLAIMED`、`SOLD_OUT`。 |
| `user_voucher` | 成功发给用户的券。`(user_id, voucher_id)` 唯一。 |
| `voucher_claim_retry` | Pending 重投的观测/重试记录，保存发布尝试、错误和下次时间；Pending ZSet 才是扫描重投的来源。 |

`voucher_claim` 同时有 `event_key` 唯一约束和 `(user_id, voucher_id)` 唯一约束。业务事件键固定为：

```text
seckill:{voucherId}:{userId}
```

请求中的 `requestId` 会被校验为非空，但当前 `PromotionService.seckill` 不用它生成事件键或做幂等判断；秒杀幂等的实际维度是“同一用户 + 同一券”。

## 3. 正常领取链路

### 3.1 同步入口：先校验，再 Redis 预约

`PromotionService.seckill` 的顺序是：

1. 查询券；非 `ACTIVE` 或已经结束，返回 `FAILED`。
2. 开始时间在未来，返回 `NOT_STARTED`，不访问 Redis。
3. 检查 `seckill:state:initialized`。不存在时返回 `STOCK_RECOVERING`，不创建任何预约。
4. 执行 Redis Lua：一次脚本同时检查库存、一人一领，并在成功时扣减库存、写用户 Set、写 Pending ZSet。

Lua 返回值和内部含义：

| Lua 返回 | `ClaimResult` | 行为 |
| --- | --- | --- |
| `0` | `ACCEPTED` | `DECR stock`、`SADD users`、`ZADD pending`，预约成功。 |
| `1` | `SOLD_OUT` | 库存为 0 或负数，不写用户/ Pending。 |
| `2` | `DUPLICATE` | 用户已在 `users` Set 中。 |
| `3` | `STOCK_MISSING` | `stock` key 不存在。 |

Lua 把“查库存、判重、扣库存、写 Pending”放在一个 Redis 原子执行单元中，所以并发请求不会在 Redis 层把同一份库存重复预约。

预约成功时，Pending 的初始 score 为“当前时间 + `pending-recovery.initial-timeout-ms`”。默认配置是 10 秒，构造函数会把配置下限限制为 1 秒。

### 3.2 发送消息与立即响应

预约成功后，服务构造 `SeckillClaimCommand(eventKey, voucherId, userId)`，发布 `SeckillClaimRequested` 到 `mealflow-seckill-commands`。

发布成功或失败，HTTP 入口都返回 `PENDING`；这符合“已预约，最终结算异步完成”的语义。

若首次发送抛出运行时异常：

- 不回滚 Redis 预约；
- 尝试把该用户当前券的 Pending score 改为“5 秒后”；
- 若这次改 score 也失败，原 Pending 成员仍保留，等 Redis 可用后由恢复任务扫描。

这里有一个容易误读的名称：`RocketMqOutboxClient` 当前只是对 RocketMQ `send` 的同步封装，**没有对应的 MySQL outbox 表，也没有本地事务消息记录**。消息首次发送失败后的可靠性来自已经存在的 Redis Pending 和定时重投，而不是持久化 Outbox。

## 4. Pending 恢复：为什么它是“收尾未确认”

`VoucherClaimPendingRecoveryScheduler` 默认在应用启动后 10 秒开始、每 5 秒运行一次。每轮遍历券 ID，并在全局 batch 上限内（默认 100）读取当前券 `pending` ZSet 中 score 不大于当前时间的成员。

对每个待恢复成员：

1. 先把它的 Pending score 前移到下一次退避时间，降低同一实例下一轮立即重复发布的概率；
2. 重新发布**相同 eventKey** 的 `SeckillClaimCommand`；
3. 将发布尝试写入或更新 `voucher_claim_retry`：发布成功标为 `RECOVERED`，失败标为 `RETRY`，并记录错误和下一次时间。

退避序列是约 10 秒、30 秒、60 秒，后续按 60 秒左移增长，并受默认 300 秒上限约束。

这里没有实现“多个调度实例对同一个 Pending 成员的原子抢占”。当前本地部署只有一个 promotion 实例；若手动触发与定时任务刚好重叠，仍可能重复发布。重复发布由下游数据库唯一约束和幂等结算吸收，不能把“先移动 score”表述成分布式锁。

Pending 不能被简单理解成“没有落库”，因为 MySQL 事务可能已经提交而第 8 步 Redis 收尾失败。此时：

```text
voucher_claim = CLAIMED 或 SOLD_OUT
Pending 仍存在
```

恢复任务重发同一个事件正是为了让 Consumer 再次执行幂等结算，并完成遗漏的 Redis 收尾。

## 5. MQ 消费、MySQL 事务与幂等

Consumer 只处理 `eventType = SeckillClaimRequested` 的消息，并校验消息属性/keys 中的 eventKey 与 JSON 中 command 的 eventKey 相同。任何运行时异常会使 `RocketMqConsumerClient` 返回 `RECONSUME_LATER`；默认最大重消费次数为 5。

### 5.1 结算事务

结算被刻意拆成两层：

- `VoucherClaimSettlementService` 是**无事务的外层协调器**；
- `VoucherClaimTransactionService.settleNew` 是**新领取的事务边界**。

第一次处理事件时，内层事务完成：

1. 普通 `INSERT` 写入 `voucher_claim(..., PROCESSING)`；
2. 条件扣减 `voucher.stock`；
3. 扣减失败：将 claim 改为 `SOLD_OUT`，提交事务；
4. 扣减成功：插入 `user_voucher(AVAILABLE)`，再将 claim 改为 `CLAIMED` 并保存 `user_voucher_id`，提交事务。

同一事件或同一用户同一券并发处理时，数据库唯一约束抛出 `DuplicateKeyException`。这会使**内层新领取事务先完整回滚**；异常回到外层协调器后，外层再按 `event_key`、其次按 `(user_id, voucher_id)` 查询已经提交的 claim：

- 已是 `CLAIMED` / `SOLD_OUT`：直接返回已有最终结果，不再扣数据库库存、不再插入用户券；
- 仍是 `PROCESSING`：抛异常，由 MQ 稍后重投；
- 唯一冲突后仍查不到记录：抛出带 eventKey、userId、voucherId 的异常，不伪造成功结果。

因此当前实现**不使用 `INSERT IGNORE`，也不在同一个事务快照中 sleep 轮询**。幂等不是依赖 JVM 内存，也不是假设“MQ 只投一次”，而是依赖确定 eventKey、数据库双唯一约束，以及“冲突事务回滚后再查询”的事务边界。

### 5.2 本次修复的竞态

旧实现使用 `INSERT IGNORE`。并发消费者中，失败方看到 affected rows 为 0 后，仍在自己的结算事务中读取 claim；此时唯一键冲突对应的另一个事务可能尚未提交，或者当前事务快照不可见，于是偶发出现“唯一键已存在但 claim 行不可用”。在事务内做 5 次短暂 sleep 不能改变这个结构性问题。

当前修复把“尝试创建新事实”和“读取已存在事实”分到两个事务阶段：唯一冲突先回滚新领取事务，外层才查询已提交结果。这样不会在失败事务的旧快照里制造“幽灵成功”，也不会靠毫秒级等待猜测另一个事务何时提交。

### 5.3 为什么 Redis 收尾放在事务之后

Consumer 在 `settle` 返回后才处理 Redis：

| MySQL 最终结果 | Redis 收尾 |
| --- | --- |
| `CLAIMED` | `ZREM seckill:{voucherId}:pending userId` |
| `SOLD_OUT` | 执行补偿 Lua：必要时归还 Redis stock、删除用户 Set 标记、删除 Pending |

这两步不在同一分布式事务中。若收尾抛异常，Consumer 抛出异常让 MQ 重投；再次消费读到已有的最终 claim 后，仍会再次做 `complete` 或 `compensate`。这就是“重复消息不仅要跳过 DB，还必须继续收尾”的原因。

`SOLD_OUT` 补偿 Lua 的关键判断是：只有 `SREM users` 实际删除了该用户，且 stock key 仍存在，才 `INCR stock`；随后无条件 `ZREM pending`。所以重复执行时，第一次成功删除用户标记后，后续执行不会再次加库存。若 stock key 已缺失，脚本只清理可安全清理的用户标记和 Pending，不凭空创建库存 key。

## 6. Redis stock 单 key 丢失的安全恢复

`STOCK_MISSING` 不等于活动未开始。处理它的前提是 Redis 整体连续性 marker 仍存在。

真实顺序如下：

1. 请求前先检查 marker；缺失直接 `STOCK_RECOVERING`。
2. Lua 返回 `STOCK_MISSING` 后，**再次**检查 marker。这样覆盖“第一次检查后 Redis 整体重启”的窗口。
3. 用当前 `voucherId` 的 `ZCARD seckill:{voucherId}:pending` 检查 Pending 总数。
4. Pending 大于 0：返回 `STOCK_RECOVERING`，不读 MySQL 重建；已有 Pending 继续由恢复任务重投。
5. Pending 为 0：读取当前 `voucher.stock`，对 stock key 执行 `SETNX`（代码为 `setIfAbsent`）。
6. 无论本请求是否拿到 `SETNX`，最多重新执行一次 Lua；成功则照常进入 `PENDING`，仍缺失则返回 `STOCK_RECOVERING`。

不能使用“DB stock - Pending 数”计算可用库存。Pending 中可能含有“DB 已经 `CLAIMED`、但 Pending 尚未删除”的成员；用它相减会把已经扣过的事实再扣一次。

`SETNX` 既恢复 stock，又充当恢复竞争控制：多个请求并发看到单 key 缺失时，只有一个写入；其他请求不会覆盖已恢复且可能已被后续请求扣减的库存。

## 7. Redis 整体状态丢失：为什么选择 fail-closed

若 `seckill:state:initialized` 也不存在，代码把它视为 Redis 秒杀状态可能整体丢失。此时 stock、用户 Set、Pending 都可能已不在，`Pending == 0` 没有证明没有 in-flight 预约。

因此系统选择：

```text
marker 缺失 → STOCK_RECOVERING → 禁止新 Redis 预约 → 不从 MySQL 自动开放秒杀
```

这是有意识的可用性取舍：短时间拒绝领取，换取不在未知 in-flight reservation 下猜库存，从而避免超卖。当前代码没有 Redisson、reservation 表、分布式事务或复杂灾备流程。

### 7.1 受控 bootstrap 的真实边界

`mealflow.promotion.seckill.bootstrap.enabled` 的应用默认值是 `false`。只有显式设为 `true` 且 marker 不存在时，应用在 `ApplicationReadyEvent`：

1. 遍历所有券，对每个 stock key 做 `SETNX(voucher.stock)`；
2. 全部调用完成后，最后写入 marker。

任何 `DataAccessException` 都不会写 marker；marker 已存在时也会跳过，以避免覆盖活动中的状态。使用应用默认配置时，普通 Promotion Service 重启不会初始化库存。

当前 `docker-compose.yml` 为了本地空环境首次启动方便，显式设置了 `SECKILL_BOOTSTRAP_ENABLED=true`。因此在 Compose 环境里，只要 marker 仍在，重启服务会安全跳过；但如果只清空 Redis 数据而保留 MySQL，再启动 promotion，bootstrap 会自动执行。这是本地演示便利性取舍，不等价于可靠的 Redis 灾难恢复。不要在仍有旧 MQ 消息或未收敛领取时单独删除 Redis volume。

这个开关只能用于：

- 全新环境首次初始化；或
- 人工确认旧 MQ/Consumer 秒杀链路已经完全静默、MySQL 已收敛后的受控恢复。

它**不是** Redis 整体丢失后的普通恢复按钮。当前 bootstrap 只初始化 stock key 和 marker；代码不会自动重建历史用户 Set、Pending 或未完成预约。因此不能把它表述为“Redis 灾难后自动恢复”。

## 8. 对外状态的面试表达

| 状态 | 真实语义 |
| --- | --- |
| `NOT_STARTED` | 当前时间早于券开始时间，尚未进入 Redis。 |
| `PENDING` | Redis 已预约，等待 MQ/MySQL 结算，或首次消息发送失败后等待 Pending 重投。 |
| `CLAIMED` | 查询到数据库结算完成，用户券已创建。 |
| `ALREADY_CLAIMED` | 再次请求时 Redis 判重，且数据库已确认 `CLAIMED`。 |
| `SOLD_OUT` | Redis 无库存，或 MySQL 最终条件扣减失败后的结算结果。 |
| `STOCK_RECOVERING` | marker 缺失、单 stock key 缺失但 Pending 未清、或一次安全恢复后仍无法预约。 |
| `FAILED` | 券非活动状态或已结束。 |

前端按这些明确状态展示“活动未开始”或“库存正在恢复”，不再根据 `startTime` 反推 Redis 故障。

## 9. 设计收益、取舍与未覆盖边界

### 能解决的问题

- Redis Lua 在入口削峰，且原子保障一人一领与库存预约。
- MySQL 条件扣库存和双唯一约束保证最终库存、最终发券不因消息重复而重复执行。
- Pending 与 MQ 重投把“Redis 已预约但消息发送/收尾失败”的窗口转化为可重试闭环。
- 单券 stock key 单独丢失时，在 Pending 已清且 marker 连续的前提下可安全恢复。

### 2026-08-29 修复后验证

- H2/MyBatis 的 100 用户/库存 10 测试在修复后曾验证得到 10 个 `CLAIMED`、90 个 `SOLD_OUT`；但本次严格复验时整组测试出现 1 次主键 `DuplicateKeyException`，随后单独重跑同一用例又通过，说明它仍是偶发失败，不能登记为稳定通过。
- 重复消息测试：同一 eventKey 并发结算 20 次，只扣减一次数据库库存、只生成一张用户券，所有调用收敛到 `CLAIMED`。
- Redis 收尾失败重投：MySQL 已提交而首次 `complete`/`compensate` 失败时，重复消费继续执行相同收尾，不重复扣库或发券。
- 真实 Redis + RocketMQ + MySQL：20 个用户抢库存 10，最终为 10 个 `CLAIMED`、10 个 `SOLD_OUT`。

这些结果证明当前测试范围内的一人一券、防超卖和重复消费幂等；它们不是多实例、大规模压测或全故障点混沌测试的替代品。

### 当前刻意接受的边界

- Producer 名字带 Outbox，但没有持久化 outbox；首次 MQ 发送可靠性依赖 Redis Pending 仍存在。
- Pending 调度只扫描 Redis；Redis 整体状态丢失后，原 Pending 本身无法自动找回。
- marker 只用于识别“可能整体丢失”，不是 Redis 全量状态的一致性快照，也不替代人工受控恢复。
- `voucher_claim_retry` 记录的是恢复任务的发布尝试，不能替代 Redis Pending 或数据库结算事实。
- 成功结算后 MySQL commit 与 Redis 收尾仍是两个步骤；系统依赖 MQ 重复投递和幂等收尾实现最终收敛，而不是提供严格的跨库原子提交。
- 领券成功后的订单使用/锁券是另一条 `user_voucher` / `voucher_lock` 链路，不属于 Redis 秒杀预约闭环。
- 受控 bootstrap 只恢复 stock 和 marker，不重建历史用户 Set；当前 Compose 自动开启 bootstrap 只适合本地首次启动或确认链路静默后的恢复。
- Pending 调度没有多实例原子抢占；当前单实例本地部署允许偶发重复发布，并由数据库幂等吸收。

### 2026-08-29 严格复核发现的未解决项

#### 已有用户券与 Redis users Set 不一致

当前受控 bootstrap 只初始化 stock 和 marker，不把已有 `user_voucher`/`voucher_claim` 回填到 Redis users Set。与此同时，`data.sql` 为用户 100、券 1000 直接种了一条 `user_voucher`，但没有对应 `voucher_claim`。

因此存在两种后果：

- 已有 `CLAIMED` 记录但 users Set 缺失的用户再次请求，会重新消耗一次 Redis 预约库存；Consumer 读取已有 `CLAIMED` 后只执行 `complete`，不会归还这次多余预约，导致 Redis stock 小于 MySQL stock。
- 只有 `user_voucher`、没有 claim 的种子用户再次请求时，内层事务会在插入 `user_voucher` 处触发唯一键冲突并整体回滚；外层把所有 `DuplicateKeyException` 都按“claim 插入冲突”处理，却查不到已有 claim，于是消息持续失败、Pending 无法正常收尾。

这不会突破 MySQL 条件扣库存和唯一约束，所以不会超卖或重复发券；但会造成 Redis 可预约库存虚减、请求长期 `PENDING` 和重复重投。修复时应同时处理两个事实：受控 bootstrap 必须重建历史已领用户标记，且结算层必须区分 claim 唯一冲突与 `user_voucher` 唯一冲突（或先把种子/迁移数据补齐为一致的 claim 事实）。在修复前，不能把“Redis 整体恢复后可直接重新开放秒杀”作为已实现能力。

#### `DuplicateKeyException` 的捕获范围仍过宽

`VoucherClaimSettlementService` 当前捕获 `settleNew` 抛出的所有 `DuplicateKeyException`，默认它们都来自 `voucher_claim` 的 eventKey 或用户+券唯一约束。但异常也可能来自 `voucher_claim.id` 主键、`user_voucher(user_id, voucher_id)` 或其他完整性约束。

本次执行 promotion 定向测试时，100 用户并发用例曾出现不同 eventKey 的 `voucher_claim.id` 主键冲突；外层把它误判为业务重复领取，随后按当前 command 查不到 claim，于是报出 `duplicate claim conflict but persisted claim is unavailable`。同一用例单独重跑通过，证明这是偶发测试失败。项目当前使用的 H2 2.2.224 在嵌入式并发 `AUTO_INCREMENT` 场景有同类已知问题（[H2 issue #3950](https://github.com/h2database/h2database/issues/3950)），所以这次主键碰撞更可能是测试数据库缺陷，而不是 MySQL 自增行为；但“把任意唯一冲突当成 claim 重复”本身仍是实现缺口，并与前述种子 `user_voucher` 冲突问题共用同一错误分支。

后续修复应只把可确认来自 `voucher_claim` 两个业务唯一键的冲突交给已有结果查询；其他主键/用户券冲突必须单独处理或原样抛出，避免掩盖真实数据问题。修复前，promotion 全量定向测试不能标记为稳定通过。

## 10. 面试时可直接回答的三句话

1. “Redis 只负责高并发预约，MySQL 才是最终库存和发券事实；两者之间靠 Pending、消息重投和唯一键幂等收敛。”
2. “Pending 表示 Redis 预约是否完成收尾，不代表一定没落库，所以不能拿 DB 库存减 Pending 数重建库存。”
3. “Redis 整体状态丢失时我选择 fail-closed：宁可返回库存恢复中，也不在不知道 in-flight reservation 的情况下用 DB 库存直接重新开放秒杀。”
