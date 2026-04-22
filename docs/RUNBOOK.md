# Runbook — Longfeng

> S0 骨架占位 · 各故障剧本与应急预案由 S10 Phase 补齐。

## Runbook 索引（S10 填充）

- `rollback.md` — 一键回滚
- `ai-provider-failover.md` — dashscope → openai 切换
- `share-token-leak.md` — 分享 token 泄露
- `observer-abuse.md` — 监督员滥用

## 当前可用操作

```bash
# 白名单自检
ops/scripts/check-allowlist.sh s0

# 架构一致性（S0 豁免放行）
ops/scripts/check-arch-consistency.sh s0

# Phase 状态连贯性（S0 空状态通过）
ops/scripts/check-continuity.sh s0
```
