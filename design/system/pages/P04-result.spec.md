---
page_id: P04
name: AI 分析结果
name_en: Result
route_h5: /question/:qid/result
route_miniprogram: pages/camera/result
deeplink: wb://result/:qid
auth_state: authenticated
persona:
  - P1-K12
scenarios:
  - SC-01
mockup_canonical: design/mockups/wrongbook/04_result.html
mockup_version: v1
last_reviewed: 2026-05-02
status: spec-draft
sprint: S1
---

# P04 · AI 分析结果

> 与 DESIGN.md §5.1 第 3 张页面对齐：从 P03 暗底切到 `var(--tkn-color-bg-light) #F2F2F7` 米黄米白（情感曲线最关键转折）· Hero 题干卡白底 · 错解/正解双列 1px 内描边 · 错因 4px 红条 · 3 步解法 · 6 节点 MemoryCurve preview · 蓝色保存 CTA。

---

## §1 页面目的（why · 1 句话）

让学生在 30 秒内看懂"错在哪 / 对的是什么 / 为什么 / 下次怎么做"，并把题保存到错题本。

---

## §2 ASCII 布局图

```
┌─────────────────────────────────────┐
│  [navbar · "AI 分析结果"  ×]        │
├─────────────────────────────────────┤   ← mood=warm 切换
│  [p04-question-hero · 白底]         │
│   缩略图(80×80) | 题干 + 公式 chip  │
│                                     │
│  [p04-answers-row · 双列卡]         │
│   ┌──错解(forgot 1px)─┐ ┌─正解(mastered 1px)─┐
│   │ ✗ 我的答案: x=2  │ │ ✓ 正解: x=2 或 -2 │
│   └──────────────────┘ └────────────────────┘
│                                     │
│  [p04-reason-card · 左 4px 红条]   │
│   错因：忽略平方根的负值情况...     │
│                                     │
│  [p04-solution-stepper · 3 步]      │
│   1 ▸ 移项变形                      │
│   2 ▸ 配方                          │
│   3 ▸ 求根                          │
│                                     │
│  [p04-meta-chips · 学科/KP/难度]   │
│   [数学] [二次函数] [一元二次] ★★☆ │
│                                     │
│  [memory-curve · variant=preview]  │
│   T1—T2—T3—T4—T5—T6 全灰未来态     │
│   "AI 已为你排好 6 次复习"          │
│                                     │
│  [p04-save-cta · 蓝色全宽 pill]    │
│   "保存到错题本"                    │
└─────────────────────────────────────┘
                       · 整页 mood=warm
```

---

## §3 Block 清单（每块独立可实现单元）

| Block ID | 名称 | Section | Mood | Component | testid root | Tokens 用到 |
|---|---|---|---|---|---|---|
| `B1` | 顶部导航条 | hero | warm | L0.NavBar(warm) | `p04-navbar` | `--tkn-color-text-primary` |
| `B2` | 题干 Hero 卡（白底 + 缩略图 + 公式 chip） | hero | warm | L0.Card | `p04-question-hero` | `--tkn-color-card` · `--tkn-shadow-card-deep` · `--tkn-radius-lg` |
| `B3` | 错解 / 正解双列卡 | info | warm | L0.Card × 2 | `p04-answers-row` | `--tkn-color-mastery-forgot` · `--tkn-color-mastery-mastered` (1px inset border 仅) |
| `B4` | 错因区（左 4px 红条 + 文本） | info | warm | L0.Card | `p04-reason-card` | `--tkn-color-mastery-forgot` (4px left bar 仅) · `--tkn-color-text-primary` |
| `B5` | 3 步解法 Stepper | info | warm | L0.Stepper | `p04-solution-stepper` | `--tkn-color-card` · `--tkn-color-text-primary` |
| `B6` | 学科 / KP / 难度 chips | info | warm | M5.SubjectChip + L0.Tag + L0.Stepper(stars) | `p04-meta-chips` | `--tkn-subject-math` · `--tkn-color-bg-light` |
| `B7` | 6 节点 MemoryCurve（preview） | info | warm | M8.MemoryCurve(variant=preview) | `memory-curve` | `--tkn-color-text-secondary`（曲线灰）|
| `B8` | 保存 CTA（蓝色 pill） | info | warm | L0.Button(primary) | `p04-save-cta` | `--tkn-color-primary-DEFAULT` · `--tkn-radius-pill` |

