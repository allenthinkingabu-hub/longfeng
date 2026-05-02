# AI 错题本 · Molecules Catalog (L1 产品分子组件)

> **Scope**: 11 个产品特定的组合组件（"分子"），由 L0 通用原语（`components.md`）组装而成，承载错题本特有业务语义。
> **Layer position**: L0 (`components.md`) → **L1 (this file)** → L2 (`pages/*.spec.md`)
> **Token usage**: 严格通过 `--tkn-*` 引用，不允许硬编码。三层 token 架构详见 `design/system/DESIGN.md` §2。
> **Mood support**: 每个 molecule 在 §spec 中标注其支持的 mood（A hero+overlap / B pure-warm / C dark-camera / D celebrate-green / E teal-observer · 详见 STYLE-TRUTH.md §3）。
> **Iron rule compliance**: 所有 molecule 必须满足铁律 8（testid + token + a11y 三件套）。

---

## Molecule Index

| # | Molecule | Used in Pages | Composed of (L0 primitives) | Iron Rule Notes |
|---|----------|----------------|------------------------------|-----------------|
| 1 | **GreetingHero** | P-HOME, P-WELCOMEBACK | Card + Avatar + StreakBar (M2) | mood=A hero+overlap · 深蓝 hero gradient (180deg hero-stop-2/4/5 · archive 01_home) · 3 层 radial blob (purple/cyan/pink) blur 18-20px |
| 2 | **StreakBar** | P-HOME, P09 | (atom) — fire SVG + number + dots grid | uses streak-fire (L3) |
| 3 | **TodayReviewCard** | P-HOME, P07 | Card + Progress(circular) + SubjectChip[] (M5) + Button | celebrate-elevated content within warm/celebrate hero |
| 4 | **WeekStrip** | P-HOME | (atom row) 7 day-cells + T-level dots + exam markers | mood=warm |
| 5 | **SubjectChip** | P-HOME, P05, P06, P08 | Tag (extended) | uses --tkn-subject-* (EXCEPTION 1) |
| 6 | **MasteryStatusCard** | P05, P06 | Card + 3 status bars + count | learning state colors only |
| 7 | **QuestionListCard** | P05, P-OBSERVER | Card + thumbnail + KP chips + Progress(segmented) + Stepper-dot | left 4px subject color bar |
| 8 | **MemoryCurve** | P04, P06, P09 | SVG + node dots (T1–T6) + path | uses mastery 3-state colors |
| 9 | **CelebrateHero** | P09 | Hero container + ConfettiBurst (M10) + checkmark SVG | mood=celebrate · gradient-celebrate-green |
| 10 | **ConfettiBurst** | P09 | SVG particles (5-color cycle) | iron rule 3 whitelisted only |
| 11 | **AnalyzingPipeline** | P03 | 4-step row + JSON stream view + cancel button | mood=C dark-camera context · 暗底 #0B0F1A 实色（archive 02_capture .screen bg）· 不是 linear gradient |

---

## 1. GreetingHero

**purpose**: 学生打开 App 第一眼的问候，传达"今天的状态"和情绪锚点

**variants**:
- `home` (default · P-HOME) — 深蓝渐变 hero 240px (`linear-gradient(180deg, hero-stop-2 #1E3A8A 0%, hero-stop-4 #3B5BDB 45%, hero-stop-5 #5B8DEF 100%)`) + 3 层 radial blob (purple .55 + cyan .45 + pink .35, blur 18-20px)，"早晨问候"基调
- `welcomeback` (P-WELCOMEBACK) — 深蓝渐变 hero 460px + 数字脉冲（"还剩 N 题"）+ memory curve preview SVG

**states**:
- `loading` — skeleton（avatar 圆 + 名字短杠 + streak 横杠）
- `ready` — 完整渲染
- `streak-milestone` — 连击 7/30/100 天，整个 hero 短暂高亮（`celebrate-streak-bump` 400ms）

**props**:
| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `studentName` | string | required | 学生昵称（未填写时显示"同学"） |
| `streakDays` | number | `0` | 连续打卡天数 |
| `todayTotal` | number | `0` | 今日待复习题数（welcomeback 变体显示此数字脉冲） |
| `variant` | `aurora\|welcomeback` | `aurora` | 视觉变体 |
| `testId` | string | `greeting-hero` | data-testid 根 |

**testid**:
- root: `greeting-hero`
- children: `greeting-hero-name`, `greeting-hero-greeting-text`, `streak-bar` (delegated to M2)

