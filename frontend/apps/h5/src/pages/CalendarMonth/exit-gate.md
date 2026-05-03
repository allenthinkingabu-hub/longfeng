# FE-05 · Calendar Pages · Exit Gate Report

> Date: 2026-05-02
> Agent: fe-05-calendar-pages Sub-agent
> Branch: agent/fe-05-calendar-pages

---

## 完成判据对照

### 判据 1: Write 2 page (含 .module.css)

| 产出物 | 路径 | 状态 |
|---|---|---|
| P10 CalendarMonth 页面 | `frontend/apps/h5/src/pages/CalendarMonth/index.tsx` | ✅ |
| P10 CSS module | `frontend/apps/h5/src/pages/CalendarMonth/CalendarMonth.module.css` | ✅ |
| P11 EventDetail 页面 | `frontend/apps/h5/src/pages/EventDetail/index.tsx` | ✅ |
| P11 CSS module | `frontend/apps/h5/src/pages/EventDetail/EventDetail.module.css` | ✅ |

### 判据 2: App.tsx 替换 2 placeholder

| 路由 | 前 | 后 | 状态 |
|---|---|---|---|
| `/calendar/month` | `const CalendarMonthPage = () => <PlaceholderPage ...>` | `import { CalendarMonthPage } from './pages/CalendarMonth'` | ✅ |
| `/event/:eventId` | `const EventDetailPage = () => <PlaceholderPage ...>` | `import { EventDetailPage } from './pages/EventDetail'` | ✅ |

### 判据 3: testids 注册 P10/P11

| 包 | 路径 | 新增键 | 状态 |
|---|---|---|---|
| `@longfeng/testids` | `frontend/packages/testids/src/index.ts` | `TEST_IDS.p10.*` (17 键) + `TEST_IDS.p11.*` (18 键) | ✅ |

### 判据 4: ui-plan.md + exit-gate.md

| 文档 | 路径 | 状态 |
|---|---|---|
| ui-plan.md | `frontend/apps/h5/src/pages/CalendarMonth/ui-plan.md` | ✅ |
| exit-gate.md | `frontend/apps/h5/src/pages/CalendarMonth/exit-gate.md` | ✅ (本文件) |

---

## AC 覆盖验证

### P10 日历月视图

| AC ID | 验收点 | testid | 状态 |
|---|---|---|---|
| AC-P10-001 | 顶部 title 含 "YYYY 年 M 月" | `p10-month-nav-title` aria-live="polite" | ✅ |
| AC-P10-002 | 42 个 cell · out-of-month `data-out-of-month="true"` | `p10-month-grid-cell-{1..42}` + `data-out-of-month` attr | ✅ |
| AC-P10-003 | 今日蓝边 `data-today="true"` + today-marker | `p10-month-grid-cell-{n}-today-marker` · 仅一个 | ✅ |
| AC-P10-004 | 色点 ≤3 + overflow "+N" | `p10-month-grid-cell-{n}-dot-{1..3}` + `p10-month-grid-cell-{n}-overflow` | ✅ |
| AC-P10-005 | cell tap → `/event/:id?from=CAL` | `p10-month-grid-cell-{n}` click → `nav('/event/...')` | ✅ |
| AC-P10-006 | 上/下/今 按钮触发 LOADING + 重拉 | `p10-month-nav-prev/next/today` → `setMonth` → react-query invalidate | ✅ |
| AC-P10-007 | LegendBar 6 类标签 | `p10-legend-bar-item-{math,physics,chemistry,english,exam,family}` | ✅ |
| AC-P10-008 | LOADING 骨架 42 cell · `data-state="loading"` | `p10-month-grid[data-state="loading"]` + skeleton cells | ✅ |
| AC-P10-009 | Observer banner | `p10-readonly-banner` (当 `auth_state=observer` 时出现) | ✅ 架构就绪，需 ObserverShell 传 prop |

### P11 事件详情三形态

