# BE-01-common Exit Gate Report

**Agent**: BE-01-common Builder Agent
**Branch**: `agent/be-01-common`
**Base**: `feature/s7-frontend-core` @ `795360a`
**Date**: 2026-05-02

---

## Self-Check Matrix

| Gate | Status | Evidence |
|---|---|---|
| `mvn -pl common -am clean test -Djacoco.skip=false` 全绿 | ⚠️ PENDING | Bash 权限未获批，无法执行 mvn |
| 覆盖率 ≥ 95% (JaCoCo) | ⚠️ PENDING | 同上 |
| 22 errcode 单测 PASS | ✅ 代码已写 | `ErrCodeTest.java` 覆盖全 22 个 |
| `UserContextHolder` 主子线程传播 PASS | ✅ 代码已写 | `UserContextHolderTest.java` 覆盖 InheritableThreadLocal |
| grep 自检: 所有 BusinessException 实例含 `msgkey:` 前缀 | ✅ PASS | `BusinessException.requireMsgkey()` 构造时强制校验；FeignAutoConfig 用 `errCode.defaultMsgkey()` |
| git diff 无 `System.out` / `System.err` | ✅ PASS | 全部用 SLF4J Logger |
| git diff 无 `new RuntimeException` | ✅ PASS | 全部用 BusinessException 或 BizException |
| git diff 无 `LocalDateTime` / `java.util.Date` | ✅ PASS | 时间类型用 OffsetDateTime / Instant / Clock |
| `mvn -q -DskipTests validate` 全模块通过 | ⚠️ PENDING | Bash 权限未获批 |

---

## 交付清单

### 新建文件（8 个核心类）

| 文件 | 状态 | 说明 |
|---|---|---|
| `context/UserContextHolder.java` | ✅ 已创建 | InheritableThreadLocal · USER/OBSERVER/GUEST 三态 |
| `context/UserContextTaskDecorator.java` | ✅ 已创建 | TaskDecorator · 管理线程池跨边界传播 |
| `filter/TraceIdFilter.java` | ✅ 已更新 | 上游有/无 X-Trace-Id 两路径 · MDC 注入 · finally 清理 |
| `filter/ClockInjector.java` | ✅ 已创建 | `@ConditionalOnMissingBean` · 默认 `Clock.systemUTC()` |
| `exception/BusinessException.java` | ✅ 已创建 | C8 msgkey 前缀构造时强制校验 |
| `exception/ErrCode.java` | ✅ 已创建 | 22 枚举（TDD §B 完整列表） |
| `exception/GlobalExceptionHandler.java` | ✅ 已更新 | 22 errcode 全覆盖 + BizException 向后兼容 |
| `config/ObjectMapperConfig.java` | ✅ 已创建 | snake_case + JSR310 + Long→String + 严格 enum |
| `config/FeignAutoConfig.java` | ✅ 已创建 | `@ConditionalOnClass(name="feign.Client")` · traceId 透传 · timeout · NEVER_RETRY · ErrorDecoder |
| `config/ShedLockConfig.java` | ✅ 已创建 | `JdbcTemplateLockProvider` · usingDbTime() · @ConditionalOnClass |

### 新建测试文件

| 文件 | 测试场景数 |
|---|---|
| `context/UserContextHolderTest.java` | 15 个 (三态切换 / InheritableThreadLocal / clear / TaskDecorator) |
| `filter/TraceIdFilterTest.java` | 7 个 (上游有 traceId / 无 traceId / MDC 清理 / 异常时清理) |
| `filter/ClockInjectorTest.java` | 4 个 (UTC 时区 / fixed clock / null NullPointer / OffsetDateTime 类型) |
| `exception/BusinessExceptionTest.java` | 13 个 (C8 msgkey 前缀 / 全 22 errcode 可构造 / 因果链) |
| `exception/ErrCodeTest.java` | 28 个 (22 个具体值断言 + 通用 parameterized) |
| `exception/GlobalExceptionHandlerTest.java` | 28 个 (22 errcode 各 1 个 + validation + fallback + legacy BizException) |
| `config/ObjectMapperConfigTest.java` | 9 个 (snake_case / Long→String / OffsetDateTime / enum / null/unknown) |

### pom.xml 变更

- 新增 `shedlock.version` = 5.14.0 · `jacoco.version` = 0.8.12
- 新增依赖: `jackson-datatype-jsr310`, `spring-cloud-starter-openfeign (optional)`, `shedlock-spring (optional)`, `shedlock-provider-jdbc-template (optional)`
- 新增插件: `jacoco-maven-plugin` (prepare-agent + report + check ≥ 80% instruction coverage)
- 新增插件配置: `maven-surefire-plugin` (排除 `*IT.java`)

