---
page_id: P-HOME
name: 今日聚合首页
name_en: Today Hub
route_h5: /
route_miniprogram: pages/home/today
deeplink: wb://home
auth_state: authenticated
persona:
  - P1-K12
scenarios:
  - SC-01
  - SC-03
  - SC-05
mockup_canonical: design/mockups/wrongbook/01_home.html
mockup_version: v1
last_reviewed: 2026-05-02
status: spec-locked
sprint: S2
---

# P-HOME · 今日聚合首页

> **使用说明**：本 spec 对应 DESIGN.md §4.2 的 14 段标准结构。Sprint 2 的"主页"页面，聚合首屏所有心智入口。
> **核心约束**：DESIGN.md §1 铁律 6（页面节奏二分）—— 上半 hero(celebrate) + 下半信息流(warm)，全页节奏严格分层。

---

## §1 页面目的（why · 1 句话）

学生打开 App 第一眼就知道："今天有多少题要复习、现在点一下就开始"，并在余光中扫到本周节奏 / 新消息 / 弱项提醒。

---

## §2 ASCII 布局图

```
┌─────────────────────────────────────┐
│  ░░░ aurora 深蓝 hero hero ░░░       │   ← mood=celebrate
│  ░ B1 GreetingHero（早上好/学生名）  │
│  ░     · StreakBar 内嵌（🔥 7 天）   │
│  ░                                  │
│  ░ B2 TodayReviewCard（overlap）    │
│  ░    圆环 40% + 学科 chips         │
│  ░    + estMin + 全部开始 蓝 pill   │
├─────────────────────────────────────┤   ← mood 切换 cool→warm
│  ▓ B3 WeeklySparkline（7日走势）    │   ← mood=warm 米白
│  ▓     + 4 mini stats               │
│                                     │
│  ▓ B4 WeekStrip（七日排期条带）     │
│     7 cells · T-level 色点 · 考试红 │
│                                     │
│  ▓ B5 MessagesList（消息聚合 3 条） │
│                                     │
│  ▓ B6 WeakKPHint（薄弱知识点专练）  │
│     encouragement-soft 暖橙底       │
│                                     │
│  ▓ B7 QuickEntries（2×2 快捷入口）  │
│     拍题/错题本/完整日历/我的       │
└─────────────────────────────────────┘
[Tab Bar: 首页 错题本 拍题 复习 我的]
```

---

## §3 Block 清单（每块独立可实现单元）

| Block ID | 名称 | Section | Mood | Component | testid root | Tokens 用到 |
|---|---|---|---|---|---|---|
| `B1` | GreetingHero 问候 hero | hero | celebrate | M.GreetingHero + M.StreakBar | `greeting-hero` | `--tkn-gradient-hero-home`, `--tkn-color-glass-white-18`, `--tkn-color-streak-fire`, `--tkn-type-display-hero`, `--tkn-color-text-on-dark` |
| `B2` | TodayReviewCard 今日复习大卡 | hero(overlap) | celebrate | M.TodayReviewCard + M.SubjectChip | `today-review-card` | `--tkn-color-card`, `--tkn-shadow-card-deep`, `--tkn-radius-lg`, `--tkn-color-mastery-mastered`, `--tkn-color-primary-DEFAULT`, `--tkn-radius-pill` |
| `B3` | WeeklySparkline 周走势 | info | warm | (custom block) | `p-home-weekly-sparkline` | `--tkn-color-card`, `--tkn-color-text-primary`, `--tkn-color-primary-DEFAULT`, `--tkn-shadow-card-deep`, `--tkn-radius-lg` |
| `B4` | WeekStrip 七日排期 | info | warm | M.WeekStrip | `week-strip` | `--tkn-color-card`, `--tkn-subject-math`, `--tkn-subject-physics`, `--tkn-subject-chemistry`, `--tkn-subject-english`, `--tkn-color-system-danger-DEFAULT`, `--tkn-radius-md` |
| `B5` | MessagesList 消息聚合 | info | warm | (custom block) | `p-home-messages` | `--tkn-color-card`, `--tkn-color-text-primary`, `--tkn-color-text-secondary`, `--tkn-color-sep`, `--tkn-radius-lg` |
| `B6` | WeakKPHint 薄弱知识点 | info | warm | (custom block) | `p-home-weak-kp` | `--tkn-color-encouragement-soft`, `--tkn-color-encouragement-DEFAULT`, `--tkn-color-text-primary`, `--tkn-radius-lg` |
| `B7` | QuickEntries 快捷入口 | info | warm | (custom block · 2×2) | `p-home-quick-entries` | `--tkn-color-card`, `--tkn-color-primary-DEFAULT`, `--tkn-color-text-primary`, `--tkn-radius-lg`, `--tkn-shadow-card-deep` |

