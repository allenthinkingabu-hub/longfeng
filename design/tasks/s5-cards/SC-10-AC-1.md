---
ac_id: SC-10.AC-1
critical: true
commit_prefix: "[SC-10-AC1]"
related_files:
  - backend/common/src/main/java/com/longfeng/common/feign/CalendarFeignClient.java
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/feign/CalendarFeignClientFallback.java
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/config/CalendarCacheConfig.java
  - backend/review-plan-service/src/main/java/com/longfeng/reviewplan/service/CalendarService.java
  - backend/review-plan-service/src/test/java/com/longfeng/reviewplan/feign/CalendarFeignClientIT.java
  - backend/review-plan-service/src/test/java/com/longfeng/reviewplan/feign/CalendarSentinelIT.java
  - backend/review-plan-service/src/main/resources/application.yml
---

# SC-10.AC-1 · Feign 调 calendar core-service · Sentinel 熔断 + Caffeine 10min cache

> **critical: true** · Verifier 必 100% 独立复写

## AC 定义（business-analysis.yml 摘录）

> CalendarFeignClient 调 calendar-platform core-service GET /calendar/nodes?date= · Sentinel 熔断（超时>3s 或 QPS>500）+ Caffeine 10min TTL cache · core 断且 cache 过期 · /review-plans?date= 返 503 source=unavailable

## arch.md §1.4 五行摘录（SC-10.AC-1）

- **API**：`CalendarFeignClient.getNodes(date)` · `@FeignClient(name="core-service", fallback=CalendarFeignClientFallback.class)` · `@GetMapping("/calendar/nodes")`
- **Domain**：`CalendarService.findNodes(date)` · `@SentinelResource("calendar-nodes", blockHandler/fallback="localCache")` · Caffeine TTL=10min · key `(userId, date)`
- **Event**：无（Feign 同步）
- **Error**：超时 > 3s → circuit open · fallback cache · 无 cache → `503 CALENDAR_DEPENDENCY_DOWN · source=unavailable`
- **NFR**：Sentinel QPS 阈值 500 · circuit breaker failureRatio > 50% · P95 ≤ 50ms · cache hit rate ≥ 80%

## verification_matrix（5 行 · Builder 分 commit）

| # | category | given | when | then | commit |
|---|---|---|---|---|---|
| 1 | happy_path.0 | core-service 正常 < 50ms | Feign call | 200 · cache write · source=fresh | **A** |
| 2 | error_paths.0 | core 超时 > 3s | Feign call | circuit open · fallback cache · `circuit_open_total +1` | **B** |
| 3 | error_paths.1 | cache 过期 & core 断 | /review-plans?date= | 503 CALENDAR_DEPENDENCY_DOWN · source=unavailable | **B** |
| 4 | boundary.0 | 1000 并发 QPS | Sentinel 阈值 | 超出 429 · core 不被压爆 | **C** |
| 5 | observable.0 | 1000 次调用 | P95 | < 50ms · cache hit ≥ 80% | **D** |

## Builder 实施步骤（§9.7 Step 8/9）

### commit A · Feign + Caffeine happy_path

```
feat(s5): [SC-10-AC1] CalendarFeignClient + Caffeine 10min

- backend/common/src/main/java/com/longfeng/common/feign/CalendarFeignClient
  - @FeignClient(name="core-service", url="${calendar.core-service.url}", fallback=CalendarFeignClientFallback)
  - @GetMapping("/calendar/nodes") getNodes(@RequestParam LocalDate date)
- backend/review-plan-service/src/main/java/com/longfeng/reviewplan/feign/CalendarFeignClientFallback
  - impl CalendarFeignClient · getNodes 读 Caffeine cache
- config/CalendarCacheConfig
  - CaffeineCacheManager · 10min TTL · size 1000
  - @Cacheable("calendar-nodes") key=(userId,date)
- service/CalendarService.findNodes(userId, date)
  - @SentinelResource(value="calendar-nodes", blockHandler="localCache", fallback="localCache")
- application.yml 补 feign.sentinel.enabled=true · spring.cloud.sentinel.transport.*
- test/feign/CalendarFeignClientIT#happy_path_0_cache_write_source_fresh
  - MockWebServer 200 · 断 service.findNodes → cache 写入 + source=fresh
- @CoversAC("SC-10.AC-1#happy_path.0")
```

### commit B · Sentinel 熔断 + 503 降级（error_paths）

```
feat(s5): [SC-10-AC1] Sentinel 熔断 + 503 降级

- CalendarFeignClientFallback.getNodes 读 Caffeine
  - cache hit & staleness ≤ 10min → 返 cached
  - miss/expired → throw CalendarDependencyDownException
- ReviewPlanController.getReviewPlans catch CalendarDependencyDownException
  - 返 503 · meta.source=unavailable · error_code=CALENDAR_DEPENDENCY_DOWN
- metric circuit_open_total via @Counted
- test/feign/CalendarSentinelIT
  - #error_paths_0_timeout_opens_circuit（MockWebServer delay 5s + 重复调用触发熔断）
  - #error_paths_1_cache_expired_returns_503（driven Caffeine clock）
- @CoversAC("SC-10.AC-1#error_paths.0") + @CoversAC("SC-10.AC-1#error_paths.1")
```

### commit C · 并发限流 boundary

```
feat(s5): [SC-10-AC1] Sentinel QPS 500 阈值

- application.yml 补 spring.cloud.sentinel.datasource.calendar.file.*（本地 Nacos 前 local JSON 规则）
- test/feign/CalendarSentinelIT#boundary_0_qps_500_limit_429
  - ExecutorService 1000 并发 · 预期 429 ~50% · core 不被压
- @CoversAC("SC-10.AC-1#boundary.0")
```

### commit D · 性能 observable

```
feat(s5): [SC-10-AC1] Feign + cache 性能基准

- test/feign/CalendarFeignPerfIT#observable_0_p95_under_50ms_hit_80
  - warmup 100 次 · 测 1000 次 P95
  - 断 cache hit ratio ≥ 80%
- @CoversAC("SC-10.AC-1#observable.0")
```

## Definition of Card Done

- [ ] 4 个 commit 全推 · 前缀 `[SC-10-AC1]`
- [ ] 5 matrix 行 `@CoversAC` 注解齐全
- [ ] V-S5-09 Feign Circuit 绿（core-service 断 · 本地 cache 返回）
- [ ] DoD-S5-09 达标
- [ ] Verifier 100% 独立复写 · reports/verifier/s5-sc10-ac1-verifier.md 绿

## Verifier 独立复写要求（critical = true）

- 会话隔离 · 不读 Builder 测试
- 5 matrix 行全部复写 · 重点：error_paths.0（熔断触发）+ error_paths.1（503 降级口径）
- Verifier 用 Testcontainers 启 MockServer 替代 Builder 的 MockWebServer · 不同工具验证同语义
- 产 `reports/verifier/s5-sc10-ac1-verifier.md`
