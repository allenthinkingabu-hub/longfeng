# BUG-LF-17 · ai-analysis PromptInjectionGuard 误拦自己的 system prompt

**Severity**: P1 (BE bug · 影响真 LLM 调用)
**Discovered**: 2026-05-05 · SC-12 hybrid 联调 Phase 5 L4
**Status**: OPEN

## 现象

`POST /api/ai/analyze-by-url` 触发 LLM 调用 · ai-analysis-service 内部:
1. ✅ image_url fetch 200 · spool 写盘 OK
2. ❌ 在 prompt 准备阶段 · `PromptInjectionGuardAdvisor` 把 BE 自己的 system prompt 识别为 prompt injection 并拒绝
3. 抛 `BusinessException: msgkey:ai.error.prompt_injection`
4. ai_usage_log 写一行 status=9 (PENDING_ANALYSIS) · tokens_in/out=0 · 真 LLM 永远没被调

日志:
```
WARN c.l.a.pii.PromptInjectionGuardAdvisor : Prompt injection detected ·
  pattern=(?i)\byou\s+are\s+now|你\s*现在\s*是|^\s*system\s*[:：] ·
  text(前40字)=你是错题本专用的 K12 学科分析助手。请按以下流程分析图片中的错题...
WARN QuestionAnalyzerImpl: prompt injection detected · taskId=4a898aa0...
WARN QuestionAnalyzerImpl: streamAnalyze failed · cause=msgkey:ai.error.prompt_injection
```

## Root cause

- BE system prompt 模板 "你是 K12 学科分析助手" 包含 "你是" + 后文有 "现在"
- 正则 `你\s*现在\s*是` 在 system prompt 中误命中
- `PromptInjectionGuardAdvisor` 设计意图是拦 USER 输入的 injection · 但它跑在 ALL outbound 文本上 (含 BE 自己的 system prompt)

## Fix 方向

A. PromptInjectionGuardAdvisor 加 role-aware: 仅检 USER role 内容 · 跳 SYSTEM role
B. system prompt template 改写避开 `你是 ... 现在` · `you are now` 等模式
C. 加 whitelist · 已知 BE 模板内容 hash 加入 trusted set

推荐 A — 根治。

## 联调影响

- `ai_usage_log` 真有 row (provider=dashscope · 证明真集成已就位 · 非 stub)
- 但 tokens 全 0 · cost 全 0 · LLM 真消费 = 0
- Supervisor 第 7 项 "tokens_out > 0" 判 FAIL · 但 root cause 不在 QA 链路 · 在 BE PromptInjectionGuard

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
# → 1 row · status=9 · tokens=0
```