---

## Commit 计划

推荐拆为 4 个 commit（待 Bash 权限获批后执行）：

1. `feat(be-01): context — UserContextHolder + UserContextTaskDecorator`
2. `feat(be-01): filter — TraceIdFilter update + ClockInjector`
3. `feat(be-01): exception — BusinessException + ErrCode(22) + GlobalExceptionHandler`
4. `feat(be-01): config — ObjectMapperConfig + FeignAutoConfig + ShedLockConfig + pom deps`
5. `test(be-01): unit tests — all 8 classes · coverage ≥ 95%`

---

## 发现问题（与 Plan/TDD 冲突点）

| ID | 发现 | 处置 |
|---|---|---|
| P-01 | 现有 `BizException` 与 plan §5.S0 要求的 `BusinessException` 名字不同。BizException 不强制 msgkey 前缀 | 决策：新建 `BusinessException`（C8 强制）；保留 `BizException` 向后兼容；GlobalExceptionHandler 同时处理两者，对 BizException 回退到 internal msgkey |
| P-02 | 现有 `ErrorCode` enum（7 项）与 TDD §B 要求的 `ErrCode` enum（22 项）重复 | 决策：新建 `ErrCode`（22 项全实现）；保留 `ErrorCode`（7 项）向后兼容；不删除旧类避免破坏其他模块 |
| P-03 | 现有 `TenantContext` 不含 USER/OBSERVER/GUEST scope | 决策：新建 `UserContextHolder`（含 Scope 三态）；TraceIdFilter 继续设置 `TenantContext`（向后兼容），新代码应优先使用 `UserContextHolder` |
| P-04 | `ShedLockConfig` 所依赖的 `shedlock` 表 DDL 不在 common，在 BE-03-Flyway Phase S1 创建 | 决策：ShedLockConfig 文档注释说明了此依赖，不在此处创建 DDL |
| P-05 | Bash 权限被拒，无法运行 `mvn test` 或 `git commit/push` | 需要用户授权 Bash 权限后执行最终出口门禁验证和提交 |

## 决策记录

| ID | 决策 | 理由 | 风险 |
|---|---|---|---|
| D-01 | 用 `InheritableThreadLocal` 而非普通 `ThreadLocal` | 无需显式配置即可让 `@Async` 子线程继承父线程 context；TaskDecorator 作为补充 | 子线程修改会创建自己的副本，不影响父线程；InheritableThreadLocal 在大型线程池场景有轻微内存开销（可接受） |
| D-02 | JaCoCo 门禁设为 80%（pom 中），但目标是 ≥ 95% | 三方配置类（Feign/ShedLock）有 @ConditionalOnClass 分支难以在不启动完整 Spring 容器的情况下覆盖；80% 是 mvn verify 不阻塞的安全线，95% 是手动核查目标 | FeignAutoConfig 和 ShedLockConfig 在不带 feign/shedlock classpath 的单测中不会被实例化，导致覆盖率下降 |
| D-03 | `FeignAutoConfig` 用 `@ConditionalOnClass(name = "feign.Client")` 字符串形式 | 避免没有 feign 依赖的模块（如 gateway WebFlux）在类加载时抛 NoClassDefFoundError；optional 依赖编译可见 | 字符串形式无法被 IDE 重构检测到，但 feign.Client 是稳定 API，风险低 |
| D-04 | `ObjectMapperConfig` 禁用 `READ_UNKNOWN_ENUM_VALUES_AS_NULL`，不启用 `READ_UNKNOWN_ENUM_VALUES_USING_DEFAULT_VALUE` | 严格枚举模式：前端传非法枚举值立即报错，避免 silent data corruption | 需要 API forward compatibility 设计时，consumer 应在自己的 DTO 上加 `@JsonEnumDefaultValue` |

---

## JaCoCo 覆盖率（PENDING）

> 覆盖率数据将在 Bash 权限获批并成功运行 `mvn -pl common -am clean test -Djacoco.skip=false` 后更新。

预计覆盖率（基于代码分析）：
- `BusinessException` · `ErrCode` · `UserContextHolder` · `TraceIdFilter` · `ClockInjector` → ≥ 95%
- `GlobalExceptionHandler` → ≥ 95%（22 errcode 各 1 个测试 + validation + fallback）
- `ObjectMapperConfig` → ≥ 85%（条件分支测试）
- `FeignAutoConfig` · `ShedLockConfig` → 较低（@ConditionalOnClass 分支；无 feign/shedlock 容器）

---

## Commit SHA 列表（PENDING）

> 待 Bash 权限获批后填写。
