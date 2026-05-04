# Design-Audit System · 5 Layer 8 Mechanism Portable Bundle

> **解决问题**: E2E 全 PASS 但 FE 实现跟设计稿对不齐 (chrome 误植 / 响应式破洞 / 文案偏离)。
>
> **方法论**: 5 层架构 · ABCDEFGH 8 机制互补 · 让 AI Agent 编码当下就按高保真写对 + 4 重防护链兜底。
>
> **来源**: `longfeng-wrongbook` 项目 P-LANDING 实施暴露 bug → 全栈解决方案。**这个 repo 自身就是活示例 demo** · 见 `frontend/apps/h5/src/pages/Landing/` + `design/mockups/wrongbook/_archive/14_landing.html`。

---

## 5 分钟 Quickstart (port 到新项目)

### 前提

- 目标项目用 React + vite + playwright + Claude Code (其他 stack 见 [适配指南](#跨技术栈适配))
- 设计稿是 HTML mockup (Figma 项目见 [Figma 适配](#figma-mockup-适配))

### 10 步迁移 checklist

详见 [`PORT-CHECKLIST.md`](./PORT-CHECKLIST.md)。简版：

```bash
# 1. 拷贝 self-contained bundle 到目标项目
cp -r tools/design-audit/agents       <target>/.claude/agents/
cp -r tools/design-audit/e2e-templates <target>/e2e/specs/
cp -r tools/design-audit/scripts      <target>/scripts/
cp -r tools/design-audit/workflows    <target>/.github/workflows/

# 2. 改 PAGE_MAP (3 处 · 见 PORT-CHECKLIST §3)
# 3. 把 snippets/CLAUDE-SNIPPET.md append 到目标 CLAUDE.md
# 4. 把 snippets/SPEC-§15-SNIPPET.md append 到目标 spec.md template
# 5. 给所有 mockup HTML 加 data-mockup-chrome attr (见 snippets/MOCKUP-EXAMPLE.html)
# 6. bash scripts/install-design-hooks.sh
# 7. cd e2e && pnpm e2e:vrt-multi --update-snapshots  (生成 baseline)
# 8. 派 design-audit-orchestrator agent 跑全页 audit (验证)
```

---

## Bundle 内容

```
tools/design-audit/
├── README.md              ← 本文件 · 5 分钟 quickstart
├── PORT-CHECKLIST.md      ← 10 步迁移详细
├── methodology/
│   └── FRAMEWORK.md       ← 5 层架构 + 8 机制 + 9 类风险兜底 (530 行 · 完整理论)
├── agents/                ← 复制到 <target>/.claude/agents/
│   ├── design-reviewer.md            (C · vision audit)
│   ├── design-audit-orchestrator.md  (5 · 多轮调度)
│   └── page-fixer.md                 (5 · 单页修)
├── e2e-templates/         ← 复制到 <target>/e2e/specs/
│   ├── vrt-multi-viewport.spec.ts    (A · 4 viewport vrt)
│   └── mockup-vs-impl.spec.ts        (B · pixel diff)
├── scripts/               ← 复制到 <target>/scripts/
│   ├── design-precommit.sh           (E · pre-commit hook)
│   ├── install-design-hooks.sh       (E · 一次性安装)
│   └── pr-screenshots.ts             (D · PR 三联截图)
├── workflows/             ← 复制到 <target>/.github/workflows/
│   ├── design-pr-screenshots.yml     (D · PR workflow)
│   └── design-sign-off.yml           (G · sign-off label gate)
└── snippets/              ← 文档 snippet · 粘贴用
    ├── CLAUDE-SNIPPET.md             (H · CLAUDE.md §2.0 + §2.11)
    ├── SPEC-§15-SNIPPET.md           (F · spec.md §15 实现边界段)
    └── MOCKUP-EXAMPLE.html           (F · mockup chrome attr 示范)
```

---

## 5 层架构总览

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
│                │ │         │ │           │ │   prompt   │
└────────────────┘ └─────────┘ └───────────┘ └────────────┘
        机械层          AI 评审      流程门          治本改造
```

**关键洞察**: 层次互补 · 不可单替
- 机械层 (A/B) catch 像素 / 响应式 (vision 漏)
- AI 评审 (C) catch 语义级 (像素漏)
- 流程门 (D/E) 防 AI Agent 跳步
- 治本 (F/G/H) 消除"设计稿语义歧义"根源

详细见 [`methodology/FRAMEWORK.md`](./methodology/FRAMEWORK.md)

---

## 4 重防护链 (让 AI 下次写对 + catch 写错)

| 阶段 | 机制 | 行为 |
|---|---|---|
| 编码当下 | **H §2.0** | AI 必 grep `[data-mockup-chrome]` · 缺 attr → ask user · 不凭推断 |
| 实施完 | **H §2.11 + C** | 必派 design-reviewer · FAIL → 修复循环 · 不交付 |
| commit 时 | **E** | 跑 mockup-diff · warn 但不阻断 (开发不卡顿) |
| PR 时 | **D** | 三联截图 comment + upload artifact |
| merge 时 | **G** | `designer-approved` label 必有 · 否则阻断 |
| 兜底测试 | **A + B** | CI 必跑 · pixel-level catch |
| 多轮自动 | **orchestrator** | sprint 末 fire · max 10 round |

---

## 跨技术栈适配

### Vue / Svelte / Angular
- A/B/D/E 完全通用 (playwright stack-agnostic)
- F (mockup attr) HTML 标准 · 通用
- H (CLAUDE.md) 改为 grep page src 路径模式 (`src/views/` 替 `src/pages/`)
- C (design-reviewer) 完全通用 (vision agent · 看截图)

### Vanilla JS / Static
- 仍可用 · 把 vite 替成你的 dev server URL · A/B 仍跑

### React Native / 移动原生
- A (vrt-multi) 不适用 (无 web viewport)
- B (mockup pixel) 仍可用 (用 detox screenshot)
- C (vision) 完全适用 · vision agent 看截图无所谓 web/native
- F/H 全适用 · 设计稿规范跨平台
- D/E (pre-commit + PR) 适用

### 不用 Claude Code (Cursor / Copilot / Gemini)
- C/orchestrator/page-fixer 是 .md prompt · 改成对应 agent 系统格式
- 概念 100% 通用

---

## Figma mockup 适配

如果你团队用 Figma 而非 HTML mockup:

1. Figma 导出 PNG 作 `baseline.png` (替代 mockup HTML)
2. 在 Figma 用 plugin/manual 标注 chrome 区域 (例如 frame 命名 `mockup-chrome:iphone-frame`)
3. mockup-vs-impl.spec.ts 改成 baseline PNG vs impl PNG (不再加载 HTML)
4. design-reviewer agent 输入 改成 (baseline PNG · impl PNG · spec.md) · 不依赖 HTML

---

## 复用 ROI · 何时该用 / 不该用

### ✅ 适合
- 高保真 mockup 必须 1:1 还原 (品牌 / 设计驱动 / 转化漏斗页)
- 多页面 (≥10 页) · ROI 才显
- AI Agent 主导 FE 实施 (CodeGen / Copilot / Claude Code)

### ❌ 不适合
- 内部 admin / dashboard (设计要求低 · token 成本不值)
- 单页 demo / 原型 / POC (架子太重)
- 纯命令行工具 / 后台服务 (无 UI)
- 团队 < 3 人 (流程门 D/G 太重 · 改用轻量 review)

---

## 当前项目状态 (longfeng-wrongbook)

7 commit 落地 ABCDEFGH:
- `0d50c5c` D4 流程门 D+E+G + .gitignore
- `93ec517` D3 orchestrator + page-fixer
- `3add048` D2 #11 F+H 全栈 (19 mockup attr + 20 spec §15 + CLAUDE.md v2)
- `13cb862` D2.5 P-LANDING 4 真 bug fix
- `9aea465` D1+D2 启动 (A/B/C/F P-LANDING + plan)
- `5e2a526` BE gateway hotfix (前置)
- `ce2ddb9` BE 5/5 + miniapp 框架 (前置)

P-LANDING 已修通 · 其他 16 页 impl 没改 · 待跑 orchestrator 多轮自动修。

---

## License

跟主项目同 · 见 root LICENSE。

---

## 致谢

设计灵感来自:
- Anthropic Claude Code agent + skill 系统
- H5 e2e 24 轮对抗 sub-agent + worktree 模式
- pixelmatch + playwright vrt 行业实践
- AI vision model (Claude Opus 4.7) 语义级 review 能力