**a11y**: `<header role="banner" aria-label="今日问候 · 已连续打卡 N 天">`. 屏幕阅读器优先朗读。

**h5**: `<header>` 深蓝渐变 hero `linear-gradient(180deg, var(--tkn-color-hero-stop-2) 0%, var(--tkn-color-hero-stop-4) 45%, var(--tkn-color-hero-stop-5) 100%)` · 含 `::before` (purple blob 280×280 right-top, blur 20px) + `::after` (cyan blob 220×220 bottom-left, blur 20px) + `.blob` (pink blob 120×120 center-left, blur 18px) · padding `var(--tkn-spacing-xl) var(--tkn-spacing-md)` · 内文层 z-index:20

**miniprogram**: `<view>` 容器；backdrop-filter 不支持 → blur 装饰层退化为半透叠加；粒子层用 `<canvas>` 或 css 动画。深蓝渐变本身可正常渲染。

**token usage**:
- bg: `linear-gradient(180deg, hero-stop-2/4/5)` 组装 · 不是单一 token
- 装饰 blob: `--tkn-color-blob-purple` + `--tkn-color-blob-cyan-soft` + `--tkn-color-blob-pink`
- name typography: `--tkn-type-display-hero` + `--tkn-color-text-on-dark`
- greeting text: `--tkn-type-sub-heading` + `rgba(255,255,255,0.85)`
- streak: delegate to StreakBar M2
- shadow below: `--tkn-shadow-hero-card` (transitions to warm info-region below)

**禁止**:
- ❌ 不能放 CTA（按钮在 TodayReviewCard 里）
- ❌ 不能堆叠 emoji（最多 1 个开头 emoji 如 "👋"，其余装饰用 SVG）
- ❌ 不能在卡片里嵌套（hero 永远是 page-level 顶级容器）

---

## 2. StreakBar

**purpose**: 显示连续打卡天数 + 火焰 icon + 7 日进度网格，强化"我每天都来"的承诺感

**variants**:
- `compact` (default · P-HOME hero 内) — 一行：🔥 + 数字 + "已连续打卡"
- `expanded` (P09 完成页 + P13 我的页) — 一行 + 7 日进度 dot 网格

**states**:
- `zero` — 0 天，灰色火焰，文案"开始你的第一天"
- `active` — N>0 天，正常显示
- `milestone-bump` — 连击 7/30/100 天，数字弹跳 + 火焰高亮（`celebrate-streak-bump` 400ms）

**props**:
| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `days` | number | `0` | 连续天数 |
| `variant` | `compact\|expanded` | `compact` | 显示模式 |
| `milestone` | boolean | `false` | 是否触发里程碑弹跳动画 |
| `testId` | string | `streak-bar` | |

**testid**:
- root: `streak-bar`
- children: `streak-bar-fire-icon`, `streak-bar-days-number`, `streak-bar-grid-day-{1..7}` (expanded only)

**a11y**: `<div role="status" aria-label="已连续打卡 7 天">`. 数字变化通过 `aria-live="polite"`.

**h5**: 火焰用 SVG（不用 emoji 🔥），数字用 `--tkn-type-display-hero`（compact 缩小到 24px），网格 dot 用 `--tkn-radius-circle` 8×8px

**miniprogram**: 同 h5；SVG 用 `<image src="/static/streak-fire.svg">` 加载

**token usage**:
- fire: `--tkn-color-streak-fire` (SVG fill)
- number: typography 24px–48px (variant) · weight 600 · color contextual (white on hero / `--tkn-color-text-primary` on warm)
- grid dot active: `--tkn-color-streak-fire`
- grid dot inactive: `rgba(255,107,53,0.16)` (compute from streak-fire)
- bump animation: `--tkn-motion-celebrate-streak-bump` + `--tkn-ease-celebrate-bounce-out`

**禁止**:
- ❌ 不能用 🔥 emoji，必须 SVG
- ❌ days=0 不能弹跳（无成就则无庆祝）

---

## 3. TodayReviewCard

**purpose**: P-HOME / P07 上承载"今日要复习的总览 + 主 CTA"，是首页焦点元素

**variants**:
- `home` (P-HOME) — 嵌在 aurora hero 内部，白底 elevated card
- `today` (P07) — 嵌在 today-blue hero 内部，结构相同但配色稍调

