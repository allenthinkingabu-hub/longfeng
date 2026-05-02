---
page_id: P07
name: 今日待复习
name_en: Today Review
route_h5: /review
route_miniprogram: pages/review/today
deeplink: wb://review/today
auth_state: authenticated
persona:
  - P1-K12
scenarios:
  - SC-03
  - SC-04
mockup_canonical: design/mockups/wrongbook/07_review_today.html
mockup_version: v1
last_reviewed: 2026-05-02
status: spec-locked
sprint: S2
---

# P07 · 今日待复习

> **使用说明**：本 spec 对应 DESIGN.md §4.2 的 14 段标准结构。Sprint 2 复习启动页。
> **核心约束**：DESIGN.md §1 铁律 6 节奏二分 —— 上半 today-blue hero(celebrate) + 下半时段卡(warm)。

---

## §1 页面目的（why · 1 句话）

学生看到"今天具体哪几题、按时间窗如何排"，并能一键开始全部复习。

---

## §2 ASCII 布局图

```
┌─────────────────────────────────────┐
│  ░░░ today-blue 渐变 hero ░░░       │  ← mood=celebrate
│  ░ 深蓝 #1F3A93 → 明亮蓝 #4A6FE3   │
│  ░ + 气泡粒子（比 aurora 更沉稳）   │
│  ░                                  │
│  ░ B1 TodayReviewCard(today)        │
│  ░  ┌──────────────────────┐        │
│  ░  │ 今日 8 题 · 估计 24min │        │
│  ░  │ ▓▓▓▓░░░░  3/8 已完成  │        │
│  ░  │ 掌握度 64%             │        │
│  ░  └──────────────────────┘        │
├─────────────────────────────────────┤   ← mood 切换 cool→warm
│  B2 SlotGroup「现在·上午」          │  ← mood=warm 暖米白
│   ┌──┬─ 09:00 [T2] ─────────┐       │
│   │红│ 数学 · 二次函数        │       │
│   │  │ 倒计时 现在 (now=红)   │       │
│   └──┴───────────────────────┘       │
│   ┌──┬─ 11:30 [T3] ─────────┐       │
│   │蓝│ 物理 · 牛顿第二定律    │       │
│   │  │ 倒计时 30 分钟 (soon=橙)│      │
│   └──┴───────────────────────┘       │
│                                     │
│  B3 SlotGroup「下午」               │
│   ┌──┬─ 14:00 ──────────────┐       │
│   │绿│ 化学 ...               │       │
│   └──┴───────────────────────┘       │
│                                     │
│  B4 SlotGroup「晚上」               │
│   ┌──┬─ 19:30 ──────────────┐       │
│   │琥│ 英语 ... wait=灰      │       │
│   └──┴───────────────────────┘       │
│                                     │
├─────────────────────────────────────┤
│  B5 BottomCTA「全部开始」 sticky    │  ← 蓝 pill 全宽
└─────────────────────────────────────┘
```

---

## §3 Block 清单（每块独立可实现单元）

| Block ID | 名称 | Section | Mood | Component | testid root | Tokens 用到 |
|---|---|---|---|---|---|---|
| `B1` | TodayReviewCard 总览（today） | hero | celebrate | M.TodayReviewCard(variant=today) | `today-review-card` | `--tkn-gradient-today-blue`, `--tkn-color-card`, `--tkn-shadow-card-deep`, `--tkn-color-mastery-mastered`, `--tkn-color-text-on-dark`, `--tkn-radius-lg`, `--tkn-color-glass-white-18` |
| `B2` | SlotGroup 现在·上午 | info | warm | (custom block + ReviewItemCard[]) | `p07-slot-morning` | `--tkn-color-bg-light`, `--tkn-color-card`, `--tkn-subject-*`, `--tkn-color-system-danger-DEFAULT`, `--tkn-color-encouragement-DEFAULT`, `--tkn-color-text-secondary`, `--tkn-radius-lg`, `--tkn-shadow-card-deep` |
| `B3` | SlotGroup 下午 | info | warm | (custom block) | `p07-slot-afternoon` | 同 B2 |
| `B4` | SlotGroup 晚上 | info | warm | (custom block) | `p07-slot-evening` | 同 B2 |
| `B5` | BottomCTA 全部开始 sticky | info(sticky) | warm | (custom block) | `p07-bottom-cta` | `--tkn-color-card`, `--tkn-color-sep`, `--tkn-color-primary-DEFAULT`, `--tkn-color-white`, `--tkn-radius-pill`, `--tkn-shadow-hero-card` |

---

## §4 数据契约（page-level interface）

