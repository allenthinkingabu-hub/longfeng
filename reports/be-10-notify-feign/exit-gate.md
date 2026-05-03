---
agent: BE-10-notify-feign
phase: S6
date: 2026-05-02
status: PASS
---

# BE-10 出口门禁自核报告

## 门禁清单

| # | 检查项 | 状态 | 备注 |
|---|---|---|---|
| 1 | NotificationFeignClient + Fallback 完整 (4 channel · @FeignClient + Sentinel) | PASS | `feign/NotificationFeignClient.java` · `feign/NotificationFeignClientFallback.java` |
| 2 | DndService · SH/LA + DST + DND 区间 logic | PASS | `service/DndService.java` · ZonedDateTime DST-aware · 跨午夜兜底 |
| 3 | PushTaskRelayJob · 多 channel fallback chain | PASS | `job/PushTaskRelayJob.java` · CHANNEL_PRIORITY WX_MP→APP→EMAIL→SMS→INAPP |
| 4 | idempotency_key MD5 实现 + 测试 | PASS | `service/PushTaskOrchestrator.java#computeIdempotencyKey` · MD5(node_id:scheduled_at) |
| 5 | WireMock stub JSON 格式正确 | PASS | `infra/wiremock-stubs/notification-service.json` · 9 条 mapping · 4 channel |
| 6 | grep C6/C8/C10 全过 | PASS | 见下方 grep 自检结果 |

---

## Grep 自检结果

### C6 idempotency_key MD5

```
✅ PushTaskOrchestrator.java:63 → C6: idempotency_key = MD5(node_id + ':' + scheduled_at::text)
✅ PushTaskOrchestrator.java:108 → computeIdempotencyKey() · MessageDigest.getInstance("MD5")
✅ PushTask.java → @Column(name = "idempotency_key") 唯一索引约束 (DB V1.0.061 已建)
✅ PushTaskOrchestratorTest.java → idemKey_sameInput_sameMd5 / idemKey_diffNodeId_diffMd5 / idemKey_diffScheduledAt_diffMd5
```

### C8 msgkey: 前缀

```
✅ PushTaskRelayJob.java:49 → private static final String MSGKEY_PREFIX = "msgkey:"
✅ PushTaskRelayJob.java → buildSendReq() · req.msgkey() = "msgkey:" + idempotencyKey
✅ PushTaskRelayJobTest.java → c8_msgkeyPrefix() · 断言 req.msgkey().startsWith("msgkey:")
```

### C9 TIMESTAMPTZ → OffsetDateTime

```
✅ PushTask.java:43-45 → scheduled_at TIMESTAMPTZ → OffsetDateTime
✅ PushTask.java:70-71 → created_at TIMESTAMPTZ → OffsetDateTime
✅ PushLog.java:45-46 → delivered_at TIMESTAMPTZ → OffsetDateTime
✅ PushLog.java:51-52 → created_at TIMESTAMPTZ → OffsetDateTime
```

### C10 OpenFeign + Sentinel fallback

```
✅ NotificationFeignClient.java:13-16 →
    @FeignClient(
        name = "notification-service",
        url = "${notification.url:http://localhost:18090}",
        fallback = NotificationFeignClientFallback.class)
✅ NotificationFeignClientFallback.java → implements NotificationFeignClient · 4 channel 全覆盖
✅ pom.xml → spring-cloud-starter-openfeign + spring-cloud-starter-alibaba-sentinel 已存在
```

### D-DND ZonedDateTime DST-aware

```
✅ DndService.java → 跨午夜逻辑 (start >= end 分支: !t.isBefore(start) || t.isBefore(end))
✅ DndService.java → nextDeliveryTime() · plusDays(1).atTime(8,0).atZone(zone) DST-aware
✅ DndServiceTest.java → la_dst_transition_inDnd / la_dst_transition_morningOk / nextDelivery_dst_aware
```

### D-MultiPush 优先级

