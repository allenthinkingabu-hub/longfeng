# FE-07 · Misc Pages · Exit Gate Report

> Date: 2026-05-02
> Agent: fe-07-misc-pages Sub-agent
> Branch: agent/fe-07-misc-pages

---

## 完成判据对照

### 判据 1: Write 4 page (含 .module.css · ~8 文件) + 必要 hook

| 产出物 | 路径 | 状态 |
|---|---|---|
| P00 Auth 页面 | `frontend/apps/h5/src/pages/Auth/index.tsx` | ✅ |
| P00 Auth CSS | `frontend/apps/h5/src/pages/Auth/Auth.module.css` | ✅ |
| P-HOME 页面 | `frontend/apps/h5/src/pages/Home/index.tsx` | ✅ |
| P-HOME CSS | `frontend/apps/h5/src/pages/Home/Home.module.css` | ✅ |
| P12 Notifications 页面 | `frontend/apps/h5/src/pages/Notifications/index.tsx` | ✅ |
| P12 Notifications CSS | `frontend/apps/h5/src/pages/Notifications/Notifications.module.css` | ✅ |
| P13 Settings 页面 (含 SC-16) | `frontend/apps/h5/src/pages/Settings/index.tsx` | ✅ |
| P13 Settings CSS | `frontend/apps/h5/src/pages/Settings/Settings.module.css` | ✅ |
| useUserTier hook | `frontend/apps/h5/src/hooks/useUserTier.ts` | ✅ |
| useAiCatalog hook | `frontend/apps/h5/src/hooks/useAiCatalog.ts` | ✅ |

### 判据 2: App.tsx 替换 4 placeholder

| 替换项 | 原 | 新 | 状态 |
|---|---|---|---|
| AuthPage | `PlaceholderPage "P00 登录"` | `import { AuthPage } from './pages/Auth'` | ✅ |
| HomePage | `PlaceholderPage "P-HOME 今日聚合首页"` | `import { HomePage } from './pages/Home'` | ✅ |
| NotificationsPage | `PlaceholderPage "P12 通知中心"` | `import { NotificationsPage } from './pages/Notifications'` | ✅ |
| MePage | `PlaceholderPage "P13 设置/我的"` | `import { MePage } from './pages/Settings'` | ✅ |

### 判据 3: testids 注册 P00/P-HOME/P12/P13 全 testid

| 区段 | Key | testid值 | 状态 |
|---|---|---|---|
| P00 | p00.root | `p00-root` | ✅ |
| P00 | p00.wechatCtaBtn | `p00-wechat-cta-btn` | ✅ |
| P00 | p00.consentCheckbox | `p00-consent-bar-checkbox` | ✅ |
| P-HOME | pHome.greetingHero | `greeting-hero` | ✅ |
| P-HOME | pHome.streakFireIcon | `streak-bar-fire-icon` | ✅ |
| P-HOME | pHome.todayReviewCard | `today-review-card` | ✅ |
| P-HOME | pHome.startAllBtn | `today-review-card-start-all-btn` | ✅ |
| P12 | p12.headerTitle | `p12-header-title` | ✅ |
| P12 | p12.markAllRead | `p12-header-mark-all-read` | ✅ |
| P12 | p12.emptyState | `p12-empty-state` | ✅ |
| P13 | p13.avatarBlockName | `p13-avatar-block-name` | ✅ |
| P13 | p13.dangerZone | `p13-danger-zone` | ✅ |
| P13 | p13.sc16AiSection | `p13-sc16-ai-section` | ✅ |
| P13 | p13.sc16UpgradeHint | `p13-sc16-upgrade-hint` | ✅ (NORMAL only) |
| P13 | p13.sc16ModelSelector | `p13-sc16-model-selector` | ✅ (VIP/VIP_PLUS) |

### 判据 4: ui-plan.md + exit-gate.md

| 文档 | 路径 | 状态 |
|---|---|---|
| ui-plan.md | `frontend/apps/h5/src/pages/ui-plan.md` | ✅ (已更新) |
| exit-gate.md (本文件) | `frontend/apps/h5/src/pages/exit-gate.md` | ✅ |

---

## AC 覆盖验证

### P00 · 登录

