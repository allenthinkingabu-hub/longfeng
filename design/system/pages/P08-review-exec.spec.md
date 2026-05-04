---
page_id: P08
name: 复习执行
name_en: Review Execute
route_h5: /review/exec/:nodeId
route_miniprogram: pages/review/exec
deeplink: wb://review/exec/:nodeId
auth_state: authenticated
persona:
  - P1-K12
scenarios:
  - SC-02
  - SC-03
  - SC-04
mockup_canonical: design/mockups/wrongbook/08_review_exec.html
mockup_version: v1
last_reviewed: 2026-05-02
status: spec-draft
sprint: S1
---

# P08 · 复习执行

> 与 DESIGN.md §5.1 第 4 张页面对齐：白底（warm-elevated）无任何渐变 hero · 顶部 4px 蓝色线状进度条 + "第 2/8 题" + × · 题干 hero 卡复用 P04 风格 · 揭示按钮 800ms checkmark 滑入 · 6 节点 MemoryCurve progress · 底部 3 等宽 mastery 按钮（**铁律 1 例外 self-grading**）。

---

## §1 页面目的（why · 1 句话）

让学生在不看答案的前提下重做一遍 → 自评掌握度 → 驱动节点推进。

---

## §2 ASCII 布局图

```
┌─────────────────────────────────────┐
│  [p08-progress-bar · 4px 蓝色线]    │ ← warm-elevated 白底
│  [p08-topbar · "第 2/8 题"  ×]      │
│  [p08-meta-chips · T2 · 数学 · 难度3]
│                                     │
│  [p08-question-hero · 题干卡白底]   │
│   "已知函数 f(x)=x²-4x..."          │
│                                     │
│  [p08-answer-area · Tab 三模式]     │
│   ◯ 手写  ◯ 键盘  ◯ 公式            │
│   ┌──────────────────────────┐    │
│   │  作答区（手写 canvas）   │    │
│   └──────────────────────────┘    │
│                                     │
│  [p08-reveal-btn · 蓝 pill]         │
│   "查看答案与解法"                  │
│   --(点击后)--                     │
│  [p08-revealed · 绿色高亮 + 3 步]  │
│   ✓ 答案：x = 2 或 -2               │
│   ▸ 步骤 1 移项                     │
│   ▸ 步骤 2 配方                     │
│   ▸ 步骤 3 求根                     │
│                                     │
│  [memory-curve · variant=progress] │
│   T1 done → T2 now (脉冲) → T3-6 灰│
│                                     │
│  [p08-grade-buttons · 3 等宽]      │  ← iron-rule-1-exception="self-grading"
│   ┌──未掌握(forgot)─┐ ┌──部分(partial)─┐ ┌──已掌握(mastered)─┐
│   │  ✗ 未掌握      │ │  ◐ 部分      │ │  ✓ 已掌握        │
│   └────────────────┘ └──────────────┘ └──────────────────┘
└─────────────────────────────────────┘
                     · 整页 mood=cool（白底沉静）
```

---

## §3 Block 清单（每块独立可实现单元）

| Block ID | 名称 | Section | Mood | Component | testid root | Tokens 用到 |
|---|---|---|---|---|---|---|
| `B1` | 顶部 4px 线状蓝色进度条 | hero | cool | L0.Progress(thin) | `p08-progress-bar` | `--tkn-color-primary-DEFAULT` · `--tkn-color-sep` |
| `B2` | "第 2/8 题" + 关闭 × | hero | cool | L0.NavBar | `p08-topbar` | `--tkn-color-text-primary` |
| `B3` | 题元 chips（T2 · 第 2 次 · 学科 · 难度） | hero | cool | M5.SubjectChip + L0.Tag × N | `p08-meta-chips` | `--tkn-subject-math` · `--tkn-color-bg-light` |
| `B4` | 题干 Hero 卡（复用 P04 QuestionCard） | hero | cool | L0.Card | `p08-question-hero` | `--tkn-color-card` · `--tkn-shadow-card-deep` |
| `B5` | 作答区 + 模式 Tab（手写 / 键盘 / 公式） | info | cool | L0.TabBar + Custom InputArea | `p08-answer-area` | `--tkn-color-bg-light` · `--tkn-radius-md` |
| `B6` | 揭示按钮（蓝 pill） + 揭示后内容（3 步解法 + 800ms checkmark-pop） | info | cool | L0.Button + L0.Card(reveal) | `p08-reveal` | `--tkn-color-primary-DEFAULT` · `--tkn-color-mastery-mastered` · `--tkn-motion-celebrate-checkmark` |
| `B7` | 6 节点 MemoryCurve progress（揭示后展开） | info | cool | M8.MemoryCurve(variant=progress) | `memory-curve` | `--tkn-color-encouragement-DEFAULT` · `--tkn-color-mastery-mastered` |
| `B8` | 底部 3 等宽 mastery 按钮（**self-grading 例外**） | info | cool | L0.Button × 3 | `p08-grade-buttons` | `--tkn-color-mastery-forgot/partial/mastered` |