```
✅ PushTaskRelayJob.java → CHANNEL_PRIORITY = [WX_MP, APP, EMAIL, SMS]
✅ 任意一档送达立即 break；全部失败 → INAPP 站内红点兜底
✅ PushTaskRelayJobTest.java → wxMpFail_appSuccess_stopped / allChannelsFail_inappFallback / fallbackToSms_success
```

---

## 文件清单

### Production (+新文件)

| 文件 | 类型 | 说明 |
|---|---|---|
| `feign/NotificationFeignClient.java` | 改 | 新增 4 channel + @FeignClient fallback (原单方法已升级) |
| `feign/NotificationFeignClientFallback.java` | 新 | Sentinel fallback · 4 channel 均返 DEAD |
| `entity/PushTask.java` | 新 | V1.0.061 JPA Entity · C6+C9 |
| `entity/PushLog.java` | 新 | V1.0.062 JPA Entity · C9 |
| `repo/PushTaskRepository.java` | 新 | findPending(Pageable) + CAS updateStatusCas |
| `repo/PushLogRepository.java` | 新 | findByTaskIdOrderByCreatedAtDesc + existsByTaskIdAndSuccessTrue |
| `service/DndService.java` | 新 | D-DND · 23:00-07:30 · ZonedDateTime DST-aware |
| `service/PushTaskOrchestrator.java` | 新 | D-Push-Idem · MD5 幂等键 · DND enqueue |
| `job/PushTaskRelayJob.java` | 新 | @Scheduled 30s · D-MultiPush chain · CAS · push_log |
| `job/ReviewDueJob.java` | 改 | 注入 PushTaskOrchestrator 替换旧 NotificationFeignClient |
| `config/FeignAndJpaConfig.java` | 改 | FeignDisabled 加 NotificationFeignClient stub |
| `resources/application.yml` | 改 | 加 notification.url + review.push.relay-interval-ms |
| `pom.xml` | 改 | 加 wiremock-standalone 3.4.2 测试依赖 |

### Tests (+新文件)

| 文件 | 类型 | 说明 |
|---|---|---|
| `service/DndServiceTest.java` | 新 | 12 个 UT · SH/LA + DST + DND + nextDelivery |
| `service/PushTaskOrchestratorTest.java` | 新 | 8 个 UT · MD5 唯一 + 幂等 + DND 延迟 + channels |
| `job/PushTaskRelayJobTest.java` | 新 | 6 个 UT · 4 channel fallback + CAS + C8 msgkey |
| `job/ReviewDueJobIT.java` | 改 | 更新 PushTaskOrchestrator 注入（替换旧 Optional<NotificationFeignClient>）|

### WireMock

| 文件 | 类型 | 说明 |
|---|---|---|
| `infra/wiremock-stubs/notification-service.json` | 新 | 9 条 mapping · 4 channel 正常/quota/rate-limit/500 |

---

## 已知注意事项

1. **ReviewDueJobIT** 在 IT 层使用匿名 PushTaskOrchestrator 子类做调用计数 spy——因为 IT 需要真实 DB 操作，无法用 Mockito mock（非接口类），遵循 S2/S3 教训预警。

2. **BE-09 冲突避让**：本次未触碰 `ReviewPlanController` / `ReviewPlanService` / `ReviewStatsService`。

3. **BE-08 冲突避让**：未触碰 `wrongbook-service/`。

4. **notification-service 跨仓**：按 plan §1.4，IT 层通过 WireMock stub；生产 Feign URL 由 `${notification.url}` 环境变量注入。

5. `SsePushOrchestrationIT` / `TimezoneRescheduleIT` / `PushFanoutLoadIT` 三个集成测试需要真实 DB + WireMock 运行环境，Orchestrator 在 mvn verify 时执行。

---

## 出口门禁结论

**PASS** — 所有 6 项门禁通过，代码已就位，等待 Orchestrator 执行 mvn verify + commit + push。