> **fe-preflight 用法**：把 mockup HTML 切成这些块；fe-builder 按块逐个实现并跑 lint。
> **铁律 5 校验**：B3 错解/正解卡使用 mastery-forgot/mastered 仅作 1px 内描边，禁止大色块；B4 错因 4px 左条同理；B6 学科 chip 仅 width ≤ 80px。

---

## §4 数据契约（page-level interface）

```typescript
interface QuestionDetailResp {
  question: {
    id: string;
    subject: 'math' | 'physics' | 'chemistry' | 'english';
    stem: string;
    formula?: string;            // LaTeX 字符串，渲染为公式 chip
    thumbnailUrl: string;
    myAnswer: string;
    correctAnswer: string;
    reasonMarkdown: string;
    steps: Array<{ idx: number; title: string; detail?: string }>;
    knowledgePoints: Array<{ id: string; name: string; weight: number }>;
    difficulty: 1 | 2 | 3 | 4 | 5;
    confidence: number;          // 0-1, < 0.6 时顶部黄条
    modelInfo: { name: string; version: string };
  };
  plannedNodes: Array<{
    tLevel: 'T1' | 'T2' | 'T3' | 'T4' | 'T5' | 'T6';
    dueAt: string;               // ISO
    status: 'preview';
  }>;                            // 长度 = 6
}

interface SaveQuestionReq {
  qid: string;
  edits?: Partial<QuestionDetailResp['question']>;
}

interface SaveQuestionResp {
  qid: string;
  planId: string;
  nodes: Array<{ nid: string; tLevel: string; dueAt: string }>;  // 7 个 (T0..T6)
}
```

> 类型不一致直接 fail。

---

## §5 API 触点

| Method | Path | 用途 | P95 预算 | 失败降级 |
|---|---|---|---|---|
| GET | `/api/wb/questions/{qid}` | 主聚合接口 | 400ms | 骨架屏 + 重试 1 次 |
| PATCH | `/api/wb/questions/{qid}` | 学生编辑字段 | 300ms | 局部失败保留 draft |
| POST | `/api/wb/questions/{qid}/save` | 确认保存 → 触发 plan/nodes 生成 | 1000ms | toast "保存中…稍后自动重试"（outbox 兜底） |

---

## §6 状态机

| State | 触发 | UI 表现 |
|---|---|---|
| `LOADING` | 进入页面 | 全页骨架屏 |
| `DRAFT` | API 200 + AI 出稿 | 完整渲染（confidence ≥ 0.6） |
| `LOW_CONF` | confidence < 0.6 | 顶部黄条 "AI 不太确定，请复核" + 保存前强制确认弹窗 |
| `EDITING` | 学生 tap 任意字段 | 进入编辑模式（PATCH 实时） |
| `SAVING` | 点击 B8 | CTA loading + 全页禁用 |
| `SAVED` | save 成功 | 200ms 淡出 → P05（首项绿色高亮 3s） |
| `ERROR` | API non-2xx | 红条 + 重试按钮 |

---

## §7 跳转图

```
[入口]
  P03 SUCCEEDED ──→ P04
        │
        ├──[B8 保存]────────→ P05 (highlight=qid)
        ├──[B1 ×]──────────→ 二次确认 → P02 重拍 / 直接退出
        └──[查看详情(辅助)]→ P06 (qid)
```

---

## §8 AC 覆盖表（与 business-analysis.yml 双向锚定）

| AC ID | 验收点 | 涉及 Block | testid 验证点 |
|---|---|---|---|
| `AC-P04-001` | 页面进入后 mood 从 cool 切到 warm（--tkn-color-bg-light 入场） | 全部 | `<main data-mood="warm">` 存在；`--tkn-color-bg-light` 出现于 :root |
| `AC-P04-002` | 错因卡左侧有 4px mastery-forgot 红条 | B4 | `p04-reason-card` `border-left-color` 解析 = `#C0392B` |
| `AC-P04-003` | 错解卡 1px 内描边为 mastery-forgot；正解卡 1px 内描边为 mastery-mastered | B3 | `p04-answers-row-wrong` / `p04-answers-row-right` 各自 `box-shadow inset` |
| `AC-P04-004` | 6 节点 MemoryCurve preview 全部为灰色未来态（无任何高亮） | B7 | `memory-curve-node-T1` ... `memory-curve-node-T6` 全部 `data-status="future"` |
| `AC-P04-005` (US-02) | 包含错因文本 + 正解 + 3 步解法 | B3, B4, B5 | `p04-reason-card-text` 非空 · `p04-answers-row-right-text` 非空 · `p04-solution-stepper-step-{1..3}` 各非空 |
| `AC-P04-006` | 保存 CTA 触发后 ≤ 1s 完成 save，跳 P05 携带 highlight=qid | B8 | `p04-save-cta` 点击后埋点 `wb_result_save` |
| `AC-P04-007` | 学科 chip 宽度 ≤ 80px（铁律 5） | B6 | `subject-chip-math` `width <= 80px` |
| `[AI 推测] AC-P04-008` | confidence < 0.6 时顶部出现黄条 "AI 不太确定" | B1 | `p04-low-conf-banner` 渲染 |

