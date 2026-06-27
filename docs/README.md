# MealFlow 设计文档索引

本目录是 MealFlow 微服务外卖项目的设计资料入口。

## 阅读顺序

1. `MealFlow-development-guide.md`
   从零开发指南和学习路线。完全重写、不复用旧代码时，以此文件作为主入口。

2. `MealFlow-microservice-architecture.md`
   总览架构，说明项目定位、服务边界和核心技术取舍。

3. `MealFlow-queue-design.md`
   高峰排队专题，说明 `QueueTicket`、`capacity_token`、等待时间、取消和补偿。

4. `MealFlow-consistency-data-model.md`
   一致性专题，说明 Outbox、consumer_record、requestId、库存预占、优惠券锁定、订单状态机。

5. `MealFlow-api-events.md`
   API 和事件契约，说明服务接口、事件清单、eventKey 规则。

6. `MealFlow-ddl.sql`
   核心 DDL 草案。开发建表优先参考此文件，再回看对应专题文档。

7. `MealFlow-status-codes.md`
   所有 TINYINT 状态码映射。写 SQL、枚举、补偿任务和测试用例时优先参考此文件。

8. `MealFlow-migration-test-plan.md`
   迁移路线和测试计划。当前若选择完全重写，它主要作为测试验收和阶段拆分参考，不再作为主开发路径。

9. `MealFlow-review-fixes.md`
   架构评审问题修正对照表。

## 单一事实源约定

- 表结构第一参考：`MealFlow-ddl.sql`。
- 状态码第一参考：`MealFlow-status-codes.md`。
- 排队状态机第一参考：`MealFlow-queue-design.md`。
- 事件清单第一参考：`MealFlow-api-events.md`。
- 幂等、Outbox、资源锁定第一参考：`MealFlow-consistency-data-model.md`。
- 从零开发主路线第一参考：`MealFlow-development-guide.md`。
- 总览文档中的 DDL 片段必须与 `MealFlow-ddl.sql` 保持一致。

## 当前注意事项

- `MerchantCapacityReleaseRequestedEvent` 的 eventKey 前缀使用生产服务名，例如 `order:` 或 `fulfillment:`。
- `consumer_record` 的 FAILED/TIMEOUT 重试使用 `locked_by + locked_until` 租约抢占。
- `capacity_token` 始终归 queue-service 管理，订单创建后由 queue-service 回填 `order_id`。
- 产能释放从 Redis 等待队列取候选时使用 ZPOPMIN 循环 + MySQL CAS，不能只弹一个。
- 直接下单创建的孤儿 `capacity_token` 通过 `request_id` 补偿扫描恢复或释放。
- `stock_reservation.uk_order_sku` 只保护订单创建后场景，ticket 阶段依赖 requestId、ticketId、活跃预占查询和短锁。
- 不同表的并发策略统一见 `MealFlow-consistency-data-model.md` 的“并发控制策略汇总”。
