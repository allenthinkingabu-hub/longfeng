# 全自动 Design-Audit 多轮对抗系统 · 解决 "E2E PASS 但视觉不对齐"

## Context

H5 e2e 24 轮跑完 53/54 PASS · 但用户截图证实 LandingPage iPad Pro 上嵌套两层 iPhone mockup + 留白错乱 · iPhone 14 上同样的双重渲染 · **E2E 完全没 catch**。

### 三层根因

1. **实现层**：FE Agent 误读 `14_landing.html` 把 iPhone chrome (边框/notch) 当成页面元素 · `.phone` class 写死 393×852 + iPhone 边框
2. **测试体系**：B/C/A 三轨全部仅跑 iPhone 15 Pro 单 viewport · vrt 只与自己历史对比 (不与设计稿) · `GUIDANCE.md §7` 的 "1:1 archive 对齐" 流程从未自动化
3. **AI Agent 行为**：CLAUDE.md "设计实施铁律" 已要求看 mockup 但 Agent 跳步 · 无 enforcement

### 用户决定

- 全套 8 机制 (ABCDEFGH) 全做
- **必须全自动多轮** (类比 H5 24 轮对抗) · AI 主导 · 不靠 human 中转
- 一份完整方案

---

## 总体架构 (5 层 · 8 机制)

```
┌─────────────────────────────────────────────────────────────┐
│  Layer 5 · Multi-Round Orchestrator (Claude Code Agent)    │
│  design-audit-orchestrator → fan-out → page-fixer × N      │
│  最大 10 轮 · 失败收集 · 并行 worktree 修 · 重跑直到全绿   │
└─────────────────────────────────────────────────────────────┘
        ▲              ▲             ▲              ▲
        │              │             │              │
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

**关键洞察**：层次互补 · 不可单替
- 机械层 (A/B) catch 像素 / 响应式 (vision 漏)
- AI 评审 (C) catch 语义级 (像素漏)
- 流程门 (D/E) 防 AI Agent 跳步
- 治本 (F/G/H) 消除"设计稿语义歧义"根源

---

## 8 机制详细设计

### Layer 1 · 自动测试机制

#### A · 多 viewport VRT 扩展

**改**：`e2e/specs/vrt-baseline.spec.ts`

```typescript
const VIEWPORTS = [
  { name: 'iphone-15-pro', width: 393, height: 852 },     // 现有
  { name: 'ipad-pro-11', width: 834, height: 1194 },     // 新
  { name: 'ipad-pro-12', width: 1024, height: 1366 },    // 新
  { name: 'desktop-1440', width: 1440, height: 900 },    // 新
];

for (const vp of VIEWPORTS) {
  test(`${pageId} @ ${vp.name}`, async ({ page }) => {
    await page.setViewportSize({ width: vp.width, height: vp.height });
    await page.goto(pageRoute);
    await expect(page).toHaveScreenshot(`${pageId}-${vp.name}.png`, {
      maxDiffPixelRatio: 0.01,
    });
  });
}
```

**输出**：19 页 × 4 viewport = 76 baseline · 第一次跑生成 · 后续 vrt 自比。

#### B · Mockup HTML vs 实现页 像素 diff

**新建**：`e2e/specs/mockup-vs-impl.spec.ts`

```typescript
import { test } from '@playwright/test';
import pixelmatch from 'pixelmatch';
import { PNG } from 'pngjs';

const PAGES = [
  { id: 'P-LANDING', mockup: '14_landing.html', route: '/welcome' },
  { id: 'P-HOME', mockup: '01_home.html', route: '/' },
  // ... 19 页
];

