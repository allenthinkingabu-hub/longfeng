---
page_id: P09
name: 复习完成
name_en: Review Done
route_h5: /review/done/:nodeId
route_miniprogram: pages/review/done
deeplink: wb://review/done/:nodeId
auth_state: authenticated
persona:
  - P1-K12
scenarios:
  - SC-02
  - SC-03
  - SC-04
mockup_canonical: design/mockups/wrongbook/09_review_done.html
mockup_version: v1
last_reviewed: 2026-05-02
status: spec-draft
sprint: S1
---

# P09 · 复习完成

> 与 DESIGN.md §5.1 第 5 张页面对齐：全屏 `gradient-celebrate-green` 顶部 hero · CelebrateHero（800ms checkmark-pop + 1200ms ConfettiBurst 仅 all-done）· MemoryCurve complete · AI Advance Banner · 下次复习 + 加日历 CTA · 3 statistics 卡 · KP 掌握度变化条形图 · 双 CTA "继续 / 结束"。

---

## §1 页面目的（why · 1 句话）

给学生即时正反馈 + 让他看见记忆曲线刚被推进了一格 + 引导继续下一题或回首页。

---

## §2 ASCII 布局图

```
┌─────────────────────────────────────┐
│  [celebrate-hero · gradient-green]  │ ← mood=celebrate
│   800ms checkmark-pop 中央大对勾    │
│   1200ms ConfettiBurst 5 色粒子（仅 all-done）
│   "已掌握 ✓"  /  "今日全部完成 🎉"  │
├─────────────────────────────────────┤   ← mood=warm 切换
│  [memory-curve · variant=complete] │
│   T1 done → T2 ✓ pulse(刚做)       │
│   T3 encouragement orange pulse     │
│   T4-T6 灰色未来                    │
│                                     │
│  [p09-advance-banner · 暖橙底]     │
│   "今天的进度让你的曲线推进了 7 天" │
│                                     │
│  [p09-next-due-card]                │
│   "下次复习: 5 月 5 日 18:00"      │
│   [+ 加日历] (蓝 pill)              │
│                                     │
│  [p09-stats-row · 3 卡]             │
│   ┌已掌握 5┐ ┌部分 2┐ ┌遗忘 1┐    │
│                                     │
│  [p09-kp-chart · KP 掌握度变化]    │
│   韦达定理   ▰▰▰▰▰▰▰░░ 70 → 85    │
│   二次函数   ▰▰▰▰▰▰░░░░ 60 → 65   │
│                                     │
│  [p09-cta-row · 双按钮]             │
│   ┌──结束本次(灰)──┐ ┌──继续复习(蓝)──┐
└─────────────────────────────────────┘
                   · 顶 hero=celebrate · 信息区=warm
```

---

## §3 Block 清单（每块独立可实现单元）

| Block ID | 名称 | Section | Mood | Component | testid root | Tokens 用到 |
|---|---|---|---|---|---|---|
| `B1` | CelebrateHero（大对勾 + 渐变绿底） | hero | celebrate | M9.CelebrateHero | `celebrate-hero` | `--tkn-gradient-celebrate-green` · `--tkn-motion-celebrate-checkmark` · `--tkn-color-white` |
| `B2` | ConfettiBurst（仅 all-done 触发） | hero | celebrate | M10.ConfettiBurst | `confetti-burst` | `--tkn-color-celebrate-confetti-1..5` · `--tkn-motion-celebrate-confetti` |
| `B3` | MemoryCurve complete（刚做节点 + 下一节点高亮） | info | warm | M8.MemoryCurve(variant=complete) | `memory-curve` | `--tkn-color-mastery-mastered` · `--tkn-color-encouragement-DEFAULT` |
| `B4` | AI Advance Banner（暖橙底） | info | warm | L0.Banner | `p09-advance-banner` | `--tkn-color-encouragement-soft` · `--tkn-color-encouragement-DEFAULT` |
| `B5` | 下次复习日期 + 加日历 CTA | info | warm | L0.Card + L0.Button | `p09-next-due-card` | `--tkn-color-card` · `--tkn-color-primary-DEFAULT` · `--tkn-radius-pill` |
| `B6` | 今日战绩 3 统计卡 | info | warm | L0.Card × 3 | `p09-stats-row` | `--tkn-color-mastery-mastered/partial/forgot` (1px accent) · `--tkn-color-card` |
| `B7` | KP 掌握度变化条形图 | info | warm | L0.Custom HorizontalBar | `p09-kp-chart` | `--tkn-subject-math/physics/...` (条颜色) · `--tkn-color-text-primary` |
| `B8` | 底部 双 CTA（结束 / 继续） | info | warm | L0.Button × 2 | `p09-cta-row` | `--tkn-color-primary-DEFAULT` · `--tkn-color-sep` |