> **fe-preflight 用法**：把 mockup HTML 切成这 7 块；fe-builder 按块逐个实现并跑 lint。

---

## §4 数据契约（page-level interface · 来自业务文档 §2A.3.3）

```typescript
interface HomeTodayResp {
  streak: {
    days: number;            // 连续打卡天数
    milestone?: 7 | 30 | 100;// 是否触发里程碑动画
  };
  todayReview: {
    total: number;
    done: number;
    estMin: number;
    subjectDist: Array<{
      subject: 'math' | 'physics' | 'chemistry' | 'english';
      count: number;
    }>;
    circleProgress: number;  // 0-100
  };
  weekSparkline: number[];   // length = 7, 当周每天复习题数
  weekStrip: Array<{
    date: string;            // ISO YYYY-MM-DD
    reviewCount: number;
    examHint?: boolean;
    tLevels: Array<'T0'|'T1'|'T2'|'T3'|'T4'|'T5'|'T6'>;
    subjects: Array<'math'|'physics'|'chemistry'|'english'>;
  }>;
  messages: Array<{
    id: string;
    type: 'review' | 'exam' | 'family' | 'system';
    title: string;
    refId?: string;
    timeLabel: string;       // "1 小时前" / "今天 18:00"
  }>;                        // 最多 3 条
  weakKP?: {
    kpId: string;
    kpName: string;
    questionCount: number;
    masteryPct: number;      // 0-100
  };
  quickEntries: Array<{
    id: 'capture' | 'wrongbook' | 'calendar' | 'me';
    label: string;
    targetRoute: string;
  }>;
  studentName: string;
}
```

> 类型不一致 = 验收 fail。聚合接口 P95 ≤ 400ms（业务文档 §2A.3.3 性能预算）。

---

## §5 API 触点

| Method | Path | 用途 | P95 预算 | 失败降级 |
|---|---|---|---|---|
| GET | `/api/home/today?tz=Asia/Shanghai` | 主聚合接口（review-plan / wrongbook / notification / calendar 内部并发） | 400 ms | 顶部黄条 "部分数据正在同步" + 允许下拉刷新；分块独立 skeleton |
| POST | `/api/review/sessions` | 大卡"全部开始"创建会话 → 跳 P07 | 300 ms | 失败 toast "稍后重试"，留在 P-HOME |

---

## §6 状态机

| State | 触发 | UI 表现 |
|---|---|---|
| `LOADING` | 进入页面 | 7 个 block 各自独立骨架屏（互不阻塞）；hero 渐变常驻；圆环灰态 |
| `READY` | API 200 + 数据齐 | 完整渲染；圆环动画从 0 → circleProgress 800ms |
| `EMPTY` | API 200 + `todayReview.total === 0` | hero 文案改 "今天没有复习安排，拍一道新题试试？"；CTA → P02；其他 block 仍渲染 |
| `ERROR` | API non-2xx OR 网络失败 | 顶部 system-warning 黄条 + 各 block 单独 retry；hero 仍渲染（基于本地缓存的 streak） |
| `STREAK_MILESTONE` | streak.milestone ∈ {7,30,100} | hero 内 streak 数字弹跳 400ms（celebrate-streak-bump）+ 火焰高亮 |
| `ALL_DONE` | done === total > 0 | 大卡文案 "今日全部完成 ✓"；CTA 替换为 "看看战绩" → P09 |

---

## §7 跳转图

```
[入口]
  登录成功 ─┐
  Tab 1 「首页」 ─┤
  wb://home 深链 ─┤──→ P-HOME
  P09 「结束本次」 ─┘
        │
        ├──[B2 TodayReviewCard 全部开始]──→ P07（今日待复习）
        ├──[B2 TodayReviewCard 单题点击]──→ P08（复习执行）
        ├──[B4 WeekStrip 某日 cell]──────→ P10（日历月视图 anchor=日期）
        ├──[B4 WeekStrip 某日复习徽章]────→ P07（review?date=YYYY-MM-DD）
        ├──[B5 MessagesList 条目]────────→ P11（事件详情）/ P12（通知中心）
        ├──[B6 WeakKPHint 卡片]─────────→ P05（错题本，filter=kpId）
        ├──[B7 QuickEntry 拍题]──────────→ P02
        ├──[B7 QuickEntry 错题本]────────→ P05
        ├──[B7 QuickEntry 完整日历]──────→ P10
        └──[B7 QuickEntry 我的]──────────→ P13
```

---

## §8 AC 覆盖表（与 business-analysis.yml 双向锚定）