```typescript
interface ReviewTodayResp {
  date: string;                 // ISO YYYY-MM-DD
  tzOffset: number;             // 时区偏移分钟
  totalCount: number;
  doneCount: number;
  inProgressCount: number;
  waitCount: number;
  estMinutes: number;
  progressPct: number;          // 0-100
  masteryPct: number;           // 0-100
  slots: Array<{
    slotKey: 'now-morning' | 'afternoon' | 'evening';
    slotLabel: string;          // "现在·上午" / "下午" / "晚上"
    items: Array<{
      nid: string;
      qid: string;
      subject: 'math'|'physics'|'chemistry'|'english';
      tLevel: 'T0'|'T1'|'T2'|'T3'|'T4'|'T5'|'T6';
      hhmm: string;             // "09:00"
      stemSnippet: string;
      kpName?: string;
      countdown: 'now' | 'soon' | 'wait';
      countdownLabel: string;   // "现在" / "30 分钟" / "今晚 19:30"
      status: 'pending' | 'in-progress' | 'done';
    }>;
  }>;
}
```

---

## §5 API 触点

| Method | Path | 用途 | P95 预算 | 失败降级 |
|---|---|---|---|---|
| GET | `/api/review/today?tz=Asia/Shanghai` | 今日待复习列表（按时段聚合） | 400 ms | 走本地缓存节点 + system-warning 黄条 |
| POST | `/api/review/sessions` | 创建批量会话 → 跳 P08 | 300 ms | 失败 toast，留在 P07 |
| GET | `/api/review/nodes/{nid}` | 单题详情（点单卡时） | 300 ms | 失败 toast |
| POST | `/api/review/nodes/{nid}/open` | 单题打开 → 跳 P08 | 300 ms | 失败 toast |

---

## §6 状态机

| State | 触发 | UI 表现 |
|---|---|---|
| `LOADING` | 进入页面 | hero + 1 slot 骨架 |
| `READY` (`LIST`) | API 200 + items > 0 | 完整渲染时段分组 |
| `EMPTY` | totalCount === 0 | hero 文案 "今天没有复习安排，恭喜 🎉" + slots 区 EMPTY 插画 + CTA "拍一道新题" |
| `ALL_DONE` | doneCount === totalCount && totalCount > 0 | hero 文案 "今日全部完成 ✓" + 跳转 P09 庆祝（一次性） |
| `ERROR` | API non-2xx | 顶部 system-warning 黄条 + retry |

---

## §7 跳转图

```
[入口]
  Tab 4 「复习」 ─┐
  推送深链 ─┤
  P06 立即复习 ─┘──→ P07
        │
        ├──[B1 hero 静态]──────（不跳转，仅展示）
        ├──[B2/B3/B4 单卡片]──→ P08（POST nodes/{nid}/open）
        ├──[B5 全部开始]──────→ P08（POST review/sessions）
        ├──[NavBar 返回]──────→ Tab 1 P-HOME
        └──[ALL_DONE 触发]────→ P09（庆祝）
```

---

## §8 AC 覆盖表（与 business-analysis.yml 双向锚定）

| AC ID | 验收点 | 涉及 Block | testid 验证点 |
|---|---|---|---|
| `AC-REVIEW-TODAY-001` | hero 应用 today-blue 渐变（mood=celebrate）+ 气泡粒子 | B1 | `today-review-card[data-mood="celebrate"]` 父容器 background 命中 `--tkn-gradient-today-blue` |
| `AC-REVIEW-TODAY-002` | hero 内显示总数 + 已完成 + estMin + 进度条 + 掌握度% | B1 | `today-review-card-total` 文本 = totalCount · `today-review-card-done` 文本 = doneCount · `today-review-card-est-min` 文本含 estMin · `today-review-card-progress-bar` aria-valuenow = progressPct · `today-review-card-mastery-pct` 文本含 masteryPct |
| `AC-REVIEW-TODAY-003` | 时段分组按 slots 顺序渲染，每段标题 = slotLabel | B2,B3,B4 | `p07-slot-morning-title` / `p07-slot-afternoon-title` / `p07-slot-evening-title` 文本 = slotLabel |
| `AC-REVIEW-TODAY-004` | 每卡片：HH:MM + T-level pill + 学科色 4px 左条 + 题干 2 行 | B2,B3,B4 | `p07-slot-{key}-item-{index}-time` 含 HH:MM · `p07-slot-{key}-item-{index}-tlevel` 文本 = tLevel · `p07-slot-{key}-item-{index}` border-left-color = `--tkn-subject-{subject}` |
| `AC-REVIEW-TODAY-005` | 倒计时颜色：now=红 (system-danger) / soon=橙 (encouragement) / wait=灰 (warm-text-secondary) | B2,B3,B4 | `p07-slot-{key}-item-{index}-countdown[data-countdown="now"]` 颜色命中 `--tkn-color-system-danger-DEFAULT` 等 |
| `AC-REVIEW-TODAY-006` | 底部 sticky 蓝 pill "全部开始 N 题"，命中 primary token | B5 | `p07-bottom-cta-start-all-btn` `position: sticky/fixed` + bg = `--tkn-color-primary-DEFAULT` + 文本含 totalCount-doneCount |
| `AC-REVIEW-TODAY-007` | 单卡点击调 `POST nodes/{nid}/open` 后跳 P08 | B2,B3,B4 | `p07-slot-{key}-item-{index}` 触发请求 + 跳 `/review/exec/{nid}` |
| `AC-REVIEW-TODAY-008` | EMPTY 态：totalCount=0 时 hero 文案 + slots 空态插画 | B1 | hero 含 "今天没有复习安排" · `p07-empty-state` 可见 |
| `AC-REVIEW-TODAY-009` | [AI 推测] hero 粒子动画 60fps，prefers-reduced-motion 时关闭 | B1 | `today-review-card-particles` 在 reduced-motion 下 `display: none` |
| `AC-REVIEW-TODAY-010` | [AI 推测] ALL_DONE 自动一次性跳 P09 庆祝（不重复跳） | B1 | `today-review-card[data-state="all-done"]` 触发跳转 + 本会话 sessionStorage flag 防重 |