**states**:
- `loading` — skeleton（圆环灰 + 学科 chip 骨架 + 按钮骨架）
- `ready` — 完整渲染
- `empty` — 今日无复习，文案变 "今天没有复习安排，拍一道新题试试？" + CTA → P02
- `all-done` — done === total，文案变 "今日全部完成 ✓"，CTA 替换为"看看战绩"

**props**:
| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `total` | number | required | 今日总题数 |
| `done` | number | `0` | 已完成数 |
| `estMinutes` | number | required | 估计耗时（分钟） |
| `subjectDist` | `Array<{subject, count}>` | `[]` | 学科分布（最多显示 4 个） |
| `circleProgress` | number | computed | 0-100，默认按 done/total 计算 |
| `variant` | `home\|today` | `home` | |
| `onStartAll` | function | required | "全部开始" CTA handler |
| `testId` | string | `today-review-card` | |

**testid**:
- root: `today-review-card`
- children: `today-review-card-total`, `today-review-card-done`, `today-review-card-est-min`, `today-review-card-circle-progress`, `today-review-card-subject-chip-{subject}` (foreach), `today-review-card-start-all-btn`

**a11y**: `<section role="region" aria-label="今日复习概览">`. 圆环进度 `<progressbar role="progressbar" aria-valuenow="3" aria-valuemax="8" aria-valuetext="3 of 8 completed">`. CTA 按钮明确 `aria-label="开始今日全部 8 题复习"`.

**h5**: 圆环用 SVG `<circle stroke-dasharray>`；学科 chip 横排用 `flex-wrap: nowrap; overflow-x: auto`

**miniprogram**: 圆环用 `<canvas>` (wxs); chip 同 h5

**token usage**:
- card bg: `--tkn-color-card`
- card radius: `--tkn-radius-lg` (12px)
- card shadow: `--tkn-shadow-card-deep`
- circle progress fill: `--tkn-color-mastery-mastered` (mastered portion) + `rgba(0,0,0,0.08)` (track)
- text primary: `--tkn-color-text-primary`
- text secondary: `--tkn-color-text-secondary`
- subject chip: delegate to SubjectChip M5
- CTA button: `--tkn-color-primary-DEFAULT` + `--tkn-radius-pill`

**禁止**:
- ❌ 不能在 cool mood 区域使用（必须配 hero 渐变背景）
- ❌ CTA 按钮不能换学科色（铁律 1）

---

## 4. WeekStrip

**purpose**: P-HOME 上的"七日排期条带"，让学生一眼看到本周复习节奏 + 考试 / 家庭事件

**variants**:
- `default` — 7 日横排
- `with-today-highlight` — 今日 cell 蓝色边框（默认即此）

**states**:
- `loading` — 7 个灰色 cell 骨架
- `ready` — 完整渲染

**props**:
| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `days` | `Array<{date, reviewCount, examHint, tLevels[]}>` | required | 长度必须 = 7 |
| `today` | string (ISO) | required | 标记今天位置 |
| `onDayTap` | function | required | 点击 cell handler → P10 |
| `testId` | string | `week-strip` | |

**testid**:
- root: `week-strip`
- children: `week-strip-day-{1..7}`（每个 cell）· `week-strip-day-{n}-tlevel-{T}` 节点 dot · `week-strip-day-{n}-exam-marker` 考试红点

**a11y**: `<nav role="navigation" aria-label="本周复习排期">`. 每个 cell `<button>` 含完整描述（"5月8日 · 3 题待复习 · T2 复习节点"）.

**h5**: 7 列 grid `display: grid; grid-template-columns: repeat(7, 1fr); gap: var(--tkn-spacing-2)`

**miniprogram**: 同 h5；`<view>` flex 横排

**token usage**:
- cell bg: `--tkn-color-card`
- cell radius: `--tkn-radius-md` (11px)
- today border: `2px solid var(--tkn-color-primary-DEFAULT)`
- date text: `--tkn-type-caption-bold` + `--tkn-color-text-primary`
- t-level dots (4 colors): `--tkn-subject-math` / `--tkn-subject-physics` / `--tkn-subject-chemistry` / `--tkn-subject-english`
- exam marker: `--tkn-color-system-danger-DEFAULT`
- family event: `--tkn-color-encouragement-DEFAULT`

**禁止**:
- ❌ 不能超过 7 个 cell（业务上"本周"= 7 天）
- ❌ T-level dot 不能用 system-success / mastery 色（必须学科色，统一识别口径）

---

## 5. SubjectChip

**purpose**: 学科识别 chip，跨页面统一学科视觉

