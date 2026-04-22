# ADR 0008 — AI SDK：Spring AI 1.0.0-M1（dashscope + openai 双 Provider）

**Status**: Accepted · 2026-04-22

## 选项

1. **Spring AI 1.0.0-M1（含 `spring-ai-dashscope`）**（本次采纳）
2. LangChain4j（社区活跃，但接口未稳定且与 Spring 生态需额外桥接）
3. 直连 RestTemplate / WebClient（零依赖，但自己 roll Token 计费 / retry / circuit-breaker）

## 决策

采纳 **Spring AI**。**LangChain4j 全局禁用**。**直接 `RestTemplate` 调 LLM 禁用**。

## 理由

- **抽象统一**：`ChatClient` / `EmbeddingClient` 接口同时覆盖 dashscope / openai · Feature Flag 切换 Provider 一行配置
- **PII 脱敏钩子**：`Advisor` 机制可在请求链路插入脱敏 · 方案 §2A.6 合规红线
- **Token 与成本埋点**：内置 `ChatResponseMetadata` 抓 Usage · 可直接写 `ai_usage_log`
- **Retry / CircuitBreaker**：与 Resilience4j / Sentinel 集成标准化

## 后果

- 1.0.0-M1 是里程碑版本 · 升级到 GA 可能需小改 · 通过 BOM 统一升
- 必须配 `spring-milestones` 仓库（parent pom 已配）

## 参考

- 落地计划 §1.3.1 · §8 Phase S4