> **fe-preflight 用法**：把 mockup HTML 切成这些块；fe-builder 按块逐个实现并跑 lint。
> **铁律 3 注意**：B2 ConfettiBurst 仅在 `variant=all-done` 时渲染（"今日全部完成"），单题完成（variant=single）禁止 confetti。

---

## §4 数据契约（page-level interface）

```typescript
interface DonePageProps {
  nid: string;
  variant: 'single' | 'all-done' | 'streak';
  previousT: 'T0' | 'T1' | 'T2' | 'T3' | 'T4' | 'T5' | 'T6';
  nextT?: 'T1' | 'T2' | 'T3' | 'T4' | 'T5' | 'T6';
  nextDueAt?: string;                // ISO
  masteryPct: number;                 // 0-100
  advanceDays?: number;               // banner: 推进了 N 天
  todayStats: {
    mastered: number;
    partial: number;
    forgot: number;
    total: number;
  };
  kpDelta: Array<{
    kpId: string;
    kpName: string;
    subject: 'math' | 'physics' | 'chemistry' | 'english';
    oldPct: number;
    newPct: number;
  }>;
  plannedNodes: Array<{
    tLevel: string;
    status: 'done' | 'just-completed' | 'next-encouragement' | 'future';
    dueAt?: string;
  }>;                                  // 长度 = 6
  hasNext: boolean;                    // 决定 "继续复习" 是否启用
  isStreakMilestone: boolean;
  streakDays?: number;
}
```

> 类型不一致直接 fail。

---

## §5 API 触点

| Method | Path | 用途 | P95 预算 | 失败降级 |
|---|---|---|---|---|
| GET | `/api/review/nodes/{nid}/result` | 主聚合接口（complete view） | 400ms | 骨架屏 + 重试 1 次 |
| POST | `/api/review/sessions/{sid}/next` | 取下一题 nid | 300ms | 失败回 P-HOME |
| POST | `/api/calendar/events/{eid}/subscribe` | + 加日历 | 500ms | toast "稍后自动同步" |

---

## §6 状态机

| State | 触发 | UI 表现 |
|---|---|---|
| `LOADING` | 进入页面 | 骨架屏（hero + 曲线 + 统计） |
| `RESULT` | API 200 + variant=single | 单题完成 hero（无 confetti）+ 全量信息流 |
| `ALL_DONE` | API 200 + variant=all-done | 全部完成 hero + ConfettiBurst 1200ms |
| `STREAK` | API 200 + variant=streak | 复用 ALL_DONE + 数字 streak-bump 400ms |
| `EXITING` | B8 任一按钮 | 200ms 淡出 → P-HOME / P08 |
| `ERROR` | API non-2xx | 红条 + 重试按钮 |

---

## §7 跳转图

```
[入口]
  P08 GRADED ──→ P09
        │
        ├──[B5 加日历]────→ +系统日历，留在原页
        ├──[B8 继续复习]──→ P08 (next nid)
        ├──[B8 结束本次]──→ P-HOME
        └──[ALL_DONE 自动倒计时]→ P-HOME (用户无操作 5s 后)
```

