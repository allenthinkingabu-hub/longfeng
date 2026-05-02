---
page_id: P13
name: 设置 / 我的
name_en: Settings / Me
route_h5: /me
route_miniprogram: pages/me/settings
deeplink: wb://me
auth_state: authenticated
persona:
  - P1-K12
  - P2-Adult
scenarios:
  - SC-08
mockup_canonical: design/mockups/wrongbook/13_settings.html
mockup_version: v1
last_reviewed: 2026-05-02
status: spec-locked
sprint: S4
---

# P13 · 设置 / 我的

## §1 页面目的

学生掌控自己的账户、复习节奏、推送频率、隐私偏好，**所有可能影响学习体验的开关都集中在此**。家长 / 班主任也通过此页发起观察者邀请。

## §2 ASCII 布局图

```
┌─────────────────────────────────────┐  ← mood=warm · color-warm-bg
│  [安全区]                            │
│  [AvatarBlock · 暖色头像区 elevated]  │
│   头像 80px · 学生名 · 学段          │
│   [编辑链接]                         │
│   [StreakBar compact · 🔥 7 天]     │
│                                     │
│  [设置组 1: 账户]                    │
│   ▸ 修改昵称 / 头像                  │
│   ▸ 绑定手机号                       │
│   ▸ 退出登录                         │
│                                     │
│  [设置组 2: 复习偏好]                │
│   ▸ 免打扰时段  23:00–07:30  [→]     │
│   ▸ 节点重置策略 (FORGOT 行为)        │
│   ▸ 首选学科  数学                    │
│                                     │
│  [设置组 3: 推送]                    │
│   ▸ 复习提醒    [Switch on]          │
│   ▸ 周报推送    [Switch on]          │
│   ▸ 频率        每日 / 每周           │
│                                     │
│  [设置组 4: 隐私]                    │
│   ▸ 设备管理 (3 台)                  │
│   ▸ 数据导出                         │
│   ▸ 观察者邀请管理                    │
│                                     │
│  [设置组 5: 关于]                    │
│   ▸ 版本 v1.0.0 · 已是最新           │
│   ▸ 用户协议                         │
│   ▸ 隐私政策                         │
│   ▸ 帮助中心                         │
│                                     │
│  [DangerZone · 红边框警示区]         │
│   ⚠ 注销账户                         │
│   ⚠ 清除所有数据                     │
│                                     │
└─────────────────────────────────────┘
[Tab Bar: 首页 错题本 拍题 复习 我的 ●]
```

## §3 Block 清单

| Block ID | 名称 | Section | Mood | Component | testid root | Tokens 用到 |
|---|---|---|---|---|---|---|
| `B1` | AvatarBlock 头像区 | info | warm | (custom) AvatarBlock + M.StreakBar | `p13-avatar-block` | `--tkn-color-card` · `--tkn-radius-circle` · `--tkn-color-streak-fire` |
| `B2` | 账户设置组 | info | warm | (custom) SettingGroup + L0.Divider | `p13-settings-account` | `--tkn-color-card` · `--tkn-color-sep` · `--tkn-radius-lg` |
| `B3` | 复习偏好设置组 | info | warm | (custom) SettingGroup | `p13-settings-review` | 同上 |
| `B4` | 推送设置组 | info | warm | (custom) SettingGroup + L0.Switch | `p13-settings-push` | 同上 + `--tkn-color-primary-DEFAULT` (Switch on) |
| `B5` | 隐私设置组 | info | warm | (custom) SettingGroup | `p13-settings-privacy` | 同上 |
| `B6` | 关于设置组 | info | warm | (custom) SettingGroup | `p13-settings-about` | 同上 |
| `B7` | DangerZone 危险区 | info | warm | (custom) DangerZone | `p13-danger-zone` | `--tkn-color-system-danger-DEFAULT` (border + text) · `--tkn-color-card` (bg) |

## §4 数据契约

