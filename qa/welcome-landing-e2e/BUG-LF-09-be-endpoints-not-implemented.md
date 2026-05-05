**Status:** ⏸️ OPEN · BE 团队 backlog · 上线前 must fix
**Severity:** **P0** (生产环境会全面 404 · 所有 anonymous 流程都断)
**Spec ref:** P-LANDING.spec.md §5 + P-GUEST-CAPTURE.spec.md §5 + P00.spec.md §5
**Discovered in:** 2026-05-04 · QA Round 4 · user 提问"数据从哪来"时穿透
**Filed by:** QA-Orchestrator

## Summary

前端 Landing / GuestCapture / Auth 页所有 `/api/*` 调用 **目前只在 MSW (浏览器层 mock) 拦截**。后台 Java/Spring (`backend/anonymous-service/`) 只有 `HealthController.java` (1 个) — **landing/guest/auth/analytics 4 类 endpoint 全部缺失**。

⚠️ **意味着**：
- 当前 dev 服务 (5173 + MSW) 看起来正常 · 因为 MSW 在浏览器内截 fetch
- 一旦把代码部署到 staging/prod (无 MSW worker) → 所有 `fetch('/api/landing/*')` 直接 404
- 全 anonymous 流程立刻断：Landing samples + KPI 走 DEGRADED · CTA 跳转还能 work · 但用户体验严重受损

## 4 个未实现 endpoint

| Endpoint | Spec | Frontend caller | 当前 BE 状态 | Prod 影响 |
|---|---|---|---|---|
| `GET /api/landing/samples?bucket={ab}` | P-LANDING.spec.md §5 · P95 200ms · CDN 1h | `Landing/index.tsx:136` | ❌ 无 controller mapping | DEGRADED 态 · 用前端硬编码 DEFAULT_SAMPLES 兜底 (UX 退化但不白屏) |
| `GET /api/landing/kpi` | P-LANDING.spec.md §5 · P95 200ms · CDN | `Landing/index.tsx:137` | ❌ 无 controller mapping (MSW 返 120w · spec 默认 100w) | KPI banner 隐藏 (B5 区段消失) |
| `POST /api/auth/wechat-login` | P00.spec.md §5 · 800ms | (sc-12-ext J5 测点 · 实际 dev 走账密走 `/api/auth/dev-login`) | ❌ 无 controller mapping | 微信登录 fail · 用户看到 toast · 退到"其他登录方式" |
| `POST /api/analytics/event` | spec §10 6 埋点 | `Landing/Auth/GuestCapture` 多处 | ❌ MSW 返 204 · 上线后无收集 | 漏斗数据完全丢失 (`anon_landing_*` 6 event + `auth_*` 4 event 全空) |

## 已实现的契约 (说明 BE 团队不是没干 · 只是 endpoint 漏)

`backend/anonymous-service/src/main/java/com/longfeng/anonymous/ratelimit/LandingRateLimiter.java`
- ✅ 30/min IP 限流逻辑 (Redis sliding-window INCR + TTL)
- ✅ Redis 不可用时优雅降级 (allow-through 不阻塞)
- ✅ plan §5.S2 BE-05 已收口
- ❌ **但** controller 端找不到 `@RequestMapping("/api/landing")` 把限流接进来

也就是说限流 **机制就位 · 接入点缺**。BE-05 应该是只完成限流器组件 · 没把 LandingController 一起补。

## Reproduction (任意时刻验)

```bash
# 1. dev 服务复现 (MSW 拦截 · 看起来正常)
curl http://localhost:5173/api/landing/samples?bucket=default
# → 200 + samples JSON · 因为 MSW worker 拦了

# 2. 直接打 backend (绕过 MSW)
# 假设 anonymous-service 在 8082
curl http://localhost:8082/api/landing/samples?bucket=default
# → 404 Not Found

# 3. 静态搜
grep -r "@RequestMapping.*landing" backend/anonymous-service/src/main/java
# → 0 matches
```

## Handoff to BE 团队

### 需要的 4 件事

#### 1. 实现 `LandingController`

新文件：`backend/anonymous-service/src/main/java/com/longfeng/anonymous/controller/LandingController.java`

