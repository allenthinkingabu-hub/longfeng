# Design-Audit Template

> **解决的问题**: E2E 测试全 PASS · 但 FE 实现跟高保真设计稿对不齐 · 用户 iPad Pro / Desktop 截图证实视觉错位 · 测试体系完全没 catch。
>
> **用本 template 做什么**: 一键给你的项目装上 5 层 8 机制 (ABCDEFGH) Design-Audit 系统 · 让 AI Agent 编码当下就按高保真写对 + 4 重防护链兜底 catch 写错。
>
> **30 分钟内完成移植** · 见 [`PORT-CHECKLIST.md`](./PORT-CHECKLIST.md)。

---

## 快速开始

[![Use this template](https://img.shields.io/badge/Use%20this%20template-2ea44f?style=for-the-badge&logo=github)](../../generate)

1. 点上面 **Use this template** 按钮 · 创自己的 repo
2. 跟 [`PORT-CHECKLIST.md`](./PORT-CHECKLIST.md) 走 10 步
3. 跑一个 demo PR · 看自动 catch 视觉差异

---

## 5 层架构

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 5 · Multi-Round Orchestrator (Claude Code Agent)    │
│  design-audit-orchestrator → fan-out → page-fixer × N      │
│  最大 10 轮 · 失败收集 · 并行 worktree 修 · 重跑直到全绿   │
└─────────────────────────────────────────────────────────────┘
        ▲              ▲             ▲              ▲
┌───────┴────────┐ ┌──┴──────┐ ┌───┴───────┐ ┌────┴───────┐
│ Layer 1 测试   │ │ Layer 2 │ │ Layer 3   │ │ Layer 4    │
│ A 多 viewport  │ │ C AI    │ │ D PR 三联 │ │ F mockup   │
│ B pixel diff   │ │ vision  │ │ E pre-    │ │   chrome   │
│   mockup vs    │ │ review  │ │   commit  │ │ G sign-off │
│   impl         │ │ agent   │ │   hook    │ │ H CLAUDE   │
└────────────────┘ └─────────┘ └───────────┘ └────────────┘
```

详细原理 → [`FRAMEWORK.md`](./FRAMEWORK.md)

---

## 适合 / 不适合

| ✅ 适合 | ❌ 不适合 |
|---|---|
| 高保真设计驱动产品 (品牌 / 转化漏斗页) | 内部 admin / dashboard (设计要求低) |
| 多页面 (≥10 页 · ROI 才显) | 单页 demo / POC |
| AI Agent 主导 FE 实施 (CodeGen / Copilot / Claude Code) | 纯 BE / CLI |
| React + vite + playwright stack | 团队 < 3 人 (流程门 D/G 太重) |

---

## 4 重防护链

| 阶段 | 机制 | 行为 |
|---|---|---|
| 编码当下 | **H §2.0** | AI 必 grep `[data-mockup-chrome]` · 缺 attr → ask user |
| 实施完 | **H §2.11 + C** | 必派 design-reviewer · FAIL → 修复循环 |
| commit 时 | **E** | 跑 mockup-diff · warn 但不阻断 |
| PR 时 | **D** | 三联截图 comment + upload artifact |
| merge 时 | **G** | `designer-approved` label 必有 · 否则阻断 |
| 兜底 | **A + B** | CI 必跑 · pixel-level catch |
| 多轮自动 | **orchestrator** | sprint 末 fire · max 10 round |

---

## 真实案例 · longfeng-wrongbook

本 template 来自 K12 错题本项目 P-LANDING 实施暴露的 bug。详细 case study →
[`PORT-FROM-LONGFENG-WRONGBOOK.md`](./PORT-FROM-LONGFENG-WRONGBOOK.md)

---

## 文档

- [`FRAMEWORK.md`](./FRAMEWORK.md) · 完整方法论 (530 行 · 5 层 8 机制 · 9 类风险兜底)
- [`PORT-CHECKLIST.md`](./PORT-CHECKLIST.md) · 10 步迁移
- [`GH-TEMPLATE-INIT.md`](./GH-TEMPLATE-INIT.md) · 把本 template 自己再发布
- `agents/` · 3 个 Claude Code agent 配置
- `e2e-templates/` · 2 个 playwright spec
- `scripts/` · 3 个 shell/ts script
- `workflows/` · 2 个 GH Actions workflow
- `snippets/` · CLAUDE.md / spec.md / mockup.html 粘贴片段

---

## License

MIT (推荐)

---

## Contribution

PR welcome · 优先方向:
- Vue / Svelte / Angular 适配
- React Native / Flutter 适配 (移动端 vrt)
- Figma mockup 替代 HTML (export PNG + 标 chrome 区域)
- 简化 design-reviewer 用 OpenAI vision (替代 Claude)
- 视频 / GIF demo 录制
