# 旧库升级兼容脚本清单

项目不使用 Flyway/Liquibase：新库首次初始化时，使用 `docker-compose.init.yml` 覆盖启动服务；完成初始化后只使用常规 Compose。服务不会再默认启动即修改数据库。**已有数据库**的结构升级由人工审核、备份后执行本目录下的一次性兼容 SQL。

## 执行原则

1. 先备份(`mysqldump` 或云快照),再执行。
2. 每个脚本只针对"旧库缺什么"做增量修改,不重跑完整 `schema.sql`。
3. MySQL 8 不支持 `ADD INDEX IF NOT EXISTS`,重复执行会报 `Duplicate key name`,属预期行为;建议先 `SHOW INDEX FROM <table>` 确认。
4. 执行后跑 `scripts/e2e-smoke.ps1` 回归,并抽查 `docs/MealFlow-performance-baseline.md` 中的 EXPLAIN 结果。

## 已提供的脚本

| 脚本 | 适用旧库 | 内容 |
|---|---|---|
| `20260825-promotion-seckill-v2.sql` | 秒杀券旧同步领取 schema | 旧 `voucher_claim`/`voucher_claim_retry` 归档重建为新事实表,回填 event_key |
| `20260826-queue-capacity-inflight.sql` | 只有 `merchant_id/limit_value/create_time/update_time` 的旧 `merchant_queue_limit` | 补 `inflight_count` 列并按 HELD token 回填派生计数 |
| `20260901-order-admin-index.sql` | 订单/Outbox 查询性能优化前的旧库 | `customer_order` 加 `(merchant_id,status,create_time)` 复合索引;`order_local_event` 加 `(status,id)` 复合索引 |

## 已知仍需按实际库结构人工补的差异

以下历史结构差异目前没有现成脚本,升级时需根据实际库执行 `SHOW CREATE TABLE` 后逐库核对:

- 早期 `customer_order` 无 `queue_ticket_id`(排队转单唯一键)与 `capacity_token_id`。
- 早期 `payment_order` 无 `uk_payment_order_order_id` / `uk_payment_order_merchant_order_no` 唯一键。
- 早期 `voucher_lock` 无 `request_id` 与 `uk_request_voucher`。
- 早期 `stock_reservation` 无 `uk_request_sku`。
- 早期 `local_event` 无 `event_key` 唯一键、`locked_by/locked_until` 租约字段与 `retry_count/next_retry_time`。

> 升级策略:先在测试环境用真实数据的脱敏副本验证,再上生产;破坏性操作(删列/归档)至少延后一个稳定版本。