**variants**:
- `filter` (default) — 可点击切换筛选（P05 学科筛选条）
- `static` — 仅展示，不可点击（P-HOME TodayReviewCard 内学科分布）
- `card-bar` — 不是 chip，是错题卡的 4px 左色条变体（QuestionListCard 内复用色 token）

**states**:
- `default` — 浅色背景 + 学科色文字（`background: encouragement-soft-equivalent` of subject color · `color: --tkn-subject-{name}`）
- `selected` — 学科色背景 + 白文字（`background: --tkn-subject-{name}` · `color: #fff`）
- `disabled` — `opacity: 0.4`

**props**:
| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `subject` | `math\|physics\|chemistry\|english` | required | 学科 |
| `count` | number | — | 可选计数（"数学 52"） |
| `selected` | boolean | `false` | |
| `variant` | `filter\|static\|card-bar` | `filter` | |
| `onClick` | function | — | filter 变体必填 |
| `testId` | string | `subject-chip-{subject}` | |

**testid**:
- root: `subject-chip-{subject}` (e.g. `subject-chip-math`)

**a11y**: `<button role="button" aria-pressed="true" aria-label="数学 · 52 题 · 已选中">` (filter variant) · `<span>` (static)

**h5**: `<button class="chip">` · `padding: var(--tkn-spacing-xs) var(--tkn-spacing-12)` · `border-radius: var(--tkn-radius-pill)`

**miniprogram**: `<view>` with `bindtap` · 同样圆角 + 内边距

**token usage**:
- selected bg: `--tkn-subject-math` / `--tkn-subject-physics` / `--tkn-subject-chemistry` / `--tkn-subject-english`
- selected text: `#ffffff`
- default bg: subject color at 10% alpha (e.g., math → `rgba(196,30,58,0.10)`)
- default text: `--tkn-subject-{name}`
- typography: `--tkn-type-caption-bold`
- radius: `--tkn-radius-pill`

**禁止**:
- ❌ 学科色 ONLY 在三种用法（chip · 卡片左色条 · icon）—— 铁律 5
- ❌ chip 宽度 ≤ 80px（超出即被铁律 5 lint 视为大色块违规）

---

## 6. MasteryStatusCard

**purpose**: P05 / P06 上展示"未掌握 / 部分 / 已掌握"3 类计数，可点击切换筛选

**variants**:
- `triple` (default) — 3 个状态横排
- `single-detail` (P06 内嵌) — 仅显示当前题目 mastery 状态

**states**:
- `default`
- `selected` — 某一状态被点击高亮
- `loading` — 3 卡骨架

**props**:
| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `forgot` | number | required | 未掌握题数 |
| `partial` | number | required | 部分掌握题数 |
| `mastered` | number | required | 已掌握题数 |
| `selected` | `forgot\|partial\|mastered\|null` | `null` | |
| `onSelect` | function | required | |
| `testId` | string | `mastery-status-card` | |

**testid**:
- root: `mastery-status-card`
- children: `mastery-status-card-forgot`, `mastery-status-card-partial`, `mastery-status-card-mastered`

**a11y**: `<div role="radiogroup" aria-label="按掌握度筛选">` · 每张卡 `<button role="radio" aria-checked="false">`

**h5**: 3 等宽卡 `display: grid; grid-template-columns: repeat(3, 1fr); gap: var(--tkn-spacing-12)`

**token usage**:
- forgot bg/border accent: `--tkn-color-mastery-forgot`
- partial bg/border accent: `--tkn-color-mastery-partial`
- mastered bg/border accent: `--tkn-color-mastery-mastered`
- card bg: `--tkn-color-card`
- card shadow: `--tkn-shadow-card-deep`
- card radius: `--tkn-radius-lg`
- count typography: `--tkn-type-display-hero` (大数字)
- label typography: `--tkn-type-caption`

---

## 7. QuestionListCard

**purpose**: P05 错题列表的单个错题卡，承载学科 / 缩略图 / 题干 / KP / 进度阶段 / 下次到期

**variants**:
- `default` (P05) — 完整信息
- `readonly` (P-OBSERVER · P-SHARED) — 同 default 但移除"立即复习"按钮

**states**:
- `default` · `pressed` (scale 0.97) · `archived` (opacity 0.6)

