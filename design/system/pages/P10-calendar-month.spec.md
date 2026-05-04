---
page_id: P10
name: 日历月视图
name_en: Calendar Month View
route_h5: /calendar/month
route_miniprogram: pages/calendar/month
deeplink: wb://calendar
auth_state: authenticated
persona:
  - P1-K12
scenarios:
  - SC-05
  - SC-06
  - SC-09
mockup_canonical: design/mockups/wrongbook/10_calendar_month.html
mockup_version: v2
last_reviewed: 2026-05-02
status: spec-draft
sprint: S4
---

# P10 · 日历月视图

> **使用说明**：Sprint 4 辅助页之一。日历月视图是"任务视角→时间视角"切换锚点，承载 SC-05 / SC-06 / SC-09 三个跨视图编排。
> **核心约束**：DESIGN.md §1 铁律 6（页面节奏二分）—— 整页 mood=warm，无 hero，仅顶部导航 + 网格 + 图例三段式。
> **观察者态**：复用整页视觉，所有点击降级为只读模态（不进入 P11 写态）；顶部叠加 system-info banner（参考 P-OBSERVER 规约）。

---

## §1 页面目的（why · 1 句话）

学生在一屏看清"本月每天有多少复习节点 / 哪些天是考试 / 哪些天有家庭事件"，并能点格子进事件详情或拉起当日列表。

---

## §2 ASCII 布局图

```
┌─────────────────────────────────────┐
│  B1 月份导航 (mood=warm)             │
│   < 上一月   2026 年 4 月   下一月 > │
│   "回到今天" 链接（当前月外才显示）   │
├─────────────────────────────────────┤
│  B2 周表头 Mon Tue Wed Thu Fri Sat Sun│
├─────────────────────────────────────┤
│  B3 7×6 日期网格                     │
│   ┌──┬──┬──┬──┬──┬──┬──┐            │
│   │30│31│ 1│ 2│ 3│ 4│ 5│            │
│   ├──┼──┼──┼──┼──┼──┼──┤            │
│   │ 6│ 7│ 8│ 9│10│11│12│            │
│   ├──┼──┼──┼──┼──┼──┼──┤            │
│   │13│14│15│16│17│18│19│            │
│   ├──┼──┼──┼──┼──┼──┼──┤            │
│   │20│21│●22│23│24│25│26│  ← 今日蓝边│
│   ├──┼──┼──┼──┼──┼──┼──┤            │
│   │27│28│29│30│ 1│ 2│ 3│            │
│   └──┴──┴──┴──┴──┴──┴──┘            │
│   每格右上角 T-level 色点（最多 3 个）│
│   超过 3 个 → "+N" 灰底圆            │
├─────────────────────────────────────┤
│  B4 LegendBar 图例（横滚条带）        │
│   ● 数学 ● 物理 ● 化学 ● 英语        │
│   ● 考试  ● 家庭                      │
└─────────────────────────────────────┘
[Tab Bar: 首页 错题本 拍题 复习 我的]
```

> 视觉节奏：单 section 信息流到底，无 hero，符合铁律 6"信息流到底"模式。

---

## §3 Block 清单（每块独立可实现单元）

| Block ID | 名称 | Section | Mood | Component | testid root | Tokens 用到 |
|---|---|---|---|---|---|---|
| `B1` | MonthNav 月份导航 | info | warm | (custom block) | `p10-month-nav` | `--tkn-color-card`, `--tkn-color-text-primary`, `--tkn-color-text-secondary`, `--tkn-color-primary-DEFAULT`, `--tkn-type-sub-heading`, `--tkn-radius-pill` |
| `B2` | WeekHeader 周表头 | info | warm | (custom block) | `p10-week-header` | `--tkn-color-text-secondary`, `--tkn-type-caption-bold`, `--tkn-color-sep` |
| `B3` | MonthGrid 月份日期网格 | info | warm | (custom block · 7×6 grid) | `p10-month-grid` | `--tkn-color-card`, `--tkn-color-bg-light`, `--tkn-color-text-primary`, `--tkn-color-text-secondary`, `--tkn-color-primary-DEFAULT`, `--tkn-subject-math`, `--tkn-subject-physics`, `--tkn-subject-chemistry`, `--tkn-subject-english`, `--tkn-color-system-danger-DEFAULT`, `--tkn-color-encouragement-DEFAULT`, `--tkn-radius-md`, `--tkn-spacing-2`, `--tkn-spacing-xs` |
| `B4` | LegendBar 图例条带 | info | warm | (custom block) | `p10-legend-bar` | `--tkn-color-card`, `--tkn-color-text-secondary`, `--tkn-subject-math`, `--tkn-subject-physics`, `--tkn-subject-chemistry`, `--tkn-subject-english`, `--tkn-color-system-danger-DEFAULT`, `--tkn-color-encouragement-DEFAULT`, `--tkn-type-caption`, `--tkn-radius-pill` |