---

## §9 异常路径

| 触发 | UI | 系统行为 |
|---|---|---|
| `/api/review/today` 失败 | 顶部 system-warning 黄条 + 中部 retry | retry 重新请求 |
| 网络离线 | 走本地缓存节点 + 离线 banner | 缓存 TTL 6h |
| 节点跨天 | 后端 XXL-Job hourly 补偿（无前端表现） | 用户无感 |
| `POST review/sessions` 失败 | toast "稍后重试" | 留在 P07 |
| ALL_DONE 重复触发 | sessionStorage flag 防重 | 仅一次跳 P09 |
| EMPTY 态点 CTA | 跳 P02 | — |

---

## §10 埋点事件

| 事件名 | 触发 Block | 必带属性 |
|---|---|---|
| `wb_today_view` | 页面 ready | `count`, `estMin`, `masteryPct` |
| `wb_today_start_all` | B5 全部开始点击 | `count`, `estMin` |
| `wb_today_start_one` | B2/B3/B4 单卡点击 | `nid`, `slotKey`, `tLevel` |
| `wb_today_slot_view` | slot 进入视口 | `slotKey`, `itemCount` |

---

## §11 性能预算

- TTI ≤ 1200 ms
- LCP ≤ 1500 ms（hero 渐变区为 LCP）
- CLS < 0.05
- API P95 ≤ 400 ms
- hero 粒子动画 60fps（GPU 合成 transform/opacity）
- sticky 底部 CTA 始终可见，不被键盘遮挡

---

## §12 A11y

- Landmarks:
  - `<header role="banner">` 包裹 hero（B1）
  - `<main role="main">` 包裹 slots（B2-B4）
  - `<footer role="contentinfo">` 包裹 sticky CTA（B5）
- 焦点顺序: `B1 进度条 → B2 第 1 卡 → B2 第 N 卡 → B3 ... → B5 全部开始`
- 屏幕阅读器朗读优先级:
  - B1 进度用 `<progressbar role="progressbar" aria-valuenow={done} aria-valuemax={total} aria-valuetext="{done} of {total} completed">`
  - 每张卡片 `<article role="article" aria-label="数学 09:00 二次函数 现在复习">`
  - SlotGroup 用 `<section role="region" aria-label="现在·上午">`
- `prefers-reduced-motion: reduce` 兜底:
  - 关闭 hero 粒子动画
  - 关闭进度条 transition

---

## §13 Mockup 锚定

- **权威**: `design/mockups/wrongbook/07_review_today.html` (v1)
- **历史变体**: 无
- **截图**: `design/system/screenshots/P07-v1-light.png`
- **反向锚定**: mockup HTML `<head>` 含 `<meta name="design-spec" content="P07-review-today.spec.md@v1">`

---

## §14 Tokens 清单（grep 校验用）

```
L1 (Apple base):
  --tkn-color-primary-DEFAULT
  --tkn-color-text-primary
  --tkn-color-text-on-dark
  --tkn-color-white
  --tkn-color-system-danger-DEFAULT
  --tkn-color-system-warning-DEFAULT
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
  --tkn-motion-duration-base
  --tkn-motion-duration-slow

L2 (warmth):
  --tkn-color-bg-light
  --tkn-color-card
  --tkn-color-bg-light
  --tkn-color-text-primary
  --tkn-color-text-secondary
  --tkn-color-sep
  --tkn-color-encouragement-DEFAULT
  --tkn-color-glass-white-18
  --tkn-color-glass-white-08
  --tkn-gradient-today-blue
  --tkn-shadow-card-deep
  --tkn-shadow-hero-card

L3 (celebration):
  --tkn-color-mastery-mastered
  --tkn-color-mastery-partial

EXCEPTION (subject):
  --tkn-subject-math
  --tkn-subject-physics
  --tkn-subject-chemistry
  --tkn-subject-english
```
