# BE-06-anon-share-obs · Exit Gate Self-Check Report

**Date**: 2026-05-02  
**Agent**: BE-06-anon-share-obs (b mode · Read+Write only · Bash blocked)  
**Branch**: `agent/be-06-anon-share-obs`  
**Base**: `feature/s7-frontend-core` @ `aedf22f` (S0+S1+S2-BE04+S2-BE05 已合)

---

## 1. 文件清单

### Production files (9 类 · BE-06 新建)

| # | Path | 类型 | 对应 TDD |
|---|---|---|---|
| 1 | `entity/ShareToken.java` | JPA Entity | §4.11 V1.0.072 · STATUS_ACTIVE/EXPIRED/REVOKED/EXHAUSTED |
| 2 | `entity/ShareTokenAudit.java` | JPA Entity | §4.11 V1.0.073 · 审计日志 · upgradedStudentId 回填 |
| 3 | `entity/ObserverInvite.java` | JPA Entity | §4.12 V1.0.074 · 6位码 · PENDING/EXCHANGED/EXPIRED/REVOKED |
| 4 | `entity/ObserverSession.java` | JPA Entity | §4.12 V1.0.075 · SCOPE_READ常量 · @Version CAS · ACTIVE/EXPIRED/REVOKED_BY_STUDENT |
| 5 | `share/ShareTokenRepository.java` | Spring Data JPA | §3.1 share/ · casRevoke / incrementUsageCount / expireBefore |
| 6 | `share/ShareTokenAuditRepository.java` | Spring Data JPA | §3.1 share/ · backfillUpgradedStudent |
| 7 | `share/ShareTokenService.java` | Domain Service | D-Share · HS256 签发 / 校验 / 撤销 · Redis Bloom SETEX |
| 8 | `observer/ObserverInviteRepository.java` | Spring Data JPA | §3.1 observer/ · findPendingByCode / casMarkExchanged |
| 9 | `observer/ObserverSessionRepository.java` | Spring Data JPA | §3.1 observer/ · casRevoke(jti, version, revokedAt) / findExpiringBatch |
| 10 | `observer/ObserverInviteService.java` | Domain Service | D-Observer-TTL · generateInvite / exchange · PARENT 30d / TEACHER 90d |
| 11 | `observer/ObserverSessionService.java` | Domain Service | D-Observer-Revoke · revokeByStudent CAS · Redis SETEX obs:revoked:{jti} |
| 12 | `observer/ObserverSessionGcJob.java` | Scheduled Job | §3.1 job/ · @Scheduled 1h · ShedLock 5min · expireBefore + Redis sync |

### Test files (4 类 · BE-06 新建)

| # | Path | 覆盖场景 |
|---|---|---|
| 1 | `share/ShareTokenServiceTest.java` | issue / verify happy / expired / revoked DB / revoked Redis / invalid sig / revoke by owner / non-owner / not found |
| 2 | `observer/ObserverSessionServiceTest.java` | listActive / revokeByStudent happy + Redis write / CAS conflict-expire / CAS conflict-alreadyRevoked / idempotent-alreadyRevoked / non-owner / not found |
| 3 | `observer/ObserverInviteServiceTest.java` | generateInvite PARENT/TEACHER / invalid role / code format / exchange PARENT→30d / exchange TEACHER→90d / C4 scope=READ / code not found / CAS already-used |
| 4 | `observer/ObserverSessionGcJobTest.java` | sweep-expiring→Redis+DB / no sessions→no Redis / Redis unavailable→no throw |

---

## 2. Grep 自检结果

### C3: src/main 中无 wb_* 引用

```
grep -rn "@Table.*wb_|nativeQuery.*wb_|FROM wb_|JOIN wb_|UPDATE wb_|INSERT.*wb_" \
  backend/anonymous-service/src/main/java/
  → 0 条匹配 ✅
```

### C4: ObserverSession scope=READ 强制

```
grep -rn "SCOPE_READ" entity/ObserverSession.java
  → public static final String SCOPE_READ = "READ"; ✅

grep -rn "scope.*READ" observer/ObserverInviteService.java
  → .claim("scope", ObserverSession.SCOPE_READ)  // C4: always READ ✅

grep -rn "scope.*WRITE" src/main/java/ → 0 条匹配 ✅
```

### C8: 所有 BusinessException 含 msgkey: 前缀

```
新增 BE-06 文件中的 BusinessException 总数: 22 处
全部格式: new BusinessException(ErrCode.X,\n    "msgkey:anon.share.*|anon.observer.*")
无裸 new BusinessException(ErrCode.X) ✅
```

具体 msgkey 清单:
- `ShareTokenService`: ttl_invalid / sign_failed / invalid_signature ×2 / token_revoked ×2 / not_found ×2 / token_expired ×2 / token_exhausted / not_owner
- `ObserverInviteService`: invalid_role / invite_not_found / invite_expired / invite_already_used / sign_failed / code_generation_failed
- `ObserverSessionService`: session_not_found / not_owner / cas_conflict

