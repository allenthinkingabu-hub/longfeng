# FE-07 · Misc Pages · UI Plan

> Updated: 2026-05-02
> Agent: fe-07-misc-pages Sub-agent
> Branch: agent/fe-07-misc-pages
> Supersedes: FE-06 partial entries (still valid below)

---

## 页面矩阵

| 页面 | 路由 | Mood | Archive 参考 | 状态 |
|---|---|---|---|---|
| P00 Auth | `/auth` | A (hero+overlap 380px) | STYLE-TRUTH §6 (archive 缺失 · 按指引) | ✅ FE-07 实现 |
| P-HOME | `/` | A (hero+overlap 240px) | `_archive/01_home.html` 1:1 | ✅ FE-07 实现 |
| P12 Notifications | `/notifications` | B (pure-warm) | `_archive/12_notifications.html` | ✅ FE-07 实现 |
| P13 Settings + SC-16 | `/me` | B (pure-warm) | `_archive/13_settings.html` | ✅ FE-07 实现 |
| P-LANDING | `/welcome` | A (hero+overlap) | `_archive/14_landing.html` | ✅ FE-06 保留 |
| P-GUEST-CAPTURE | `/guest/capture` | C (dark-camera) | `_archive/15_guest_capture.html` | ✅ FE-06 保留 |
| P-SHARED | `/s/:shareToken` | E (teal-observer) | `_archive/16_shared.html` | ✅ FE-06 保留 |

---

## Hooks

| Hook | 文件 | 用途 | 状态 |
|---|---|---|---|
| `useUserTier` | `hooks/useUserTier.ts` | SC-16: 获取用户 tier (NORMAL/VIP/VIP_PLUS) | ✅ FE-07 新增 |
| `useAiCatalog` | `hooks/useAiCatalog.ts` | SC-16: GET /api/ai/models tier 过滤后 catalog | ✅ FE-07 新增 |
| `useDeviceFingerprint` | `hooks/useDeviceFingerprint.ts` | 5来源指纹 hash | ✅ FE-06 保留 |

---

## P00 · 登录 Block 实现状态

| Block ID | 名称 | testid | AC 覆盖 |
|---|---|---|---|
| B1 | StatusBar | `p00-statusbar` | — |
| B2 | Logo + Slogan 区 | `p00-logo-zone`, `p00-logo-zone-logo` | AC-P00-001 |
| B3 | 微信按钮 (wechat-brand 例外) | `p00-wechat-cta-btn` + `data-iron-rule-1-exception="wechat-brand"` | AC-P00-002/003 |
| B4 | 其他登录方式 | `p00-other-methods-link` | AC-P00-004 |
| B5 | 协议勾选 | `p00-consent-bar`, `p00-consent-bar-checkbox`, `p00-consent-bar-link-tos`, `p00-consent-bar-link-privacy` | AC-P00-003/008 |

---

## P-HOME · 今日聚合首页 Block 实现状态

| Block ID | 名称 | testid | AC 覆盖 |
|---|---|---|---|
| B1 | GreetingHero + StreakBar | `greeting-hero`, `streak-bar-fire-icon` (SVG), `streak-bar-days-number` | AC-HOME-001/005/010 |
| B2 | TodayReviewCard + 进度环 + SubjectChips | `today-review-card`, `today-review-card-circle-progress`, `today-review-card-total`, `today-review-card-est-min`, `today-review-card-start-all-btn` | AC-HOME-002/003/006 |
| B3 | WeeklySparkline | `p-home-weekly-sparkline` | AC-HOME-001 |
| B4 | WeekStrip 7日 | `week-strip`, `week-strip-day-{1..7}`, `data-today`, `week-strip-day-{n}-tlevel-{T}` | AC-HOME-004 |
| B5 | MessagesList | `p-home-messages`, `p-home-messages-item-{1..3}`, `p-home-messages-more-link` | AC-HOME-007 |
| B6 | WeakKPHint | `p-home-weak-kp` | AC-HOME-008 |
| B7 | QuickEntries 2×2 | `p-home-quick-entries`, `p-home-quick-entries-item-{1..4}` | AC-HOME-009 |

---

## P12 · 通知中心 Block 实现状态