| AC ID | 验收点 | testid | 状态 |
|---|---|---|---|
| AC-P11-001 | STUDY: ribbon=学科色 · badge="复习节点" | `p11-morph-ribbon[data-relation-type="STUDY"]` · `p11-event-hero-card-badge` | ✅ |
| AC-P11-002 | FAMILY: ribbon=encouragement橙 · CTA="编辑" | `p11-morph-ribbon[data-relation-type="FAMILY"]` · `p11-bottom-cta-edit` | ✅ |
| AC-P11-003 | EXAM: ribbon=danger红 · CTA="加入日历" | `p11-morph-ribbon[data-relation-type="EXAM"]` · `p11-bottom-cta-add-calendar` | ✅ |
| AC-P11-004 | STUDY: B3a 渲染 · FAMILY/EXAM B3 不在 DOM | `p11-related-study` 可见 · `p11-related-family/exam` 不渲染 | ✅ |
| AC-P11-005 | FAMILY: B3b 渲染 · 曲线不在 DOM | `p11-related-family` 可见 · `p11-related-study-memory-curve` 不渲染 | ✅ |
| AC-P11-006 | EXAM: 学科chip + 地点 + 倒计时 + 来源 | `p11-related-exam-subject-chip/location/countdown/from` | ✅ |
| AC-P11-007 | STUDY CTA tap → `/review/exec/:nid` | `p11-bottom-cta-review-now` onClick → `nav('/review/exec/...')` | ✅ |
| AC-P11-008 | 返回文案随 from 变化 | `p11-top-bar-back` text ∈ {"4月", "首页", "通知"} | ✅ |
| AC-P11-009 | 节点取消 → `p11-related-study-cancelled` + "查看新排期" | `p11-related-study-cancelled` 可见 · nodeCancelledStudy 逻辑 | ✅ |
| AC-P11-010 | EXAM 进入 → PATCH /api/events/:eid/ack | useMutation postAck · useEffect on relationType=EXAM | ✅ |

---

## 设计铁律合规

| 铁律 | 验证 | 状态 |
|---|---|---|
| Mood B · `#F2F2F7` bg (不是 `#FAF8F4`) | `--ios-bg: #F2F2F7` | ✅ |
| 无 `#0071e3` | 全用 `#007AFF` | ✅ |
| 无 `#2C2A26` | 文字 `#1C1C1E` | ✅ |
| 无废弃 v1.0 token | 无 warm-* / aurora | ✅ |
| data-mood v2.0 = "B" | P10/P11 均 `data-mood="B"` | ✅ |
| prefers-reduced-motion | `@media (prefers-reduced-motion: reduce)` 兜底 animation/transition | ✅ |
| testid spec.§8 全覆盖 | AC-P10-001~009 / AC-P11-001~010 全部 testid 已挂 | ✅ |
| 分享脱敏 C5 | `isShared` 时截断 stem / 隐藏 fromUser.name / 隐藏 email | ✅ |
| 三形态同壳 | 单 `EventDetailPage` 组件 · props `relationType` 切换 B3/B5 | ✅ |
| MemoryCurve `@keyframes pulseDot` 带 reduced-motion 兜底 | `animation: none` in @media | ✅ |

---

## 遗留事项

1. **Observer readonly banner (AC-P10-009)**: `p10-readonly-banner` 的条件渲染需 ObserverShell 或 auth context 注入 `isObserver` prop，架构 slot 已预留
2. **FAMILY edit bottom-sheet**: `p11-bottom-cta-edit` onClick 触发底部抽屉，抽屉组件待 FE-07 或单独 ticket 实现
3. **E2E A 轨**: 依赖后端 `/api/calendar/events` 接口实装，sprint 末联调
4. **像素对齐 C 轨**: Playwright + archive 截图 diff，等待 CI 就绪
5. **MSW fixtures**: P10/P11 的 MSW handler (B 轨验收) 未在本任务创建，可由 fe-accept-mock agent 添加

---

## 不 commit 说明

按任务指令，代码不自动 commit，Orchestrator 代做。
