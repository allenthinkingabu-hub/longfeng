---
ac_id: SC-08.AC-1
critical: true
commit_prefix: "[SC-08-AC1]"
related_files:
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/controller/ReviewPlanController.java
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/service/ReviewPlanService.java
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/entity/ReviewOutcome.java
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/entity/ReviewPlanOutbox.java
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/repo/ReviewOutcomeRepository.java
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/repo/ReviewPlanOutboxRepository.java
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/dto/CompleteReviewReq.java
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/dto/CompleteReviewResp.java
  - backend/review-plan-service/src/test/java/com/longfeng/reviewplan/service/ReviewPlanServiceCompleteIT.java
  - backend/review-plan-service/src/test/java/com/longfeng/reviewplan/controller/ReviewPlanControllerIT.java
  - backend/common/src/main/resources/db/migration/V1.0.053__review_outcome.sql
  - backend/common/src/main/resources/db/migration/V1.0.054__review_plan_outbox.sql
---

# SC-08.AC-1 · POST /review-plans/{id}/complete · 单事务 + 乐观锁 + mastered 原子性

> **critical: true** · Verifier 必 100% 独立复写

## AC 定义（business-analysis.yml 摘录）

> POST /review-plans/{id}/complete body {quality: 0-5} · 同事务：(1) SELECT FOR UPDATE；(2) SM2Algorithm.compute；(3) UPDATE review_plan 乐观锁（dispatch_version）；(4) INSERT review_outcome；(5) 发 review.completed（Outbox 兜底）。连续 3 次 ease≥2.8 触发 mastered + 发 review.mastered + 软删所有 7 行。乐观锁冲突返 409。

## arch.md §1.4 五行摘录（SC-08.AC-1）

- **API**：`POST /review-plans/{id}/complete` body `{quality: 0-5}` → 200/404/400/409/410 · OpenAPI §3.1
- **Domain**：`ReviewPlanService.complete(planId, quality)` 单事务 · SELECT FOR UPDATE → compute → UPDATE 乐观锁 → INSERT outcome → Outbox
- **Event**：出 `review.completed {planId, quality, nextReviewAt, easeFactorAfter}` + 可选 `review.mastered {wrongItemId, masteredAt}` · 走 Outbox（ADR 0005）
- **Error**：`404 PLAN_NOT_FOUND` / `400 INVALID_QUALITY` / `409 Conflict` / `410 PLAN_MASTERED`
- **NFR**：P95 ≤ 200ms · 409 率 ≤ 1% · QPS ≤ 200

## verification_matrix（7 行 · Builder 分 commit）

| # | category | given | when | then | commit |
|---|---|---|---|---|---|
| 1 | happy_path.0 | active plan (ease=2.5, int=2h) | POST complete quality=5 | 200 · plan 更新 · outcome +1 · review.completed 出 | **A** |
| 2 | error_paths.0 | plan 不存在 | POST /9999/complete | 404 PLAN_NOT_FOUND | **B** |
| 3 | error_paths.1 | body quality=6 | POST complete | 400 INVALID_QUALITY | **B** |
| 4 | error_paths.2 | 已 mastered 再 POST | POST complete | 410 PLAN_MASTERED | **D**（mastered 一起） |
| 5 | boundary.0 | 2 并发 POST 同 plan | 乐观锁冲突 | 1 个 200 · 1 个 409 · outcome +1 不重复 | **C** |
| 6 | boundary.1 | 连续 3 次 ease≥2.8 | 第 3 次 POST | 触发 mastered · 7 行 soft-delete · review.mastered 出 | **D** |
| 7 | observable.0 | 100 次 complete | 查 P95 | review_complete_p95_ms < 200ms · 409 率 < 1% | **E** |

## Builder 实施步骤（§9.7 Step 4/5/10）

### commit A · DDL + Entity + Service.complete + Controller happy_path

