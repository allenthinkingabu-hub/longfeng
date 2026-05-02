# Project Architecture Blueprint — Longfeng AI 错题本

> **Generated**: 2026-04-28 (updated from 2026-04-27)  
> **Detection**: Auto-detect — Java + Spring Cloud Microservices (Backend) · React + TypeScript Monorepo (Frontend) · WeChat Mini-program  
> **Pattern**: Microservices + Layered Architecture (per service) + Event-Driven (Outbox/RocketMQ)  
> **Detail Level**: Comprehensive + Implementation-Ready  
> **Current Branch**: feature/s7-frontend-core  
> **Phase Status**: S7 A轨 4/5 AC ✅ · S8 设计阶段 ✅ · S5 DTO 契约补全 ✅

---

## 1. Architectural Overview

**Longfeng AI 错题本** 是一款面向中学生的 AI 驱动错题管理与间隔复习平台。学生拍照上传错题后，系统通过 AI 自动解析、生成知识点标签、按 Ebbinghaus+SM-2 混合算法规划复习计划，并通过移动端 H5 和微信小程序交付学习体验。

### 核心架构原则

| 原则 | 实现方式 |
|---|---|
| **最终一致性优先** | Outbox + RocketMQ 事务消息替代 Seata 全局事务 |
| **类型安全** | 后端 JPA+QueryDSL · 前端 TypeScript · API Contracts 共享包 |
| **单一真源** | Flyway DDL → JPA 实体 · design/arch/<phase>.md → 代码符号 |
| **合规红线** | AI 调用前 PII 脱敏 · 图片仅存 object_key · audit_log 同事务写入 |
| **可观测性内置** | 每服务 Micrometer+OTEL · 结构化 JSON 日志 · traceId 全链路透传 |
| **AI 辅助开发** | 三段式（Pre-flight → Builder → Acceptance）工作流驱动各 Phase 实现 |
| **权限分级** | Gateway JWT scope 解析 · scope=READ JWT 守门家长视图（S8 ADR 0018） |

---

## 2. 架构可视化

### 2.1 C4 Level 1 — 系统上下文图

```
┌─────────────────────────────────────────────────────────────────┐
│                     Longfeng 错题本平台                          │
│                                                                 │
│  ┌──────────┐    HTTPS    ┌──────────────────────────────────┐  │
│  │  学生     │ ──────────▶ │  Web/H5 (React)                 │  │
│  └──────────┘             │  微信小程序 (WXML)               │  │
│                           └────────────┬─────────────────────┘  │
│  ┌──────────┐    观察     │            │ HTTPS                  │
│  │  家长     │ ──────────▶ │            ▼                        │
│  └──────────┘  邀请码兑换  │  ┌──────────────────────────────┐   │
│                (S8 stub)  │  │  API Gateway (SCG 4.1)       │   │
│                           │  │  JWT验证+scope解析 · 限流      │   │
│                           │  └──────────┬───────────────────┘   │
│                           │             │ HTTP/OpenFeign         │
│                           │  ┌──────────┴───────────────────┐   │
│                           │  │    Backend Microservices      │   │
│                           │  └──────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
         │                          │                    │
   [阿里云 dashscope]          [PostgreSQL 16]      [MinIO/OSS]
   [OpenAI API]               [pgvector 0.6]
```

### 2.2 C4 Level 2 — 容器图（Backend 微服务）

```
[ Web / H5 / 小程序 ]
        │ HTTPS
        ▼
┌────────────────────────────────────────────────────────────────────────┐
│  Spring Cloud Gateway (:8080)                                          │
│  JwtAuthFilter → scope解析(READ/WRITE) → RateLimitFilter → TraceIdFilter│
│  路由: /api/v1/wrongbook/** → :8081                                     │
│        /api/v1/ai/**       → :8082                                     │
│        /api/v1/review/**   → :8083                                     │
│        /api/v1/file/**     → :8084                                     │
│        /api/v1/anon/**     → :8085                                     │
│  写接口守门: scope=READ JWT → 403 SCOPE_INSUFFICIENT (ADR 0018)         │
└──────┬────────┬────────────┬────────────┬────────────┬─────────────────┘
       │        │            │            │            │
       ▼        ▼            ▼            ▼            ▼
  ┌─────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────────┐
  │wrongbook│ │ai-analysis│ │review    │ │file      │ │anonymous       │
  │-service │ │-service  │ │-plan-svc │ │-service  │ │-service        │
  │  :8081  │ │  :8082   │ │  :8083   │ │  :8084   │ │  :8085         │
  └────┬────┘ └────┬─────┘ └────┬─────┘ └────┬─────┘ └────────────────┘
       │           │            │            │
       └───────────┴────────────┘            │
                   │ JPA/QueryDSL            │ StorageProvider
                   ▼                         ▼
            [PostgreSQL 16]            [MinIO / 阿里云OSS]
            [pgvector 0.6]
                   │                         │
            [Flyway 27 migrations]    [ClamAV 防病毒扫描]
       
[RocketMQ 5.1]  ←── Outbox Relay ──── wrongbook-service
       │
       ├──▶ ai-analysis-service (消费 wrongbook.item.changed)
       └──▶ review-plan-service (消费 wrongbook.item.analyzed)

[Nacos 2.3]   — 服务注册与发现
[Redis 7]     — 幂等 Key (idem:wb:{requestId}) · 日历缓存
[XXL-Job 2.4] — ReviewDueJob 定时任务
```

### 2.3 数据流图 — 错题采集完整流

```
学生拍照
  │
  ▼ POST /api/v1/file/upload
file-service ──▶ 防病毒扫描(ClamAV) ──▶ MinIO存储 ──▶ 返回 object_key
  │
  ▼ POST /api/v1/wrongbook/items (带 object_key)
wrongbook-service
  ├── 写 wrong_item (status=draft)
  ├── 写 wrong_item_outbox (同事务)
  └── 写 audit_log (同事务)
       │
       ▼ Outbox Relay (后台)
RocketMQ → wrongbook.item.changed {itemId, action, version, occurredAt}
       │
       ▼ WrongItemChangedConsumer
ai-analysis-service
  ├── 调 PII脱敏器
  ├── 调 Spring AI ChatClient (dashscope/openai)
  ├── 写 wrong_item_analysis
  └── 发布 wrongbook.item.analyzed
       │
       ▼ WrongItemAnalyzedConsumer
review-plan-service
  ├── 幂等 INSERT 7行 review_plan (node_index 0..6)
  │   偏移: [2h, 1d, 2d, 4d, 7d, 14d, 30d]
  └── 回调 wrongbook-service 更新 status=scheduled
```