> **fe-preflight 用法**：4 块独立切分，每块单独 grep token；MonthGrid 是核心，testid 对每个 cell 都有索引。

---

## §4 数据契约（page-level interface）

```typescript
interface CalendarMonthResp {
  month: string;                  // "2026-04"
  tzOffset: string;               // "Asia/Shanghai"
  today: string;                  // "2026-04-22" ISO date
  cells: CalendarCell[];          // length = 42 (6 weeks × 7 days)
}

interface CalendarCell {
  date: string;                   // "2026-04-22" ISO
  inMonth: boolean;               // false → 灰显（上/下月溢出）
  isToday: boolean;               // 今日蓝边
  events: CalendarCellEvent[];    // 该日所有 event 摘要（最多前 N 个图示）
}

interface CalendarCellEvent {
  eventId: string;                // 进 P11 用
  relationType: 'STUDY' | 'EXAM' | 'FAMILY';
  // STUDY 用学科色；EXAM 用 system-danger；FAMILY 用 encouragement
  subject?: 'math' | 'physics' | 'chemistry' | 'english'; // STUDY 必填
  tLevel?: 'T0' | 'T1' | 'T2' | 'T3' | 'T4' | 'T5' | 'T6'; // STUDY 必填
  startAt: string;                // ISO timestamp · 排序用
}
```

> 类型不一致直接 fail。

---

## §5 API 触点

| Method | Path | 用途 | P95 预算 | 失败降级 |
|---|---|---|---|---|
| GET | `/api/calendar/events?month=YYYY-MM&tz=Asia/Shanghai` | 主聚合接口 · 返回整月 cells | 500ms | EMPTY 态 + retry 链接 |
| PATCH | `/api/me/preferences` | 持久化 `calendar.showStudy` 切换（来自 SC-05 步骤 2.B） | 200ms | 仅本地生效，不阻断 |

> 来源：`SC-05` 步骤 1（`GET /api/calendar/events?month=2026-04`）、`SC-06` 步骤 1。

---

## §6 状态机

| State | 触发 | UI 表现 |
|---|---|---|
| `LOADING` | 进入页面 / 切换月份 | 月份网格骨架（42 个灰色 cell） + 月份导航文字 skeleton |
| `READY` | API 200 + cells 长度 = 42 | 完整渲染所有 cell 与色点 |
| `EMPTY` | API 200 但 cells 全为 inMonth 但所有 events 为空 | 网格仍渲染 + 中央覆盖一句"本月没有排期"小提示（不挡格子） |
| `ERROR` | API non-2xx 或网络失败 | 网格区出现错误占位 + "重试"链接（蓝色） |
| `FILTER_CHANGED` | 用户切换"显示复习"开关（仅观察者/学生设置） | 重新渲染当前 cells，仅本地过滤，不调后端 |

---

## §7 跳转图

```
[入口]
  P-HOME 周条带"完整日历 →"        ─┐
  P-HOME 快捷入口"完整日历"         ─┤
  P13 我的 → "我的日历"            ─┤── → P10
  深链 wb://calendar               ─┤
  P11 顶部返回（from=CAL）         ─┘
                                   │
        ├──[B1 上一月]──→ 重新拉本页（month 减 1）
        ├──[B1 下一月]──→ 重新拉本页（month 加 1）
        ├──[B1 回到今天]──→ 重新拉本页（month=today）
        ├──[B3 cell tap (有事件)]──→ P11 事件详情（携带 from=CAL + first eventId）
        ├──[B3 cell tap (无事件)]──→ 当日 BottomSheet "今日无排期"（无写操作）
        └──[B4 图例 tap]──→ 切换"显示复习"开关 → FILTER_CHANGED
```