**props**:
| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `qid` | string | required | 错题 ID |
| `subject` | `math\|physics\|chemistry\|english` | required | 学科色条 |
| `thumbnailUrl` | string | required | 错题图缩略图 |
| `stemSnippet` | string | required | 题干前 50 字 |
| `kpList` | string[] | `[]` | 知识点 chip 列表（最多 3 个） |
| `nodeStage` | number (0-6) | required | 当前在 T0-T6 哪个节点 |
| `nextDueAt` | string (ISO) | — | 下次到期时间 |
| `mastery` | `forgot\|partial\|mastered` | — | 当前掌握度（决定边框 accent） |
| `archived` | boolean | `false` | |
| `onTap` | function | required | 进 P06 |
| `index` | number | required | 列表索引（用于 testid） |
| `testId` | string | `question-list-card-{index}` | |

**testid**:
- root: `question-list-card-{index}` (e.g., `question-list-card-1`)
- children: `question-list-card-{index}-thumbnail`, `question-list-card-{index}-stem`, `question-list-card-{index}-kp-{n}`, `question-list-card-{index}-stage-{0..6}`, `question-list-card-{index}-due`

**a11y**: `<article role="article" aria-label="数学错题 · 二次函数 · T2 节点 · 明日 18:00 复习">`

**h5**: 卡片高 ~96px · 左侧 4px 学科色条 · 缩略图 80×80 · 文字区 flex 1 · 右侧 stage dots + due

**token usage**:
- card bg: `--tkn-color-card`
- card radius: `--tkn-radius-lg`
- card shadow: `--tkn-shadow-card-deep`
- left bar: `--tkn-subject-{subject}` (4px width)
- thumbnail radius: `--tkn-radius-sm`
- stem typography: `--tkn-type-body` + `--tkn-color-text-primary`
- KP chip: `--tkn-color-bg-light` bg + `--tkn-color-text-secondary` text
- stage dots (6 个):
  - completed (`< nodeStage`): `--tkn-color-mastery-mastered`
  - current (`= nodeStage`): `--tkn-color-encouragement-DEFAULT` 脉冲
  - future (`> nodeStage`): `rgba(0,0,0,0.16)`
- due text: `--tkn-type-caption` + `--tkn-color-encouragement-DEFAULT` (if soon) / `--tkn-color-text-secondary` (if wait)
- pressed shadow: `--tkn-shadow-card-deep-pressed` + `transform: scale(0.97)`

**禁止**:
- ❌ 整卡背景不能用学科色（铁律 5）—— 学科色仅 4px 左条
- ❌ archived 态不能完全隐藏（保留可见性 + opacity 处理）

---

## 8. MemoryCurve

**purpose**: SVG 遗忘曲线 + 6 节点（T1–T6）三态可视化，是产品记忆点之一

**variants**:
- `preview` (P04) — 全部节点灰色未来态，仅作"即将开始的复习曲线"预告
- `progress` (P06) — 当前在 T_n，T_<n 已完成，T_n 当前，T_>n 未来
- `complete` (P09) — 完成最新节点后，刚做的节点高亮 mastered + 下一节点高亮 encouragement

**states**:
- `preview` · `progress` · `complete`（同 variants）

**props**:
| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `nodes` | `Array<{tLevel, status, dueAt}>` | required | 长度 = 6 |
| `currentT` | `T0\|T1\|T2\|T3\|T4\|T5\|T6` | — | 当前节点 |
| `variant` | `preview\|progress\|complete` | `progress` | |
| `testId` | string | `memory-curve` | |

**testid**:
- root: `memory-curve`
- children: `memory-curve-svg`, `memory-curve-node-{T1..T6}`, `memory-curve-node-{T1..T6}-status` (data attribute)

**a11y**: `<figure role="img" aria-label="艾宾浩斯遗忘曲线 · T2 已完成 · T3 进行中 · T4-T6 未开始">`. SVG 自身 `aria-hidden`，描述全在 figure label.

**h5**: SVG `<path>` 平滑曲线（quadratic bezier）· 6 个 `<circle>` 节点 · 节点动画 `@keyframes sse-pulse` (current node) 或 `@keyframes checkmark-pop` (just-completed)

**miniprogram**: `<canvas>` 绘制（wxs）

**token usage**:
- curve stroke: `--tkn-color-text-secondary`
- node done: `--tkn-color-mastery-mastered`
- node current: `--tkn-color-encouragement-DEFAULT` (with pulse animation)
- node future: `rgba(0,0,0,0.16)`
- node just-completed (variant=complete): `--tkn-color-mastery-mastered` (with checkmark-pop)
- pulse: `--tkn-motion-duration-base` linear infinite (但只对 current 节点开启)