### 2.4 数据流图 — S8 复习执行主路径

```
用户进 /review/today
  │
  ▼ GET /review-plans?date=today (staleTime=30s · refetchOnWindowFocus=true)
review-plan-service → [ReviewPlanDto × N] → 前端时段分组(now/morning/afternoon/evening)
  │
用户点卡片 plan id=P1
  │
  ▼ GET /review-plans/P1
review-plan-service → ReviewItemDetail (stemText + answerStandard + steps[])
  │
用户点"揭示答案" → 点评分(0|3|5 → ADR 0016: 未掌握/部分/已掌握)
  │
  ▼ POST /review-plans/P1/complete {quality: 0|3|5}
review-plan-service
  ├── SM2Algorithm.compute(quality) → UPDATE review_plan
  ├── INSERT review_outcome
  ├── 乐观锁 dispatch_version
  └── 200 CompleteReviewResp {plan_id, next_review_at, ease_factor_after, mastered}
  │
前端 invalidate ['review-plans','today'] → navigate(/review/done)
```

---

## 3. 后端架构组件

### 3.1 common (共享库)

**定位**: 所有服务依赖的基础能力库，不包含业务逻辑。

| 包 | 职责 |
|---|---|
| `com.longfeng.common.domain.BaseEntity` | 所有 JPA 实体的父类，提供 id/created_at/updated_at/deleted_at |
| `com.longfeng.common.dto.ApiResult<T>` | 统一响应包装器 |
| `com.longfeng.common.exception.GlobalExceptionHandler` | 全局异常处理，BizException → HTTP 4xx/5xx |
| `com.longfeng.common.exception.ErrorCode` | 错误码枚举 |
| `com.longfeng.common.context.TenantContext` | ThreadLocal 租户上下文 |
| `com.longfeng.common.config.MdcLoggingConfig` | MDC 结构化日志配置 |
| `com.longfeng.common.filter.TraceIdFilter` | X-Request-Id 注入 MDC |
| `com.longfeng.common.test.CoversAC` | 验收条件测试注解，链接 AC 编号 |
| `db/migration/V1.0.xxx` | 所有服务共享的 Flyway 迁移（27个） |

### 3.2 gateway (API 网关)

**定位**: 系统唯一入口，处理横切关注点。

```
JwtAuthFilter          — 验证 RSA JWT，提取 userId + scope 注入 Header
RateLimitFilter        — Resilience4j 限流 (20 req/s default)
TraceIdFilter          — 生成/透传 X-Request-Id
JWT Scope Guard        — scope=READ JWT 调写接口 → 403 SCOPE_INSUFFICIENT (ADR 0018)
路由表 (application.yml) — Path 匹配 → 后端服务 URI
```

**关键设计**: 响应式模式（`web-application-type: reactive`），非阻塞 IO。  
**S8 新增**: scope=READ 家长 JWT 的写接口拦截层（SC-14 三重防御之网关层）。

### 3.3 wrongbook-service (错题主域)

**定位**: 系统核心聚合根，管理错题全生命周期。

**分层架构**:
```
controller/   ← HTTP 入口，VO 映射，@Operation OpenAPI 注解
service/      ← 业务编排，事务边界，调用 repo
domain/       ← 领域逻辑（状态机，业务规则）
repo/         ← Spring Data JPA + QueryDSL 动态查询
entity/       ← JPA 实体（wrong_item, wrong_item_tag, audit_log 等）
dto/          ← Request/Response VO（snake_case JSON）
event/        ← Outbox 事件发布
mq/           ← RocketMQ Producer/Consumer
support/      ← SnowflakeIdGenerator（分布式 ID）
```

**错题状态机**:
```
draft(0) → analyzed(1) → scheduled(2) ⇄ reviewed(3) → mastered(8)
                                                      ↘ archived(9)
```

**幂等保护**: Redis Key `idem:wb:{requestId}` TTL 24h，防止重复提交。  
**S8 扩展**: `GET /wrongbook/items?ownerId={studentId}` 加 ownerId 参数供家长视图使用（ADR 0018）。

### 3.4 ai-analysis-service (AI 解析)

**定位**: LLM 调用的防腐层，隔离 AI 复杂性。

**关键抽象**:
```java
interface HttpLlmProvider {
    AnalysisResult analyze(String sanitizedText, String imageKey);
}
// 实现: DashscopeProvider, OpenAiProvider, StubProvider（测试用）
// 路由: ProviderRouter — Feature Flag 切换
```

**PII 合规流程**:
```
原始文本 → PIIRedactor.redact() → 脱敏文本 → LLM API
```

**AI 解析输出**: `stemText + answerStandard + steps[]` — 由 review-plan-service 透传给前端，S8 直接消费。  
**Token 成本埋点**: `ChatResponseMetadata.usage()` → 写入 `ai_usage_log`。

### 3.5 review-plan-service (复习计划)

**定位**: SM-2 算法 + Ebbinghaus 曲线混合调度引擎。

**算法实现**:
```
消费 wrongbook.item.analyzed 事件
→ 幂等 INSERT 7 行 review_plan (node_index 0..6)
  初始 ease_factor=2.5, interval=[2h,1d,2d,4d,7d,14d,30d]

用户 complete 当前节点:
→ SM2Algorithm.compute(quality) 微调当前行
  quality ≥ 3: new_ease = clamp(ease + 0.1 - (5-q)*(0.08+(5-q)*0.02), 1.3, 2.5)
  quality < 3: reset ease=2.5, interval=1d
  连续3次 ease≥2.8 → mastered (软删7行 + 发 review.mastered)
```

**5 个 API 端点** (已实现 · S7 E2E 4/5 AC 通过):

| 端点 | SC | 状态 |
|---|---|---|
| `GET /review-plans?date=` | SC-07 · SC-10 | ✅ 实现 · DayViewResp |
| `GET /review-plans/{id}` | SC-08.AC-2 | ✅ 实现 · ReviewPlanDto |
| `POST /review-plans/{id}/complete` | SC-08.AC-3 | ✅ 实现 · CompleteReviewResp |
| `GET /review-stats` | SC-09 | ✅ 实现 · ReviewStatsResp |
| `POST /review-plans/batch-reset` (admin) | — | ✅ 实现 |

**新增 DTO（S7 验收后 · 契约补全 G-01~G-06）**:

