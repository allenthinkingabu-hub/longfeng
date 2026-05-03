# FE-05 · Calendar Pages · UI Plan (P10 + P11)

> Generated: 2026-05-02
> Agent: fe-05-calendar-pages Sub-agent
> Branch: agent/fe-05-calendar-pages

---

## 页面矩阵

| 页面 | 路由 | Mood | Archive 参考 | 状态 |
|---|---|---|---|---|
| P10 日历月视图 | `/calendar/month` | B (pure-warm) | `_archive/10_calendar_month.html` | ✅ 实现 |
| P11 事件详情 (3 形态) | `/event/:eventId` | B (pure-warm) | `_archive/11_event_detail.html` | ✅ 实现 |

---

## P10 Block 实现状态

| Block ID | 名称 | testid | AC 覆盖 |
|---|---|---|---|
| B1 | MonthNav 月份导航 | `p10-month-nav`, `p10-month-nav-title`, `p10-month-nav-prev`, `p10-month-nav-next`, `p10-month-nav-today` | AC-P10-001, AC-P10-006 |
| B2 | WeekHeader 周表头 | `p10-week-header` | — |
| B3 | MonthGrid 7×6 日期网格 | `p10-month-grid`, `p10-month-grid-cell-{1..42}`, `p10-month-grid-cell-{n}-today-marker`, `p10-month-grid-cell-{n}-dot-{1..3}`, `p10-month-grid-cell-{n}-overflow` | AC-P10-002, AC-P10-003, AC-P10-004, AC-P10-005, AC-P10-008 |
| B4 | LegendBar 图例 | `p10-legend-bar`, `p10-legend-bar-item-{math,physics,chemistry,english,exam,family}` | AC-P10-007 |
| — | 只读 banner (Observer) | `p10-readonly-banner` | AC-P10-009 |
| — | Filter toggle | `p10-filter-study` | — |

---

## P11 Block 实现状态 (三形态同壳)

| Block ID | 名称 | testid | AC 覆盖 |
|---|---|---|---|
| B0 | TopBar 顶部返回栏 | `p11-top-bar`, `p11-top-bar-back` | AC-P11-008 |
| B1 | MorphRibbon 形态色条带 | `p11-morph-ribbon[data-relation-type]` | AC-P11-001, AC-P11-002, AC-P11-003 |
| B2 | EventHeroCard 事件卡 | `p11-event-hero-card`, `p11-event-hero-card-badge` | AC-P11-001 |
| B3a | RelatedStudy (STUDY) | `p11-related-study`, `p11-related-study-memory-curve`, `p11-related-study-cancelled` | AC-P11-004, AC-P11-009 |
| B3b | FamilyMemo (FAMILY) | `p11-related-family` | AC-P11-005 |
| B3c | ExamMeta (EXAM) | `p11-related-exam`, `p11-related-exam-subject-chip`, `p11-related-exam-location`, `p11-related-exam-countdown`, `p11-related-exam-from` | AC-P11-006 |
| B4 | MetaRow 元信息行 | `p11-meta-row` | — |
| B5 | BottomCta | `p11-bottom-cta`, `p11-bottom-cta-review-now`, `p11-bottom-cta-edit`, `p11-bottom-cta-add-calendar` | AC-P11-002, AC-P11-003, AC-P11-007 |

---

## 三形态同壳设计

P11 使用单一组件 `EventDetailPage`，通过 `relationType` 切换 B3/B5：

- `STUDY` → B3a (QuestionThumb + MemoryCurveCard) + B5 "立即复习" 蓝 pill
- `FAMILY` → B3b (备忘文本卡) + B5 "编辑" pill (shared 时隐藏)
- `EXAM` → B3c (学科chip + 地点 + 倒计时) + B5 "加入日历" 蓝 pill
- `shared` (shareToken in URL) → 读 P-SHARED 路径 + C5 脱敏 + 注册 CTA

---

## 设计铁律合规

| 铁律 | 验证 | 状态 |
|---|---|---|
| Mood B pure-warm · `#F2F2F7` bg | 两页均 `data-mood="B"` · `.root` bg = `#F2F2F7` | ✅ |
| 无 `#0071e3` | 仅使用 `#007AFF` 或 `var(--ios-blue)` | ✅ |
| 无 `#2C2A26` | 文字色 `#1C1C1E` | ✅ |
| 无 `#FAF8F4` | 背景 `#F2F2F7` | ✅ |
| 无废弃 v1.0 token | 无 `--tkn-color-warm-*` / `--tkn-gradient-aurora` | ✅ |
| data-mood v2.0 | 两页均 `data-mood="B"` | ✅ |
| prefers-reduced-motion | 各页 `@media` 兜底 animation/transition: none | ✅ |
| testid 覆盖 spec.§8 AC | 全部 AC-P10-001~009 / AC-P11-001~010 testid 已注册 | ✅ |
| 分享脱敏版 C5 | 隐藏 student email / questionStem 截断 / fromUser.name 隐藏 | ✅ |

---

## App.tsx 路由集成

- `/calendar/month` → `CalendarMonthPage` (真实页，替换 placeholder)
- `/event/:eventId` → `EventDetailPage` (真实页，替换 placeholder)