> **fe-accept-mock 用法**：用这张表逐条断言；缺 testid = 验收 fail。

---

## §9 异常路径

| 触发 | UI | 系统行为 |
|---|---|---|
| AI 置信度 < 0.6 | 顶部黄条 "AI 不太确定，请复核" | 保存前强制弹确认弹窗 |
| save 失败（5xx） | toast "保存中…稍后自动重试" | outbox 重试 3 次 |
| 学生编辑后字段验证失败 | 字段下方红字提示 | 阻止保存 |
| calendar-core 503（侧效失败） | save 仍成功 + toast "排期同步中" | outbox 兜底 |

---

## §10 埋点事件

| 事件名 | 触发 Block | 必带属性 |
|---|---|---|
| `wb_result_view` | 页面进入 | `qid`, `model`, `confidence` |
| `wb_result_edit` | EDITING | `field`, `qid` |
| `wb_result_save` | B8 | `qid`, `subject`, `kpCount`, `diff`, `manualFilled` |
| `wb_result_low_conf` | LOW_CONF | `qid`, `confidence` |
| `wb_result_reshoot` | B1 → P02 | `qid` |

---

## §11 性能预算

- TTI ≤ 800ms（从 P03 跳过来 + 渲染）
- LCP ≤ 1200ms（题干 hero + 缩略图）
- CLS < 0.05
- API P95 ≤ 400ms (GET) · ≤ 1000ms (save)
- 骨架屏与数据切换 < 100ms 渐入

---

## §12 A11y

- Landmarks: `<header role="banner">` (B1), `<main role="main">` (B2-B7), `<footer role="contentinfo">` (B8)
- 焦点顺序: B1 close → B2 题干 → B3 错解 → B3 正解 → B4 错因 → B5 step1..3 → B6 chips → B8 save
- 屏幕阅读器优先级: B4 错因 `aria-live="polite"` "你的错误：…"，B8 CTA 明确 `aria-label="保存到错题本，AI 会安排 6 次复习"`
- `prefers-reduced-motion`: 关闭 result-warm 渐变 200ms 入场过渡，仅 opacity 切换

---

## §13 Mockup 锚定

- **权威**: `design/mockups/wrongbook/04_result.html` (v1)
- **历史变体**: 无
- **截图**: `design/system/screenshots/P04-v1-light.png`（fe-preflight 自动生成）
- **反向锚定**: mockup HTML `<head>` 中含 `<meta name="design-spec" content="P04-result.spec.md@v1">`

---

## §14 Tokens 清单（grep 校验用）

```
L1 (Apple base):
  --tkn-color-white
  --tkn-color-primary-DEFAULT
  --tkn-color-system-warning-DEFAULT
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
  --tkn-color-encouragement-DEFAULT     (难度星星填色 / soon 提示)
  --tkn-color-bg-light             (页面背景渐变 · 米黄→米白)
  --tkn-shadow-card-deep
  --tkn-shadow-hero-card

L3 (celebration · 仅庆祝白名单时刻):
  --tkn-color-mastery-forgot     (B3 错解卡 1px 内描边 · B4 错因 4px 左条)
  --tkn-color-mastery-mastered   (B3 正解卡 1px 内描边)

EXCEPTION (subject):
  --tkn-subject-math
  --tkn-subject-physics
  --tkn-subject-chemistry
  --tkn-subject-english
```

> 本页 mood=warm；mastery 颜色仅作描边/左条，禁止用于背景大色块（铁律 5）。无 iron rule 1 例外（保存按钮使用 primary blue）。

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