| DTO | 字段 | 说明 |
|---|---|---|
| `ReviewPlanDto` | id, wrong_item_id, user_id, node_index, next_due_at, mastery, ease_factor, interval, status | 补全 next_due_at/user_id/mastery/interval 四缺失字段 |
| `DayViewResp` | items, calendarNodes, source | 日视图 + calendar-platform 节点聚合 |
| `CompleteReviewResp` | plan_id, next_review_at, ease_factor_after, mastered | complete 响应 snake_case 合规 |

**外部依赖**: OpenFeign 调用 calendar-service（获取日历节点）· notification-service（发送提醒）。  
**定时任务**: XXL-Job 驱动 `ReviewDueJob`，扫描 `next_review_at ≤ now()` 的计划。  
**S8 扩展（待 s5-v2）**: `GET /review-stats` v2 扩展 `subjectBreakdown[] + ebbinghaus[]` 字段（ADR 0017 · 阻塞 SC-09.AC-2/AC-3）。

### 3.6 file-service (文件存储)

**定位**: 多 Provider 文件存储抽象，含防病毒扫描。

```java
interface StorageProvider {
    PresignResp presign(PresignReq req);   // 前端直传预签名
    CompleteResp complete(String key);     // 确认上传完成
    DownloadResp download(String key);     // 生成下载链接
}
// 实现: OssProvider (阿里云), MinioProvider (私有化)
// 切换: StorageConfigRegistration — @ConditionalOnProperty
```

**防病毒**: `AntivirusClient` 调 ClamAV Socket，`ClamStub` 用于测试。  
**图像处理**: `ImageProcessor` 压缩/生成缩略图。  
**文件大小/MIME 白名单**: 在 Service 层校验，超出抛 `OversizeException`/`MimeNotAllowedException`。

### 3.7 anonymous-service (匿名访问)

**定位**: 游客模式入口，管理 guest_session/consent/quota，当前实现为最小骨架。  
**S8 扩展(stub)**: `POST /v1/parent/redeem-invite` — S8 阶段返回 mock JWT scope=READ，S11 接管真实 invite_code 表 + JWT 颁发。

---

## 4. 架构分层与依赖规则

### 4.1 后端服务内分层

```
┌─────────────────────────────────────┐
│  Controller Layer (HTTP 入口)        │  ← 只做 HTTP↔VO 转换，调 Service
├─────────────────────────────────────┤
│  Service Layer (业务编排)            │  ← 事务边界，调 Repo，发事件
├─────────────────────────────────────┤
│  Domain Layer (领域逻辑)             │  ← 纯函数，无 Spring 依赖
├─────────────────────────────────────┤
│  Repository Layer (持久层)           │  ← Spring Data JPA + QueryDSL
├─────────────────────────────────────┤
│  Entity Layer (JPA 实体)             │  ← 继承 BaseEntity
└─────────────────────────────────────┘
```

**依赖规则**:
- Controller → Service（禁止 Controller 直接调 Repo）
- Service → Domain + Repo（禁止 Repo 反向依赖 Service）
- 禁止 `EntityManager.createNativeQuery` 绕开类型（DDL 脚本豁免）
- 禁止 MyBatis / MyBatis-Plus（ADR 0006 全局禁止）

### 4.2 服务间依赖

```
gateway → [所有服务]（路由，无业务耦合）
wrongbook-service → RocketMQ（发布事件）
ai-analysis-service → RocketMQ（消费）+ LLM API
review-plan-service → RocketMQ（消费）+ OpenFeign→calendar/notification
file-service → OSS/MinIO + ClamAV
```

**禁止循环依赖**: 服务间只能通过消息或 API 调用，禁止数据库共享。

### 4.3 前端包依赖

```
apps/h5          → packages/ui-kit, api-contracts, testids, i18n, shared-logic
apps/prototype   → packages/ui-kit, i18n
apps/miniapp     → packages/i18n, testids

packages/api-contracts → packages/testids (类型引用)
packages/ui-kit        → 无内部依赖（纯 design tokens + 组件）
packages/telemetry     → @sentry/react (H5), @sentry/minapp (小程序) ADR 0015
```

### 4.4 前端页面路由（S7 + S8）

| 路由 | 页面 | Phase | 状态 |
|---|---|---|---|
| `/items` | 错题列表 | S7 | ✅ done |
| `/items/new` | 录入错题 | S7 | ✅ done |
| `/items/:id` | 错题详情 | S7 | ✅ done |
| `/review/today` | P-REVIEW-TODAY | S8 | 设计中 |
| `/review/exec/:planId` | P-REVIEW-EXEC | S8 | 设计中 |
| `/review/done` | P-REVIEW-DONE | S8 | 设计中 |
| `/insight` | P-INSIGHT | S8 | 设计中 |
| `/observer/:studentId` | P-OBSERVER | S8 | 设计中 |

---

## 5. 数据架构

### 5.1 核心领域模型

```
wrong_item (核心聚合根)
├── id (Snowflake), user_id, status (0/1/2/3/8/9)
├── stem_text, ocr_text, subject, difficulty (1-5)
├── version (乐观锁), created_at, deleted_at
│
├── wrong_item_tag (N:M via tag_taxonomy)
├── wrong_item_image (object_key 引用 OSS)
├── wrong_item_analysis (AI 解析结果 + pgvector embedding)
├── wrong_item_outbox (事件发布 outbox)
└── wrong_attempt (作答记录)

review_plan (复习计划聚合根)
├── id (Snowflake), wrong_item_id, student_id (user_id), node_index (0-6)
├── ease_factor (BigDecimal), interval_index (0..6)
├── next_due_at (timestamptz UTC)
├── consecutive_good_count (mastery 0..3), status (active=0 · mastered=1)
├── dispatch_version (乐观锁)
└── review_plan_outbox (review.completed 事件)

review_outcome — 每次复习结果快照 {quality, ease_factor_before/after, interval_before/after}

tag_taxonomy — 知识点层级树 (is_active 软控制)

file_asset — 文件元数据 (path/object_key/checksum/size/mime_type)

audit_log — 不可变审计轨迹（同事务写入）
idem_key  — 幂等 Key 表（X-Request-Id → 响应缓存）
```

### 5.2 Flyway 迁移策略

- 所有 DDL 在 `common/src/main/resources/db/migration/` 统一管理
- 各服务通过 `classpath:db/migration` 引用（共享单库模式）
- `out-of-order: true` 允许回填（如 S3 补充 V1.0.019-022）
- `ddl-auto: none`，禁止 Hibernate 自动 DDL

### 5.3 数据访问模式