```typescript
interface MeProfile {
  user: {
    id: string;
    nickname: string;
    avatarUrl: string;
    grade: string;             // "高一" / "考研" 等
    phoneBound: boolean;
    streakDays: number;
  };
  preferences: {
    quietHours: { start: string; end: string };  // "23:00" / "07:30"
    forgotResetStrategy: 'reset-T0' | 'keep-current';
    defaultSubject: 'math' | 'physics' | 'chemistry' | 'english' | 'chinese' | null;
    push: {
      reviewReminder: boolean;
      weeklyReport: boolean;
      frequency: 'daily' | 'weekly';
    };
  };
  devices: Array<{ id: string; name: string; lastActiveAt: string }>;
  observerInvites: Array<{ id: string; observerName: string; status: 'pending' | 'active' | 'revoked' }>;
  app: {
    version: string;
    isLatest: boolean;
  };
}
```

## §5 API 触点

| Method | Path | 用途 | P95 预算 | 失败降级 |
|---|---|---|---|---|
| GET | `/api/me` | 完整 profile | 300ms | 缓存兜底 |
| PATCH | `/api/me/preferences` | 更新偏好（防抖 500ms 提交） | 200ms | toast 失败提示 |
| GET | `/api/me/devices` | 设备列表 | 200ms | — |
| DELETE | `/api/me/devices/{id}` | 移除设备 | 300ms | 二次确认 |
| GET | `/api/me/observer-invites` | 观察者邀请列表 | 200ms | — |
| POST | `/api/me/observer-invites/{id}/revoke` | 撤销观察者 | 200ms | — |
| POST | `/api/me/danger/logout` | 退出登录 | 200ms | — |
| POST | `/api/me/danger/account-deletion` | 注销账户（异步，72h 冷静期） | 300ms | — |
| POST | `/api/me/danger/clear-data` | 清除所有数据 | 300ms | — |

## §6 状态机

| State | 触发 | UI 表现 |
|---|---|---|
| `LOADING` | 进入页面 | 全页骨架（avatar 圆 + 各组占位） |
| `READY` | API 200 | 完整渲染 |
| `EDITING` | Switch / Picker 改动 | 顶部出现 "保存中" indicator (250ms) → 自动 PATCH |
| `SAVE_ERROR` | PATCH 失败 | toast `system-warning` + 回滚 UI |
| `DANGER_CONFIRM` | DangerZone 按钮点击 | 全屏 Modal 二次确认（输入"注销"才能继续） |
| `LOGGED_OUT` | 退出登录成功 | 跳 P-LANDING |

## §7 跳转图

```
[入口]
  Tab 5 我的 ──→ P13
        │
        ├──[B2 退出登录]──→ confirm Modal → LOGGED_OUT → P-LANDING
        ├──[B3 免打扰时段]──→ TimePicker Sheet → 保存返回
        ├──[B4 推送 Switch]──→ 同页 PATCH (无跳转)
        ├──[B5 设备管理]──→ Devices Sheet 列表
        ├──[B5 数据导出]──→ Modal 输入邮箱 → 后端发送
        ├──[B5 观察者邀请管理]──→ ObserverInviteSheet
        ├──[B6 用户协议]──→ Webview 法律文档
        └──[B7 注销账户/清除数据]──→ DANGER_CONFIRM Modal → API
```

## §8 AC 覆盖表

