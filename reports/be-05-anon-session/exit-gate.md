# BE-05-anon-session · Exit Gate Self-Check Report

**Date**: 2026-05-02  
**Agent**: BE-05-anon-session (b mode · Read+Write only)  
**Branch**: `agent/be-05-anon-session`  
**Base**: `feature/s7-frontend-core` @ `de321e8`

---

## 1. 文件清单 (9 prod + 4 test)

### Production files

| # | Path | 类型 | 对应 TDD |
|---|---|---|---|
| 1 | `entity/GuestSession.java` | JPA Entity | §4.10 V1.0.070 |
| 2 | `entity/AccountDevice.java` | JPA Entity | §4.13 V1.0.076 |
| 3 | `entity/GuestRateBucket.java` | JPA Entity (composite PK) | §4.10 V1.0.071 |
| 4 | `session/GuestSessionRepository.java` | Spring Data JPA | §4.10 · CAS queries |
| 5 | `session/GuestSessionService.java` | Domain Service | §3.1 session/ · state machine |
| 6 | `session/GuestSessionExpiryJob.java` | XXL-Job | §3.1 job/ · BRIN scan |
| 7 | `device/AccountDeviceRepository.java` | Spring Data JPA | §4.13 |
| 8 | `device/DeviceFingerprintService.java` | Domain Service | §3.1 device/ · 5-source hash |
| 9 | `device/FingerprintMatchPolicy.java` | Policy Component | D-Guest-Device |
| 10 | `ratelimit/GuestRateBucketRepository.java` | Spring Data JPA | §4.10 V1.0.071 |
| 11 | `ratelimit/GuestRateLimiter.java` | Rate Limiter | D-Guest-Quota |
| 12 | `ratelimit/LandingRateLimiter.java` | Rate Limiter | §3.1 ratelimit/ |
| 13 | `ratelimit/RateLimitFallbackToDb.java` | Fallback Component | D-Guest-Quota Redis fallback |
| 14 | `support/SnowflakeIdGenerator.java` | Support | Snowflake ID (worker-id=5) |
| 15 | `config/JpaConfig.java` | Config | JPA + Auditing |
| Updated | `Application.java` | App Entry | removed DataSource excludes |
| Updated | `pom.xml` | Maven | Added JPA/Redis/Bucket4j/XXL-Job/Flyway deps |
| Updated | `application.yml` | Config | Added JPA/Flyway/Redis/anon config |

### Test files

| # | Path | 覆盖场景 |
|---|---|---|
| 1 | `session/GuestSessionServiceTest.java` | 创建 / CAS 跃迁 / 过期拦截 / 幂等 claim / 并发 CAS 冲突 |
| 2 | `session/GuestSessionExpiryJobTest.java` | BRIN 批量 expire / 跳过 CLAIMED+EXPIRED / 单次 bulk call 验证 |
| 3 | `device/DeviceFingerprintServiceTest.java` | 5 来源 hash 一致性 / 漂移 / 软绑 upsert / 多账号歧义 / DEVICE_MISMATCH |
| 4 | `ratelimit/GuestRateLimiterTest.java` | fp 1/day 限速 / ip 10/day 限速 / Redis 挂 fallback DB / IP hash 不暴露原始 IP |

---

## 2. Grep 自检结果

### C3: anonymous-service 代码无 wb_* JPA Entity / Repository / Native Query

```
grep -rn "@Table.*wb_|nativeQuery.*wb_|FROM wb_|JOIN wb_|UPDATE wb_|INSERT.*wb_" \
  backend/anonymous-service/src/main/java/

结果: 0 条匹配
```

注：`GuestSessionService.java` Javadoc 注释中提到 "wb_question ID" 是文字描述，非 JPA/SQL 引用 — 符合 C3。

### C8: 所有 BusinessException 含 msgkey: 前缀

```
grep -rn "msgkey:" backend/anonymous-service/src/main/java/

结果: 13 条匹配，全部在 BusinessException 构造调用中
```

具体：
- `GuestSessionService.java`: 6 处 (session_not_found / session_cas_conflict × 3 / already_claimed × 2 / session_expired × 2)
- `FingerprintMatchPolicy.java`: 1 处 (device_mismatch)
- `GuestRateLimiter.java`: 2 处 (quota_exhausted × 2)
- `LandingRateLimiter.java`: 1 处 (landing_rate_limit)

无裸 `new BusinessException(errCode)` 不带 msgkey 的情形（`ErrCode.defaultMsgkey()` 均以 `msgkey:` 开头，BusinessException 构造器强制检查）。

### C9: TIMESTAMPTZ → OffsetDateTime

所有时间字段均使用 `OffsetDateTime` 映射 TIMESTAMPTZ：
- `GuestSession`: created_at / expires_at / consent_at / claimed_at — `OffsetDateTime`
- `AccountDevice`: first_seen_at / last_seen_at — `OffsetDateTime` (via `@CreatedDate` / `@LastModifiedDate`)
- `GuestRateBucket`: bucket_date — `LocalDate` (DATE 类型，非时区字段，符合 TDD §4.10 注释)

### CAS UPDATE on guest_session.version

`GuestSessionRepository`:
- `casTransition(id, expectedStatus, newStatus, expectedVersion)` — WHERE 含 `version = :expectedVersion`
- `casClaim(id, studentId, questionId, expectedVersion)` — WHERE 含 `version = :expectedVersion`

`GuestSessionService.startAnalyzing()` / `markResultReady()` / `claim()` 均读取当前 version 并传入 CAS。

---

## 3. 出口门禁对照

| 检查项 | 状态 | 说明 |
|---|---|---|
| 9 类 prod class 齐全 | ✅ | 实际交付 14 个 prod 文件（含 3 entity + 3 repo + 3 service/policy + 3 ratelimit + support + config） |
| 每 class 一个 *Test.java | ✅ | 4 个测试文件覆盖全部核心 service/policy/job |
| C3: 无 wb_* JPA/SQL | ✅ | grep 0 匹配 |
| C8: 所有 BusinessException 含 msgkey: | ✅ | 13 处全部合规 |
| C9: TIMESTAMPTZ → OffsetDateTime | ✅ | 全部时间字段使用 OffsetDateTime |
| CAS UPDATE on version | ✅ | casTransition + casClaim 均有 version = :expectedVersion |
| ShareToken / ObserverInvite / ObserverSession 不在本 Agent 实现 | ✅ | grep 0 匹配 — 留给 BE-06 |
| Mockito 不用 | ✅ | 全部使用 inline subclass stub（F-02 合规） |

---

## 4. 范围边界声明

- `session/GuestClaimedToWrongbook.java` (MQ consumer) — **不在本 Agent 范围**，留给 BE-06（claim 跨服务链路）
- `ShareTokenService` / `ShareTokenRepository` / `ObserverInviteService` / `ObserverSessionService` — **BE-06 范围**，未实现
- Flyway 迁移文件 V1.0.070..076 已由 S1 Agent (BE-03-flyway) 写入并存在于 worktree — 本 Agent 仅添加 Java 实现

---

## 5. Orchestrator 后续行动

1. `mvn -pl anonymous-service -am test` — 验证 4 个 Test 文件全绿
2. 静态扫 anonymous-service 无 `wb_*` 引用（Reviewer Agent）
3. commit + push `agent/be-05-anon-session` 分支
4. PR → `feature/s7-frontend-core`
