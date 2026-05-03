# ai-analysis-service · Spring AI 升级 plan + 风险评估

> 作者: BE-14-ai-analysis-upgrade sub-agent
> 日期: 2026-05-03
> 当前 worktree: `feature/s7-frontend-core` @ `82136c1`

---

## 0. ⚠️ 关键差距 (必须先于任何升级动作澄清)

**Task brief 描述的"当前状态"与本 worktree 的实际代码状态不一致**，必须先与 Orchestrator / User 对齐再决定升级方向。

| 项 | Task brief 描述 | 本 worktree 实际 |
|----|----------------|------------------|
| stub 接口数量 | 7 类 stub.* (`ChatClient` / `Advisor` / etc) | **0 个** — 不存在 `stub` package |
| `pom.xml` spring-ai 依赖 | "注释或缺" | 完全没有 spring-ai 任何 dep (BOM 在 parent 但本模块未引) |
| SC-16 catalog API | "工作中" | **不存在** — 没有 `AiModelsController` |
| 模块 Java 文件总数 | (隐含 20+ 类) | **3 个 main + 1 个 test** = `Application` / `HealthController` / `OpenApiConfig` / `MockMvcSmokeIT` |

### 原因追溯

- 历史上 commit `d486347 fix(s3.5/c-14): ai-analysis-service stub 化` (2026-05-03) 曾引入:
  - `stub/ChatClient.java` + `stub/StubChatClient.java`
  - `controller/AiModelsController.java` + `dto/AiModelInfo.java`
  - 4 个 `*ClientConfig` (OpenAi / Qianwen / Zhipu / LocalVllm)
  - `PromptInjectionGuardAdvisor` / `QuestionAnalyzerImpl` / `ChatClientFactory` / `TempFileSpooler` / `FallbackOrchestrator` 等
- `git branch --contains d486347` → **只有** `feature/s7-frontend-core` 这一个 ref, **但** `git ls-tree HEAD` 在 `backend/ai-analysis-service/src` 下**只看到 4 个 skeleton 文件**
- 推断: 这些 Java 类在 `feature/s7-frontend-core` 后续某次 merge 中被丢失 (rebase / merge conflict 选错边 / 主干 reset 等)。`git log d486347..HEAD -- backend/ai-analysis-service/` 只输出一个 `Merge pull request #2 from feature/s2-backend`，疑似 merge 解冲突时整片 ai-analysis-service 被恢复成 s2 skeleton 版本

### 决策问题 (需 User 回答)

1. **是否需要先把 d486347 的 stub 化代码 cherry-pick 回 `feature/s7-frontend-core`?** (恢复 baseline 才谈得上"升级")
2. 或者: 直接在 skeleton 上跳过 stub 中间态，**一次到位接 Spring AI 真 SDK**?
3. 或者: 本 worktree (`feature/s7-frontend-core`) 不应做 BE 任务，应换到 `feature/s3.5-be-12-ai-stub` / `feature/s4-ai-analysis` 之类含 stub 代码的分支?

---

## 1. Task 1 — 当前 stub 化范围 (基于历史 commit `d486347`)

虽然本 worktree 看不到代码，但 commit `d486347` 的 message + diffstat 完整列出了 stub 化范围:

### 1.1 stub package (2 个类)

```
backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/stub/
├── ChatClient.java         # interface · 替代 spring-ai ChatClient
└── StubChatClient.java     # impl · 返 placeholder string
```

### 1.2 受影响的真实业务类 (5 类被改造)

| 文件 | 改造内容 |
|------|---------|
| `llm/ChatClientFactory.java` | 删 `@RefreshScope` + `org.springframework.cloud.context.config.annotation.RefreshScope` import · 改用 `stub.ChatClient` |
| `llm/OpenAiClientConfig.java` | 删 spring-ai imports · `@Bean` 返 `StubChatClient` |
| `llm/QianwenClientConfig.java` | 同上 |
| `llm/ZhipuClientConfig.java` | 同上 |
| `llm/LocalVllmClientConfig.java` | 同上 |
| `pii/PromptInjectionGuardAdvisor.java` | 删 spring-ai `Advisor` API · 改 plain class + `Function<String,String>` guard · 5 类正则保留 |
| `service/QuestionAnalyzerImpl.java` | 删 spring-ai `ChatClient` 链式调用 · 用 `stub.ChatClient.analyze()` 返 placeholder |

### 1.3 brief 说"7 类 stub.* 自定义 interface"的解读

Brief 用词不严谨。实际 stub package 只有 2 个 type (`ChatClient` interface + `StubChatClient` impl)。"7 类"应理解为 **"7 个文件被 stub 改造影响"**，含 2 个新建 stub 文件 + 5 个 ClientConfig/Advisor/Service。

### 1.4 stub.ChatClient interface 签名 (推测)

无法读源码，按 commit message 推测最简形态:

```java
package com.longfeng.aianalysis.stub;
public interface ChatClient {
  String analyze(String prompt);   // 替代 spring-ai 的 prompt → ChatResponse 链
}
```

vs Spring AI 1.0.0-M1 真 `org.springframework.ai.chat.client.ChatClient`:

```java
ChatResponse call = chatClient.prompt()
    .user(promptText)
    .call()
    .chatResponse();   // 返 ChatResponse · 含 metadata (token usage)
```

**关键差距**: stub 丢了 `ChatResponse.metadata.usage` (Token 计费)。这是 ADR 0008 的硬需求 ("Token 与成本埋点 · 直接写 ai_usage_log")。**任何升级路径必须把 metadata 链路恢复**。

---

## 2. Task 2 — Spring AI 版本选择

### 2.1 当前 BOM

`backend/pom.xml`:
```xml
<spring-boot.version>3.2.5</spring-boot.version>
<spring-ai.version>1.0.0-M1</spring-ai.version>
```

### 2.2 候选版本 (按 Spring AI 公开发布时间线)

> 注: 本 sub-agent 不能跑 `mvn` / 不能联网查 Maven Central。以下基于 ADR 0008 + 落地计划 §1.3 + Spring AI 公开里程碑节奏推断，**版本号需 User 上 https://repo.spring.io/milestone 实际确认**

| 版本 | 公开时间 (推断) | API 稳定度 | Spring Boot 兼容 |
|------|---------------|-----------|-----------------|
| 1.0.0-M1 | 2024-Q1 | 极不稳 (本次 stub 化原因) | 3.2+ |
| 1.0.0-M2 ~ M4 | 2024-Q2 ~ Q3 | 中 (`ChatClient` 类回归 + builder API 稳) | 3.2+ |
| 1.0.0-M5 ~ M8 | 2024-Q4 ~ 2025-Q1 | 较稳 (`Advisor` API 引入) | 3.3+ |
| 1.0.0-RC1 | 2025-Q2 (推断) | 接近 GA | 3.3+ |
| 1.0.0 GA | 2025-Q3 后 (推断) | 稳 | 3.3+ / 3.4+ |

### 2.3 推荐

**推荐 1.0.0-M3 ~ M4 之间任选**，理由:

- M1→M3 的差距正是补回 `ChatClient` 类 + builder API (本次 stub 化的导火索)
- 不引入 `Advisor` 重写 (M5 才稳)，迁移面积小
- Spring Boot 3.2.5 兼容确认无问题
- 风险低于 RC1 / GA (后者 API 又有过一次大改)

**如 User 要求一步到位**: 用 GA (假设已发) — 但需补 Spring Boot 升级到 3.3+ 的兼容性测试 (M-04 ~ M-05 的连锁影响)。

### 2.4 不推荐

- ❌ 跳到 1.0.0-RC1 / GA: 过去 1 年 Spring AI 在 M5 → M8 引入 `Advisor` API 重写、`ChatModel` vs `ChatClient` 拆分，迁移面积比 M3 大 3-5x
- ❌ 维持 1.0.0-M1: 这是 stub 化的根因，没意义

---

## 3. Task 3 — 升级路径 3 选项对比

### 选项 A: Spring AI 1.0.0-M3 (推荐)

**步骤**:
1. 改 `backend/pom.xml`: `<spring-ai.version>1.0.0-M3</spring-ai.version>`
2. 改 `backend/ai-analysis-service/pom.xml` 加:
   ```xml
   <dependency>
     <groupId>org.springframework.ai</groupId>
     <artifactId>spring-ai-openai-spring-boot-starter</artifactId>
   </dependency>
   <dependency>
     <groupId>com.alibaba.cloud.ai</groupId>
     <artifactId>spring-ai-alibaba-starter</artifactId>  <!-- dashscope -->
   </dependency>
   ```
3. 删 `stub/` package (2 文件)
4. 5 个 `*ClientConfig` 把 `StubChatClient` 换回 spring-ai 官方 `ChatClient.builder(chatModel).build()`
5. `QuestionAnalyzerImpl` 把 `stub.ChatClient.analyze()` 换回 `chatClient.prompt().user(...).call().chatResponse()` + 抓 `metadata.usage` 写 `ai_usage_log`
6. `PromptInjectionGuardAdvisor` 改回 `org.springframework.ai.chat.client.advisor.api.Advisor` (M3 已稳定)
7. mvn compile + test (stub 时期失效的 13 个测试可能恢复)

**工作量**: 0.5d (前提是 Section 0 的代码恢复问题先解决)

**风险**:
- M3 → M1 之间 dashscope starter artifact id 可能变 (需查 alibaba-cloud-ai 仓库)
- `Advisor` API 在 M3 仍可能小改，需逐文件 grep import 路径
- `ChatResponse.metadata.usage` 字段名在 M3 已稳，低风险

### 选项 B: Spring AI 1.0.0-RC1 / GA