**禁止**:
- ❌ preview variant 不能任何节点高亮（避免学生误以为已开始）
- ❌ 同时高亮多个节点（仅 1 个 current）

---

## 9. CelebrateHero

**purpose**: P09 复习完成的庆祝时刻 hero，是产品最大的情绪锚点

**variants**:
- `single` — 单题完成（仅 checkmark-pop，无 confetti）
- `all-done` — 今日全部完成（checkmark + ConfettiBurst 联动）
- `streak` — 连击里程碑（checkmark + streak-bump 强化）

**states**:
- `entering` — 800ms 入场动画
- `settled` — 静态展示

**props**:
| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `variant` | `single\|all-done\|streak` | `single` | |
| `headline` | string | required | "已掌握 ✓" / "今日全部完成 🎉" / "已连续打卡 7 天" |
| `subline` | string | — | 次要文案 |
| `streakDays` | number | — | streak 变体必填 |
| `testId` | string | `celebrate-hero` | |

**testid**:
- root: `celebrate-hero`
- children: `celebrate-hero-checkmark`, `celebrate-hero-headline`, `celebrate-hero-confetti` (all-done only · delegated to ConfettiBurst M10)

**a11y**: `<header role="banner" aria-label="复习完成 · 已掌握">` + `<div role="alert" aria-live="assertive">` 朗读庆祝信息

**h5**: 全屏 hero · `padding: var(--tkn-spacing-3xl) var(--tkn-spacing-md)` · 中央 SVG checkmark `width: 96px` · 动画 entry: scale(0)→scale(1.2)→scale(1) bounce-out

**miniprogram**: 同 h5；ConfettiBurst 用 `<canvas>` 实现

**token usage**:
- bg: `--tkn-gradient-celebrate-green`
- checkmark fill: `#ffffff` (transparent in some skins)
- checkmark stroke: `#ffffff` width=8px round cap
- headline typography: `--tkn-type-display-hero` + `#ffffff`
- subline typography: `--tkn-type-body` + `rgba(255,255,255,0.85)`
- entry animation: `--tkn-motion-celebrate-checkmark` + `--tkn-ease-celebrate-bounce-out`
- confetti delegate: M10

**禁止**:
- ❌ all-done 之外的 variant 不能放 confetti（铁律 3）
- ❌ 不能在 P09 之外使用此 molecule

---

## 10. ConfettiBurst

**purpose**: 庆祝粒子，仅 P09 all-done 触发

**variants**:
- `default` — 5 色循环粒子，从顶部撒落

**states**:
- `idle` — 隐藏
- `bursting` — 0–1200ms 动画期
- `complete` — 动画结束，DOM 保留但 `display: none`

**props**:
| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `active` | boolean | `false` | 触发 burst |
| `particleCount` | number | `40` | 粒子数（性能权衡：H5 40，小程序 24） |
| `duration` | number | from token | 总持续时长 |
| `testId` | string | `confetti-burst` | |

**testid**:
- root: `confetti-burst`
- children: `confetti-burst-particle-{1..N}`

**a11y**: `aria-hidden="true"` （庆祝由 CelebrateHero 朗读，粒子是装饰）。`prefers-reduced-motion` → 直接跳过粒子动画。

**h5**: SVG 全屏覆盖层 · 每个粒子 `<rect>` 12×4px · keyframe `@keyframes confetti` 包含 translate + rotate + opacity

**miniprogram**: `<canvas>` (wxs)

**token usage**:
- particle 5 colors (round-robin): `--tkn-color-celebrate-confetti-1`, `-2`, `-3`, `-4`, `-5`
- duration: `--tkn-motion-celebrate-confetti` (1200ms)
- ease: `cubic-bezier(0.4, 0, 0.2, 1)` (linear-ish for falling)

**禁止**:
- ❌ 不能在 P09 之外使用（iron rule 3 whitelist）
- ❌ 粒子色不能换其他 token（5 色固定）
- ❌ 不能循环（一次 burst 后必须停止）

---

## 11. AnalyzingPipeline

**purpose**: P03 AI 分析中页面，让学生在 4–8s 等待中保持"被看见"

**variants**:
- `default` — 4 步流水线 + JSON 流式区 + 取消按钮

**states**:
- `queued` — 步骤 0 等待中
- `step-1` / `step-2` / `step-3` / `step-4` — 当前步骤进行中
- `succeeded` — 全部完成（瞬时态，立即跳 P04）
- `failed` — 任何步骤失败 → 切到 retry / 手填降级
- `cancelled` — 用户主动取消 → 返回 P02