| 场景 | 方案 |
|---|---|
| 简单 CRUD | Spring Data JPA Repository (接口声明) |
| 复杂动态过滤 | QueryDSL `JPAQuery` + Q 类元模型 |
| 向量相似度搜索 | pgvector 0.6 + Hibernate 6 `VectorType` |
| N+1 防御 | `@EntityGraph` / JPQL `fetch join` + CI hibernate-statistics 断言 |
| 批量操作 | `order_inserts: true`, `order_updates: true` (Hibernate batch) |

---

## 6. 横切关注点实现

### 6.1 认证与授权

```
Gateway JwtAuthFilter:
  → 验证 RSA 公钥签名 (jwt.public-key-path)
  → 提取 userId 注入 X-User-Id Header
  → 提取 scope (READ/WRITE 默认) 注入 X-Jwt-Scope Header
  → 下游服务信任 Header（内网不再验 JWT）

Gateway Scope Guard (ADR 0018 · S8 新增):
  → scope=READ JWT + 写接口 (POST/PATCH/DELETE) → 403 SCOPE_INSUFFICIENT
  → scope=READ + ownerId 不在 JWT.parentOf → 403 OWNER_MISMATCH

Gateway RateLimitFilter:
  → Resilience4j RateLimiter (20 req/s, 0ms timeout)
  → 超限返回 429
```

### 6.2 错误处理

```
GlobalExceptionHandler (common):
  BizException(ErrorCode) → HTTP 4xx + ApiResult.error(code, message)
  ConstraintViolationException → 400
  OptimisticLockException → 409 (dispatch_version 冲突)
  其他 → 500 + log.error (含 traceId)

服务级异常:
  file-service: OversizeException, VirusDetectedException, MimeNotAllowedException
  review-plan-service: PlanNotFoundException, PlanAlreadyMasteredException (→ 410)
  gateway: ScopeInsufficientException (→ 403 · ADR 0018)
```

### 6.3 日志与可观测性

```
结构化 JSON 日志 (MdcLoggingConfig):
  字段: timestamp, level, service, traceId, requestId, userId, message

Metrics (Micrometer → Prometheus → Grafana):
  SLO: P95/P99 延迟 · QPS · 错误率 · JVM/DB Pool 饱和度

Tracing (OTEL → Tempo):
  traceId 全链路透传 (Gateway → Service → RocketMQ Consumer)

Error Tracking: Sentry (前后端共用 DSN)
  前端 H5: @sentry/react + web-vitals + @sentry/vite-plugin (ADR 0015)
  前端小程序: @sentry/minapp (ADR 0015)
  Sentry tag 规则: ac=SC-XX.AC-Y · critical=true · phase=sN

Grafana Dashboards: ops/grafana/ 目录
```

### 6.4 校验

```
Controller 层: Bean Validation (@Valid) → 400
Service 层: 业务规则校验 → BizException
Entity 层: JPA 约束 (@NotNull, @Size, @Check)

difficulty: CHECK (difficulty IS NULL OR difficulty BETWEEN 1 AND 5)
status: CHECK IN (0,1,2,3,8,9)
quality (前端发送): 0|3|5 (ADR 0016 · 3档映射 · 禁止 1/2/4)
```

### 6.5 配置管理

```
application.yml 分层:
  硬编码默认值 (本地开发)
  ${ENV_VAR:default} 环境变量覆盖 (生产)

Nacos 2.3: 动态配置（Feature Flag 切换 LLM Provider · observer.real_jwt 开关）
Kubernetes Secrets: DB_PASSWORD, JWT_PUBLIC_KEY_PATH 等敏感值

Feature Flags (关键):
  ai.provider: dashscope|openai|stub  — LLM Provider 切换
  observer.real_jwt: false(dev/staging) | true(prod) — S8 stub vs S11 真实链路
```

---

## 7. 服务通信模式

### 7.1 同步通信

```
外部: HTTPS → Gateway → HTTP (内网)
服务间: Spring Cloud OpenFeign
  review-plan-service → CalendarFeignClient (Sentinel熔断 + Caffeine 10min cache)
  review-plan-service → NotificationFeignClient
```

### 7.2 异步通信 (Outbox + RocketMQ)

```
发布方 (wrongbook-service):
  1. 同事务写 wrong_item_outbox 行 (not_sent)
  2. Outbox Relay 轮询 → RocketMQ 事务消息
  3. 发送成功 soft delete outbox 行

消费方 (ai-analysis-service):
  WrongItemChangedConsumer → 幂等处理 → 写 wrong_item_analysis
  → 发布 wrongbook.item.analyzed

消费方 (review-plan-service):
  WrongItemAnalyzedConsumer → 幂等 INSERT review_plan 7行
```

**消息格式**: Thin payload `{itemId, action, version, occurredAt}`（防 PII 透传）。  
**顺序保证**: orderly consumer，同 key 入同队列。

### 7.3 前端轮询模式（S8 ADR 0018 · 替代 WebSocket）

```
review.due 实时唤起:
  React Query staleTime=30s · refetchOnWindowFocus=true
  POST complete 后手动 invalidate ['review-plans','today'] → 重 fetch
  不引入 WebSocket / SSE（网关无 WS 路由 · s5 review.due 是 RocketMQ 内部事件）
```

---

## 8. 技术栈详细清单

### 8.1 后端

| 层面 | 技术 | 版本 | 决策依据 |
|---|---|---|---|
| 语言 | Java | 21 (LTS) | — |
| 框架 | Spring Boot | 3.2.5 | — |
| 微服务 | Spring Cloud | 2023.0.1 | — |
| 微服务(Alibaba) | Spring Cloud Alibaba | 2023.0.1.0 | — |
| 网关 | Spring Cloud Gateway | 4.1 | ADR 0007 over Zuul |
| 服务发现 | Nacos | 2.3 | ADR 0003 over Eureka |
| 限流 | Resilience4j (Sentinel 备选) | — | ADR 0004 |
| 持久层 | Spring Data JPA + Hibernate 6 | — | ADR 0006 over MyBatis |
| 动态查询 | QueryDSL | 5.0.0 | ADR 0006 |
| 数据库 | PostgreSQL | 16 + pgvector 0.6 | — |
| 迁移 | Flyway | Spring Boot 管理 | — |
| 消息队列 | RocketMQ | 5.1 | ADR 0005 over Kafka |
| 分布式事务 | Outbox + RocketMQ 事务消息 | — | ADR 0002 over Seata |
| AI SDK | Spring AI | 1.0.0-M1 | ADR 0008 over LangChain4j |
| AI Provider | dashscope (主) / openai (备) | — | ProviderRouter 切换 |
| 缓存 | Redis | 7 | 幂等 Key · 日历缓存 |
| 定时任务 | XXL-Job | 2.4 | ADR 0015 over Quartz |
| 分布式 ID | Snowflake (自实现) | — | SnowflakeIdGenerator |
| 文件存储 | MinIO / 阿里云 OSS | — | StorageProvider 抽象 |
| 防病毒 | ClamAV | — | AntivirusClient |
| API 文档 | SpringDoc (OpenAPI 3) | — | @Operation 注解 |
| 代码风格 | Checkstyle | 10.17.0 | checkstyle.xml |