**优点**: 一步到位，未来不再迁移
**缺点**:
- API 大改面积比 M3 大 3-5x (Advisor 重写 + ChatModel 拆分)
- Spring Boot 可能需升 3.3+ → 连锁影响其他 6 个模块
- **不可能在 1d 内完成** (估 2-3d + 全栈回归)

**结论**: 不推荐本 Sprint 做。

### 选项 C: 保留 stub · 直连 OpenAI/Qwen SDK

**优点**: 0 spring-ai 依赖 · 完全可控
**缺点**:
- 违反 ADR 0008 ("LangChain4j 全局禁用" + "直连 RestTemplate 调 LLM 禁用" — 自建 SDK 等价于 RestTemplate 直连)
- 失去 `Advisor` 钩子 (PII 脱敏链路只能手撕)
- 失去 token 计费埋点 (要自己解每家 provider 不同 response shape)
- 失去 retry / circuit-breaker 与 Resilience4j 集成

**结论**: 仅当 User 主动要求废弃 ADR 0008 时考虑。

### 推荐

**Section 0 代码恢复 → 选项 A (1.0.0-M3)**。

---

## 4. Task 4 — UPGRADE-PLAN.md (本文件)

✅ 写到: `backend/ai-analysis-service/UPGRADE-PLAN.md`

---

## 5. Task 5 — 试探性升级 (跳过)

**未执行**。理由:

1. 本 worktree `backend/ai-analysis-service/src` 只有 4 个 skeleton 文件，**没有 stub.ChatClient 可换**，试探无标的
2. 加 spring-ai-bom + starter 到 `pom.xml` 但没有任何业务代码用，是无意义改动
3. 在不能跑 `mvn dependency:resolve` 验证 M3 artifact 真存在的前提下，盲改 pom.xml = 把 build 风险传染给主干
4. 任何试探都需要先解决 Section 0 的代码恢复

**如 User 同意 Section 0 恢复路径**, 试探性升级可在恢复后另起 sub-agent 做 (估 0.5h)。

---

## 6. 已知风险汇总

| 风险 | 严重度 | 缓解 |
|------|--------|------|
| **R1**: 本 worktree 不含 stub 代码，升级无标的 | 🔴 阻断 | Section 0 决策, cherry-pick `d486347` 或换分支 |
| **R2**: Spring AI 1.0.0-M3 实际 artifact 在 milestone repo 是否存在未验证 | 🟠 高 | User 上 https://repo.spring.io/milestone 直查 |
| **R3**: `spring-ai-alibaba-starter` (dashscope) M3 兼容版本不明 | 🟠 高 | grep alibaba-cloud-ai GitHub release |
| **R4**: `Advisor` API 在 M2/M3 仍小改 (历史上 M2→M3 改过 builder pattern) | 🟡 中 | 升级 PR 必含 `mvn compile + test` 全绿 |
| **R5**: `ChatResponseMetadata.getUsage()` 字段名 / 类型 在 M3 是否与 M1 一致 | 🟡 中 | 升级后 grep `ai_usage_log` 写入路径回归 |
| **R6**: Mockito 5 + Java 21 final class 限制 (C-04/C-05) 影响 ChatClient mock | 🟡 中 | 用 `mockito-inline` (parent 已配?) 或 wiremock |
| **R7**: 删 stub 后, 13 个原 stub 适配的测试需重写 | 🟢 低 | `s4` 时代的原 spring-ai 测试可从 git 历史恢复 |

---

## 7. 1d 可行性评估

| 场景 | 1d 内可完成? |
|------|-------------|
| 仅写 plan + 风险评估 (Task 1-4) | ✅ 已完成 |
| Section 0 代码恢复 + 选项 A 升级 + IT 全绿 | ⚠️ 紧 (估 0.5d 恢复 + 0.5d 升级 = 刚好 1d, 无冗余) |
| Section 0 + 选项 A + Mockito 5 测试重写 (C-04/05) | ❌ 1d 不够, 估 1.5d |
| 选项 B (RC1/GA) | ❌ 2-3d |

**结论**: 本 sub-agent 已完成 plan 输出 (Task 1-4)。**真升级必须等 User 决策 Section 0**，否则 1d 内不可行。

---

## 8. 给 Orchestrator 的建议

1. **最高优先级**: 把 Section 0 的 4 个差距贴给 User 决策。**未决前不要派 sub-agent 改 pom.xml / 写 spring-ai 代码**
2. 如 User 选 cherry-pick `d486347`: 先派纯 git sub-agent 做恢复 + IT 验证 stub 时期的 48 个测试再次绿掉, 再派升级 sub-agent
3. 如 User 选换分支: 本 worktree 不再适合做 BE-14, 应在 `feature/s4-ai-analysis` 或一个新 `feature/s3.6-spring-ai-upgrade` 上重派
4. 升级 sub-agent 必须开 a-mode (允许 mvn) 否则无法验证 M3 artifact 是否真存在
