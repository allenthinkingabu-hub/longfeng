# ADR 0001 — Monorepo 结构

**Status**: Accepted · 2026-04-22
**Context**: 前后端 + 设计系统 + E2E + Helm + IaC 同一产品线，如何组织仓库？

## 选项

1. **Monorepo**（本次采纳）— 单仓多模块 · `backend/` `frontend/` `helm/` `infra/` `design/` `e2e/` `ops/` 平级
2. Multi-repo — 每个子模块独立仓库 · 通过 Git submodule 或 npm 私有包依赖
3. Hybrid — 后端单仓 + 前端单仓 + 设计系统单仓

## 决策

采纳 **Monorepo**。

## 理由

- **原子变更**：一个 PR 可同时跨前后端改（OpenAPI 改了前端 types 同时更新）
- **Design Gate 统一**：`design/arch/<phase-id>.md` 作为代码符号真源（§1.7），跨仓无法跨 Phase 对齐
- **CI 简化**：一套 `.github/workflows/ci.yml` 覆盖 Java + Node + Playwright + Helm lint
- **AI Agent 可见**：Planner Agent 只读一个工作目录就能看到全局依赖

## 后果

- 仓库体积偏大 · 需 `.gitattributes` 处理二进制
- `CODEOWNERS` 必须细化到子目录
- CI 必须按子目录选择性跑（`paths:` filter）

## 参考

- 落地计划 §3.1 — Monorepo 结构（最终态）