### 8.2 前端

| 层面 | 技术 | 说明 |
|---|---|---|
| 语言 | TypeScript | 严格模式 |
| 框架 | React | — |
| 构建 | Vite | H5 + prototype |
| 包管理 | pnpm workspace | monorepo |
| 状态/数据 | TanStack Query | `useInfiniteQuery` 游标分页 · `staleTime`/`keepPreviousData` 平滑过渡 |
| 路由 | React Router | — |
| 国际化 | i18next | 共享 `@longfeng/i18n` 包 |
| 组件库 | `@longfeng/ui-kit` | 内部设计系统 |
| API 客户端 | `@longfeng/api-contracts` | 类型安全 HTTP 客户端 |
| 测试 ID | `@longfeng/testids` | 中心化 testid 常量 |
| 图表(H5) | `recharts` | 2.x · tree-shake 仅引 LineChart/BarChart (ADR 0014 · S8) |
| 图表(小程序) | `echarts-for-weixin` | bar/line/scatter · 主包贡献 ≤ 400KB |
| 错误追踪(H5) | `@sentry/react` | 8.x · web-vitals 双写 (ADR 0015) |
| 错误追踪(小程序) | `@sentry/minapp` | 社区 latest (ADR 0015) |
| 单元测试 | Vitest + Testing Library | — |
| API Mock | MSW (Mock Service Worker) | B 轨验收 |
| E2E 测试 | Playwright | A 轨验收 |
| 可访问性 | jest-axe | verify-a11y.sh · WCAG AA |
| 设计 Token | CSS 变量 `--tkn-*` | 来自 Style Dictionary |
| S8 新 Token | `--tkn-chart-{1..5}` · `--tkn-subject-{math,physics,chem,eng,chi}` | S8 图表色板 |
| 小程序 | 微信原生 WXML | miniapp 目录 |

### 8.3 基础设施

| 层面 | 技术 | 说明 |
|---|---|---|
| 容器编排 | Kubernetes | kubeadm 3节点 + 阿里云 Spot 混合 |
| 包管理 | Helm | 每服务一个 Chart + umbrella |
| GitOps | ArgoCD | infra/argocd/ |
| IaC | Terraform | infra/terraform/ |
| 镜像仓库 | Harbor | 企业云原生方案 |
| 平台管理 | Rancher | K8s 集群管理 |
| CI | GitHub Actions | .github/workflows/ |
| 监控 | Prometheus + Grafana | ops/grafana/ |
| 追踪 | OpenTelemetry → Tempo | — |
| 日志 | Loki | 结构化 JSON |
| 错误追踪 | Sentry | — |
| 性能审计 | lighthouse-ci | 0.13 · P-INSIGHT 硬门禁 ≥ 85 |

---

## 9. 实现模式

### 9.1 Controller 实现模式

```java
@RestController
@RequestMapping("/api/v1/wrongbook/items")
@Tag(name = "WrongItem", description = "错题主域")
public class WrongItemController {

    @PostMapping
    @Operation(summary = "创建错题")
    public ApiResult<WrongItemVO> create(
        @RequestHeader("X-User-Id") Long userId,
        @RequestHeader("X-Request-Id") String requestId,  // 幂等 Key
        @Valid @RequestBody CreateWrongItemReq req) {
        return ApiResult.ok(service.create(userId, requestId, req));
    }

    @PatchMapping("/{id}/tags")
    @Operation(summary = "替换标签 G-01")
    public ApiResult<Void> replaceTags(
        @PathVariable Long id,
        @RequestHeader("X-User-Id") Long userId,
        @Valid @RequestBody ReplaceTagsReq req) { ... }
}
```

### 9.2 Service 实现模式（事务 + Outbox）

```java
@Service
@Transactional
public class WrongItemService {

    public WrongItemVO create(Long userId, String requestId, CreateWrongItemReq req) {
        // 1. 幂等检查（Redis idem key）
        // 2. 业务校验
        WrongItem item = new WrongItem(userId, req);
        repo.save(item);
        // 3. 同事务写 Outbox（ADR 0002 核心规则）
        outboxRepo.save(new WrongItemOutbox(item.getId(), "CREATED"));
        // 4. 同事务写 audit_log
        auditRepo.save(new AuditLog(userId, "WRONG_ITEM", "CREATE", item.getId()));
        return mapper.toVO(item);
    }
}
```

### 9.3 Repository 实现模式（QueryDSL 动态查询）

```java
@Repository
public class WrongItemQueryRepository {
    
    public Page<WrongItem> findByFilter(Long userId, WrongItemFilter filter, Pageable pageable) {
        QWrongItem q = QWrongItem.wrongItem;
        JPAQuery<WrongItem> query = jpaQueryFactory.selectFrom(q)
            .where(q.userId.eq(userId))
            .where(q.deletedAt.isNull());
        
        if (filter.getSubject() != null)
            query.where(q.subject.eq(filter.getSubject()));
        if (filter.getStatusGroup() != null)
            query.where(q.status.in(filter.getStatusGroup().statusValues()));
        
        return PageableExecutionUtils.getPage(
            query.offset(pageable.getOffset()).limit(pageable.getPageSize()).fetch(),
            pageable, query::fetchCount);
    }
}
```

### 9.4 Java Record DTO 模式（S5 契约补全规范）

```java
// snake_case JSON · @JsonProperty · @Schema · static factory from(Entity)
@Schema(description = "复习计划节点 VO")
public record ReviewPlanDto(
    @JsonProperty("id")            String id,
    @JsonProperty("wrong_item_id") String wrongItemId,
    @JsonProperty("user_id")       String userId,
    @JsonProperty("node_index")    int nodeIndex,
    @JsonProperty("next_due_at")   String nextDueAt,
    @JsonProperty("mastery")       int mastery,
    @JsonProperty("ease_factor")   BigDecimal easeFactor,
    @JsonProperty("interval")      int interval,
    @JsonProperty("status")        String status) {

  public static ReviewPlanDto from(ReviewPlan plan) { ... }
}
```