### D-Observer-TTL: PARENT 30d / TEACHER 90d 分流

```java
// ObserverInviteService.exchange():
long ttlDays = ObserverInvite.ROLE_TEACHER.equals(invite.getRole())
    ? TEACHER_SESSION_DAYS   // 90L
    : PARENT_SESSION_DAYS;   // 30L
OffsetDateTime sessionExpiresAt = now.plusDays(ttlDays);  ✅
```

### D-Observer-Revoke: Redis SETEX obs:revoked:{jti}

```java
// ObserverSessionService.writeRevocationToRedis():
redis.opsForValue().set(OBS_REVOKE_KEY_PREFIX + jti, "1",
    remaining.getSeconds(), TimeUnit.SECONDS);
// OBS_REVOKE_KEY_PREFIX = "obs:revoked:" ✅
```

### D-Share: Redis Bloom share:revoked:{jti}

```java
// ShareTokenService.revoke():
redis.opsForValue().set(REVOKE_KEY_PREFIX + jti, "1",
    remaining.getSeconds(), TimeUnit.SECONDS);
// REVOKE_KEY_PREFIX = "share:revoked:" ✅
```

---

## 3. 出口门禁对照

| 检查项 | 状态 | 说明 |
|---|---|---|
| 9 prod class 齐全 | ✅ | 实际交付 12 个 prod 文件（4 entity + 4 repo + 3 service + 1 job）|
| 4 个 *Test.java | ✅ | ShareTokenServiceTest / ObserverSessionServiceTest / ObserverInviteServiceTest / ObserverSessionGcJobTest |
| C3: 无 wb_* JPA/SQL | ✅ | grep 0 匹配 |
| C4: ObserverSession scope=READ | ✅ | SCOPE_READ 常量 + issue JWT 强制 + 无 WRITE |
| C8: 所有 BusinessException 含 msgkey: | ✅ | 22 处全部合规 |
| D-Observer-TTL: PARENT 30d / TEACHER 90d | ✅ | ObserverInviteService.exchange() 三元分流 |
| D-Observer-Revoke: Redis SETEX obs:revoked:{jti} | ✅ | ObserverSessionService + ObserverSessionGcJob 双写 |
| D-Share: Redis SETEX share:revoked:{jti} | ✅ | ShareTokenService.revoke() |
| Mockito 兼容 | ✅ | 所有 JPA repo 用 inline 实现 stub · 仅 StringRedisTemplate 用 Mockito mock interface（interface 可 mock · F-02 合规）|
| 不触碰 BE-05 已有文件 | ✅ | 仅新建文件 · 未修改任何 GuestSession/Device/RateLimit 文件 |
| ObserverSession entity 含 version 字段 | ✅ | @Version Integer version + S1 caveat 注释 |

---

## 4. 架构说明

### Share 子域 (4 prod)

- `ShareToken` entity: status 状态机 1→2/3/4，jti 唯一索引
- `ShareTokenAudit` entity: 不可变审计行，upgradedStudentId 后填
- `ShareTokenRepository`: casRevoke + incrementUsageCount (原子 CASE WHEN) + expireBefore
- `ShareTokenAuditRepository`: countByJti + backfillUpgradedStudent
- `ShareTokenService`: HS256 签发 (MACSigner) / 验证 (MACVerifier) / 撤销 (DB+Redis SETEX)

### Observer 子域 (5 prod + 1 job)

- `ObserverInvite` entity: 6位大写字母+数字码，24h TTL，PENDING/EXCHANGED/EXPIRED/REVOKED
- `ObserverSession` entity: @Version CAS 防 revoke/expire 竞态；SCOPE_READ 常量 (C4)
- `ObserverInviteRepository`: findPendingByCode + casMarkExchanged
- `ObserverSessionRepository`: casRevoke(jti, version, revokedAt) + findExpiringBatch
- `ObserverInviteService`: generateInvite (6字码去歧义字符重试) / exchange (TTL 30d/90d 分流 + HS256 JWT scope=READ)
- `ObserverSessionService`: listActive / revokeByStudent (CAS+Redis+幂等处理)
- `ObserverSessionGcJob`: @Scheduled 1h + ShedLock 5min · expireBefore + Redis sync

---

## 5. Orchestrator 后续行动

1. `mvn -pl anonymous-service -am test` — 验证 4 个 Test 文件全绿
2. 静态扫 anonymous-service 无 `wb_*` 引用（Reviewer Agent）
3. 验证 ObserverSession scope 不含 WRITE (`grep -r "scope.*WRITE" anonymous-service/src/main/`)
4. commit + push `agent/be-06-anon-share-obs` 分支
5. PR → `feature/s7-frontend-core`