```
feat(s5): [SC-08-AC1] POST complete 单事务 + Outbox 兜底

- migration/V1.0.053__review_outcome.sql (ADR 0014 · id/plan_id/quality/completed_at/ease_before/after/interval_before/after)
- migration/V1.0.054__review_plan_outbox.sql (ADR 0014 · id/plan_id/event_type/payload jsonb/status/dispatched_at)
- entity/ReviewOutcome.java + repo
- entity/ReviewPlanOutbox.java + repo
- dto/CompleteReviewReq.java @Valid @Min(0)@Max(5) quality
- dto/CompleteReviewResp.java (planId, nextReviewAt, easeFactorAfter, mastered boolean)
- service/ReviewPlanService.complete(planId, quality) @Transactional
  - SELECT FOR UPDATE
  - SM2Algorithm.compute
  - UPDATE plan SET ease/interval/nextReviewAt WHERE id AND dispatch_version
  - INSERT review_outcome
  - INSERT review_plan_outbox (event_type=completed, payload jsonb)
- controller/ReviewPlanController.complete POST /review-plans/{id}/complete
- test/service/ReviewPlanServiceCompleteIT#happy_path_0_quality_5_updates_plan
- @CoversAC("SC-08.AC-1#happy_path.0")
```

### commit B · Error paths（404 + 400）

```
feat(s5): [SC-08-AC1] complete 异常分支 404/400

- Service 抛 PlanNotFoundException + QualityInvalidException
- GlobalExceptionHandler 映射 404 PLAN_NOT_FOUND / 400 INVALID_QUALITY
- test/controller/ReviewPlanControllerIT#error_paths_0_plan_not_found + #error_paths_1_quality_out_of_range
- @CoversAC("SC-08.AC-1#error_paths.0") + @CoversAC("SC-08.AC-1#error_paths.1")
```

### commit C · 并发乐观锁（boundary.0）

```
feat(s5): [SC-08-AC1] complete 并发乐观锁 409

- UPDATE ... WHERE dispatch_version = ? · 0 rows affected → throw ConflictException
- GlobalExceptionHandler 映射 409 Conflict
- test/service/ReviewPlanServiceCompleteIT#boundary_0_concurrent_two_requests_conflict
  - CompletableFuture 并发 2 请求 · 断 1 个 200 + 1 个 409 + review_outcome 仅 +1
- @CoversAC("SC-08.AC-1#boundary.0")
```

### commit D · Mastered 触发 + error_paths.2（boundary.1 + error_paths.2）

```
feat(s5): [SC-08-AC1] mastered 触发原子性 + 已 mastered 410

- Service.complete 判 mastered 条件：aggregate 本 wrong_item 最近 3 次 outcome · 连续 ease≥2.8 (Q-G)
- 触发时：UPDATE 所有 7 行 status=mastered + deleted_at + INSERT outbox(event_type=mastered)
- 已 mastered plan 再 POST → 404/410? 按 arch spec 用 410 PLAN_MASTERED
- test/service/ReviewPlanServiceCompleteIT#boundary_1_mastered_trigger_atomic
  - 第 3 次 complete 后 · 断 7 行 status=mastered · outbox +1 条 mastered 事件
- test/controller/ReviewPlanControllerIT#error_paths_2_mastered_returns_410
- @CoversAC("SC-08.AC-1#boundary.1") + @CoversAC("SC-08.AC-1#error_paths.2")
```

### commit E · 性能 observable

```
feat(s5): [SC-08-AC1] complete 性能基准

- test/service/ReviewPlanServicePerfIT#observable_0_p95_under_200ms
  - JMH / System.nanoTime · 100 请求 P95
- @CoversAC("SC-08.AC-1#observable.0")
```

## Definition of Card Done

- [ ] 5 个 commit 全推 · 前缀 `[SC-08-AC1]`
- [ ] 7 matrix 行 `@CoversAC` 注解齐全
- [ ] V-S5-05 quality=5 ease 上升 / V-S5-06 quality=0 reset · 绿
- [ ] V-S5-01 mvn -pl review-plan-service verify 绿
- [ ] DoD-S5-05/06/20 达标
- [ ] Verifier 100% 独立复写 7 matrix 行 · reports/verifier/s5-sc08-ac1-verifier.md 绿

## Verifier 独立复写要求（critical = true）

- 会话隔离 · 不读 Builder 测试文件
- 7 matrix 行全部复写 · 建议用 WebTestClient 端到端（Builder 可能用 @SpyBean service · Verifier 走真实 Controller）
- mastered boundary.1 必须单独验证聚合原子性（7 行 + 1 条 outbox）
- 产 `reports/verifier/s5-sc08-ac1-verifier.md`