### 9.5 LLM Provider 路由模式

```java
@Component
public class ProviderRouter {
    private final Map<String, HttpLlmProvider> providers;
    
    public HttpLlmProvider route() {
        String active = config.getActiveProvider(); // Nacos 动态配置
        return providers.getOrDefault(active, providers.get("stub"));
    }
}
```

### 9.6 前端 API 客户端模式

```typescript
// packages/api-contracts/src/clients/review.ts
export const reviewClient = {
  listToday: (date: string, tz: string) =>
    http.get<DayViewResp>('/api/v1/review/review-plans', { 
      params: { date }, 
      headers: { 'X-User-Timezone': tz } 
    }),

  complete: (planId: string, quality: 0 | 3 | 5) =>
    http.post<CompleteReviewResp>(`/api/v1/review/review-plans/${planId}/complete`, { quality }),
};

// 使用 (apps/h5/src/pages/ReviewTodayPage.tsx)
const { data } = useQuery({
  queryKey: ['review-plans', 'today', date, tz],
  queryFn: () => reviewClient.listToday(date, tz),
  staleTime: 30_000,
  refetchOnWindowFocus: true,
});
```

### 9.7 前端复习会话状态机（S8）

```
ReviewExecPage 状态机:
  Loading → Thinking (GET /review-plans/{id} 200)
  Loading → NotFound (404 · 跳回 today)
  Thinking → Revealed (用户点 btn-reveal-answer)
  Revealed → Submitting (用户点 rating-{forgot|partial|mastered})
  Submitting → Done (200 · 跳 /review/done)
  Submitting → ConflictRetry (409 · 自动重试 1 次)
  ConflictRetry → Done (重试 200)
  ConflictRetry → Failed (仍 409 · toast)
  Submitting → Done via 410 (PLAN_MASTERED · 走 Done hero=已掌握)
  Submitting → Failed (400/500 · toast + 回 Revealed)

Observer 会话状态机 (SC-14):
  Idle → Submitting (输入邀请码 + 点兑换)
  Submitting → Active (stub 200)
  Submitting → ErrInvalid/ErrExpired/ErrTooMany (4xx)
  Active → Expiring (剩余 ≤ 5min)
  Active → Revoked (学生撤销 → 401)
  Expiring → Expired (倒计时归零)
  Expired/Revoked → [*] (清 sessionStorage + 跳登录)
```

---

## 10. 测试架构

### 10.1 后端测试策略

| 层面 | 工具 | 标注 | 要求 |
|---|---|---|---|
| 单元测试 | JUnit 5 + Mockito | `@CoversAC("SC-xx.AC-x")` | 覆盖率 ≥ 70%（硬红线） |
| 集成测试 | Spring Boot Test + Testcontainers | `@SpringBootTest` | 真实 PostgreSQL，禁止 Mock DB |
| API 合同 | OpenAPI Spec | 验收维度 A | Controller 注解 → 自动生成 |

**关键原则**: 覆盖率 70% 是硬红线，面临时间压力时缩减功能，不缩减测试（s3-wrongbook.md Q8 决策）。

### 10.2 前端测试策略

| 轨道 | 触发 | 工具 | 验证内容 |
|---|---|---|---|
| A 轨 (e2e) | 每 Sprint | Playwright + 真实后端 | OCR/SSE/持久化/游标分页 |
| B 轨 (mock) | 每 PR | Playwright + MSW | Pixel diff · testid 可见性 · 交互路径 |
| C 轨 (diff) | 随时 | Vite dev server + 截图 | 视觉结构 gap report |

**S7 E2E 验收状态**: 4/5 AC ✅ 通过 · 后端 API 契约差异已记录 (commit e810917)  
**S8 测试矩阵**: 9 AC × 42 行 verification_matrix（含 critical SC-08.AC-3 / SC-14.AC-1 / SC-14.AC-2）  
**验收注解**: `@CoversAC` 链接代码到 AC 编号，确保测试覆盖追溯。

---

## 11. 部署架构

### 11.1 Kubernetes 拓扑

```
3 Node kubeadm Cluster + 阿里云 Spot 混合
├── Harbor (镜像仓库)
├── ArgoCD (GitOps 部署)
├── Rancher (集群管理)
└── Helm Umbrella Chart (所有服务一键部署)
    ├── gateway/
    ├── wrongbook-service/
    ├── ai-analysis-service/
    ├── review-plan-service/
    ├── file-service/
    └── anonymous-service/
```

### 11.2 Liveness/Readiness 探针

所有服务暴露 `/actuator/health/liveness` 和 `/actuator/health/readiness`：

```yaml
management:
  endpoint:
    health:
      probes:
        enabled: true
```

### 11.3 环境配置策略

```
本地开发: application.yml 硬编码默认值 (postgres:wb, redis:localhost)
CI:       Testcontainers 自动启动
生产:     Kubernetes Secrets → 环境变量覆盖 ${DB_PASSWORD}

Feature Flag 生产守门:
  observer.real_jwt=true (必须 · 否则拒服务)
  ai.provider=dashscope (主) | openai (故障切换)
```

---

## 12. 扩展与演进模式

### 12.1 新增微服务

1. 在 `backend/` 下创建 Spring Boot 服务目录
2. 继承 `wrongbook-parent` POM
3. 引入 `common` 依赖（获得 BaseEntity, ApiResult, GlobalExceptionHandler）
4. 在 `gateway/application.yml` 添加路由规则
5. 在 `helm/` 创建对应 Helm Chart
6. 更新 `helm/umbrella/Chart.yaml` 添加子 Chart 依赖

### 12.2 新增 API 端点

1. 在对应服务 `design/arch/<phase>.md` 更新端点定义（单一真源）
2. Entity → Repository → Service → Controller 按层实现
3. Controller 添加 `@Operation` 注解（OpenAPI 自动生成）
4. `@CoversAC` 注解链接测试到 AC
5. 在 `frontend/packages/api-contracts/src/clients/` 添加 TypeScript 客户端方法

### 12.3 新增 LLM Provider

```java
@Component("myNewProvider")
public class MyNewProvider implements HttpLlmProvider {
    @Override
    public AnalysisResult analyze(String sanitizedText, String imageKey) { ... }
}
// ProviderRouter 自动发现，Nacos 配置切换
```

### 12.4 新增前端页面

