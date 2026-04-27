# Project Architecture Blueprint — Longfeng AI 错题本

> **Generated**: 2026-04-27  
> **Detection**: Auto-detect — Java + Spring Cloud Microservices (Backend) · React + TypeScript Monorepo (Frontend)  
> **Pattern**: Microservices + Layered Architecture (per service) + Event-Driven (Outbox/RocketMQ)  
> **Detail Level**: Comprehensive + Implementation-Ready

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
│  └──────────┘             │  ┌──────────────────────────────┐   │
│                           │  │  API Gateway (SCG 4.1)       │   │
│                           │  │  JWT验证 · 限流 · TraceId注入  │   │
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
│  JwtAuthFilter → RateLimitFilter(Resilience4j) → TraceIdFilter         │
│  路由: /api/v1/wrongbook/** → :8081                                     │
│        /api/v1/ai/**       → :8082                                     │
│        /api/v1/review/**   → :8083                                     │
│        /api/v1/file/**     → :8084                                     │
│        /api/v1/anon/**     → :8085                                     │
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
JwtAuthFilter          — 验证 RSA JWT，提取 userId 注入 Header
RateLimitFilter        — Resilience4j 限流 (20 req/s default)
TraceIdFilter          — 生成/透传 X-Request-Id
路由表 (application.yml) — Path 匹配 → 后端服务 URI
```

**关键设计**: 响应式模式（`web-application-type: reactive`），非阻塞 IO。

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

**外部依赖**: OpenFeign 调用 calendar-service（获取日历节点）· notification-service（发送提醒）。

**定时任务**: XXL-Job 驱动 `ReviewDueJob`，扫描 `next_review_at ≤ now()` 的计划。

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
```

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
├── item_id, user_id, node_index (0-6)
├── ease_factor, interval_days, next_review_at
├── quality (最近 quality 评分), status
└── review_plan_outbox (review.completed 事件)

review_outcome — 每次复习结果快照

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
  → 下游服务信任 Header（内网不再验 JWT）

Gateway RateLimitFilter:
  → Resilience4j RateLimiter (20 req/s, 0ms timeout)
  → 超限返回 429
```

### 6.2 错误处理

```
GlobalExceptionHandler (common):
  BizException(ErrorCode) → HTTP 4xx + ApiResult.error(code, message)
  ConstraintViolationException → 400
  OptimisticLockException → 409 (version 冲突)
  其他 → 500 + log.error (含 traceId)

服务级异常:
  file-service: OversizeException, VirusDetectedException, MimeNotAllowedException
  review-plan-service: PlanNotFoundException
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
Grafana Dashboards: ops/grafana/ 目录
```

### 6.4 校验

```
Controller 层: Bean Validation (@Valid) → 400
Service 层: 业务规则校验 → BizException
Entity 层: JPA 约束 (@NotNull, @Size, @Check)

difficulty: CHECK (difficulty IS NULL OR difficulty BETWEEN 1 AND 5)
status: CHECK IN (0,1,2,3,8,9)
```

### 6.5 配置管理

```
application.yml 分层:
  硬编码默认值 (本地开发)
  ${ENV_VAR:default} 环境变量覆盖 (生产)

Nacos 2.3: 动态配置（Feature Flag 切换 LLM Provider）
Kubernetes Secrets: DB_PASSWORD, JWT_PUBLIC_KEY_PATH 等敏感值
```

---

## 7. 服务通信模式

### 7.1 同步通信

```
外部: HTTPS → Gateway → HTTP (内网)
服务间: Spring Cloud OpenFeign
  review-plan-service → CalendarFeignClient
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
| 状态/数据 | TanStack Query | `useInfiniteQuery` 游标分页 |
| 路由 | React Router | — |
| 国际化 | i18next | 共享 `@longfeng/i18n` 包 |
| 组件库 | `@longfeng/ui-kit` | 内部设计系统 |
| API 客户端 | `@longfeng/api-contracts` | 类型安全 HTTP 客户端 |
| 测试 ID | `@longfeng/testids` | 中心化 testid 常量 |
| 单元测试 | Vitest + Testing Library | — |
| API Mock | MSW (Mock Service Worker) | B 轨验收 |
| E2E 测试 | Playwright | A 轨验收 |
| 可访问性 | jest-axe | verify-a11y.sh |
| 设计 Token | CSS 变量 `--tkn-*` | 来自 Style Dictionary |
| 错误追踪 | Sentry | 同后端 DSN |
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

### 9.4 LLM Provider 路由模式

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

### 9.5 前端 API 客户端模式

```typescript
// packages/api-contracts/src/clients/wrongbook.ts
export const wrongbookClient = {
  list: (params: ListParams) =>
    http.get<WrongItemListResponse>('/api/v1/wrongbook/items', { params }),

  create: (req: CreateWrongItemReq) =>
    http.post<WrongItemVO>('/api/v1/wrongbook/items', req),
};

// 使用 (apps/h5)
const { data, fetchNextPage } = useInfiniteQuery({
  queryKey: ['wrongItems', filters],
  queryFn: ({ pageParam }) => wrongbookClient.list({ cursor: pageParam, ...filters }),
  getNextPageParam: (last) => last.nextCursor,
});
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

1. 在 `design/specs/` 创建页面 spec 文档
2. 运行 `/fe-preflight <页面名>` 提取 design tokens 映射
3. 运行 `/fe-builder <页面名>` 实现 TSX + CSS Module
4. 运行 `/fe-accept-diff` / `/fe-accept-mock` / `/fe-accept-e2e` 验收
5. 在 `@longfeng/testids` 注册新 testid 常量

### 12.5 新增存储 Provider

```java
@Component
@ConditionalOnProperty(name = "storage.provider", havingValue = "mycloud")
public class MyCloudProvider implements StorageProvider { ... }
```

### 12.6 常见扩展陷阱

| 陷阱 | 说明 | 正确做法 |
|---|---|---|
| 绕过 Outbox 直接发 MQ | 事务提交失败后消息已发出，数据不一致 | 必须同事务写 outbox 行 |
| Service 直接写另一服务的 DB | 打破服务边界 | 通过 API 调用或 MQ 事件 |
| N+1 查询 | JPA 懒加载默认行为 | 强制 `@EntityGraph` + CI 断言 |
| 前端硬编码颜色值 | 绕过 design token 体系 | 只用 `--tkn-*` CSS 变量 |
| Controller 含业务逻辑 | 难以测试，违反分层 | 业务逻辑下沉到 Service/Domain |
| 不写 `@CoversAC` | 测试与 AC 失去追溯 | 每个测试方法必须标注 |

---

## 13. 架构决策记录 (ADR) 索引

| ADR | 决策 | 状态 |
|---|---|---|
| 0001 | Monorepo 结构（backend + frontend 统一仓库） | Accepted |
| 0002 | **Outbox + RocketMQ** 事务消息（不用 Seata）— 最终一致性 | Accepted |
| 0003 | **Nacos 2.3**（不用 Eureka/Consul）— Alibaba 家族对齐 | Accepted |
| 0004 | **Sentinel** 首选（Resilience4j 回退）— 限流熔断 | Accepted |
| 0005 | **RocketMQ 5.1**（不用 Kafka）— 事务消息原生支持 | Accepted |
| 0006 | **JPA + QueryDSL 5**（不用 MyBatis）— 类型安全 · pgvector 友好 | Accepted |
| 0007 | **Spring Cloud Gateway**（不用 Zuul）— 响应式 · 活跃维护 | Accepted |
| 0008 | **Spring AI 1.0.0-M1**（不用 LangChain4j）— PII Advisor 钩子 · Token 埋点 | Accepted |
| 0009 | **Micrometer+OTEL+Sentry+Prometheus+Grafana+Loki+Tempo** 观测栈 | Accepted |
| 0010 | 工具链漂移：JDK 25/Node 25（本地）· Java 21/Node 20（CI 基线） | Accepted |
| 0013 | **Ebbinghaus+SM-2 混合算法**（不用纯 Ebbinghaus 固定曲线） | Accepted |
| 0014 | review_outcome + review_plan_outbox 独立表设计 | Accepted |
| 0015 | **XXL-Job 2.4**（不用 Quartz）— ReviewDueJob 分布式调度 | Accepted |

完整 ADR 文本见 `docs/adr/` 目录。

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

### 14.2 禁止项（全局红线）

- 禁止 MyBatis / MyBatis-Plus
- 禁止 LangChain4j
- 禁止 Seata
- 禁止 `EntityManager.createNativeQuery`（DDL 豁免）
- 禁止 Controller 层含业务逻辑
- 禁止绕过 Outbox 直接调 RocketMQ Producer
- 禁止前端硬编码颜色值（必须用 `--tkn-*` CSS 变量）
- 禁止跨服务直接访问对方数据库

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
- [ ] OpenAPI YAML 同步更新
- [ ] 单元/集成测试 + `@CoversAC` 注解
- [ ] `api-contracts` 前端客户端同步更新

### 15.3 新前端功能清单

- [ ] `design/specs/` 新增页面 spec
- [ ] `@longfeng/testids` 注册 testid 常量
- [ ] `/fe-preflight` 生成 build-spec.json
- [ ] 实现 TSX + CSS Module（只用 `--tkn-*` 变量）
- [ ] MSW handlers 新增 mock 数据
- [ ] `/fe-accept-diff` → `/fe-accept-mock` 逐轨验收

---

*本蓝图由 `/architecture-blueprint-generator` skill 于 2026-04-27 生成。*  
*建议在每个重镇 Phase（S3/S4/S5/S7/S8/S11）完成后更新 §3、§5、§12 相关章节。*