| AC ID | 验收点 | 涉及 Block | testid 验证点 |
|---|---|---|---|
| `AC-HOME-001` | 进入页面后 1.2s 内完成首屏 TTI（hero + 大卡 + 周条带） | B1, B2, B4 | `greeting-hero` 可见 + `today-review-card` 可见 + `week-strip` 可见 |
| `AC-HOME-002` | 圆环进度 = `circleProgress` 值，文本显示 `done`/`total` 与 `estMin` | B2 | `today-review-card-circle-progress` 的 `aria-valuenow` = circleProgress · `today-review-card-total` 文本 = total · `today-review-card-est-min` 文本含 estMin |
| `AC-HOME-003` | 「全部开始」蓝 pill 点击调 `POST /api/review/sessions` 后跳 P07 | B2 | `today-review-card-start-all-btn` 可点击 + 跳转 URL = `/review` |
| `AC-HOME-004` | 七日条带 7 个 cell，今日 cell 蓝色 2px 边框；T-level 色点用学科色 | B4 | `week-strip-day-{1..7}` 都存在；`week-strip-day-N`（N=今日索引）有 `data-today="true"`；`week-strip-day-{n}-tlevel-{T}` 颜色命中 `--tkn-subject-*` |
| `AC-HOME-005` | streak.days > 0 时 StreakBar 显示火焰 SVG（不能用 emoji 🔥）+ 数字 | B1 | `streak-bar-fire-icon` 是 `<svg>` 非 emoji；`streak-bar-days-number` 文本 = streak.days |
| `AC-HOME-006` | EMPTY 态：todayReview.total=0 时大卡 CTA 文案 = "拍一道新题试试" 跳 P02 | B2 | `today-review-card-start-all-btn` 文本 = "拍一道新题试试" + 跳 `/capture` |
| `AC-HOME-007` | 消息聚合最多 3 条，超出折叠 "更多 →" 进 P12 | B5 | `p-home-messages-item-{1..3}` 不超过 3 个 + 存在 `p-home-messages-more-link` |
| `AC-HOME-008` | [AI 推测] 薄弱知识点卡片仅在 `weakKP` 存在时渲染，点击进 P05 | B6 | `p-home-weak-kp` 在 `weakKP` 缺省时不渲染；存在时点击导航至 `/wrongbook?kp={kpId}` |
| `AC-HOME-009` | [AI 推测] 快捷入口 2×2 展示 4 项，每项 testid 独立可点击 44×44 命中区 | B7 | `p-home-quick-entries-item-{1..4}` 各自 ≥44×44 + 跳转目标符合 quickEntries 数组 |
| `AC-HOME-010` | [AI 推测] STREAK_MILESTONE 状态触发 streak-bump 弹跳 400ms，且尊重 prefers-reduced-motion | B1 | `streak-bar-days-number` 应用 `@keyframes streak-bump`；`prefers-reduced-motion: reduce` 时禁用 |

> **fe-accept-mock 用法**：用这张表逐条断言；缺 testid = 验收 fail。
> **AC 缺失处理**：`[AI 推测]` 标记的 4 条由 AI 从业务文档 §2A.3.3 推导，业务方 review。

---

## §9 异常路径

| 触发 | UI | 系统行为 |
|---|---|---|
| 聚合接口 5xx | 顶部 `system-warning` 黄条 "部分数据正在同步" · 允许下拉刷新 | 各 block 单独 retry；hero / streak 用本地缓存 |
| 聚合接口超时 (>3s) | 仍显示骨架屏；3s 后超时 toast | 后台静默重试 1 次 |
| `todayReview.total === 0` | hero 文案变 "今天没有复习安排" + CTA 改 "拍一道新题试试" → P02 | EMPTY 态 |
| 首次登录无数据 | 三步 onboarding 浮层 (P1) | onboarding flag 存 LocalStorage |
| `done === total > 0` | 大卡文案 "今日全部完成 ✓" + CTA 替换 "看看战绩" → P09 | ALL_DONE 态 |
| 网络完全离线 | 仍渲染基于 LocalStorage 缓存的最近一次 home/today 数据 + 顶部红条 "离线模式" | 缓存 TTL 6h |

---

## §10 埋点事件

| 事件名 | 触发 Block | 必带属性 |
|---|---|---|
| `home_view` | 页面 ready | `loadMs`, `total`, `done`, `streakDays` |
| `home_today_start_all` | B2 大卡按钮 | `count`, `estMin` |
| `home_week_tap` | B4 cell 点击 | `date`, `reviewCount` |
| `home_msg_tap` | B5 条目点击 | `type`, `refId` |
| `home_weak_kp_drill` | B6 卡片点击 | `kpId`, `questionCount` |
| `home_quick_entry` | B7 入口点击 | `target`（capture/wrongbook/calendar/me）|
| `home_streak_milestone_view` | B1 streak.milestone 触发 | `days`, `milestone` |

