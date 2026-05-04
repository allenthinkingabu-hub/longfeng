<!--
  Design-Audit System · H 机制 · CLAUDE.md snippet
  Append 到目标项目根 CLAUDE.md 末尾 · 让 AI Agent 实施页面前/后强制走边界识别 + design-review。
-->

---

## 设计实施铁律 v2 · §2.0 边界识别 + §2.11 自检 design-review（H 机制 · MUST）

> **背景**：FE Agent 误把 mockup HTML 的 iPhone 边框/notch/status chrome 当成页面元素 · 导致 iPad/Desktop 视觉错位。F+H 机制治本：mockup HTML 加 `data-mockup-chrome` attr · CLAUDE.md 强制 AI 读边界 + 完成后自检。

### §2.0 边界识别（在常规设计实施流程之前 · 必做）

任何"实现 / 修 / 写 / 画"页面任务 · **第 0 步必做**：

1. **打开 mockup HTML**: `<your-mockup-dir>/<file>.html` (or 项目对应路径)
2. **grep `[data-mockup-chrome]`** 列出所有 chrome 元素：
   ```bash
   grep -nE 'data-mockup-chrome' <mockup-file>.html
   ```
   - `iphone-frame` → 整个 phone wrapper · 装饰 · **不实现** (改 width:100% / min-height:100vh)
   - `iphone-statusbar` → 9:41 + 信号 + 电池 chrome · **不实现** (浏览器原生)
   - `iphone-homebar` → 底部 home indicator · **不实现**
   - `iphone-notch` → 凹槽 / Dynamic Island · **不实现**
3. **读 spec.md `§15 实现边界`** 段（每个 spec 必有此段 · 见 SPEC-§15-SNIPPET.md）
4. **如果 mockup 缺 `data-mockup-chrome` attr OR spec 缺 §15 段**：
   ❗ **立即停 · ask user**: "请确认 X 是 chrome 装饰还是实现内容"
   ❗ **不要凭推断实施** · 这是 chrome 误植 bug 的源头

### §2.11 自检 design-review（实施完成后 · commit 前 · 必做）

实施完成 commit **之前** · 主动派 `design-reviewer` agent 跑：

```bash
# 单页验证
pnpm e2e:mockup-diff -- --grep <page_id>
pnpm e2e:vrt-multi -- --grep <page_id>
# 派 design-reviewer agent (Claude Code Agent tool)
# subagent_type: design-reviewer · 输入 page_id
```

收到 verdict 处理：
- ✅ `PASS` → 可 commit
- ❌ `FAIL` → 进入修复循环 · 不交付（按 issues[].suggested_fix 修 · 重跑直到 PASS）
- ⚠️ `AMBIGUOUS` → ask user 决策 · 不假设 (可能 mockup/spec 缺 attr/§15)

### 禁止

- ❌ 不 grep `[data-mockup-chrome]` 直接出代码 = 跳步 = 自我阻断
- ❌ 不跑 `design-reviewer` 自检就 commit = 跳步 = 自我阻断
- ❌ 凭推断实施 chrome 元素 (statusbar / homebar / iPhone 边框 / notch) — 必须以 mockup `data-mockup-chrome` 为准
