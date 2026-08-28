# 秒杀券设计复盘（以当前代码为准）

> 目标读者：Java 校招面试复习。本文描述的是当前 `meal-promotion` 的真实实现，不是目标架构，也不是接口手册。
>
> 核心代码：`PromotionService`、`RedisVoucherSeckillGuard`、`VoucherClaimPendingRecoveryScheduler`、`SeckillClaimRocketMqConsumer`、`VoucherClaimSettlementService`。

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

1. 先把它的 Pending score 前移到下一次退避时间，避免重叠调度同时发布同一预约；
2. 重新发布**相同 eventKey** 的 `SeckillClaimCommand`；
3. 将发布尝试写入或更新 `voucher_claim_retry`：发布成功标为 `RECOVERED`，失败标为 `RETRY`，并记录错误和下一次时间。

退避序列是约 10 秒、30 秒、60 秒，后续按 60 秒左移增长，并受默认 300 秒上限约束。

Pending 不能被简单理解成“没有落库”，因为 MySQL 事务可能已经提交而第 8 步 Redis 收尾失败。此时：

```text
voucher_claim = CLAIMED 或 SOLD_OUT
Pending 仍存在
```

恢复任务重发同一个事件正是为了让 Consumer 再次执行幂等结算，并完成遗漏的 Redis 收尾。

## 5. MQ 消费、MySQL 事务与幂等

Consumer 只处理 `eventType = SeckillClaimRequested` 的消息，并校验消息属性/keys 中的 eventKey 与 JSON 中 command 的 eventKey 相同。任何运行时异常会使 `RocketMqConsumerClient` 返回 `RECONSUME_LATER`；默认最大重消费次数为 5。

### 5.1 结算事务

`VoucherClaimSettlementService.settle` 在一个 Spring MySQL 事务中完成：

1. `INSERT IGNORE voucher_claim(..., PROCESSING)`；
2. 若插入成功，条件扣减 `voucher.stock`；
3. 扣减失败：将 claim 改为 `SOLD_OUT`，提交事务；
4. 扣减成功：插入 `user_voucher(AVAILABLE)`，再将 claim 改为 `CLAIMED` 并保存 `user_voucher_id`，提交事务。

同一事件重复投递时，插入 claim 返回 0。结算服务读取已有 claim：

- 已是 `CLAIMED` / `SOLD_OUT`：直接返回该最终结果，不再扣数据库库存、不再插入用户券；
- 仍是 `PROCESSING`：抛异常，由 MQ 稍后重投；
- 并发事务导致唯一键先出现、记录暂不可见时：最多查询 5 次、每次间隔 5 ms，再决定结果。

因此幂等不是依赖 JVM 内存，也不是“MQ 只投一次”，而是依赖确定 eventKey 和数据库唯一约束。

### 5.2 为什么 Redis 收尾放在事务之后

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

`mealflow.promotion.seckill.bootstrap.enabled` 默认 `false`。只有显式设为 `true` 且 marker 不存在时，应用在 `ApplicationReadyEvent`：

1. 遍历所有券，对每个 stock key 做 `SETNX(voucher.stock)`；
2. 全部调用完成后，最后写入 marker。

任何 `DataAccessException` 都不会写 marker；marker 已存在时也会跳过，以避免覆盖活动中的状态。普通 Promotion Service 重启不会初始化库存。

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

### 当前刻意接受的边界

- Producer 名字带 Outbox，但没有持久化 outbox；首次 MQ 发送可靠性依赖 Redis Pending 仍存在。
- Pending 调度只扫描 Redis；Redis 整体状态丢失后，原 Pending 本身无法自动找回。
- marker 只用于识别“可能整体丢失”，不是 Redis 全量状态的一致性快照，也不替代人工受控恢复。
- `voucher_claim_retry` 记录的是恢复任务的发布尝试，不能替代 Redis Pending 或数据库结算事实。
- 成功结算后 MySQL commit 与 Redis 收尾仍是两个步骤；系统依赖 MQ 重复投递和幂等收尾实现最终收敛，而不是提供严格的跨库原子提交。
- 领券成功后的订单使用/锁券是另一条 `user_voucher` / `voucher_lock` 链路，不属于 Redis 秒杀预约闭环。

## 10. 面试时可直接回答的三句话

1. “Redis 只负责高并发预约，MySQL 才是最终库存和发券事实；两者之间靠 Pending、消息重投和唯一键幂等收敛。”
2. “Pending 表示 Redis 预约是否完成收尾，不代表一定没落库，所以不能拿 DB 库存减 Pending 数重建库存。”
3. “Redis 整体状态丢失时我选择 fail-closed：宁可返回库存恢复中，也不在不知道 in-flight reservation 的情况下用 DB 库存直接重新开放秒杀。”