---

## §8 AC 覆盖表（与 business-analysis.yml 双向锚定）

| AC ID | 验收点 | 涉及 Block | testid 验证点 |
|---|---|---|---|
| `AC-P10-001` | 进入 P10 默认显示当月 · 顶部 title 含 "YYYY 年 M 月" 与时区文案 | B1 | `p10-month-nav-title` 文本 = "2026 年 4 月" |
| `AC-P10-002` | 月份网格渲染 6 周 = 42 个 cell · 上/下月溢出 cell 的文字色为 warm-text-secondary | B3 | `p10-month-grid-cell-{1..42}` 共 42 个；inMonth=false 的 cell 含 `data-out-of-month="true"` |
| `AC-P10-003` | 今日 cell 含蓝色 2px border · 仅一个 cell 命中 | B3 | `p10-month-grid-cell-{n}-today-marker` 出现且仅一次 |
| `AC-P10-004` | 学科色点 = 4 学科色 + 考试红 + 家庭橙 · 每 cell 最多 3 dot · 超出 +N 圆 | B3 | `p10-month-grid-cell-{n}-dot-{1..3}` 各色 token；超出含 `p10-month-grid-cell-{n}-overflow` 文本 = "+N" |
| `AC-P10-005` | tap 一个有事件的 cell · 跳 P11 携带 first eventId + from=CAL 参数 (`SC-05` 步 4) | B3 | `p10-month-grid-cell-{n}` click → 路由 `/event/:eventId?from=CAL` |
| `AC-P10-006` | tap 上一月 / 下一月 / 回到今天 三个按钮 · 触发新 LOADING 并重拉数据 | B1 | `p10-month-nav-prev` / `p10-month-nav-next` / `p10-month-nav-today` 可点击 |
| `AC-P10-007` | LegendBar 显示 6 类标签（4 学科 + 考试 + 家庭）· 颜色与 cell dot 完全一致 | B4 | `p10-legend-bar-item-math/physics/chemistry/english/exam/family` 各 1 个 |
| `AC-P10-008` | [AI 推测] LOADING 态显示 42 个灰色骨架 cell · API 返回后 < 100ms 渐入替换 | B3 | `p10-month-grid` 含 `data-state="loading"` 期间存在 `p10-month-grid-skeleton` |
| `AC-P10-009` | [AI 推测] 观察者态顶部叠加只读 banner · cell tap 仍可进 P11 但 P11 写按钮置灰 | B1 | `p10-readonly-banner` 出现仅当 `auth_state="observer"` |

> **fe-accept-mock 用法**：每条 AC 至少绑定 1 个 testid；缺一个即 fail。
> **AC 缺失处理**：业务文档 §2A.4 P10 仅给出"网格 + T 级色点"概念，未细化 LOADING 与 observer 态，已用 `[AI 推测]` 标注供业务方 review。

---

## §9 异常路径

| 触发 | UI | 系统行为 |
|---|---|---|
| API 网络失败 | B3 区域错误占位 "加载失败 · 重试" 蓝色链接 | 不消费埋点；重试按钮调同一接口 |
| 切月超出范围（如 2099 年） | B1 "下一月"按钮 disabled · 灰色 | 后端可拒收且降级 toast "已到末月" |
| cell 事件 > 99 个 | dot 区显示 "+99" | 不过 99；点击进入当日列表（P11 当日聚合视图） |
| 跨时区登录 | 顶部 toast "已切换到 LA 时区" | 重新拉接口（来源：SC-08 步 5） |
| 观察者会话过期 | 顶部 banner 突变红色 + 跳 P-LANDING | JWT 黑名单生效（SC-15 F07） |

---

## §10 埋点事件

