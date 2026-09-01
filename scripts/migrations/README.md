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
| `20260827-order-expiry-and-address.sql` | 未保存订单地址快照、支付/券锁无到期字段的旧库 | 补地址快照、订单支付到期和券锁到期字段 |
| `20260831-order-remark.sql` | 已保存地址快照但未保存用户备注的订单库 | 补订单备注字段；历史订单备注保持为空 |
| `20260827-auth-single-merchant.sql` | 允许同一员工账号关联多个商户的旧库 | 人工确认无冲突后，增加 `user_id` 全局唯一约束并移除内部运维权限 |
| `20260829-notify-recipient.sql` | 只有 `user_id` 的旧通知表 | 增加统一收件人类型/ID，回填用户通知并增加商户通知查询索引 |
| `20260830-promotion-claim-backfill.sql` | 已有秒杀钱包券但缺少 claim 的旧库 | 幂等回填 `CLAIMED` 领取事实，补齐用户/券事件键与钱包券关联 |
| `20260903-queue-history-timeout-notification.sql` | 尚未支持用户排队历史与超时通知的旧库 | 新增用户历史查询索引和可重试的排队超时通知记录表 |
| `20260904-platform-voucher-scope.sql` | 缺少平台管理员与商家券归属的旧库 | 新增独立平台管理员、`PLATFORM_ADMIN` 权限与优惠券平台/商家范围字段；历史券保守保留为平台券 |
| `20260905-system-admin-governance.sql` | 已执行 20260904 的旧库 | 将平台治理角色升级为 `SYSTEM_ADMIN`，授予最小化治理权限，撤销旧 `PLATFORM_ADMIN` token，并保留独立 `platform_admin` 成员资格 |
| `20260906-system-admin-demo-phone.sql` | 已执行 20260905 的旧库 | 将带有旧演示手机号的活跃 `SYSTEM_ADMIN` 账户迁移为 `17739819838`，不改变其平台成员资格或会话安全策略 |

## 已知仍需按实际库结构人工补的差异

以下历史结构差异目前没有现成脚本,升级时需根据实际库执行 `SHOW CREATE TABLE` 后逐库核对:

- 早期 `customer_order` 无 `queue_ticket_id`(排队转单唯一键)与 `capacity_token_id`。
- 早期 `payment_order` 无 `uk_payment_order_order_id` / `uk_payment_order_merchant_order_no` 唯一键。
- 早期 `voucher_lock` 无 `request_id` 与 `uk_request_voucher`。
- 早期 `stock_reservation` 无 `uk_request_sku`。
- 早期 `local_event` 无 `event_key` 唯一键、`locked_by/locked_until` 租约字段与 `retry_count/next_retry_time`。

> 升级策略:先在测试环境用真实数据的脱敏副本验证,再上生产;破坏性操作(删列/归档)至少延后一个稳定版本。