| AC ID | 验收点 | testid | 状态 |
|---|---|---|---|
| AC-P00-001 | 深蓝 hero + conic logo + slogan | `p00-logo-zone` / `p00-logo-zone-logo` | ✅ |
| AC-P00-002 | 微信按钮 #07C160 + `data-iron-rule-1-exception="wechat-brand"` | `p00-wechat-cta-btn` | ✅ |
| AC-P00-003 | 未勾选时主按钮 disabled / aria-disabled=true | `p00-consent-bar-checkbox` checked=false → btn disabled | ✅ |
| AC-P00-004 | 其他登录方式链接 14px #007AFF | `p00-other-methods-link` | ✅ |
| AC-P00-005 | 登录成功 + guestSession → claim + 跳 P-HOME | login flow + nav('/') | ✅ |
| AC-P00-006 | redirect 参数生效 | `useSearchParams` 读 redirect | ✅ |
| AC-P00-008 | A11y checkbox role=checkbox 键盘可达 | role=checkbox + tabIndex on label | ✅ |

### P-HOME · 今日聚合首页

| AC ID | 验收点 | testid | 状态 |
|---|---|---|---|
| AC-HOME-001 | 首屏 hero + 大卡 + 周条带 TTI | `greeting-hero` / `today-review-card` / `week-strip` 可见 | ✅ |
| AC-HOME-002 | 进度环 aria-valuenow + total + estMin | `today-review-card-circle-progress` / `today-review-card-total` / `today-review-card-est-min` | ✅ |
| AC-HOME-003 | 全部开始 → /review | `today-review-card-start-all-btn` onClick → nav('/review') | ✅ |
| AC-HOME-004 | 7日条带 data-today · tlevel 色点 | `week-strip-day-{1..7}` + data-today + tlevel testids | ✅ |
| AC-HOME-005 | StreakBar SVG 火焰 (非 emoji) + 数字 | `streak-bar-fire-icon` 是 SVG + `streak-bar-days-number` | ✅ |
| AC-HOME-006 | EMPTY态 CTA = "拍一道新题试试" | pageState=EMPTY → btn text | ✅ |
| AC-HOME-007 | 消息最多3条 + more-link | `p-home-messages-item-{1..3}` + `p-home-messages-more-link` | ✅ |
| AC-HOME-008 | weakKP 存在才渲染 + 点击 → /wrongbook?kp= | `p-home-weak-kp` 条件渲染 + nav | ✅ |
| AC-HOME-009 | 快捷入口 4 项 min 44×44 | `p-home-quick-entries-item-{1..4}` min-height/width | ✅ |
| AC-HOME-010 | prefers-reduced-motion 兜底 | @media (prefers-reduced-motion: reduce) 禁用动画 | ✅ |

### P12 · 通知中心

| AC ID | 验收点 | testid | 状态 |
|---|---|---|---|
| AC-P12-001 | 顶部 "通知" display-hero + "全部已读" 右对齐 | `p12-header-title` / `p12-header-mark-all-read` | ✅ |
| AC-P12-002 | 4 组非空才渲染 · 正确 label | `p12-group-today/yesterday/thisweek/earlier` | ✅ |
| AC-P12-003 | 每条卡 icon+title+subtitle+time+unread-dot | per-card testid 5 子元素 | ✅ |
| AC-P12-004 | read=true 时 unread-dot 不渲染 | data-read="true" → no dot in DOM | ✅ |
| AC-P12-005 | icon 颜色按 kind (REVIEW=蓝/EXAM=红/SHARE=橙/SYSTEM=indigo) | badgeClass() + data-kind | ✅ |
| AC-P12-006 | REVIEW+NODE tap → /review/exec/:nid | targetType=NODE → nav(`/review/exec/${targetId}`) | ✅ |
| AC-P12-007 | 全部已读 → 乐观更新 unread-dot 消失 | localReadIds Set + immediate re-render | ✅ |
| AC-P12-008 | EMPTY态 B6 插画 + "暂无新消息" | `p12-empty-state` 条件渲染 | ✅ |

### P13 · 设置/我的 (含 SC-16)