| AC ID | 验收点 | 涉及 Block | testid 验证点 |
|---|---|---|---|
| `AC-P13-001` | AvatarBlock 显示头像 + 学生名 + 学段 + StreakBar compact | B1 | `p13-avatar-block-name` 含昵称, `streak-bar` 渲染 |
| `AC-P13-002` | 5 个设置组按"账户/复习/推送/隐私/关于"顺序排列 | B2-B6 | DOM 顺序正确 |
| `AC-P13-003` | 推送 Switch 切换触发 PATCH /api/me/preferences | B4 | `p13-settings-push-review-reminder-switch` click → API |
| `AC-P13-004` | 免打扰时段点击调起 TimePicker Sheet | B3 | `p13-settings-review-quiet-hours-row` click → Sheet |
| `AC-P13-005` | 版本号显示 + "已是最新"或"有更新可用" | B6 | `p13-settings-about-version` textContent |
| `AC-P13-006` | DangerZone 红色边框 + system-danger 文字 | B7 | `p13-danger-zone` border + text 命中 token |
| `AC-P13-007` | 注销账户按钮触发 Modal，需输入"注销"才能确认 | B7 | `p13-danger-zone-account-deletion-btn` click → Modal `p13-danger-confirm` |
| `AC-P13-008` | 退出登录成功后跳 P-LANDING | B2 | `p13-settings-account-logout-row` click → confirm → 路由 |
| `AC-P13-009` | `[AI 推测]` 推送频率切换"每日/每周"立即更新预览（"明日 18:00 提醒"） | B4 | `p13-settings-push-frequency-preview` 文本切换 |
| `AC-P13-010` | `[AI 推测]` 数据导出成功后 toast "已发送至您的邮箱 X" | B5 | toast 含 email |

## §9 异常路径

| 触发 | UI | 系统行为 |
|---|---|---|
| PATCH 失败 | toast + UI 回滚 | 重试 1 次 |
| 注销账户输入错误 | 错误提示 | 不允许提交 |
| 设备管理列表为空 | "仅当前设备" 空态 | 不显示移除按钮 |
| 观察者邀请被撤销 | 列表立即更新 | 无需刷新 |
| 网络断开 | offline indicator + 禁用 Switch | 恢复后自动同步 |

## §10 埋点事件

| 事件名 | 触发 | 必带属性 |
|---|---|---|
| `wb_settings_view` | 进入页面 | — |
| `wb_settings_change` | 任意 PATCH 成功 | `field`, `from`, `to` |
| `wb_settings_logout` | B2 退出登录 | — |
| `wb_settings_danger_attempt` | B7 任一危险按钮 click | `action` |
| `wb_settings_danger_confirm` | DangerZone Modal 确认成功 | `action` |
| `wb_settings_observer_revoke` | B5 撤销观察者 | `inviteId` |

## §11 性能预算

- TTI ≤ 1000ms
- LCP（AvatarBlock）≤ 1200ms
- PATCH 防抖 500ms 后请求；P95 ≤ 200ms
- CLS < 0.05

## §12 A11y

- Landmarks: `<header role="banner">` 包 B1, `<main role="main">` 包 B2-B7
- 焦点顺序: B1 编辑链接 → B2 第一行 → ... → B7 最后一个危险按钮
- 屏幕阅读器:
  - 每个 SettingGroup 用 `<section role="region" aria-labelledby="group-title">`
  - Switch 用 `role="switch" aria-checked="true|false"`
  - DangerZone `aria-label="危险操作区 · 慎重选择"`
  - 危险按钮 `aria-describedby` 链接到说明文字
- `prefers-reduced-motion`: Switch 动画退化瞬时

## §13 Mockup 锚定

- 权威: `design/mockups/wrongbook/13_settings.html` (v1)
- 反向锚定: `<meta name="design-spec" content="P13-settings.spec.md@v1">`

## §14 Tokens 清单

```
L1 (Apple base):
  --tkn-color-primary-DEFAULT
  --tkn-color-system-danger-DEFAULT
  --tkn-color-system-warning-DEFAULT
  --tkn-type-card-title
  --tkn-type-body
  --tkn-type-body-emphasis
  --tkn-type-caption
  --tkn-type-micro
  --tkn-spacing-sm
  --tkn-spacing-md
  --tkn-spacing-lg
  --tkn-spacing-xl
  --tkn-radius-sm
  --tkn-radius-md
  --tkn-radius-lg
  --tkn-radius-circle
  --tkn-radius-pill
  --tkn-motion-duration-fast
  --tkn-motion-duration-base
  --tkn-shadow-focus

L2 (warmth):
  --tkn-color-bg-light
  --tkn-color-card
  --tkn-color-text-primary
  --tkn-color-text-secondary
  --tkn-color-sep
  --tkn-shadow-card-deep

L3 (celebration):
  --tkn-color-streak-fire (B1 StreakBar)
```