> **fe-preflight 用法**：把 mockup HTML 切成这些块；fe-builder 按块逐个实现并跑 lint。
> **铁律 1 例外（self-grading）**：B8 三个按钮使用 `--tkn-color-mastery-{forgot|partial|mastered}` 而非 primary blue。HTML 必须 `data-iron-rule-1-exception="self-grading"`。
> **铁律 3 注意**：B6 揭示动画使用 `checkmark-pop` 800ms（白名单内）。

---

## §4 数据契约（page-level interface）

```typescript
interface ExecPageProps {
  sessionId: string;
  cursor: number;                  // 当前第几题（1-based）
  total: number;
  node: {
    nid: string;
    tLevel: 'T0' | 'T1' | 'T2' | 'T3' | 'T4' | 'T5' | 'T6';
    openedAt: number;              // unix ms · open 接口返回
  };
  question: {
    qid: string;
    subject: 'math' | 'physics' | 'chemistry' | 'english';
    stem: string;
    correctAnswer: string;
    steps: Array<{ idx: number; title: string; detail?: string }>;
    knowledgePoints: Array<{ id: string; name: string }>;
    difficulty: 1 | 2 | 3 | 4 | 5;
    reviewCount: number;            // 第 N 次复习
  };
  plannedNodes: Array<{ tLevel: string; status: 'done' | 'current' | 'future'; dueAt?: string }>;  // 长度 = 6
  answerDraft?: string;
  revealed: boolean;
}

interface GradeReq {
  grade: 'MASTERED' | 'PARTIAL' | 'FORGOT';
  timeSpentMs: number;
  answerText?: string;
}

interface GradeResp {
  nid: string;
  nextNodeId?: string;              // session 还有下一题时返回
  navigateTo: 'P09';
}
```

> 类型不一致直接 fail。

---

## §5 API 触点

| Method | Path | 用途 | P95 预算 | 失败降级 |
|---|---|---|---|---|
| POST | `/api/review/nodes/{nid}/open` | 记录开始时间，返回完整 question | 400ms | 重试 1 次 |
| POST | `/api/review/nodes/{nid}/reveal` | 记录揭示时间 + 等待时长 | 200ms | 仅埋点失败，不阻塞 UI |
| POST | `/api/review/nodes/{nid}/grade` | 提交自评 → 推进 / 重排 / 维持 | 500ms | 失败保留答题状态，红 toast 重试 |

---

## §6 状态机

| State | 触发 | UI 表现 |
|---|---|---|
| `READING` | 进入页面 + open 200 | 题干渲染、作答区可输入、揭示按钮可点 |
| `ANSWERING` | 学生输入 | 揭示按钮保持可见 |
| `REVEALED` | 点击揭示 → reveal 200 | 揭示卡 800ms checkmark-pop 入场 + 3 步解法滑入 + MemoryCurve 展开；mastery 三按钮启用 |
| `GRADED` | 三按钮其一点击 → grade 200 | 200ms 淡出 → P09 |
| `EXIT_CONFIRM` | 点击 × 且未自评 | 弹 Sheet "本次复习尚未自评，退出将保留在原计划"[取消/退出] |
| `EXIT_SKIP` | EXIT_CONFIRM 确认退出 | 埋点 `wb_exec_skip`，节点保持原计划 |

---

## §7 跳转图

```
[入口]
  P07 全部开始 ──┐
  P06 立即复习  ─┤──→ P08
  推送深链      ─┘
        │
        ├──[B6 揭示]────────→ REVEALED 同页
        ├──[B8 mastery 按钮]──→ P09 (nid · grade)
        ├──[B2 ×]───────────→ EXIT_CONFIRM Sheet
        │                      └→ 退出 → P07/P-HOME（来源决定）
        └──[grade fail]─────→ 红 toast 重试
```

---

## §8 AC 覆盖表（与 business-analysis.yml 双向锚定）