**props**:
| Prop | Type | Default | Description |
|------|------|---------|-------------|
| `taskId` | string | required | AI 分析任务 ID |
| `model` | string | required | 模型名（"qwen-vl-max" / "gpt-4o-mini"） |
| `currentStep` | `0\|1\|2\|3\|4` | `0` | 当前步骤 |
| `stepStatus` | `Array<'wait'\|'now'\|'done'\|'fail'>` | length=4 | 每步状态 |
| `partialJson` | string | `''` | 流式 JSON 片段 |
| `onCancel` | function | required | |
| `testId` | string | `analyzing-pipeline` | |

**testid**:
- root: `analyzing-pipeline`
- children: `analyzing-pipeline-thumbnail`, `analyzing-pipeline-model-badge`, `analyzing-pipeline-step-{1..4}`, `analyzing-pipeline-step-{n}-status` (data attribute), `analyzing-pipeline-json-stream`, `analyzing-pipeline-cancel-btn`

**a11y**: `<section role="region" aria-label="AI 分析中" aria-live="polite">` · 步骤更新通过 live region 朗读 · cancel button focus order 最后

**h5**: 暗底 `var(--tkn-color-bg-camera)` #0B0F1A 实色 · 4 步竖排 · 每步左侧状态 icon（圆 / 脉冲 / 勾 / 叉）+ 文字 · JSON 区 monospace · cancel 在最下方 灰 pill

**miniprogram**: 同 h5；JSON 滚动区用 `<scroll-view>`

**token usage**:
- bg: `--tkn-color-bg-camera` #0B0F1A 实色 (inherit from page · archive 02_capture .screen bg · 不是 linear gradient)
- step circle wait: `rgba(255,255,255,0.16)`
- step circle now: `--tkn-color-primary-dark` (Apple Blue dark variant)
- step circle done: `--tkn-color-mastery-mastered`
- step circle fail: `--tkn-color-system-danger-DEFAULT`
- step pulse: `@keyframes sse-pulse` 1.2s linear infinite
- step text: `--tkn-type-body` + `#ffffff`
- json stream: `--tkn-type-caption` + `#ffffff` + `font-family: 'SF Mono', monospace`
- cancel button: `--tkn-color-sep` bg + `rgba(255,255,255,0.72)` text + `--tkn-radius-pill`

**禁止**:
- ❌ 不能在 P03 之外使用
- ❌ JSON 区不能展开收起（始终可见，以保持"流动感"）

---

## Token Manifest (本文件用到的所有 token · 用于 grep 校验 · v2.0 archive-aligned)

