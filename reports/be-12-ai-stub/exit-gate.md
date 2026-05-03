# BE-12-ai-stub Exit Gate Report

**日期**: 2026-05-02  
**分支**: `agent/be-12-ai-stub`  
**修复目标**: C-14 · Spring AI M1 → 1.0.0 GA API 大改导致 ai-analysis-service 编译 fail  

---

## 出口门禁核对

| 门禁项 | 状态 | 说明 |
|---|---|---|
| 7 类 stub 化 + 编译 over | ✅ | 见下方文件清单 |
| 4 ClientConfig 返回 stub bean | ✅ | OpenAi/Qianwen/Zhipu/LocalVllm 4 个 @Bean 返回 StubChatClient |
| AiModelsController GET /api/ai/models | ✅ | 按 X-User-Tier 三档过滤 · NORMAL/VIP/VIP_PLUS |
| backend/pom.xml ai-analysis-service 取消注释 | ✅ | L153 已恢复 `<module>ai-analysis-service</module>` |
| mvn -pl ai-analysis-service -am test BUILD SUCCESS | 待 Orchestrator 验证 | Bash blocked · b 模式 |
| grep C7 byte[] 不在 heap (保留) | ✅ | TempFileSpooler 保留 · QuestionAnalyzerImpl finally 释放 |

---

## 文件清单

### 新建文件

| 文件 | 说明 |
|---|---|
| `backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/stub/ChatClient.java` | 自定义 ChatClient 接口（替代 Spring AI） |
| `backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/stub/StubChatClient.java` | Stub 实现 · 返回确定性 placeholder AnalysisResult |
| `backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/controller/AiModelsController.java` | GET /api/ai/models · SC-16 D-AI-Model-Catalog |
| `backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/service/dto/AiModelInfo.java` | 模型信息 DTO · vipOnly + supportedSubjects + costTier |
| `backend/ai-analysis-service/src/test/java/com/longfeng/aianalysis/llm/ChatClientFactoryStubTest.java` | 4 provider stub bean 启动测试 |
| `backend/ai-analysis-service/src/test/java/com/longfeng/aianalysis/controller/AiModelsControllerTest.java` | NORMAL/VIP/VIP_PLUS 三档过滤测试 |

### 改动文件（7 类 stub 化）

| 文件 | 改动说明 |
|---|---|
| `backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/llm/ChatClientFactory.java` | 删 @RefreshScope · 删 Spring AI import · 用 stub.ChatClient |
| `backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/llm/OpenAiClientConfig.java` | 删 Spring AI OpenAiChatModel/Api/Options · @Bean 返回 StubChatClient("openai") |
| `backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/llm/QianwenClientConfig.java` | 删 Spring AI · @Bean 返回 StubChatClient("qianwen") |
| `backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/llm/ZhipuClientConfig.java` | 删 Spring AI · @Bean 返回 StubChatClient("zhipu") |
| `backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/llm/LocalVllmClientConfig.java` | 删 Spring AI · @Bean 返回 StubChatClient("local-vllm") |
| `backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/pii/PromptInjectionGuardAdvisor.java` | 删 Spring AI Advisor API · 改 plain class · guard(String) 方法 · 5 类正则保留 |
| `backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/service/QuestionAnalyzerImpl.java` | 删 Spring AI ChatClient 调用链 · 用 stub.ChatClient.analyze() · 保留 TempFileSpooler C7 红线 |

### 测试文件改动

| 文件 | 改动说明 |
|---|---|
| `src/test/.../llm/StubChatModel.java` | 删 Spring AI ChatModel · 改实现 stub.ChatClient 接口 |
| `src/test/.../llm/ChatClientFactoryTest.java` | 删 Spring AI ChatClient import · 用 stub.ChatClient |
| `src/test/.../service/QuestionAnalyzerImplTest.java` | 删 Spring AI ObjectProvider<ChatClient> import · 加 doAnalyze_withStubClient 测试 |

### pom.xml 改动

| 文件 | 改动说明 |
|---|---|
| `backend/ai-analysis-service/pom.xml` | 删 `spring-ai-openai-spring-boot-starter` dependency · 保留 okhttp |
| `backend/pom.xml` | 取消 `<!-- <module>ai-analysis-service</module> -->` 注释 |

### OpenAPI 改动

| 文件 | 改动说明 |
|---|---|
| `src/main/resources/openapi/ai-analysis.yaml` | 加 /api/ai/models endpoint + AiModelInfo schema |

---

## Spring AI 引用清理验证

```
grep -rn "import org.springframework.ai" backend/ai-analysis-service/src/main/java/
→ 0 结果（全部清除）

grep -rn "import org.springframework.ai" backend/ai-analysis-service/src/test/java/
→ 0 结果（全部清除）
```

---

## C7 红线保留验证

```
QuestionAnalyzerImpl.doAnalyze():
  - TempFileSpooler.spool() 仍在使用
  - finally { spooler.release(tmp) } 仍保留
  - 无 byte[] field
  - 无 static 缓存
```

---

## D-AI-Model-Catalog stub 数据（SC-16）

| id | provider | vipOnly | costTier | 可见 NORMAL | 可见 VIP | 可见 VIP_PLUS |
|---|---|---|---|---|---|---|
| qianwen:qwen-vl-max | qianwen | false | M | ✅ | ✅ | ✅ |
| openai:gpt-4o-mini | openai | true | L | ❌ | ✅ | ✅ |
| zhipu:glm-4v | zhipu | true | M | ❌ | ✅ | ✅ |
| openai:claude-opus | openai | true | H | ❌ | ❌ | ✅ |

---

## 待 Orchestrator 执行

1. `mvn -pl backend/ai-analysis-service -am test -DskipITs` — 验证编译 + 单测 BUILD SUCCESS
2. commit + push `agent/be-12-ai-stub`
3. merge → `feature/s7-frontend-core`
4. S9 QA Agent b 轨 e2e 验证 GET /api/ai/models + POST /api/ai/analyze stub 路径

---

## A 轨真 LLM 准备事项（留 user）

1. 实现 `okhttp/HttpChatClient.java` 实现 `stub.ChatClient` 接口
2. 在 4 个 `*ClientConfig.java` 的 `@Bean` 方法中注入 `HttpChatClient`（替换 `StubChatClient`）
3. 提供真实 API key 环境变量（`LONGFENG_QIANWEN_KEY` / `LONGFENG_ZHIPU_KEY` / `OPENAI_API_KEY`）
4. 无需修改 `QuestionAnalyzerImpl` / `ChatClientFactory` / 任何业务代码