---

## §8 AC 覆盖表（与 business-analysis.yml 双向锚定）

| AC ID | 验收点 | 涉及 Block | testid 验证点 |
|---|---|---|---|
| `AC-P09-001` | hero section `data-mood="celebrate"`，背景使用 `--tkn-gradient-celebrate-green` | B1 | `celebrate-hero` `data-mood="celebrate"` 存在；CSS 含 gradient-celebrate-green |
| `AC-P09-002` | variant=single 时不渲染 ConfettiBurst | B2 | `confetti-burst` 当 `variant=single` 时不存在 |
| `AC-P09-003` | variant=all-done 时 ConfettiBurst 1200ms 5 色粒子 | B2 | `confetti-burst-particle-{1..5}` 至少各 1 个 + 动画 `--tkn-motion-celebrate-confetti` |
| `AC-P09-004` (US-06) | MemoryCurve complete 中刚做节点为 mastered + 下一节点为 encouragement orange | B3 | `memory-curve-node-T2` `data-status="just-completed"` · `memory-curve-node-T3` `data-status="next-encouragement"` |
| `AC-P09-005` | AI Advance Banner 文案 "今天的进度让你的曲线推进了 N 天"（暖橙底） | B4 | `p09-advance-banner-text` 文案匹配 `推进了\s+\d+\s+天` |
| `AC-P09-006` | 3 statistics 卡分别带 mastered/partial/forgot 1px 内描边 | B6 | `p09-stats-row-mastered` 等 3 个 testid 各自 `border-color` 解析 |
| `AC-P09-007` | "继续复习" 按钮使用 primary blue（铁律 1） | B8 | `p09-cta-row-continue-btn` `background-color` 解析 = `#007AFF` |
| `AC-P09-008` | "+ 加日历" CTA 调用 `POST /api/calendar/events/.../subscribe` | B5 | `p09-next-due-card-add-calendar-btn` 点击后埋点 `wb_done_add_calendar` |
| `AC-P09-009` | KP 掌握度条形图按学科色横向显示 newPct，oldPct 显示为浅色对比 | B7 | `p09-kp-chart-row-{n}-bar-new` 颜色 = subject 色 |
| `[AI 推测] AC-P09-010` | streak milestone（7/30/100 天）时数字 streak-bump 400ms | B1 | `celebrate-hero-streak-number` 含 `--tkn-motion-celebrate-streak-bump` 动画 |
| `[AI 推测] AC-P09-011` | `prefers-reduced-motion` 启用时不渲染 ConfettiBurst | B2 | `@media (prefers-reduced-motion)` 规则下 `confetti-burst` `display: none` |

> **fe-accept-mock 用法**：用这张表逐条断言；缺 testid = 验收 fail。

---

## §9 异常路径

| 触发 | UI | 系统行为 |
|---|---|---|
| `/result` 失败 | 骨架屏 + "加载失败 重试" | 重试 1 次后回 P-HOME |
| `/next` 失败 | 继续按钮 loading 失败 | 红 toast 重试，或自动回 P-HOME |
| 加日历失败 | toast "稍后自动同步" | outbox 重试 3 次 |
| FORGOT 进入 | hero 切换为 RESULT 中性态（无 confetti），文案 "记住了新的起点" | variant=single + 特殊文案 |

---

## §10 埋点事件

| 事件名 | 触发 Block | 必带属性 |
|---|---|---|
| `wb_done_view` | 页面进入 | `nid`, `nextT`, `variant` |
| `wb_done_session_complete` | ALL_DONE | `total`, `mastered`, `partial`, `forgot` |
| `wb_done_continue` | B8 继续 | `prevNid`, `nextNid` |
| `wb_done_exit` | B8 结束 | `nid`, `returnTo: 'home' \| 'calendar'` |
| `wb_done_add_calendar` | B5 | `nid`, `eid` |
| `wb_done_streak_milestone` | STREAK | `streakDays` |