1. 在 `design/specs/` 或 `design/arch/<phase>.md` 创建页面 spec 文档
2. 运行 `/fe-preflight <页面名>` 提取 design tokens 映射
3. 运行 `/fe-builder <页面名>` 实现 TSX + CSS Module
4. 运行 `/fe-accept-diff` / `/fe-accept-mock` / `/fe-accept-e2e` 验收
5. 在 `@longfeng/testids` 注册新 testid 常量（三段式 `<screen>.<region>.<element>`）

### 12.5 新增存储 Provider

```java
@Component
@ConditionalOnProperty(name = "storage.provider", havingValue = "mycloud")
public class MyCloudProvider implements StorageProvider { ... }
```

### 12.6 S5-v2 子 Phase 扩展（待执行 · 阻塞 SC-09.AC-2/AC-3）

```
启动条件: s5-arch-frozen tag + 用户授权
工作内容:
  1. 写 design/analysis/s5-v2-business-analysis.yml
  2. 改 design/arch/s5-review-plan.md 加 v2 增量段
  3. 后端实现: GET /review-stats 响应增加:
     subjectBreakdown[]: { subject, reviewCount, correctCount, masteredCount }
     ebbinghaus[]:        { nodeIndex, expectedRetention, actualRetention, completedAt }
  4. ownerId 参数 (ADR 0018): s3 wrongbook-service + s5 review-plan-service 同步扩参
  5. 重打 s5-stats-v2-frozen tag → 解除 SC-09.AC-2/AC-3 fe-builder 阻塞
```

### 12.7 常见扩展陷阱

| 陷阱 | 说明 | 正确做法 |
|---|---|---|
| 绕过 Outbox 直接发 MQ | 事务提交失败后消息已发出，数据不一致 | 必须同事务写 outbox 行 |
| Service 直接写另一服务的 DB | 打破服务边界 | 通过 API 调用或 MQ 事件 |
| N+1 查询 | JPA 懒加载默认行为 | 强制 `@EntityGraph` + CI 断言 |
| 前端硬编码颜色值 | 绕过 design token 体系 | 只用 `--tkn-*` CSS 变量 |
| Controller 含业务逻辑 | 难以测试，违反分层 | 业务逻辑下沉到 Service/Domain |
| 不写 `@CoversAC` | 测试与 AC 失去追溯 | 每个测试方法必须标注 |
| DTO 字段用驼峰 JSON | 前端消费不一致 | 所有 DTO 用 `@JsonProperty` 强制 snake_case |
| scope=READ JWT 写操作 | 合规红线 · 家长视图数据泄露 | 三重防御: 网关 403 + 前端 disabled + ARIA |
| 前端直接用 WebSocket | 网关无 WS 路由 | React Query 轮询替代 (ADR D5) |

---

## 13. 架构决策记录 (ADR) 索引

| ADR | 决策 | Phase | 状态 |
|---|---|---|---|
| 0001 | Monorepo 结构（backend + frontend 统一仓库） | — | Accepted |
| 0002 | **Outbox + RocketMQ** 事务消息（不用 Seata）— 最终一致性 | S1 | Accepted |
| 0003 | **Nacos 2.3**（不用 Eureka/Consul）— Alibaba 家族对齐 | S1 | Accepted |
| 0004 | **Sentinel** 首选（Resilience4j 回退）— 限流熔断 | S2 | Accepted |
| 0005 | **RocketMQ 5.1**（不用 Kafka）— 事务消息原生支持 | S2 | Accepted |
| 0006 | **JPA + QueryDSL 5**（不用 MyBatis）— 类型安全 · pgvector 友好 | S3 | Accepted |
| 0007 | **Spring Cloud Gateway**（不用 Zuul）— 响应式 · 活跃维护 | S2 | Accepted |
| 0008 | **Spring AI 1.0.0-M1**（不用 LangChain4j）— PII Advisor 钩子 · Token 埋点 | S4 | Accepted |
| 0009 | **Micrometer+OTEL+Sentry+Prometheus+Grafana+Loki+Tempo** 观测栈 | S2 | Accepted |
| 0010 | 工具链漂移：JDK 25/Node 25（本地）· Java 21/Node 20（CI 基线） | — | Accepted |
| 0013 | **Ebbinghaus+SM-2 混合算法**（不用纯 Ebbinghaus 固定曲线） | S5 | Accepted |
| 0014 | **双端组件对称性 + testid 三段式命名**（S7 新立） | S7 | Accepted |
| 0015 | **Sentry 双端独立 SDK**（@sentry/react + @sentry/minapp 各自独立，不合并） | S8 | Candidate |
| 0016 | **复习自评 3 档**（未掌握/部分/已掌握 → quality 0/3/5 · 跳过 4 · mockup 08 权威） | S8 | Candidate |
| 0017 | **s5 GET /review-stats v2 字段扩展**（subjectBreakdown[] + ebbinghaus[]）向后兼容 | S8/s5-v2 | Candidate |
| 0018 | **ownerId 参数 + scope=READ JWT 跨账号读契约**（s3+s5 同步扩 · 网关守门） | S8 | Candidate |
| review_outcome + review_plan_outbox 独立表设计 | S5 | Accepted |
| 0015(old) | **XXL-Job 2.4**（不用 Quartz）— ReviewDueJob 分布式调度 | S5 | Accepted |

> 完整 ADR 文本见 `docs/adr/` 目录。

---

## 14. 架构治理

### 14.1 代码质量门禁

| 门禁 | 工具 | 执行时机 |
|---|---|---|
| Java 代码风格 | Checkstyle 10.17.0 | CI 编译阶段 |
| 测试覆盖率 ≥ 70% | JaCoCo | CI 测试阶段 |
| TypeScript 类型检查 | tsc --noEmit | CI |
| ESLint (含自定义规则) | eslint-plugin-local | CI |
| Design Token 合规 | scripts/verify-tokens.sh | B轨验收 |
| TestID 合规 | scripts/verify-testid.sh | B轨验收 |
| 可访问性 | jest-axe + verify-a11y.sh | B轨验收 |
| API 合约一致性 | api-contracts/adapter-contract.spec.ts | CI |
| 无 N+1 查询 | hibernate-statistics CI 断言 | 集成测试 |
| Lighthouse Performance | lighthouse-ci | P-INSIGHT ≥ 85 · P-REVIEW-TODAY ≥ 85 |
| Lighthouse Accessibility | lighthouse-ci | ≥ 95 |
| observer watermark z-index | CSS 静态扫描 | B轨验收 |

### 14.2 禁止项（全局红线）