for (const p of PAGES) {
  test(`${p.id} · mockup vs impl pixel diff`, async ({ page, context }) => {
    // 1. 截 mockup HTML (file://)
    await page.goto(`file://${MOCKUP_DIR}/${p.mockup}`);
    const mockupShot = await page.screenshot({ fullPage: true });

    // 2. 截实现页
    await page.goto(`http://localhost:5173${p.route}`);
    const implShot = await page.screenshot({ fullPage: true });

    // 3. pixelmatch 对比 · 容差 5% (因 React 渲染天然不同)
    const diff = pixelmatch(...);
    if (diff.ratio > 0.05) {
      await test.info().attach('mockup', { body: mockupShot, contentType: 'image/png' });
      await test.info().attach('impl', { body: implShot, contentType: 'image/png' });
      throw new Error(`${p.id} pixel diff ${(diff.ratio * 100).toFixed(1)}% > 5%`);
    }
  });
}
```

**容差 5%** 因 mockup 是静态 HTML · React hydration 后字体/动画稍异 · 主要 catch "整体布局走形"。

### Layer 2 · AI 评审机制

#### C · Design-Reviewer Vision Agent

**新建**：`.claude/agents/design-reviewer.md`

```markdown
---
name: design-reviewer
description: Vision-based design audit · compares impl screenshot with mockup HTML & baseline PNG · outputs structured diff report. Use proactively after any FE page implementation completes.
model: opus
tools: Read, Bash, WebFetch
---