| Block ID | 名称 | testid | AC 覆盖 |
|---|---|---|---|
| B1 | Header + 全部已读 | `p12-header-title`, `p12-header-mark-all-read` | AC-P12-001/007 |
| B2 | TimelineGroup 今天 | `p12-group-today` | AC-P12-002 |
| B3 | TimelineGroup 昨天 | `p12-group-yesterday` | AC-P12-002 |
| B4 | TimelineGroup 本周 | `p12-group-thisweek` | AC-P12-002 |
| B5 | TimelineGroup 更早 | `p12-group-earlier` | AC-P12-002 |
| B6 | EmptyState | `p12-empty-state` | AC-P12-008 |
| B7 | NotifCard | `p12-notif-card-{n}`, `-icon`(data-kind), `-title`, `-subtitle`, `-time`, `-unread-dot`(data-read) | AC-P12-003/004/005/006 |

---

## P13 · 设置/我的 Block 实现状态

| Block ID | 名称 | testid | AC 覆盖 |
|---|---|---|---|
| B1 | AvatarBlock + StreakBar | `p13-avatar-block`, `p13-avatar-block-name` | AC-P13-001 |
| B2 | 账户设置组 | `p13-settings-account`, `p13-settings-account-logout-row` | AC-P13-002/008 |
| B3 | 复习偏好设置组 | `p13-settings-review`, `p13-settings-review-quiet-hours-row` | AC-P13-002/004 |
| B4 | 推送设置组 | `p13-settings-push`, `p13-settings-push-review-reminder-switch`, `p13-settings-push-frequency-preview` | AC-P13-003/009 |
| B5 | 隐私设置组 | `p13-settings-privacy` | AC-P13-002 |
| B6 | 关于设置组 | `p13-settings-about`, `p13-settings-about-version` | AC-P13-002/005 |
| B7 | DangerZone | `p13-danger-zone`, `p13-danger-zone-account-deletion-btn`, `p13-danger-confirm` | AC-P13-006/007 |
| SC-16 | AI 模型子区 | `p13-sc16-ai-section`(data-sc16-tier), `p13-sc16-upgrade-hint`(NORMAL), `p13-sc16-model-selector`(VIP+), `p13-sc16-model-item-{id}` | SC-16 三层分流 |

---

## SC-16 三层 UI 决策

| Tier | UI 表现 | 安全设计 |
|---|---|---|
| NORMAL | 只显示 `p13-sc16-upgrade-hint` · 不渲染选择器 · 不暴露任何模型 ID | 防止 tier 信号泄露；后端 aiModelHint 静默忽略不返 403 |
| VIP | 显示 `p13-sc16-model-selector` · catalog 来自 GET /api/ai/models (tier 过滤后) · 选中保存为 user_setting.preferred_ai_model | PATCH /api/me/ai-preference 由后端 @PreAuthorize 验证 |
| VIP_PLUS | 同 VIP + 实验池 (claude/private) + cost/latency 元信息 chip (cost_tier H/M/L + avg_latency_ms) | 同上 + 实验池仅 VIP_PLUS 可见 |

**优先级链** (D-AI-User-Override): 单次 aiModelHint > user_setting.preferred_ai_model > 系统默认

---

## 设计铁律合规

| 铁律 | 状态 | 备注 |
|---|---|---|
| P00 Mood A hero+overlap 380px | ✅ | 深蓝渐变 + 3 blob (::before 紫/::after 青/.blob 粉) |
| P00 wechat-brand 例外 #07C160 | ✅ | `data-iron-rule-1-exception="wechat-brand"` 已注册 |
| P-HOME Mood A 1:1 archive 01_home.html | ✅ | reviewhero + weekly + weekcard + msgs + kpcard + quick 全套 |
| P12/P13 Mood B pure-warm | ✅ | #F2F2F7 底 + 玻璃态 nav (rgba+blur) + 白卡 |
| 无硬编码废弃 token | ✅ | 无 warm-* / aurora / #0071e3 / #2C2A26 |
| data-mood v2.0 五类 | ✅ | P00=A, P-HOME=A, P12=B, P13=B |
| prefers-reduced-motion | ✅ | 各页面 @media 兜底 |
| SC-16 NORMAL 不暴露选择器 | ✅ | 条件渲染 tier !== 'NORMAL' |
| StreakBar SVG 火焰 (非 emoji) | ✅ | `<svg>` path fill="#FFD166" |

---

## 路由注册 (App.tsx 变更)

```
/auth         → AuthPage (FE-07 新)
/             → HomePage (FE-07 新, 替换 placeholder)
/notifications→ NotificationsPage (FE-07 新, 替换 placeholder)
/me           → MePage (FE-07 新, 替换 placeholder)
```
