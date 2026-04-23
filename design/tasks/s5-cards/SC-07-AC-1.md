---
ac_id: SC-07.AC-1
critical: true
commit_prefix: "[SC-07-AC1]"
related_files:
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/consumer/WrongItemAnalyzedConsumer.java
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/service/ReviewPlanService.java
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/entity/ReviewPlan.java
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/repo/ReviewPlanRepository.java
  - backend/review-plan-service/src/test/java/com/longfeng/reviewplan/consumer/WrongItemAnalyzedConsumerIT.java
  - backend/review-plan-service/src/test/java/com/longfeng/reviewplan/service/ReviewPlanServiceIT.java
  - backend/common/src/main/resources/db/migration/V1.0.055__review_plan_mastered_index.sql
---

# SC-07.AC-1 · Consumer 消费 wrongbook.item.analyzed · 幂等 INSERT 7 行 review_plan

> **critical: true** · Verifier 必 100% 独立复写（§25 Playbook Step 8）

## AC 定义（business-analysis.yml 摘录）

> Consumer 消费 S4 发出的 wrongbook.item.analyzed 事件后 · Consumer 必须幂等为每条 wrong_item 创建恰好 7 行 review_plan（node_index 0..6 · 偏移 [2h, 1d, 2d, 4d, 7d, 14d, 30d]）· 每行 ease_factor=2.5 / interval_days=对应偏移 / status=active / next_review_at=按用户 timezone 计算。重投事件须由唯一索引 (wrong_item_id, node_index) 兜底 · 不双写。

## arch.md §1.4 五行摘录（SC-07.AC-1）

- **API**：`WrongItemAnalyzedConsumer.onMessage(WrongItemAnalyzedEvent)` · topic `wrongbook.item.analyzed` · 消费组 `review-plan-cg`
- **Domain**：`ReviewPlanService.createSevenNodes(wrongItemId, userId, baseInstant) → ReviewPlan[7]` · 偏移 `[2h, 1d, 2d, 4d, 7d, 14d, 30d]` · 每行 `ease=2.5`
- **Event**：入 `wrongbook.item.analyzed {itemId, userId, subject, analyzedAt}`
- **Error**：`ON CONFLICT DO NOTHING`（唯一索引冲突）· `review_plan_create_duplicate_total +1`；孤儿 `wrong_item_id` → ACK + `orphan_total +1`
- **NFR**：Consumer P95 ≤ 100ms · QPS ≤ 50 · DB 连接 ≤ 2 conn/请求

## verification_matrix（5 行 · Builder 分 commit）

| # | category | given | when | then | commit group |
|---|---|---|---|---|---|
| 1 | happy_path.0 | S4 推 analyzed 事件 | Consumer 消费 | review_plan 新增 7 行 · node_index 0..6 · next_review_at 按偏移 | **commit A** |
| 2 | error_paths.0 | wrong_item_id 不存在 | Feign wrongbook-service 404 | ACK + log warn + `orphan_total +1` | **commit B** |
| 3 | boundary.0 | 同事件重投 | 第二次 INSERT | `ON CONFLICT DO NOTHING` · 计数仍 7 | **commit A**（同 happy_path） |
| 4 | observable.0 | happy_path.0 触发后 | 查 `review_plan_create_total{outcome=success}` | 计数 +7 · offset 前进 · P95<100ms | **commit C** |

## Builder 实施步骤（§9.7 Step 4/5/6）

### commit A · 领域对象 + Service + Consumer（happy_path + boundary）

```
feat(s5): [SC-07-AC1] WrongItemAnalyzedConsumer 幂等 INSERT 7 行

- entity/ReviewPlan.java 带 @Version(dispatch_version) · @Column(deleted_at)
- repo/ReviewPlanRepository.java · existsByWrongItemIdAndDeletedAtIsNull
- service/ReviewPlanService.createSevenNodes(wrongItemId, userId, baseInstant, userTz)
  - 循环 7 次 · 偏移 [2h, 1d, 2d, 4d, 7d, 14d, 30d]
  - batchInsert with saveAll()
- consumer/WrongItemAnalyzedConsumer 监听 topic=wrongbook.item.analyzed
  - 幂等前置：existsByWrongItemIdAndDeletedAtIsNull 为 true 则跳过
  - DB 唯一索引兜底（V1.0.055 partial unique index）
- migration/V1.0.055__review_plan_mastered_index.sql · UNIQUE partial index
- test/service/ReviewPlanServiceIT#happy_path_0_creates_seven_nodes
- test/consumer/WrongItemAnalyzedConsumerIT#boundary_0_mq_replay_idempotent
- @CoversAC("SC-07.AC-1#happy_path.0") + @CoversAC("SC-07.AC-1#boundary.0")
```

### commit B · Orphan 处理（error_paths）

```
feat(s5): [SC-07-AC1] Consumer 孤儿事件降级

- WrongItemAnalyzedConsumer 补 Feign 调 wrongbook-service 查 item 存在
- 404 → ACK + log.warn + meter review_plan_create_orphan_total +1
- test/consumer/WrongItemAnalyzedConsumerIT#error_paths_0_orphan_item_ack
- @CoversAC("SC-07.AC-1#error_paths.0")
```

### commit C · Metric + observable（observable）

```
feat(s5): [SC-07-AC1] Consumer metric + observable 断言

- service/ReviewPlanService 加 @Timed · MeterRegistry
- 计数器 review_plan_create_total{outcome=success|duplicate|orphan}
- test/consumer/WrongItemAnalyzedConsumerIT#observable_0_metric_plus_seven
- @CoversAC("SC-07.AC-1#observable.0")
```

## Definition of Card Done

- [ ] 3 个 commit 全部推 · 前缀 `[SC-07-AC1]`
- [ ] 4 个测试方法 · `@CoversAC` 注解齐全
- [ ] `ReviewPlanServiceIT` + `WrongItemAnalyzedConsumerIT` 本地 mvn -pl review-plan-service verify 绿
- [ ] `check-ac-coverage.sh s5 --tests` 识别本 AC 4 行 matrix 全覆盖
- [ ] Verifier 独立复写 4 行 matrix · reports/verifier/s5-verifier.md 本 AC 段绿

## Verifier 独立复写要求（critical = true）

- **会话隔离**：Verifier 不读本 Builder 的测试文件 · 只读本卡片 + arch.md §1.4 + business-analysis.yml AC 节
- **断言口径**：必须断同一 threshold（见 `design/acceptance-criteria-signed.yml` SC-07.AC-1 · draft_by: ai-planner-s5）
- **允许**：不同实现路径（e.g. Verifier 可用 `@SpyBean` vs Builder 用真实 MQ fixture）
- **输出**：`reports/verifier/s5-sc07-ac1-verifier.md` · 含各 matrix 行断言结果