---

## §11 性能预算

- TTI ≤ 600ms（从 P08 跳过来）
- LCP ≤ 800ms（CelebrateHero）
- CLS < 0.05（confetti 浮层不影响布局）
- API P95 / result ≤ 400ms · next ≤ 300ms · subscribe ≤ 500ms
- ConfettiBurst 1200ms 内完成且 cleanup（不留 DOM 痕迹影响交互）

---

## §12 A11y

- Landmarks: `<header role="banner">` (B1), `<main role="main">` (B3-B7), `<footer role="contentinfo">` (B8)
- 焦点顺序: B5 加日历 → B8 结束 → B8 继续（继续是主 CTA，焦点终点）
- 屏幕阅读器优先级: B1 `<div role="alert" aria-live="assertive">` 朗读 "复习完成 · 已掌握"；B2 ConfettiBurst `aria-hidden="true"`（装饰）
- `prefers-reduced-motion`: 关闭 confetti、关闭 checkmark-pop（直接显示对勾）、关闭 streak-bump、关闭曲线节点脉冲

---

## §13 Mockup 锚定

- **权威**: `design/mockups/wrongbook/09_review_done.html` (v1)
- **历史变体**: 无
- **截图**: `design/system/screenshots/P09-v1-light.png`（fe-preflight 自动生成）
- **反向锚定**: mockup HTML `<head>` 中含 `<meta name="design-spec" content="P09-review-done.spec.md@v1">`

---

## §14 Tokens 清单（grep 校验用）

```
L1 (Apple base):
  --tkn-color-primary-DEFAULT
  --tkn-color-white
  --tkn-color-text-on-dark
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
  --tkn-spacing-3xl
  --tkn-radius-sm
  --tkn-radius-md
  --tkn-radius-lg
  --tkn-radius-pill
  --tkn-radius-circle
  --tkn-shadow-focus
  --tkn-motion-dur-base
  --tkn-motion-ease-standard

L2 (warmth · 仅 mood=warm/celebrate 区段使用):
  --tkn-color-bg-light
  --tkn-color-card
  --tkn-color-text-primary
  --tkn-color-text-secondary
  --tkn-color-sep
  --tkn-color-encouragement-DEFAULT     (B3 next 节点 + B4 banner 文字)
  --tkn-color-encouragement-soft        (B4 banner 底色)
  --tkn-gradient-celebrate-green        (B1 hero 背景)
  --tkn-shadow-card-deep
  --tkn-shadow-hero-card

L3 (celebration · 仅庆祝白名单时刻):
  --tkn-color-mastery-forgot            (B6 forgot 卡 1px 内描边)
  --tkn-color-mastery-partial           (B6 partial 卡 1px 内描边)
  --tkn-color-mastery-mastered          (B3 just-completed 节点 · B6 mastered 卡 1px)
  --tkn-color-celebrate-confetti-1      (B2 ConfettiBurst 粒子 1)
  --tkn-color-celebrate-confetti-2
  --tkn-color-celebrate-confetti-3
  --tkn-color-celebrate-confetti-4
  --tkn-color-celebrate-confetti-5
  --tkn-color-streak-fire               (B1 streak variant 火焰)
  --tkn-motion-celebrate-checkmark      (B1 大对勾 800ms)
  --tkn-motion-celebrate-confetti       (B2 1200ms)
  --tkn-motion-celebrate-streak-bump    (B1 streak 数字 400ms)
  --tkn-ease-celebrate-bounce-out       (B1 入场缓动)

EXCEPTION (subject):
  --tkn-subject-math
  --tkn-subject-physics
  --tkn-subject-chemistry
  --tkn-subject-english
```

> 本页是设计系统中**唯一**完整使用 L3 庆祝层的页面（white-listed celebration moment）。无 iron rule 1 例外（继续 CTA 使用 primary blue）。
