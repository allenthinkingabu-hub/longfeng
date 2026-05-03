# FE-04 · Review Pages · UI Plan

> Generated: 2026-05-02
> Agent: fe-04-review-pages Sub-agent
> Branch: agent/fe-04-review-pages

---

## 页面矩阵

| 页面 | 路由 | Mood | Archive 参考 | 状态 |
|---|---|---|---|---|
| P07 ReviewToday  | `/review` | B (pure-warm) | `_archive/07_review_today.html` | ✅ 实现 |
| P08 ReviewExec   | `/review/exec/:nodeId` | B (pure-warm) | `_archive/08_review_exec.html` | ✅ 实现 |
| P09 ReviewDone   | `/review/done` | D (celebrate-green) | `_archive/09_review_done.html` | ✅ 实现 |

---

## 文件清单

| 文件 | 说明 |
|---|---|
| `pages/ReviewToday/index.tsx` | P07 主页面组件 |
| `pages/ReviewToday/ReviewToday.module.css` | P07 样式 |
| `pages/ReviewExec/index.tsx` | P08 主页面组件 |
| `pages/ReviewExec/ReviewExec.module.css` | P08 样式 |
| `pages/ReviewDone/index.tsx` | P09 主页面组件 |
| `pages/ReviewDone/ReviewDone.module.css` | P09 样式 |
| `packages/testids/src/index.ts` | 追加 p07/p08/p09 testids + 动态 helpers |
| `App.tsx` | 替换 3 个 placeholder import 为真实页 |

---

## P07 Block 实现状态

| Block ID | 名称 | testid | AC 覆盖 |
|---|---|---|---|
| B1 | TodayReviewCard hero (深蓝渐变 + particles) | `today-review-card` | AC-REVIEW-TODAY-001/002/009/010 |
| B2 | SlotGroup 现在·上午 | `p07-slot-now-morning-header` | AC-REVIEW-TODAY-003/004/005 |
| B3 | SlotGroup 下午 | `p07-slot-afternoon-header` | AC-REVIEW-TODAY-003/004/005 |
| B4 | SlotGroup 晚上 | `p07-slot-evening-header` | AC-REVIEW-TODAY-003/004/005 |
| B5 | BottomCTA 全部开始 sticky | `p07-bottom-cta`, `p07-bottom-cta-start-all-btn` | AC-REVIEW-TODAY-006 |
| — | EMPTY 态 | `p07-empty-state` | AC-REVIEW-TODAY-008 |

## P08 Block 实现状态

| Block ID | 名称 | testid | AC 覆盖 |
|---|---|---|---|
| B1 | 顶部 4px 线状进度条 | `p08-progress-bar` | AC-P08-001 |
| B2 | "第 2/8 题" + × 关闭 | `p08-topbar`, `p08-topbar-cursor` | AC-P08-001/009 |
| B3 | 题元 chips | `p08-meta-chips` | — |
| B4 | 题干卡 | `p08-question-hero` | AC-P08-004 |
| B5 | 作答区 + 3 模式 | `p08-answer-area` | — |
| B6 | 揭示按钮 + 揭示内容 + 800ms checkmark | `p08-reveal-btn`, `p08-reveal-content`, `p08-reveal-checkmark` | AC-P08-004/005 |
| B7 | 6 节点 MemoryCurve progress | `memory-curve`, `memory-curve-node-{T}` | AC-P08-010 |
| B8 | 3 等宽 mastery 按钮 (IRON RULE 1 EXCEPTION) | `p08-grade-buttons`, `p08-grade-buttons-{forgot/partial/mastered}` | AC-P08-002/003/006/007 |

## P09 Block 实现状态

| Block ID | 名称 | testid | AC 覆盖 |
|---|---|---|---|
| B1 | CelebrateHero (Mood D 绿渐变 + checkmark-pop) | `celebrate-hero` | AC-P09-001 |
| B2 | ConfettiBurst (仅 all-done · prefers-reduced-motion 关) | `confetti-burst`, `confetti-burst-particle-{1..8}` | AC-P09-002/003/011 |
| B3 | MemoryCurve complete | `memory-curve`, `memory-curve-node-{T}` | AC-P09-004 |
| B4 | AI Advance Banner | `p09-advance-banner`, `p09-advance-banner-text` | AC-P09-005 |
| B5 | 下次复习 + 加日历 | `p09-next-due-card`, `p09-next-due-card-add-calendar-btn` | AC-P09-008 |
| B6 | 今日战绩 3 统计卡 | `p09-stats-row`, `p09-stats-row-{mastered/partial/forgot}` | AC-P09-006 |
| B7 | KP 掌握度条形图 | `p09-kp-chart`, `p09-kp-chart-row-{n}-bar-new` | AC-P09-009 |
| B8 | 双 CTA (结束/继续) | `p09-cta-row`, `p09-cta-row-continue-btn`, `p09-cta-row-end-btn` | AC-P09-007 |

---

## 设计铁律合规

| 铁律 | 状态 | 备注 |
|---|---|---|
| Mood B pure-warm (P07/P08) | ✅ | #F2F2F7 bg + 白卡 + iOS glass nav |
| Mood D celebrate-green (P09) | ✅ | linear-gradient(175deg,#0F7F3E,#1FAE5C,#34C759) |
| P08 自评 3 按钮 self-grading exception | ✅ | `data-iron-rule-1-exception="self-grading"` · mastery colors |
| P09 ConfettiBurst 仅 all-done | ✅ | `variant === 'all-done'` 触发 |
| prefers-reduced-motion 兜底 | ✅ | particles/confetti/animations all guarded |
| testid 覆盖 spec.§8 AC | ✅ | 全 AC 对应 testid 实现 |
| data-mood v2.0 | ✅ | P07/P08=B · P09=D (hero data-mood="celebrate") |
| D-Cancel-Race | ✅ | P08 退出弹二次确认 · node SCHEDULED 不取消 |
| 无硬编码废弃 token | ✅ | 无 warm-*/aurora/错误 hex |
| primary blue = #007AFF | ✅ | 验证无 #0071e3 |
| 无 warm 棕字 #2C2A26 | ✅ | 用 #1C1C1E |

---

## 路由注册 (App.tsx)

- `/review` → `ReviewTodayPage` (P07 · 真实组件)
- `/review/exec/:nodeId` → `ReviewExecPage` (P08 · 真实组件)
- `/review/done` → `ReviewDonePage` (P09 · 真实组件)
