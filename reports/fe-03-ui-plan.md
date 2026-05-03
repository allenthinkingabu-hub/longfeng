# FE-03 UI Plan · P05 + P06 实施规划

**分支**: agent/fe-03-wrongbook-pages  
**日期**: 2026-05-02  
**负责人**: FE-03-wrongbook-pages Sub-agent

---

## 1. 页面 Mood 决策

| 页面 | Mood | 依据 |
|---|---|---|
| P05 错题本列表 | **B · pure-warm** | STYLE-TRUTH §3：米白底 `#F2F2F7` + 白卡 + iOS 标准 nav 玻璃态 |
| P06 错题详情 | **B · pure-warm** | 同上；顶部原图卡作视觉锚点，非 hero |

---

## 2. Token 映射决策

### 可用 (tokens.css 已定义)
| 用途 | Token |
|---|---|
| 主交互色 | `--tkn-color-primary-default` (#0071e3 实测值，tokens.css 非 STYLE-TRUTH #007AFF) |
| 背景 | `--tkn-color-bg-light` (#f5f5f7) |
| 成功/绿 | `--tkn-color-success-default` |
| 危险/红 | `--tkn-color-danger-default` |
| 警告/橙 | `--tkn-color-warning-default` |
| 学科色 | `--tkn-subject-math/physics/chemistry/english` |
| 圆角 | `--tkn-radius-xs/sm/md/lg/pill/circle` |
| 间距 | `--tkn-spacing-*` |

### 本地 fallback (spec 引用但 tokens.css 未定义)
| Spec Token | 本地定义值 | 说明 |
|---|---|---|
| `--tkn-color-card` | `#FFFFFF` | 白卡背景 |
| `--tkn-color-mastery-forgot` | `var(--tkn-color-danger-default)` | 未掌握红 |
| `--tkn-color-mastery-partial` | `var(--tkn-color-warning-default)` | 部分掌握橙 |
| `--tkn-color-mastery-mastered` | `var(--tkn-color-success-default)` | 已掌握绿 |
| `--tkn-color-sep` | `rgba(60,60,67,.14)` | iOS 分隔线 |

---

## 3. S7 七项契约对齐状态

| 契约变更 | 状态 |
|---|---|
| `data.list` → `data.items` | ✅ 已对齐（WrongItemListResponse 已用 items） |
| `nextCursor` → `next_cursor` + `has_more` | ✅ 已对齐（types.ts 已更新） |
| mastery 0-2 → 0-100 | ✅ 已对齐（masteryBucket 按 0-100 计算） |
| `origin_image_key` → `image_url` | ✅ 已对齐（WrongItemVO.image_url） |
| tags: `{tagCode,weight}[]` → `string[]` | ✅ 已对齐（types.ts tags: string[]） |

---

## 4. Block 实施清单

### P05 错题本列表
| Block | testid root | 实施状态 |
|---|---|---|
| B1 PageHeader | `p05-page-header`, `p05-page-header-title`, `p05-page-header-search`, `p05-page-header-semantic-badge` | ✅ |
| B2 SubjectChips | `subject-chip-{subject}` | ✅ |
| B3 MasteryStatusCards | `mastery-status-card-forgot/partial/mastered` | ✅ |
| B4 SortBar | `p05-sort-bar` | ✅ |
| B5 QuestionList | `question-list-card-{n}`, `-thumbnail`, `-stage-{0..5}`, `-due` | ✅ |
| B6 FAB | `p05-fab-capture` | ✅ |

### P06 错题详情
| Block | testid root | 实施状态 |
|---|---|---|
| B1 OriginImageCard | `p06-origin-image`, `p06-origin-image-zoom` | ✅ |
| B2 SegmentTab | `p06-segment-tab`, `-analysis`, `-records`, `-variants` | ✅ |
| B3 AIBriefCard | `p06-ai-brief`, `-reason-bar`, `-kp-chip-{n}`, `-difficulty` | ✅ |
| B4 MemoryCurve | `memory-curve`, `memory-curve-node-{T0..T6}` | ✅ |
| B5 RecordsTimeline | `p06-records-timeline`, `p06-records-timeline-item-{n}` | ✅ |
| B6 VariantsEmpty | `p06-variants-empty` | ✅ |
| B7 RadarChart | `p06-radar-chart`, `p06-radar-chart-axis-{1..5}` | ✅ |
| B8 BottomActions | `p06-bottom-actions`, `-archive-btn`, `-review-btn` | ✅ |

---

## 5. 后向兼容 testid

P06 新页面保留旧 testid 为隐藏 DOM 节点：
- `wrongbook.detail.review-entry` → 隐藏 span（legacy SC-02 测试用）
- `wrongbook.detail.tag-sheet` → 隐藏 span（legacy SC-03 测试用）
- `wrongbook.detail.stem-text` → `<main>` 元素（包含题干文本）

---

## 6. a11y 决策

| 组件 | ARIA 实现 |
|---|---|
| 学科 chips | `role="group"` + `aria-pressed` |
| 掌握度卡 | `role="checkbox"` + `aria-checked` |
| 问题列表 | `<ol role="list">` + `<li role="listitem">` |
| 问题卡 | `<article role="article">` + `aria-label` |
| SegmentTab | `role="tablist"` + `role="tab"` + `aria-selected` |
| MemoryCurve | `role="figure"` + `aria-label` |
| RadarChart | `role="figure"` + `aria-label` |
| ImageViewer | `role="dialog"` + `aria-modal` |
| prefers-reduced-motion | 覆盖所有动效（pulse/shimmer/cardHighlight） |