| AC ID | 验收点 | 涉及 Block | testid 验证点 |
|---|---|---|---|
| `AC-P08-001` | 顶部进度条颜色为 primary blue（4px 高），文本显示 "第 2/8 题" | B1, B2 | `p08-progress-bar` `background-color` 解析 = `#007AFF` · `p08-topbar-cursor` 文本含 "2/8" |
| `AC-P08-002` (US-05) | 三个 mastery 按钮颜色严格对应 forgot/partial/mastered | B8 | `p08-grade-buttons-forgot` `background` 解析 = `#C0392B` · `-partial` = `#E8741C` · `-mastered` = `#34A853` |
| `AC-P08-003` | mastery 按钮容器有 `data-iron-rule-1-exception="self-grading"` 标注 | B8 | `p08-grade-buttons` 元素 `data-iron-rule-1-exception="self-grading"` |
| `AC-P08-004` | 揭示前看不到答案 / 解法（DOM 不渲染或 hidden） | B6 | `p08-reveal-content` 不存在或 `aria-hidden="true"` |
| `AC-P08-005` | 点击揭示后 800ms checkmark-pop 入场 + 答案与 3 步可见 | B6 | `p08-reveal-checkmark` 进入后 `data-status="popped"` 内含 step-1/2/3 |
| `AC-P08-006` (US-05) | 选 FORGOT 调用 `/grade` 后取消后续节点从 now 重排（业务侧效，前端只需正确传值） | B8 | `wb_exec_grade{grade=FORGOT}` 埋点上报 |
| `AC-P08-007` | grade 成功后 ≤ 500ms 跳 P09，URL 携带 nid + grade | B8 | URL 跳到 `/review/done/:nid?grade=...` |
| `AC-P08-008` | 已揭示后 mastery 按钮中 "已掌握" 不可再选（业务规则：只允许 partial/forgot） | B8 | `p08-grade-buttons-mastered` `disabled="true"` 当 `revealed && wasShown` |
| `[AI 推测] AC-P08-009` | × 按钮在未自评时弹出二次确认 Sheet | B2 | `p08-exit-confirm-sheet` 渲染 |
| `[AI 推测] AC-P08-010` | MemoryCurve progress 当前节点 T2 为脉冲态 | B7 | `memory-curve-node-T2` `data-status="current"` |

> **fe-accept-mock 用法**：用这张表逐条断言；缺 testid = 验收 fail。

---

## §9 异常路径

| 触发 | UI | 系统行为 |
|---|---|---|
| `/open` 失败 | 题干区骨架屏 + "加载失败 重试" | 重试 1 次 |
| `/grade` 失败 | 按钮 loading 失败后红 toast + 保留答题状态 | 用户手动重试 |
| 揭示前强制关闭 | 弹 Sheet 二次确认 | 退出保留 SCHEDULED 状态 |
| 网络断开 | 顶部红条 "网络异常，作答已本地缓存" | 自动恢复后续传 |

---

## §10 埋点事件

| 事件名 | 触发 Block | 必带属性 |
|---|---|---|
| `wb_exec_open` | 进入页面 | `nid`, `T`, `cursor`, `total` |
| `wb_exec_reveal` | B6 揭示 | `nid`, `waitMs` |
| `wb_exec_grade` | B8 | `nid`, `grade`, `totalMs`, `T` |
| `wb_exec_skip` | EXIT_SKIP | `nid`, `progress` |
| `wb_exec_exit_confirm` | EXIT_CONFIRM 弹出 | `nid`, `progress` |

---

## §11 性能预算

- TTI ≤ 600ms（从 P07 跳过来）
- LCP ≤ 800ms（题干 hero）
- CLS < 0.05（揭示动画在固定区域内展开）
- API P95 / open ≤ 400ms · grade ≤ 500ms
- 揭示动画总时长 800ms（含 checkmark-pop + 滑入）

---

## §12 A11y

- Landmarks: `<header role="banner">` (B1+B2), `<main role="main">` (B3-B7), `<footer role="contentinfo">` (B8)
- 焦点顺序: B2 close → B5 答题区 → B6 揭示 → (揭示后) B7 → B8 forgot → partial → mastered
- 屏幕阅读器优先级: B1 进度条 `aria-valuenow=2 aria-valuemax=8`；B6 揭示按钮 `aria-expanded` 切换；B8 三按钮 `<div role="radiogroup" aria-label="自评掌握度">`
- `prefers-reduced-motion`: 关闭 checkmark-pop（直接显示揭示内容），关闭 MemoryCurve current 节点脉冲

---

## §13 Mockup 锚定

- **权威**: `design/mockups/wrongbook/08_review_exec.html` (v1)
- **历史变体**: 无
- **截图**: `design/system/screenshots/P08-v1-light.png`（fe-preflight 自动生成）
- **反向锚定**: mockup HTML `<head>` 中含 `<meta name="design-spec" content="P08-review-exec.spec.md@v1">`