| 事件名 | 触发 Block | 必带属性 |
|---|---|---|
| `home_open_full_calendar` | 入口（来自 P-HOME） | `from=weekstrip\|quickentries`, `month` |
| `calendar_view` | 进入 P10 | `month`, `total_events`, `has_today` |
| `calendar_change_month` | B1 上/下/回到今日 | `from_month`, `to_month`, `direction=prev\|next\|today` |
| `calendar_event_tap` | B3 cell tap | `eventId`, `relationType`, `cellIndex` |
| `calendar_filter_toggle` | B4 图例 tap | `key=showStudy`, `value=on\|off` |
| `obs_calendar_view` | 观察者进入 | `student_id_hash`, `month` |

> 所有事件经 `packages/analytics` 包；`obs_*` 事件携带 `student_id_hash` 不带原始 PII。

---

## §11 性能预算

- TTI ≤ 800ms（首次进入）
- LCP ≤ 600ms（MonthGrid 视为 LCP 元素）
- CLS < 0.05
- API P95 ≤ 500ms（来源 §5）
- 切换月份 → 新 LOADING → READY 渐入 < 250ms
- 骨架屏与数据切换 < 100ms 渐入

---

## §12 A11y

- Landmarks: `<main role="main">` 包裹 B1+B2+B3+B4；B1 用 `<nav role="navigation" aria-label="月份切换">`；B3 用 `<table role="grid" aria-label="2026 年 4 月日历">`
- 焦点顺序: 上一月 → 月份标题 → 下一月 → 回到今天 → 网格 cell（按 row 主序）→ 图例
- 屏幕阅读器朗读优先级: 月份标题（aria-live="polite"，月份切换时朗读）；今日 cell `aria-label="今日 4 月 22 日 · 8 个事件"`
- `prefers-reduced-motion`: 关闭 cell tap 的 scale(0.97)；月份切换 fade 改为瞬切

---

## §13 Mockup 锚定

- **权威**: `design/mockups/wrongbook/10_calendar_month.html` (v2)
- **历史变体**: 早期 v1 版本（无 testid · 无 mood 标注）已废弃，本次 spec 触发 v2 重做
- **截图**: `design/system/screenshots/P10-v2-light.png`（fe-preflight 自动生成）
- **反向锚定**: mockup HTML `<head>` 含 `<meta name="design-spec" content="P10-calendar-month.spec.md@v2">`

---

## §14 Tokens 清单（grep 校验用）

> mockup HTML 的 CSS 中**每一处** hex / rgb / px 数值必须能映射回这张清单中的 token。grep 不命中 = lint fail。

```
L1 (Apple base):
  --tkn-color-primary-DEFAULT
  --tkn-color-primary-dark
  --tkn-color-system-danger-DEFAULT
  --tkn-color-text-on-dark
  --tkn-font-display
  --tkn-font-text
  --tkn-type-display-hero
  --tkn-type-sub-heading
  --tkn-type-body
  --tkn-type-caption
  --tkn-type-caption-bold
  --tkn-spacing-2
  --tkn-spacing-xs
  --tkn-spacing-sm
  --tkn-spacing-12
  --tkn-spacing-md
  --tkn-spacing-lg
  --tkn-spacing-xl
  --tkn-radius-xs
  --tkn-radius-sm
  --tkn-radius-md
  --tkn-radius-lg
  --tkn-radius-pill
  --tkn-radius-circle
  --tkn-shadow-focus
  --tkn-motion-duration-fast
  --tkn-motion-duration-base
  --tkn-motion-ease-standard

L2 (warmth · 整页 mood=warm):
  --tkn-color-bg-light
  --tkn-color-card
  --tkn-color-bg-light
  --tkn-color-text-primary
  --tkn-color-text-secondary
  --tkn-color-sep
  --tkn-color-encouragement-DEFAULT
  --tkn-color-encouragement-soft
  --tkn-shadow-card-deep

L3 (celebration):
  (本页不使用 · 庆祝白名单不含 P10)

EXCEPTION (subject):
  --tkn-subject-math
  --tkn-subject-physics
  --tkn-subject-chemistry
  --tkn-subject-english
```

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
