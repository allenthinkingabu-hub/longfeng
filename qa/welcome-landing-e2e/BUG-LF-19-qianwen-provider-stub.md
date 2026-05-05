# BUG-LF-19 · QianwenClientConfig 仍是 stub · 真 DashScope HTTP 调用未集成

**Severity**: P1 (影响真 LLM 消费 · 跟 BUG-LF-17 是孪生 bug)
**Discovered**: 2026-05-05 · BUG-LF-17 fix verify 时连带发现
**Status**: ✅ FIXED · 2026-05-05 commit `a954f71` · hybrid 真 DashScope 消费证毕

## ✅ Fix 落地证据 (2026-05-05 15:01)

**commit**: `a954f71` (zhe.wang) — `fix(s7/ai-analysis/lf-19): real DashScope HTTP integration · replace StubChatClient`

**实施**: 选项 B (okhttp 直调 OpenAI 兼容端点)
- 新文件 `Usage.java` `ChatResponse.java` `DashscopeChatClient.java` (327 行真 HTTP impl)
- `ChatClient.analyze()` 返回类型升级 `AnalysisResult` → `ChatResponse(result, usage)`
- `QianwenClientConfig` 据 apiKey 切 stub/real (key 是 `sk-qwen-test` placeholder 时仍 stub · 防 IT 跑挂)
- `QuestionAnalyzerImpl.recordUsage()` 用真 `usage.promptTokens()`/`completionTokens()` · usage zero 时回退老估算 (stub 兜底)
- `FallbackOrchestrator.tryWithFallback()` 签名同步升级
- 测试同步: `StubChatModel` `ChatClientFactoryStubTest` `FallbackOrchestratorTest`

**真测铁证**:
```
ai_usage_log row 5 (新 · 真 DashScope):
  provider=dashscope · model=qianwen-default
  tokens_in=774 · tokens_out=337 (真 DashScope billing)
  status=0 SUCCESS · latency_ms=9262 (真 9.2s 网络调用)

ai-analysis-service log:
  INFO QianwenClientConfig: DashscopeChatClient activated · baseUrl=https://dashscope.aliyuncs.com/compatible-mode/v1 model=qwen-vl-max
  INFO DashscopeChatClient: DashScope analyze · provider=qianwen model=qwen-vl-max latencyMs=9235 promptTokens=774 completionTokens=337
```

**对比修前**:
- 修前 row 1-3: tokens_out ≤ 7 (stub 估算 length/4)
- 修后 row 5: tokens_out=337 (真 DashScope 计费)
- 修前 log: `[stub] ChatClient.analyze called`
- 修后 log: `DashScope analyze · promptTokens=774 ...`

## 现象

修 BUG-LF-17 (PromptInjectionGuardAdvisor role-aware) 后 · pipeline 不再被 guard 拦 · ai_usage_log 真写一行 status=0 (SUCCESS) · tokens_in=290 / tokens_out=7。

但 ai-analysis-service 日志显示:
```
INFO StubChatClient : [stub] ChatClient.analyze called · provider=qianwen subject=math · returning placeholder
```

## Root cause

`backend/ai-analysis-service/src/main/java/com/longfeng/aianalysis/llm/QianwenClientConfig.java`:

```java
@Bean
ChatClient qianwenChatClient(@Value("${longfeng.ai.qianwen.api-key:sk-qwen-test}") String apiKey, ...) {
    // C-14 stub: 不调真 LLM · 返回确定性 placeholder
    // TODO(A 轨): 用 okhttp 封装真实 DashScope 调用 · apiKey + baseUrl + model 已绑定
    return new StubChatClient("qianwen");  // ← 仍是 stub
}
```

注释明确说 "C-14 stub" · TODO 是 "A 轨实现只需换 okhttp 封装 + 本配置注入真实凭证"。

OpenAiClientConfig.java + LocalVllmClientConfig.java 同款 stub。

## 影响

- 真 user 的 DashScope API key (sk-21ba4e60...) 配在 env 也没用 · provider 永远走 stub
- ai_usage_log 写的 tokens_in/out 是 stub 假造数字 (固定 290/7) · 不反映真消费
- cost_cents 也假
- LLM 真分析不发生 · 错题真分析结果 (reason/steps/kp) 是 stub placeholder

## Fix 方向

### 选项 A · 用 Spring AI 1.0 GA OpenAI client (推荐)

DashScope 提供 OpenAI 兼容端点 (base-url=https://dashscope.aliyuncs.com/compatible-mode/v1)。最少改动:

```java
@Bean
ChatClient qianwenChatClient(
    @Value("${longfeng.ai.qianwen.api-key}") String apiKey,
    @Value("${longfeng.ai.qianwen.base-url}") String baseUrl,
    @Value("${longfeng.ai.qianwen.model:qwen-vl-max}") String model) {

  OpenAiApi api = new OpenAiApi(baseUrl, apiKey);
  OpenAiChatModel model = new OpenAiChatModel(api, OpenAiChatOptions.builder()
      .withModel(model).build());
  return new SpringAiChatClientAdapter(model); // wrap 适配 com.longfeng.aianalysis.stub.ChatClient interface
}
```

加 spring-ai-openai-spring-boot-starter 依赖到 ai-analysis-service/pom.xml。

### 选项 B · okhttp 直调 DashScope SDK

用阿里云 dashscope-sdk-java 真接 · 工作量大但符合"国内合规"路径。

### 选项 C · 暂留 stub · 但日志清晰区分 (tactical)

stub 模式下 · ai_usage_log 写 `status=99 (STUB)` 而不是 0 · 防止 Supervisor 误判 PASS。

推荐 A · 1-2 小时工作量 · 真 LLM 消费立刻生效。

## 复现

```bash
docker exec lf-dev-minio mc anonymous set download local/wrongbook-dev
curl -X POST -H "Content-Type: application/json" \
  -H "X-Forwarded-For: 10.0.x.x" -H "X-Device-Fp: any-fp" \
  -d '{"device_fp":"any","subject":"math","image_url":"http://localhost:19000/wrongbook-dev/qa-test/sample.jpg"}' \
  http://localhost:9880/api/guest/analyze

# 等 5s
PGPASSWORD=wb psql -h localhost -p 15432 -U postgres -d longfeng_ai \
  -c "SELECT * FROM ai_usage_log ORDER BY id DESC LIMIT 1"
# → 1 row · status=0 · tokens_out=7 (stub 假数字 · 不是真 DashScope)

grep "StubChatClient" logs/ai-analysis-service.log
# → "[stub] ChatClient.analyze called" · 证明 stub 兜底
```

## 关联 BUG

- BUG-LF-17 (✅ FIXED) · PromptInjectionGuard 误拦 · 修了之后 pipeline 通了 · 才暴露这个 stub bug
- BUG-LF-09 (✅ FIXED · BE endpoint 缺) · 修了之后 endpoint 通了 · 才能跑到 LF-17
- 都是"修一层 · 暴露下一层 stub" 类型 · 典型 sub-agent 留 TODO 没做完