你是 design QA 专家。输入：
- impl_screenshot: 实现页截图 (4 viewport 各 1 张)
- mockup_html: 高保真 HTML path (design/mockups/wrongbook/*.html)
- baseline_png: 设计师 baseline PNG
- spec_md: 页面 spec.md (含 §X 实现边界段)

执行：
1. Read 三方图 + spec
2. 检查 spec.md "§X 实现边界" 段 · 提取 chrome 边界声明
3. 对每个 viewport 输出 JSON:
   ```
   {
     "page_id": "P-LANDING",
     "viewport": "ipad-pro-12",
     "verdict": "FAIL",
     "issues": [
       {
         "severity": "critical",
         "category": "chrome-misread",
         "desc": "实现页面包含 iPhone 边框 chrome (border-radius:54px + notch)，但 spec.md §X 明确标注 'iPhone 框为 mockup 装饰，不应实现'",
         "suggested_fix": "删除 .phone class 的 border-radius/box-shadow/notch 定义，改为 width:100% height:100vh"
       },
       ...
     ]
   }
   ```
4. 如果 spec.md 缺 "§X 实现边界" 段 · verdict = "AMBIGUOUS" · 不报 fail · 提示设计师补 spec

PROHIBITED:
- 不修代码 (只评审)
- 不检查像素差异 (那是 B 轨的事 · 你看语义)
- 不评判颜色微差 (那是 token grep 的事)
```

**输出**：JSON 报告写到 `e2e/reports/design-review/{page_id}.json` · orchestrator 读后判 verdict。

### Layer 3 · 流程 enforcement

#### D · PR 自动三联截图

**新建**：`.github/workflows/design-pr-screenshots.yml`

```yaml
on: pull_request
jobs:
  screenshots:
    runs-on: macos-latest  # vrt 需 macOS 字体
    steps:
      - uses: actions/checkout@v4
      - run: pnpm install && pnpm dev &  # vite background
      - name: Capture screenshots (impl + mockup + baseline)
        run: pnpm tsx scripts/pr-screenshots.ts ${{ github.event.pull_request.number }}
      - name: Comment on PR
        uses: actions/github-script@v7
        with:
          script: |
            const fs = require('fs');
            const report = fs.readFileSync('e2e/reports/pr-screenshots.md', 'utf8');
            github.rest.issues.createComment({...report});
```

**`scripts/pr-screenshots.ts`** 检测 PR diff 中改了哪些 `pages/*` · 对每页输出 markdown：
```
## P-LANDING (impl vs mockup vs baseline)
| viewport | impl | mockup | baseline | verdict |
|---|---|---|---|---|
| iPhone 15 | ![](impl-iphone.png) | ![](mockup-iphone.png) | ![](baseline-iphone.png) | ⚠ vision: chrome misread |
| iPad Pro 12 | ... | ... | ... | ❌ pixel: 38% diff |
```

#### E · Pre-commit Hook

**新建**：`.husky/pre-commit` (or `.git/hooks/pre-commit`)

```bash
#!/bin/bash
CHANGED_PAGES=$(git diff --cached --name-only | grep -E "^frontend/apps/h5/src/pages/[A-Z]")
[ -z "$CHANGED_PAGES" ] && exit 0

echo "🎨 Design audit on changed pages..."
pnpm tsx scripts/design-precommit.ts "$CHANGED_PAGES"

# 模式: warning-only (不阻断 commit · 仅打印)
# PR-time 才硬阻断 (workflow 里设 required check)
```

`scripts/design-precommit.ts`:
1. 启 vite (复用已跑实例 · 否则 spawn)
2. 对每个 changed page 跑 A + B + C 简化版
3. 打印彩色 diff summary · exit 0 (不阻断)

#### G · 设计师 Sign-off (PR Label)

**新建**：`.github/workflows/design-sign-off.yml`

```yaml
on: pull_request
jobs:
  check:
    runs-on: ubuntu-latest
    steps:
      - run: |
          if [[ ! "${{ join(github.event.pull_request.labels.*.name, ',') }}" =~ "designer-approved" ]]; then
            echo "❌ Missing 'designer-approved' label - 需 PM/设计师 review 三联截图后打标"
            exit 1
          fi
```

**触发条件**：PR diff 含 `frontend/apps/h5/src/pages/[A-Z]*` (高保真页 · P00-P13/P-*)。普通 components 不需要。

**手动开门**：紧急修可用 `design-emergency-bypass` label override (留 audit 痕迹)。

### Layer 4 · 治本改造

#### F · Mockup HTML 加结构化 chrome 标注

**改**：`design/mockups/wrongbook/*.html` (19 张 · 一次性)

```html
<!-- BEFORE -->
<body>
  <div class="phone">
    <div class="notch"></div>
    <div class="page-content">
      <h1>真实页面</h1>
    </div>
  </div>
</body>

<!-- AFTER -->
<body>
  <div class="phone" data-mockup-chrome="iphone-frame">
    <div class="notch" data-mockup-chrome="iphone-notch"></div>
    <div class="page-content" data-page-content data-implement="true">
      <h1>真实页面</h1>
    </div>
  </div>
</body>
```

`scripts/B-mockup-vs-impl.spec.ts` 截图时只截 `[data-page-content]` 内部 (跳过 chrome) · 实现端截全屏 · 这样 pixel diff 就有意义。

**spec.md template 加 §X**:

```markdown
## §X 实现边界（Implementation Boundaries · 必读 · MUST）

| 元素 | 是否实现 | 备注 |
|---|---|---|
| iPhone 边框 / notch / 状态栏装饰 | ❌ 不实现 | mockup chrome only |
| 内容区 (`<div data-page-content>`) | ✅ 实现 | 100% viewport |
| 顶部 statusbar 时间 / 信号 / 电池 | ❌ 不实现 | 浏览器原生 status |
| 底部 home indicator | ❌ 不实现 | iOS chrome |
```

`design/system/pages/_template.spec.md` 加上此段 · 跑 `scripts/migrate-spec-add-x-section.ts` 一次性补全 19 个 spec。

#### H · CLAUDE.md 加 AI Agent 反问行为

**改**：`/Users/allenwang/build/longfeng-wrongbook/CLAUDE.md` (project) 设计实施铁律段加：

```markdown
## 设计实施铁律 v2

新增第 0 步 (在 §2 10 步流程之前):

### §2.0 边界识别（必做）
1. 打开 mockup HTML · grep `[data-mockup-chrome]` 列出所有 chrome 元素
2. grep `[data-page-content]` 找到实现边界
3. 读 spec.md §X 实现边界表
4. 如果 mockup 缺 data-attr OR spec 缺 §X：
   ❗ 立即停 · ask user "请确认 X 是 chrome 装饰还是实现内容"
   不要凭推断实施

新增第 11 步 (10 步后):
### §2.11 自检 design-review
- 实施完成后 · 主动派 design-reviewer agent 跑 (即使 user 没要求)
- 收到 verdict=FAIL → 进入修复循环 · 不交付
```

并在 `.claude/agents/page-implementer.md` 强制 prompt 加：
> 实施前必读 CLAUDE.md §2.0 · 缺 data-attr 必反问 · 不要凭推断生成代码。

### Layer 5 · 多轮 Orchestrator (核心整合层)

#### 调度逻辑

**新建**：`.claude/agents/design-audit-orchestrator.md`

```markdown
---
name: design-audit-orchestrator
description: Multi-round design audit orchestrator. Runs A+B+C tests, dispatches page-fixer subagents in parallel worktrees, repeats until all green or max 10 rounds. Use proactively after any major FE work.
model: sonnet
tools: Bash, Read, Write, Agent
---

输入: page_ids[] (e.g., ["P-LANDING", "P-HOME"]) · 默认全 19 页

Round Loop (max 10):

1. **Test phase**:
   - 跑 A: pnpm e2e:vrt-multi-viewport
   - 跑 B: pnpm e2e:mockup-vs-impl
   - 派 design-reviewer agent (C) 给所有 fail 页生成 vision report
   - 收集 fail 列表 → /tmp/audit-round-{N}.json

2. **Decision phase**:
   - 如果 fail 列表空 → 报告全绿 · 终止
   - 如果 round = 10 → 报告超限 · 列剩余 fail 给 human
   - 如果 fail 列表与上轮完全一致 → 报告"卡死" · 列同样 fail 给 human (防死循环)
   - 否则 → fan-out

3. **Fix phase (并行)**:
   - 对每个 fail page · spawn 1 个 page-fixer sub-agent in worktree
   - 输入: design-reviewer 给的 issues JSON + 原 page src path
   - sub-agent 任务: 按 issues.suggested_fix 改代码 + 自跑 A/B/C 验证 + commit
   - 主 orchestrator 等所有 worktree 完成后 merge changes 到主仓

4. **回到 step 1** · round++

终止条件:
- ✅ All green (理想)
- ⚠ Max 10 rounds reached
- ⚠ 卡死 (失败列表 2 轮无变化)
- ⚠ 单个 fixer agent 报"无法修" (e.g., spec 歧义需 human)
```

#### Page-fixer sub-agent

**新建**：`.claude/agents/page-fixer.md`

```markdown
---
name: page-fixer
description: Fix a single page based on design-review issues. Run in isolated worktree. Output: commit hash + verification result.
model: sonnet
tools: Read, Edit, Write, Bash
---

输入:
- page_id (e.g., P-LANDING)
- issues_json (from design-reviewer · 含 suggested_fix)
- src_paths (e.g., frontend/apps/h5/src/pages/Landing/*)

执行:
1. Read 全部 src_paths + spec.md
2. 对每个 issue.suggested_fix 应用代码修改
3. 跑 A+B+C 验证本页:
   - playwright vrt --grep P-LANDING
   - mockup-vs-impl --page P-LANDING
   - design-reviewer (单页 mode)
4. 全绿 → commit + 输出 hash
5. 仍有 fail → 输出 "stuck" + 详细 reason

PROHIBITED:
- 改 spec.md (不是 fixer 的事)
- 改 design/* (不是 fixer 的事)
- 改其他页 (隔离原则)
```

---

## 关键文件清单

### 新建 (10 个)

| Path | 用途 | Layer |
|---|---|---|
| `e2e/specs/vrt-multi-viewport.spec.ts` | A · 4 viewport vrt | 1 |
| `e2e/specs/mockup-vs-impl.spec.ts` | B · pixel diff | 1 |
| `.claude/agents/design-reviewer.md` | C · vision audit | 2 |
| `.claude/agents/page-fixer.md` | 5 · 单页修复 | 5 |
| `.claude/agents/design-audit-orchestrator.md` | 5 · 多轮调度 | 5 |
| `.github/workflows/design-pr-screenshots.yml` | D · CI 三联截图 | 3 |
| `.github/workflows/design-sign-off.yml` | G · sign-off check | 3 |
| `.husky/pre-commit` (or `scripts/install-hooks.sh`) | E · pre-commit | 3 |
| `scripts/pr-screenshots.ts` | D · 截图 + comment | 3 |
| `scripts/design-precommit.ts` | E · pre-commit 跑测 | 3 |

### 修改 (4 类)

| Path | 改什么 | Layer |
|---|---|---|
| `design/mockups/wrongbook/*.html` (19 张) | F · 加 `data-mockup-chrome` / `data-page-content` | 4 |
| `design/system/pages/*.spec.md` (19 份) | F · 加 §X 实现边界段 | 4 |
| `CLAUDE.md` (project + global) | H · 设计实施铁律 v2 加 §2.0/§2.11 | 4 |
| `e2e/package.json` | A/B 加 scripts: `e2e:vrt-multi`, `e2e:mockup-diff`, `e2e:design-audit-all` | 1 |

---

## 落地路线图 (4 phase · 渐进交付 · 9-10d)

### Phase 1 · 基础设施 (Day 1-2)

**目标**：让 A + B 单跑能用 · 无 orchestrator

- 改 `vrt-baseline.spec.ts` 加 4 viewport · 跑生成 76 baseline
- 写 `mockup-vs-impl.spec.ts` 用 pixelmatch
- F 改造**只先做 P-LANDING 一张** mockup HTML 加 data-attr (验证机制)
- 验证：`pnpm e2e:vrt-multi` + `pnpm e2e:mockup-diff` 跑通 · P-LANDING 报当前 bug

### Phase 2 · AI 评审接入 (Day 3-4)

**目标**：design-reviewer agent 工作

- 写 `.claude/agents/design-reviewer.md`
- 手动跑 design-reviewer P-LANDING · 看是否能 catch "iPhone chrome 误植"
- 写 spec.md §X template + migrate 19 个 spec (脚本批量加段)
- 完成 19 张 mockup HTML 的 F 改造 (剩 18 张)
- 验证：design-reviewer 能输出准确 issue JSON · 能引导后续 fixer

### Phase 3 · Orchestrator 多轮 (Day 5-6)

**目标**：跑通端到端多轮闭环

- 写 `design-audit-orchestrator` + `page-fixer` agents
- 用 P-LANDING 跑第一次完整 round (期望 round 1 fail · round 2 修后绿)
- 加 worktree 隔离 + 并行 fan-out
- 验证：手动 fire orchestrator · 跑完 19 页 · 报告全绿 (或列卡死页)

### Phase 4 · 流程门 + CI (Day 7-9)

**目标**：D + E + G + H 全部上 · 形成闭环

- 写 `.github/workflows/design-pr-screenshots.yml` + `design-sign-off.yml`
- 写 `.husky/pre-commit` + `scripts/design-precommit.ts`
- 改 CLAUDE.md (project + global) §2.0 + §2.11
- 跑一个真实 PR 验证：开 PR → CI 跑 D → comment 三联 → label gate → merge
- 验证：故意提一个"破坏 P-LANDING"的 PR · 看能否被 4 重防护 (pre-commit warn / vrt fail / vision report / sign-off block) 全部拦截

### Phase 5 (可选 · Day 10) · CI 集成 orchestrator

**目标**：多轮在 CI 自动跑

- GitHub Actions 上自动 fire orchestrator on `frontend/apps/h5/**` 改动
- 需要 GH Actions self-hosted macOS runner (vision agent 调 Claude API · GH Secret 注 ANTHROPIC_API_KEY)
- 留 P1 · 因为 macOS runner 自建成本高 · 可先本地 sprint 末跑

---

## 验证方法

| Phase | 验证命令 | 期望 |
|---|---|---|
| 1 | `pnpm e2e:vrt-multi --grep P-LANDING` | 4 viewport baseline 生成 · iPad/Desktop 截图肉眼可见嵌套 bug |
| 1 | `pnpm e2e:mockup-diff --grep P-LANDING` | 报 pixel diff > 5% (因 chrome 误植) |
| 2 | `claude agents fire design-reviewer --page P-LANDING` | JSON 含 `category=chrome-misread` · `severity=critical` |
| 3 | `claude agents fire design-audit-orchestrator --pages P-LANDING` | round 1 fail · round 2 fixer 删 .phone chrome · round 3 全绿 |
| 4 | 提 demo PR 改 P-LANDING 引入新 bug | CI 三连阻断: vrt fail · vision FAIL · sign-off block |
| 终极 | 在 main 上 fire orchestrator on 19 页 | 全绿 (或剩 ≤ 2 卡死页 humans take over) |

---

## 关键 Design Decisions (推荐 · 可调)

| 决策 | 推荐 | 替代 |
|---|---|---|
| Vision model (C) | Sonnet 4.6 (cheap fast) · 关键页 Opus 4.7 | 全 Opus (慢且贵) |
| Pre-commit 阻断模式 | warning-only (不卡开发) · PR-time 硬阻断 | 全部硬阻断 (开发反感) |
| 多轮上限 | **10 轮** (对齐 H5 24 轮经验值打 0.4 · 因机制更准) | 5/15 |
| Fixer 并行度 | worktree 并行 · 不限上限 | 顺序 (慢但稳) |
| Pixel diff 容差 | **5%** (允许 React vs static HTML 差异) | 1% (太严必假阳) / 10% (太松漏 bug) |
| Orchestrator 卡死检测 | 失败列表 2 轮无变化即停 | 永不停 (危险) |
| Sign-off label | `designer-approved` 必须 · `design-emergency-bypass` 紧急 | 设计师 review comment 文字检测 (脆弱) |
| Mockup chrome 标注格式 | HTML `data-mockup-chrome` (DOM 可读) | CSS class (vision agent 看不到) / HTML 注释 (DOM 不暴露) |

---

## 风险与兜底

| 风险 | 严重 | 兜底 |
|---|---|---|
| F 改 19 张 mockup 工作量大 (各页 chrome 各异) | 高 | Phase 1 只先做 P-LANDING 验证机制 · Phase 2 批量按 archive 模式批改 (大部分都是 .phone wrapper) |
| Vision agent (C) 误判 (vision LLM 不准) | 中 | A/B 互补 · 三方 verdict 不一致时 escalate human |
| Orchestrator 死循环 (fixer 改一个 break 另一个) | 中 | "失败列表 2 轮无变化"检测 · 卡死立停 |
| Pre-commit hook 拖慢开发 | 中 | warning-only mode · 全套 audit 仅 PR-time 跑 |
| Sign-off 卡 PR (设计师不 available) | 高 | `design-emergency-bypass` label + audit log + 7d 内必须补 review |
| GH Actions macOS runner 成本 | 中 | Phase 5 可选 · 不强制 · 本地 sprint 末跑也可 |
| Claude API 调用费用 (vision agent) | 中 | Sonnet 4.6 默认 · 仅高保真页用 Opus · monthly cap budget |
| 旧 baseline 与新机制冲突 (76 baseline 第一次都报 fail) | 高 | Phase 1 跑 `--update-snapshots` 一次性接受现状 · 之后才严格 vrt |
| 多轮跑 1h+ 消耗 context | 中 | orchestrator 是 sonnet · 跑大量 tool 但单 context 不爆 · 必要时 sub-agent compact 自决 |

---

## 与现有体系整合

- **保留 B/C/A 三轨** (mock-b / vrt / e2e-a) · 不破坏
- **加第 4 轨 design-d** (Design audit) · 独立 npm script: `pnpm e2e:design-d`
- **现有 vrt-baseline.spec.ts 升级** 而非删除 (加 viewport)
- **CLAUDE.md 加版本号 v2** · 旧规则保留 · 新增不删

---

## Out of scope (本 plan 不涉及 · 留 P1)

- BE 业务接口 ( /api/landing/samples 等 ) 实现 — 那是 BE 任务 · 与本 plan 视觉对齐无关
- 小程序 (miniapp) 设计审计 — 本 plan 仅 H5 · miniapp 留独立 plan
- 设计 token 自动化 lint (硬编码 hex 检测) — 已有 `scripts/verify-tokens.sh` · 本 plan 不重做
- 暗黑模式 / RTL / dynamic type 等扩展 viewport — Phase 1 仅 4 viewport · 后续可加