```
L1 (color.json) — iOS HIG base + archive 真相:
  // 主色 / 文字 / 表面
  --tkn-color-primary-DEFAULT       (iOS Blue #007AFF · 修正自 #0071e3)
  --tkn-color-primary-dark          (#2997ff · 暗底链接)
  --tkn-color-primary-link          (#0066cc · 亮底链接)
  --tkn-color-text-primary          (iOS #1C1C1E · 修正自 #1d1d1f / #2C2A26)
  --tkn-color-text-secondary        (iOS #636366)
  --tkn-color-text-tertiary         (iOS #8E8E93)
  --tkn-color-text-on-dark          (#ffffff)
  --tkn-color-bg-light              (iOS #F2F2F7 · 替代旧 warm-bg / warm-sunken)
  --tkn-color-bg-camera             (#0B0F1A · Mood C 实色 · 替代旧 gradient-focus-night)
  --tkn-color-card                  (#FFFFFF · 替代旧 warm-elevated)
  --tkn-color-sep                   (rgba(60,60,67,.14) · 替代旧 warm-divider)
  // iOS HIG 9 色系统色
  --tkn-color-system-red            (#FF3B30)
  --tkn-color-system-orange         (#FF9500)
  --tkn-color-system-green          (#34C759)
  --tkn-color-system-indigo         (#5856D6)
  --tkn-color-system-yellow         (#FFCC00 · 02_capture 检测)
  --tkn-color-system-purple         (#AF52DE)
  --tkn-color-system-teal           (#30B0C7)
  --tkn-color-system-pink           (#FF2D55)
  --tkn-color-system-danger-DEFAULT (#C0392B)
  // Hero stops（深蓝 hero 渐变组装）
  --tkn-color-hero-stop-1 / -2 / -3 / -4 / -5 / -6 / -7
  // Hero 装饰 blob
  --tkn-color-blob-purple / -cyan / -cyan-soft / -pink / -coral / -mint / -gold
  // Em 强调字
  --tkn-color-em-from-gold / -to-coral / -to-amber
  // KP 暖米橙
  --tkn-color-kp-bg-from / -bg-to / -border / -text-title / -text-body / -text-em
  // 答案对错卡
  --tkn-color-ans-wrong-from / -wrong-to / -right-from / -right-to
  // 玻璃态白透
  --tkn-color-glass-white-{08|10|12|14|16|18|22|24|30|35|78|86|92}
  --tkn-color-glass-black-{35|40|45|55}
  // 学科 (EXCEPTION 1)
  --tkn-subject-math / -physics / -chemistry / -english
  // 微信品牌 (EXCEPTION wechat-brand)
  --tkn-color-brand-wechat

L1 (typography.json):
  --tkn-type-display-hero (Apple HIG 56px · web only)
  --tkn-type-sub-heading
  --tkn-type-body
  --tkn-type-caption
  --tkn-type-caption-bold
  // archive 实际还用：32/28/24/22/15/13.5/13/12/11/10/9px (mobile)

L1 (spacing.json):
  --tkn-spacing-2 / -xs / -sm / -12 / -md / -lg / -xl / -2xl / -3xl

L1 (radius.json) v2.0:
  --tkn-radius-phone (54px) / -phone-camera (55px)
  --tkn-radius-scroll-overlap (26px) / -scroll-overlap-sm (24px)
  --tkn-radius-hero-card (22px) / -card-lg (18px) / -card-sm (16px)
  --tkn-radius-btn (14px) / -cell (12px) / -ic-md (11px) / -ic-sm (10px)
  --tkn-radius-sm (8px) / -xs (6px) / -micro (4px) / -tiny (3px)
  --tkn-radius-pill (999px) / -circle (50%)

L1 (shadow.json) v2.0:
  --tkn-shadow-card                 (0 1px 2px rgba(0,0,0,.04) · 卡片基线)
  --tkn-shadow-card-light           (0 1px 0 rgba(0,0,0,.03) · 极轻)
  --tkn-shadow-card-deep            (双层 · 替代旧 warm-card)
  --tkn-shadow-hero-card            (0 10px 30px rgba(31,60,140,.25) · 上限)
  --tkn-shadow-phone / -phone-deep
  --tkn-shadow-cta-blue / -cta-deep / -cta-orange
  --tkn-shadow-rh-btn / -shutter / -glass-active
  --tkn-shadow-glow-yellow / -glow-pulse
  --tkn-shadow-avatar / -logo / -paper / -paper-thumb / -step-num
  --tkn-shadow-focus                (0 0 0 2px #007AFF · 修正自 #0071e3)
  --tkn-shadow-nav-glass            (backdrop-filter saturate(180%) blur(20px))

L1 (motion.json):
  --tkn-motion-duration-base / -fast / -slow
  --tkn-motion-ease-standard / -decel / -bounce-out

L3 (celebration.json) v2.0:
  --tkn-color-mastery-forgot
  --tkn-color-mastery-partial
  --tkn-color-mastery-mastered
  --tkn-color-celebrate-confetti-{1..5}
  --tkn-color-celebrate-celebrate-green-stop-{1..3}  (P09 hero 渐变 stops)
  --tkn-gradient-celebrate-hero     (linear-gradient 175deg, archive 09 实测)
  --tkn-color-streak-fire
  --tkn-motion-celebrate-confetti / -checkmark / -streak-bump
  --tkn-ease-celebrate-bounce-out

L2 (warmth.json) ⛔️ DEPRECATED:
  // 仅保留作下游引用兼容 · 所有 _deprecated_* 命名空间内 token 都已重映射
  // 详见 warmth.json#_meta.migration_map
```

**Lint 规则（grep 校验 must-fail patterns）**：
- ❌ `#0071e3` （应改 #007AFF）
- ❌ `#1d1d1f` 或 `#2C2A26` （应改 #1C1C1E）
- ❌ `gradient-aurora` （应用 hero-stop-* 组装）
- ❌ `gradient-focus-night` （应用 bg-camera 实色）
- ❌ `tkn-color-warm-text-primary` 或 `tkn-color-warm-elevated` 或 `tkn-color-warm-bg` （应用 iOS HIG 命名）