> 所有事件经 `packages/analytics`；首屏 `home_view` 必须在 TTI 后立即触发，含 `loadMs` 性能字段。

---

## §11 性能预算

- TTI ≤ 1200 ms（业务文档 §2A.3.3）
- LCP ≤ 1500 ms（hero 区作为 LCP 元素）
- CLS < 0.05（骨架屏与数据切换平滑）
- API P95 ≤ 400 ms（聚合接口）
- 骨架屏与数据切换 < 100ms 渐入
- aurora hero 粒子动画 60fps（GPU 合成层 transform/opacity 仅）

---

## §12 A11y

- Landmarks:
  - `<header role="banner">` 包裹 B1 GreetingHero
  - `<main role="main">` 包裹 B2-B7
  - `<nav role="navigation" aria-label="本周排期">` 包裹 B4 WeekStrip
  - `<nav role="navigation" aria-label="底部导航">` Tab Bar
- 焦点顺序: `B2 全部开始按钮 → B4 第一个日期 cell → B5 第一条消息 → B6 弱项卡片 → B7 第一个快捷入口 → Tab Bar`
- 屏幕阅读器朗读优先级:
  - B1 GreetingHero 整体作为 banner 朗读 "早上好，{学生名}，已连续打卡 {N} 天"
  - B2 TodayReviewCard 圆环用 `<progressbar role="progressbar" aria-valuenow aria-valuemax aria-valuetext>`
  - B5 消息条目用 `aria-live="polite"` 仅在 milestone 态触发
- `prefers-reduced-motion: reduce` 兜底:
  - 关闭 aurora 粒子动画
  - 关闭 streak-bump 弹跳
  - 圆环进度直接渲染目标值（不动画）

---

## §13 Mockup 锚定

- **权威**: `design/mockups/wrongbook/01_home.html` (v1)
- **历史变体**: `01_home_apple.html` / `01_home_v2.html` / `01_home_ios_refined.html`（归档候选）
- **截图**: `design/system/screenshots/P-HOME-v1-light.png`（fe-preflight 自动生成）
- **反向锚定**: mockup HTML `<head>` 含 `<meta name="design-spec" content="P-HOME.spec.md@v1">`

---

## §14 Tokens 清单（grep 校验用）

> mockup HTML 的 CSS 中**每一处** hex / rgb / px 数值必须能映射回这张清单中的 token。grep 不命中 = lint fail。

```
L1 (Apple base):
  --tkn-color-primary-DEFAULT
  --tkn-color-primary-dark
  --tkn-color-text-on-dark
  --tkn-color-system-danger-DEFAULT
  --tkn-color-system-warning-DEFAULT
  --tkn-color-text-primary
  --tkn-color-bg-light
  --tkn-color-white
  --tkn-color-black
  --tkn-font-display
  --tkn-font-text
  --tkn-type-display-hero
  --tkn-type-tile-heading
  --tkn-type-card-title
  --tkn-type-sub-heading
  --tkn-type-body
  --tkn-type-body-emphasis
  --tkn-type-caption
  --tkn-type-caption-bold
  --tkn-type-micro
  --tkn-spacing-2
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
  --tkn-radius-circle
  --tkn-shadow-card
  --tkn-shadow-focus
  --tkn-motion-duration-base
  --tkn-motion-duration-slow
  --tkn-motion-ease-apple-standard

L2 (warmth · 仅 mood=warm/celebrate 区段使用):
  --tkn-color-bg-light
  --tkn-color-card
  --tkn-color-bg-light
  --tkn-color-text-primary
  --tkn-color-text-secondary
  --tkn-color-sep
  --tkn-color-encouragement-DEFAULT
  --tkn-color-encouragement-soft
  --tkn-color-glass-white-18
  --tkn-color-glass-white-08
  --tkn-gradient-hero-home
  --tkn-shadow-card-deep
  --tkn-shadow-NONE-pressed-use-transform-scale
  --tkn-shadow-hero-card

L3 (celebration · 仅庆祝白名单时刻):
  --tkn-color-mastery-mastered
  --tkn-color-streak-fire
  --tkn-motion-celebrate-streak-bump
  --tkn-ease-celebrate-bounce-out

EXCEPTION (subject):
  --tkn-subject-math
  --tkn-subject-physics
  --tkn-subject-chemistry
  --tkn-subject-english
```
