# MealFlow 性能基线(索引盘点 + EXPLAIN)

> 目的:让"SQL 快"有可复现的证据,而不是口头承诺。本文给出关键表的索引盘点、热点查询的
> EXPLAIN 步骤与验收方式;数据量增长后应重新执行并记录结果,任何索引变更都回到这里对比基线。

## 1. 复现环境

```bash
docker compose up -d --build
# 等所有容器 healthy 后,进入 MySQL 执行 EXPLAIN
docker compose exec mysql mysql -uroot -pmealflow mealflow
```

## 2. 索引盘点(2026-09-01 基线)

### 订单域(`meal-order`)

| 表 | 索引 | 服务查询 |
|---|---|---|
| `customer_order` | `idx_customer_order_user_id(user_id)` | 用户订单列表 `WHERE user_id=?` |
| `customer_order` | `idx_customer_order_status(status)` | 状态统计 |
| `customer_order` | `idx_customer_order_merchant_status_time(merchant_id,status,create_time)` | **后台订单查询** `WHERE merchant_id=? AND status=? AND create_time BETWEEN ...` |
| `customer_order` | `idx_customer_order_queue_ticket_id(queue_ticket_id)` | 排队转单 `WHERE queue_ticket_id=?` |
| `order_local_event` | `idx_order_local_event_status_id(status,id)` | **Outbox 派发** `WHERE status IN ('NEW','FAILED') ORDER BY id LIMIT n` |
| `order_local_event` | `uk_order_local_event_key(event_key)` | 事件唯一 |
| `order_consumer_record` | `uk_order_consumer_event_group(event_key,consumer_group)` | 消费幂等 |
| `order_saga_step` | `idx_order_saga_dispatch(status,next_retry_time)` | Saga 恢复扫描 |

### 其他域要点

| 表(服务) | 索引 | 用途 |
|---|---|---|
| `queue_ticket` (queue) | `idx_queue_ticket_merchant_status(merchant_id,status)`、`idx_queue_ticket_status_score(status,score)` | 排队推进/重建 |
| `capacity_token` (queue) | `idx_capacity_token_merchant_status(merchant_id,status)`、`uk_capacity_token_request(request_id)` | 产能占用/幂等 |
| `payment_order` (payment) | `uk_payment_order_order_id(order_id)`、`uk_payment_order_merchant_order_no(merchant_order_no)` | 一单一支付、防重复 |
| `payment_local_event` (payment) | `idx_payment_local_event_dispatch(status,next_retry_time)` | 支付 Outbox 派发 |
| `stock_reservation` (catalog) | `uk_request_sku(request_id,sku_id)` | 库存预占幂等 |
| `voucher_lock` (promotion) | `uk_request_voucher(request_id,user_voucher_id)` | 锁券幂等 |

## 3. 热点查询 EXPLAIN 基线

在 MySQL 客户端逐条执行,`type` 不应出现 `ALL`(全表扫描),`rows` 应随索引大幅缩小:

```sql
-- 3.1 后台订单分页查询(复合索引生效:type=range/ref,key=idx_customer_order_merchant_status_time)
EXPLAIN SELECT id, user_id, merchant_id, status, amount_cent
FROM customer_order
WHERE merchant_id = 10 AND status = 'WAIT_MERCHANT_ACCEPT'
  AND create_time >= '2026-01-01 00:00:00'
ORDER BY id DESC
LIMIT 20 OFFSET 0;

-- 3.2 Outbox 派发扫描(status,id 一次索引完成过滤+排序)
EXPLAIN SELECT id, event_key, status
FROM order_local_event
WHERE status IN ('NEW', 'FAILED')
ORDER BY id
LIMIT 100;

-- 3.3 排队转单唯一性查询(uk/普通索引)
EXPLAIN SELECT id FROM customer_order WHERE queue_ticket_id = 50002;

-- 3.4 产能 HELD 扫描(复合索引)
EXPLAIN SELECT COUNT(*) FROM capacity_token WHERE merchant_id = 10 AND status = 'HELD';
```

验收口径:3.1 与 3.2 的 `key` 必须命中复合索引;若数据量小(几十行)优化器可能选择全表,此时
用 `ANALYZE TABLE` 或构造批量数据后复核,并记录当时的 `rows` 作为该数据量下的基线。

## 4. 已落地的性能治理

- **后台/内部列表分页**:`/orders/admin`、`/vouchers/admin`、`/catalog/admin/skus`、
  `/auth/admin/employees` 支持 `page/pageSize`(上限 100)并返回 `PageResult{items,total,page,pageSize}`,
  不再整表拉入 JVM(commit `6bdc677`)。
- **Outbox/队列派发加 LIMIT**:order/payment/fulfillment 的派发、Saga 恢复、refund 查询均分批
  `LIMIT` 执行(既有实现,基线确认)。
- **订单后台复合索引 + Outbox (status,id) 索引**:本次新增,旧库执行
  `scripts/migrations/20260901-order-admin-index.sql`。

## 5. 已知未优化点(后续增强,不阻塞演示)

| 位置 | 问题 | 建议 |
|---|---|---|
| `CatalogService.buildSnapshots` | 逐 SKU `findSku`(N+1) | 按 `sku_id IN (...)` 批量查询快照 |
| `QueueService.tickets()` | 先查 ID 再逐票查询 + 逐票 Redis rank(N+1) | 批量查票、rank 按需/分页 |
| 深度分页 | `LIMIT x OFFSET y` 深翻页变慢 | 大表改用 `(create_time,id)` 游标 |
| 压测 | 无固定版本/数据规模的 QPS 基线 | 用 `scripts/load-*.ps1` 先建立吞吐/P95 基线,再谈优化 |

> 已修复(2026-09-02):`AuthUserService` 签到月历与连续天数改为读取 MySQL `points_ledger`,
> 不再逐日 GETBIT(31 次往返);积分余额以 `user_account.points` 为事实,Redis 仅为派生缓存。

## 6. 何时更新本文

新增索引、改造查询、或首次在更大数据集上跑出 EXPLAIN 时,回到本文更新对应行并附当时的
`rows`/`key` 输出。性能结论必须以本文件的可复现步骤为准。
