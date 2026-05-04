## Skill routing

When the user's request matches an available skill, invoke it via the Skill tool. When in doubt, invoke the skill.

Key routing rules:
- Product ideas/brainstorming → invoke /office-hours
- Strategy/scope → invoke /plan-ceo-review
- Architecture → invoke /plan-eng-review
- Design system/plan review → invoke /design-consultation or /plan-design-review
- Full review pipeline → invoke /autoplan
- Bugs/errors → invoke /investigate
- QA/testing site behavior → invoke /qa or /qa-only
- Code review/diff check → invoke /review
- Visual polish → invoke /design-review
- Ship/deploy/PR → invoke /ship or /land-and-deploy
- Save progress → invoke /context-save
- Resume context → invoke /context-restore

---

## 设计实施铁律（前端 / QA 任务自动应用）

**实施 runbook**：`design/system/GUIDANCE.md` ← **任务开始前必读**

**权威优先级**（冲突时高优先级胜出）：
1. `design/system/STYLE-TRUTH.md` —— 设计真相（archive 19 张 mockup 反推）
2. `design/system/DESIGN.md` —— 设计宪法 v2.0（铁律 + token 三层 + mood 5 类）
3. `design/system/GUIDANCE.md` —— FE / QA Agent step-by-step 执行手册
4. `design/system/pages/{ID}.spec.md` —— 单页规格卡（数据 / 状态 / API / AC / testid）

**自动行为**：
- 接到"实现 / 修 / 写 / 画"某页面任务 → 立即跳 `GUIDANCE.md §2`，走 10 步流程（每步含 INPUT / DO / OUTPUT / VERIFY 四件套）
- 接到"测 / 验 / accept / e2e / a11y"任务 → 立即跳 `GUIDANCE.md §3`，走 3 轨（B mock 必过 / C 像素参考 / A E2E sprint 末）
- 任一 VERIFY 步骤的 grep / playwright / lint 命令未通过 → 停止后续步骤，转 `GUIDANCE.md §4` 五类 fail 场景排查
- 在代码里写 hex 色 / px 值前 → 先 grep `STYLE-TRUTH.md §2` 找 token；硬编码 = 自我阻断
- 接到 archive 已有的页面（除 P00 外的 19 张）→ 优先 `cp design/mockups/wrongbook/_archive/{N}.html` 作 1:1 参考，不要凭空重画
- 接到 archive 没有的页面（目前仅 P00 login）→ 跳 `GUIDANCE.md §4 场景 E` + `STYLE-TRUTH.md §6` 的缺失页指引

**禁止**：
- ❌ 不读 STYLE-TRUTH 直接出代码 / 出设计
- ❌ 用已废 v1.0 token：`--tkn-color-warm-*` / `--tkn-gradient-aurora` / `--tkn-gradient-focus-night` / `--tkn-gradient-result-warm` / `--tkn-shadow-warm-*` / `--tkn-color-aurora-*` —— 完整迁移表见 `GUIDANCE.md §4 场景 C`
- ❌ 用错值 hex：`#0071e3`（应 `#007AFF`）/ `#1d1d1f` / `#2C2A26`（应 `#1C1C1E`）/ `#FAF8F4` 暖米白（应 `#F2F2F7` iOS bg-light）
- ❌ 用旧三分法 `data-mood="cool|warm|celebrate"`（应改 v2.0 五类 `data-mood="A|B|C|D|E"`，分别对应 hero+overlap / pure-warm / dark-camera / celebrate-green / teal-observer）
- ❌ 跳过 `spec.§8 AC` 的 testid 验证就提 PR
- ❌ 跳过 `prefers-reduced-motion` a11y 兜底就提 PR
- ❌ 凭空生成 archive 已有页面 mockup（应直接复制 `_archive/*.html` 作参考）

**特殊页面快速索引**：
- `P00` 登录 — archive 缺失 · 按 `STYLE-TRUTH.md §6` 设计建议 · 微信按钮带 `data-iron-rule-1-exception="wechat-brand"`
- `P02` / `P15` 拍题相机 — Mood C 全屏 `#0B0F1A` 实色 + 黄色检测元素 + 模拟纸面 viewfinder
- `P08` 复习自评 — 三按钮带 `data-iron-rule-1-exception="self-grading"`（mastery-{forgot|partial|mastered} 色，不是 primary 蓝）
- `P09` 复习完成 — Mood D 绿渐变 + ConfettiBurst 仅在"今日全部完成"触发（铁律 3 庆祝有节制）

---

## 设计实施铁律 v2 · §2.0 边界识别 + §2.11 自检 design-review（H 机制 · MUST）

> **背景**：P-LANDING 实施暴露 FE Agent 把 mockup HTML 的 iPhone 边框/notch/status chrome 当成页面元素 · 导致 iPad/Desktop 视觉错位 · E2E 没 catch。F+H 机制治本：mockup HTML 加 `data-mockup-chrome` attr · CLAUDE.md 强制 AI 读边界 + 完成后自检。
>
> **完整方案**：`docs/DESIGN-AUDIT-SYSTEM-PLAN.md`

### §2.0 边界识别（在 GUIDANCE.md §2 10 步流程之前 · 必做）

任何"实现 / 修 / 写 / 画"页面任务 · **第 0 步必做**：

1. **打开 mockup HTML**: `design/mockups/wrongbook/_archive/{N}_{name}.html`
2. **grep `[data-mockup-chrome]`** 列出所有 chrome 元素：
   ```bash
   grep -nE 'data-mockup-chrome' design/mockups/wrongbook/_archive/{N}_{name}.html
   ```
   - `iphone-frame` → 整个 phone wrapper · 是装饰 · 不实现 (改 width:100% / min-height:100vh)
   - `iphone-statusbar` → 9:41 + 信号 + 电池 chrome · 不实现 (浏览器原生提供)
   - `iphone-homebar` → 底部 home indicator · 不实现
   - `iphone-notch` → 凹槽 / Dynamic Island · 不实现
3. **读 spec.md `§X 实现边界`** 段（Phase D2 起每个 spec 必有此段）
4. **如果 mockup 缺 `data-mockup-chrome` attr OR spec 缺 §X 段**：
   ❗ **立即停 · ask user**: "请确认 X 是 chrome 装饰还是实现内容"
   ❗ **不要凭推断实施** · 这正是 P-LANDING bug 的源头

### §2.11 自检 design-review（实施完成后 · commit 前 · 必做）

实施完成 commit **之前** · 主动派 `design-reviewer` agent 跑：

```bash
# 单页验证
pnpm e2e:mockup-diff -- --grep {page_id}
pnpm e2e:vrt-multi -- --grep {page_id}
# 派 design-reviewer agent
# 用 Agent 工具 · subagent_type: design-reviewer · 输入 page_id
```

收到 verdict 处理：
- ✅ `PASS` → 可 commit
- ❌ `FAIL` → 进入修复循环 · 不交付（按 issues[].suggested_fix 修 · 重跑直到 PASS）
- ⚠️ `AMBIGUOUS` → ask user 决策 · 不假设 (可能 mockup/spec 缺 attr/§X 段)

### 禁止 (v2 新增)

- ❌ 不 grep `[data-mockup-chrome]` 直接出代码 = 跳步 = 自我阻断
- ❌ 不跑 `design-reviewer` 自检就 commit = 跳步 = 自我阻断
- ❌ 凭推断实施 chrome 元素 (statusbar / homebar / iPhone 边框 / notch) — 必须以 mockup `data-mockup-chrome` 为准