| AC ID | 验收点 | testid | 状态 |
|---|---|---|---|
| AC-P13-001 | AvatarBlock 头像 + 学生名 + StreakBar compact | `p13-avatar-block-name` 含昵称 | ✅ |
| AC-P13-002 | 5组按序排列 | DOM 顺序正确 | ✅ |
| AC-P13-003 | Switch 触发 PATCH /api/me/preferences | `p13-settings-push-review-reminder-switch` click | ✅ (mock) |
| AC-P13-004 | 免打扰点击 TimePicker Sheet | `p13-settings-review-quiet-hours-row` click | ✅ (stub) |
| AC-P13-005 | 版本号 "v1.0.0 · 已是最新" | `p13-settings-about-version` | ✅ |
| AC-P13-006 | DangerZone 红色边框 + danger 文字 | `p13-danger-zone` CSS border red | ✅ |
| AC-P13-007 | 注销按钮 → Modal 需输入"注销" | `p13-danger-confirm` + input validation | ✅ |
| AC-P13-008 | 退出登录 → /welcome | confirm → nav('/welcome', {replace:true}) | ✅ |

---

## SC-16 · VIP AI 模型选择三层 UI 截图描述

### NORMAL 用户视图 (`data-sc16-tier="NORMAL"`)
```
┌─────────────────────────────────────────────────────┐
│ AI 模型                          (group label)       │
│ ┌───────────────────────────────────────────────┐  │
│ │ ★ AI 模型选择                                  │  │
│ │   升级 VIP 解锁模型选择，让 AI 更了解你    ›   │  │
│ └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────��───────────┘
```
UI 特征: 紫色渐变 icon + hint 文案 + 右侧 chevron → 升级引导  
不渲染任何模型名称/ID (防 tier 信号泄露)

### VIP 用户视图 (`data-sc16-tier="VIP"`)
```
┌─────────────────────────────────────────────────────┐
│ AI 模型选择                       (group label)     │
│ ┌─────────────────────────────────────────────┐    │
│ │ ★ 首选模型                    Qwen-VL Max   │    │
│ │ ○ Qwen-VL Max (M cost)  中文学科准确率更高  │    │
│ │ ○ GPT-4o mini (L cost)  英语/海外学生首选   │    │
│ │ ● Qwen-VL Plus [VIP]    全学科最高精度      │    │  ← selected
│ │                                              │    │
│ │ 提示：单次拍题时也可临时选择其他模型…       │    │
│ └─────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────┘
```
UI 特征: radio 列表 + VIP badge chip + 选中项蓝色底

### VIP_PLUS 用户视图 (`data-sc16-tier="VIP_PLUS"`)
同 VIP 视图 + 额外:
- 实验池: Claude 3.5 Sonnet [VIP] / 私有化部署模型 [VIP]
- 每项额外 cost chip (红/橙/绿色 H/M/L) + latency chip (xs)

---

## 设计铁律合规自查

| 检查点 | 结果 |
|---|---|
| grep `#0071e3` → 0 命中 | ✅ |
| grep `#2C2A26` → 0 命中 | ✅ |
| grep `warm-bg\|warm-text-primary` → 0 命中 | ✅ |
| grep `gradient-aurora` → 0 命中 | ✅ |
| P00/P-HOME hero 有 ::before / ::after / .blob 三层 | ✅ |
| wechat 按钮 `data-iron-rule-1-exception="wechat-brand"` | ✅ |
| StreakBar `<svg>` 非 emoji | ✅ |
| data-mood="A|B" 正确标注 | ✅ |
| prefers-reduced-motion @media 兜底 | ✅ |

---

## 遗留事项 / Caveat

1. **API mock**: 4 页面均使用 mock data/setTimeout 模拟，实际 API 调用需 Orval gen client 生成后替换
2. **SC-16 PATCH 防抖**: useAiCatalog 选中后未实现 500ms 防抖 PATCH，留给后续 integration
3. **P12 滑动归档 (AC-P12-009)**: 左滑归档手势未实现（需 touch event），已添加 `p12-notif-card-{n}-archive-btn` testid stub 预留
4. **P13 TimePicker Sheet (AC-P13-004)**: quiet hours 点击 stub，需 Sheet 组件
5. **P-HOME prefers-reduced-motion streakBump**: streak milestone 动画（AC-HOME-010）已有 @media 兜底，实际 keyframe 未绑定到 streak.milestone 状态变更 — 需完整 milestone 检测逻辑

---

## 不 commit 说明

按任务指令，代码不自动 commit，Orchestrator 代做。