- 禁止 MyBatis / MyBatis-Plus
- 禁止 LangChain4j
- 禁止 Seata
- 禁止 `EntityManager.createNativeQuery`（DDL 豁免）
- 禁止 Controller 层含业务逻辑
- 禁止绕过 Outbox 直接调 RocketMQ Producer
- 禁止前端硬编码颜色值（必须用 `--tkn-*` CSS 变量）
- 禁止跨服务直接访问对方数据库
- 禁止 DTO 使用驼峰 JSON key（必须 `@JsonProperty` 强制 snake_case）
- 禁止 scope=READ JWT 通过写接口（网关层 403 · 前端三重防御）
- 禁止 observer.real_jwt=false 在生产环境（Feature Flag 守门）

### 14.3 架构文档维护规范

每个重镇 Phase（S3/S4/S5/S7/S8/S11）产出独立 `design/arch/<phase-id>.md`，作为**该 Phase 代码符号的唯一真源**。新功能开发前必须先更新 arch 文档，通过 `biz_gate: approved` 后方可执行 Builder 阶段。

---

## 15. 新功能开发工作流

### 15.1 AI 辅助三段式开发流程

```
Phase N 需求确认
       │
       ▼ Stage 1: Pre-flight
/be-preflight <phase>     → 生成 be-build-spec.json (接口契约/业务规则/架构限制)
/fe-preflight <页面名>     → 生成 fe-build-spec.json (design token 映射/组件识别)
       │
       ▼ Stage 2: Builder
/be-builder <phase>       → 按层 (entity→repo→service→controller→openapi) 逐层实现
/fe-builder <页面名>       → 按区块逐一实现 CSS Module + TSX
       │
       ▼ Stage 3: Acceptance
/be-accept <phase>        → 三维验收 (接口形状/业务行为/架构合规)
/fe-accept-diff <页面名>   → C轨: 视觉 pixel diff
/fe-accept-mock <页面名>   → B轨: MSW + Playwright
/fe-accept-e2e <页面名>    → A轨: 真实后端 e2e
       │
       ▼ 修复
/fe-repair <页面名>        → 按 gap report 执行修复
```

### 15.2 新后端功能清单

- [ ] 在 `design/arch/<phase>.md` 定义实体/接口/事件
- [ ] 通过 `biz_gate` 审核
- [ ] 在 `common/db/migration/` 新增 Flyway SQL
- [ ] Entity 继承 `BaseEntity`，添加 JPA 注解
- [ ] Repository: Spring Data 接口 + QueryDSL 复杂查询
- [ ] Service: 业务逻辑 + 事务边界 + Outbox 写入
- [ ] Controller: VO 映射 + `@Operation` + `@Valid`
- [ ] DTO: Java record + `@JsonProperty` snake_case + `@Schema` + static `from(Entity)`
- [ ] OpenAPI YAML 同步更新
- [ ] 单元/集成测试 + `@CoversAC` 注解
- [ ] `api-contracts` 前端客户端同步更新

### 15.3 新前端功能清单

- [ ] `design/specs/` 或 arch 文档中新增页面 spec
- [ ] `@longfeng/testids` 注册 testid 常量（三段式命名）
- [ ] `/fe-preflight` 生成 build-spec.json
- [ ] 实现 TSX + CSS Module（只用 `--tkn-*` 变量）
- [ ] MSW handlers 新增 mock 数据
- [ ] Sentry 埋点: `ac=SC-XX.AC-Y` tag + critical AC 标 `critical=true`
- [ ] `/fe-accept-diff` → `/fe-accept-mock` 逐轨验收

### 15.4 S8 Phase 执行路线图（当前进行中）

```
已完成: 设计阶段 (0.0.5 + 0.1 + 0.2) ✅
              ↓
[并行启动] s5-v2 子 Phase
  - 解冻 s5 · 扩展 GET /review-stats + GET /wrongbook/items
  - 落地 subjectBreakdown[] + ebbinghaus[] + ownerId 参数 (ADR 0017/0018)
  - 重打 s5-stats-v2-frozen tag
              ↓
[阶段 2] s8 fe-preflight (5 mockup token 映射)
  - ReviewTodayPage / ReviewExecPage / ReviewDonePage / ObserverPage
  - InsightPage (Sd 缺 mockup · 需补 19_insight.html)
              ↓
[阶段 3] s8 fe-builder (双端实施)
  - SC-08 4 AC · SC-09.AC-1 · SC-14 2 AC 不阻塞
  - SC-09.AC-2/AC-3 阻塞至 s5-v2 完成
              ↓
[阶段 4] s8 fe-accept-mock (9 AC × 42 行 matrix)
              ↓
[阶段 5] s8 fe-accept-e2e
              ↓
[阶段 6] 打 s8-done tag
```

---

## 16. Phase 完成状态总览

| Phase | 描述 | 状态 | 关键标签 |
|---|---|---|---|
| S0 | 项目引导 + 环境 | ✅ | s0-done |
| S1 | 数据模型 + Flyway | ✅ | s1-done |
| S2 | 平台基础设施 | ✅ | s2-done |
| S3 | wrongbook-service | ✅ | s3-done |
| S4 | ai-analysis-service | ✅ | s4-done |
| S5 | review-plan-service | ✅ + DTO补全 | s5-done · s5-arch-frozen |
| S5.5 | α' 6/9 chain 集成测试 | ✅ | s5.5-done |
| S6 | file-service | ✅ | s6-done |
| S7 | 前端错题主循环 | ✅ A轨 4/5 AC | s7-done (e810917) |
| S8 | 复习执行 + 学情 + 家长视图 | 🔄 设计完成 · 建设中 | s8-arch-frozen (待打) |
| S9 | E2E 全量 | ⏸ 待 S8 | — |
| S11 | 匿名域 + 真实邀请码链路 | ⏸ 待 S8 | — |

---

*本蓝图由 `/architecture-blueprint-generator` skill 于 2026-04-28 更新生成。*  
*增量变更 (vs 2026-04-27 版): §2.4 S8数据流 · §3.3/3.5 S7E2E状态+新DTO · §3.7 S8 stub · §4.4 S8路由 · §6.1 scope守门 · §9.4 Record DTO模式 · §9.7 S8状态机 · §12.6 s5-v2路线 · §13 ADR 0015-0018 · §15.4 S8执行路线 · §16 Phase总览。*  
*建议更新触发点: S8 fe-builder 完成后更新 §15/§16 · s5-v2 解冻后更新 §3.5 · S8 验收完成后更新 §16。*
