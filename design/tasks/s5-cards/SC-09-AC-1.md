---
ac_id: SC-09.AC-1
critical: false
commit_prefix: "[SC-09-AC1]"
related_files:
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/controller/ReviewPlanController.java
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/service/ReviewStatsService.java
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/repo/ReviewOutcomeRepository.java
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/dto/ReviewStatsResp.java
  - backend/review-plan-service/src/test/java/com/longfeng/reviewplan/controller/ReviewStatsControllerIT.java
  - backend/review-plan-service/src/test/java/com/longfeng/reviewplan/service/ReviewStatsServiceIT.java
---

# SC-09.AC-1 · GET /review-stats · 按 timezone 聚合复习结果

> **critical: false** · Verifier 30% 抽样复写（≥ 2 matrix 行）

## AC 定义（business-analysis.yml 摘录）

> GET /review-stats?range=week|month|quarter&subject=<opt> · 按 header X-User-Timezone（默认 Asia/Shanghai · Q-E）从 review_event 聚合返回 {correctRate, masteredCount, reviewCount} × day · 180d 外历史 warning

## arch.md §1.4 五行摘录（SC-09.AC-1）

- **API**：`GET /review-stats?range=&subject=` header `X-User-Timezone` → 200 / 400（INVALID_RANGE / INVALID_TIMEZONE）
- **Domain**：`ReviewStatsService.aggregate(userId, range, subject, timezone)` · JPA query `SELECT date(completed_at AT TIME ZONE ?) ... GROUP BY d`（ADR 0011）
- **Event**：无（纯查询）
- **Error**：`400 INVALID_RANGE`（非 week/month/quarter）· `400 INVALID_TIMEZONE`（非法 ZoneId · 降级默认 Asia/Shanghai）· `warnings=[{code:PARTIAL_HISTORY}]` 跨 180d
- **NFR**：P95 ≤ 500ms · 10 万 review_event 规模 · Caffeine L2 5min TTL

## verification_matrix（6 行 · Builder 分 commit）

| # | category | given | when | then | commit |
|---|---|---|---|---|---|
| 1 | happy_path.0 | user 42 tz=Asia/Shanghai · 本周 20 complete / 10 mastered | GET range=week | 7 day 数组 · +08:00 切日 | **A** |
| 2 | error_paths.0 | range=year | GET | 400 INVALID_RANGE | **A** |
| 3 | error_paths.1 | X-User-Timezone=Invalid/Zone | GET | 400 INVALID_TIMEZONE · 或降级默认 | **A** |
| 4 | boundary.0 | 本周 0 次 | GET | 7 day 数组 · correctRate=null · reviewCount=0 | **A** |
| 5 | boundary.1 | range=quarter 跨 180d | GET | response.warnings 含 PARTIAL_HISTORY | **B** |
| 6 | observable.0 | DB 10 万 row | GET P95 | < 500ms · 命中 `(subject, completed_at)` 索引 | **C** |

## Builder 实施步骤（§9.7 Step 10）

### commit A · Controller + Service + 3 error_path

```
feat(s5): [SC-09-AC1] GET /review-stats 聚合 API · happy_path + error

- service/ReviewStatsService.aggregate · JPA Criteria · 按 AT TIME ZONE 切日
- dto/ReviewStatsResp (range, subject, data[{date, correctRate, masteredCount, reviewCount}], warnings[])
- controller/ReviewPlanController.getReviewStats @GetMapping("/review-stats")
  - @RequestHeader(X-User-Timezone, defaultValue=Asia/Shanghai)
  - @RequestParam range ∈ [week, month, quarter]
- GlobalExceptionHandler 映射 INVALID_RANGE / INVALID_TIMEZONE
- test/controller/ReviewStatsControllerIT
  - #happy_path_0_week_seven_days
  - #error_paths_0_invalid_range
  - #error_paths_1_invalid_timezone_fallback_default
  - #boundary_0_zero_reviews
- @CoversAC 4 处
```

### commit B · 180d warnings

```
feat(s5): [SC-09-AC1] 180d 历史 warning 提示

- Service 检测 range 边界 · 若 end > 180d ago · 加 warnings = [{code:PARTIAL_HISTORY, detail:...}]
- test/service/ReviewStatsServiceIT#boundary_1_partial_history_warning
- @CoversAC("SC-09.AC-1#boundary.1")
```

### commit C · Caffeine + 性能 observable

```
feat(s5): [SC-09-AC1] stats Caffeine L2 5min + 性能基准

- config/StatsCacheConfig @Cacheable("review-stats") TTL 5min
- test/service/ReviewStatsServicePerfIT#observable_0_p95_under_500ms
  - @Sql 插 10 万 review_outcome · 测 P95
- @CoversAC("SC-09.AC-1#observable.0")
```

## Definition of Card Done

- [ ] 3 个 commit · 前缀 `[SC-09-AC1]`
- [ ] 6 matrix 行 `@CoversAC` 注解齐全
- [ ] mvn verify 绿 · P95 < 500ms 基准达标
- [ ] Verifier 抽样 ≥ 2 行

## Verifier 抽样要求（critical = false · ≥ 30%）

- 建议抽 happy_path.0（timezone 切日核心）+ error_paths.1（timezone 降级是 Q-E 决策核心）
- Verifier 可用 MockMvc + @Sql fixture