---

## §14 Tokens 清单（grep 校验用）

```
L1 (Apple base):
  --tkn-color-primary-DEFAULT
  --tkn-color-white
  --tkn-color-system-danger-DEFAULT
  --tkn-font-display
  --tkn-font-text
  --tkn-type-display-hero
  --tkn-type-card-title
  --tkn-type-body
  --tkn-type-body-emphasis
  --tkn-type-caption
  --tkn-type-caption-bold
  --tkn-spacing-xs
  --tkn-spacing-sm
  --tkn-spacing-12
  --tkn-spacing-md
  --tkn-spacing-lg
  --tkn-spacing-xl
  --tkn-radius-sm
  --tkn-radius-md
  --tkn-radius-lg
  --tkn-radius-pill
  --tkn-shadow-focus
  --tkn-motion-dur-base
  --tkn-motion-ease-standard

L2 (warmth · 仅 mood=warm/celebrate 区段使用):
  --tkn-color-bg-light
  --tkn-color-card
  --tkn-color-bg-light
  --tkn-color-text-primary
  --tkn-color-text-secondary
  --tkn-color-sep
  --tkn-color-encouragement-DEFAULT     (MemoryCurve current 节点脉冲色)
  --tkn-shadow-card-deep

L3 (celebration · 仅庆祝白名单时刻):
  --tkn-color-mastery-forgot           (B8 未掌握按钮 · self-grading 例外)
  --tkn-color-mastery-partial          (B8 部分按钮 · self-grading 例外)
  --tkn-color-mastery-mastered         (B8 已掌握按钮 + B6 揭示绿色高亮)
  --tkn-motion-celebrate-checkmark     (B6 揭示 800ms 入场)

EXCEPTION (subject):
  --tkn-subject-math
  --tkn-subject-physics
  --tkn-subject-chemistry
  --tkn-subject-english

IRON RULE EXCEPTION:
  iron-rule-1 / self-grading       → B8 容器 data-iron-rule-1-exception="self-grading"
```

> 本页 mood=cool（白底沉静风），不使用 hero 渐变；mastery 三色仅出现于 B8 自评按钮（标注 self-grading 例外）。

---

## §15 实现边界（Implementation Boundaries · F 机制 · MUST）

> **背景**：mockup HTML 含 iPhone chrome 装饰（边框 / 状态栏 / home indicator / notch）· 这些是 mockup 展示用 · **不是**真实页面应实现的元素。
>
> **完整方案**：`docs/DESIGN-AUDIT-SYSTEM-PLAN.md` · F 机制 + H 机制 + design-reviewer agent

### 实施前检查（per CLAUDE.md §2.0 · 强制）

```bash
grep -nE 'data-mockup-chrome' design/mockups/wrongbook/_archive/<file>.html
```

### Chrome 边界表（通用 · 各页 mockup 实际含哪些以 `data-mockup-chrome` attr 为准）

| 元素 | 实现否 | 备注 |
|---|---|---|
| `[data-mockup-chrome="iphone-frame"]` (phone wrapper · 黑色边框 + radius:54px + box-shadow inset) | ❌ 不实现 | mockup 装饰 · 改 `width:100% min-height:100vh` |
| `[data-mockup-chrome="iphone-statusbar"]` (9:41 + 信号 + wifi + 电池) | ❌ 不实现 | 浏览器/系统原生提供 |
| `[data-mockup-chrome="iphone-homebar"]` (底部 home indicator 横条) | ❌ 不实现 | iOS chrome |
| `[data-mockup-chrome="iphone-notch"]` (顶部凹槽 / Dynamic Island) | ❌ 不实现 | iOS chrome |
| 其他（hero / nav / cta / list / cards / 等所有未标 chrome 的元素） | ✅ 实现 | 真实页面内容 · 100% viewport 自适应 |

### 自检（per CLAUDE.md §2.11 · 实施完 commit 前必做）

```bash
pnpm e2e:mockup-diff -- --grep <page_id>      # B 机制 · 容差 5%
pnpm e2e:vrt-multi   -- --grep <page_id>      # A 机制 · 4 viewport baseline
# 派 design-reviewer agent (subagent_type: design-reviewer · 输入 page_id)
```

verdict 处理：
- ✅ `PASS` → 可 commit
- ❌ `FAIL` → 修复循环 · 不交付（按 issues[].suggested_fix 改 · 重跑直到 PASS）
- ⚠️ `AMBIGUOUS` → ask user 决策 · 不假设