```java
@RestController
@RequestMapping("/api/landing")
public class LandingController {

  private final LandingSamplesService samplesService;  // 新增 service
  private final LandingKpiService kpiService;          // 新增 service
  private final LandingRateLimiter rateLimiter;        // 已存在 · 直接注

  @GetMapping("/samples")
  public LandingSamplesResp getSamples(@RequestParam(defaultValue = "default") String bucket,
                                       HttpServletRequest req) {
    rateLimiter.check(req);  // 30/min IP
    return samplesService.byBucket(bucket);  // 返 3 题 · 见 BUG-LF-10 prod 数据源决定
  }

  @GetMapping("/kpi")
  public LandingKpiResp getKpi() {
    return kpiService.current();  // 从数据仓库读 · headline + 7d retention
  }
}
```

#### 2. 实现 `WechatLoginController` (auth-service 或 anonymous-service)

```java
@RestController
@RequestMapping("/api/auth")
public class WechatLoginController {
  @PostMapping("/wechat-login")
  public WechatLoginResp wechatLogin(@RequestBody WechatLoginReq req) { ... }
}
```

#### 3. 实现 `AnalyticsController`

新文件 (or 接 Kafka producer)：
```java
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {
  @PostMapping("/event")
  public void track(@RequestBody EventPayload e) {
    kafkaProducer.send("analytics-events", e);
  }
}
```

埋点 fire-and-forget · 返 204 即可 · 不阻塞前端流程。

#### 4. Gateway 路由配置 + RateLimiter 接入

`backend/gateway/.../config/RouteConfig.java` 加 routing：
- `/api/landing/**` → anonymous-service
- `/api/auth/**` → auth-service
- `/api/analytics/**` → analytics-service (or Kafka)

## QA Verification Log

### Round 4 — discovered (2026-05-04 19:25)

- 仅记录 · 不修复 BE 代码 (本 round 范围明确不动 BE)
- 上线前 BE 团队需 close 此 bug

### 上线前 checklist (BE 团队执行)

- [ ] LandingController 实现 + IT 测 + curl 验
- [ ] WechatLoginController 实现 + IT 测 + 微信开放平台对接
- [ ] AnalyticsController 实现 (or Kafka) + 埋点收集链路 work
- [ ] Gateway 路由配 + LandingRateLimiter 接入 LandingController
- [ ] BUG-LF-10 (prod 数据源) 决策完成 → samplesService 实现
- [ ] 跑 sc-11/sc-12/sc-funnel @e2e-a track (真 API 而非 MSW) · 全 PASS
- [ ] 关闭本 bug



---

## ✅ FIXED · 2026-05-05 · Round 6 hybrid 联调

全部 4+ endpoint 真打通 · 见 `qa/sc-11-12-hybrid/final-report.md` 详证。

**Fix commits** (zhe.wang 署名):
- `dde7821` WT1 LandingController + GuestController + AnalyticsController
- `40f8a3a` WT3 QuestionDetailController
- `1e7fe18` WT4 ai-analysis SSE + analyze-by-url
- `70eae4e` WT2 file-service /api/file/presign 路径 + DTO snake_case
- `d1bd705` WT5 gateway routes 13 条 (7 新 + 5 legacy)
- `9b65a38` WT6 GuestSession entity @CreatedDate fix
- `18fb721` WT7 file @MapsId saveAndFlush
- `0576f80` GuestController.analyze RestClient → ai-analysis bridge
- `6ec15cb` auth-service POST /api/auth/wechat-login (user 加)

**Hybrid 真返证据** (curl 通 gateway · 不 mock):
- `GET /api/landing/kpi` 200 真返
- `GET /api/landing/samples` 200 真返 3 sample
- `POST /api/analytics/event` 204 · PG analytics_event 9 row
- `GET /api/guest/quota` 200 真返
- `POST /api/guest/analyze` 200 · PG guest_session 6 row
- `POST /api/file/presign` 200 + MinIO presigned URL · PG wb_file 11 row
- `POST /api/auth/wechat-login` (user 已实现 · 见 03-s7-wechat-hybrid-sweep-report.md)

**遗留** (跟 BUG-LF-09 无关):
- BUG-LF-17 · ai-analysis PromptInjectionGuard 误拦自己 system prompt · LLM 真消费 0
- BUG-LF-18 (待 file) · FE Playwright PUT MinIO CORS-blocked
