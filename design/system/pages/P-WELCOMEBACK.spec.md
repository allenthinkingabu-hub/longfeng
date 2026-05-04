---
page_id: P-WELCOMEBACK
name: 回流唤起
name_en: Welcome Back
route_h5: /welcome-back
route_miniprogram: pages/welcome/back
deeplink: wb://welcome-back
auth_state: anonymous
persona:
  - P1-K12
  - P3-Returning
scenarios:
  - SC-14
mockup_canonical: design/mockups/wrongbook/17_welcomeback.html
mockup_version: v1
last_reviewed: 2026-05-02
status: spec-draft
sprint: S5
---

# P-WELCOMEBACK · 回流唤起

> **使用说明**：本 spec 对应 DESIGN.md §4.2 的 14 段标准结构。匿名态 P1 页面，业务文档 §2A.3.2 与 §2B.15 SC-14 为权威来源。AI 推测条目均显式标注。

---

## §1 页面目的（why · 1 句话）

让设备指纹命中过去登录账号的流失用户在 ≤3 秒内秒懂"账号还在 · 还剩 N 题等你"，一键 OAuth 刷 Token 直回 P-HOME，永不打扰、不强行登录。

---

## §2 ASCII 布局图

```
┌─────────────────────────────────────┐
│  [statusbar · mood=celebrate]
│  [welcomeback-hero · mood=celebrate]
│   ┌──── 深蓝 hero gradient (180deg, hero-stop-2/4/5) ────┐
│   │  · Avatar (脱敏首字)                       │
│   │  · 欢迎回来，张*                           │
│   │  · caption: 你离开 12 天了，TA 们还在等你  │
│   │  ┌─ pending-pulse ───────┐                │
│   │  │   14            (display-hero · streak-bump bounce)
│   │  │   还剩 个待复习                          │
│   │  └───────────────────────┘                │
│   │  preview MemoryCurve 灰色未来态           │
│   └────────────────────────────────────────────┘
├─────────────────────────────────────┤  ← mood 切到 warm
│  [account-summary-card · mood=warm]
│   · 累计入库 82 题 · 上次登录 12 天前
│   · 一键回登 (蓝 pill 主) → POST /auth/device-refresh
│   · 看看新功能 (灰链接) → P-LANDING
│  [auto-exit-timer · mood=warm]
│   · "X 分钟不操作将退出此页"
└─────────────────────────────────────┘
```

---

## §3 Block 清单（每块独立可实现单元）

| Block ID | 名称 | Section | Mood | Component | testid root | Tokens 用到 |
|---|---|---|---|---|---|---|
| `B1` | StatusBar | hero | celebrate | (atom) | `welcomeback-statusbar` | `--tkn-color-text-on-dark` |
| `B2` | WelcomeBackHero | hero | celebrate | M.GreetingHero(variant=welcomeback) | `welcomeback-hero` | `--tkn-gradient-hero-home`, `--tkn-color-glass-white-18`, `--tkn-color-glass-white-08`, `--tkn-color-text-on-dark`, `--tkn-type-display-hero`, `--tkn-type-sub-heading` |
| `B3` | PendingPulse | hero | celebrate | (custom · 数字脉冲) | `welcomeback-pending-pulse` | `--tkn-type-display-hero`, `--tkn-color-text-on-dark`, `--tkn-motion-celebrate-streak-bump`, `--tkn-ease-celebrate-bounce-out` |
| `B4` | MemoryCurvePreview | hero | celebrate | M.MemoryCurve(variant=preview) | `memory-curve` | `--tkn-color-text-secondary`, `rgba(0,0,0,0.16)` (via mol token) |
| `B5` | AccountSummaryCard | info | warm | (atom · Card) | `welcomeback-account-summary` | `--tkn-color-bg-light`, `--tkn-color-card`, `--tkn-color-text-primary`, `--tkn-color-text-secondary`, `--tkn-shadow-card-deep`, `--tkn-radius-lg`, `--tkn-spacing-md` |
| `B6` | PrimaryOAuthCTA | info | warm | (atom · Button) | `welcomeback-oauth-cta` | `--tkn-color-primary-DEFAULT`, `--tkn-color-text-on-dark`, `--tkn-radius-pill`, `--tkn-spacing-md` |
| `B7` | SwitchAccountLink | info | warm | (atom · Link) | `welcomeback-switch-account` | `--tkn-color-text-secondary`, `--tkn-color-primary-link`, `--tkn-type-caption` |
| `B8` | AutoExitTimer | info | warm | (atom) | `welcomeback-auto-exit` | `--tkn-color-text-secondary`, `--tkn-type-caption` |

> **fe-preflight 用法**：把 mockup HTML 切成这些块；fe-builder 按块逐个实现并跑 lint。

---

## §4 数据契约（page-level interface）

```typescript
// POST /api/session/resolve 响应
interface SessionResolveResp {
  fingerprintMatched: boolean;
  // 命中时返回脱敏摘要
  maskedAccount?: {
    nick: string;            // "张*" — 仅首字 + *
    avatarSeed: string;      // monogram 字母（例："Z"）
    lastLoginAt: string;     // ISO timestamp
    elapsedDays: number;     // 距上次登录天数
    pendingReview: number;   // 还剩待复习数（仅数量，不含内容）
    accumulatedTotal: number;// 累计入库错题数
  };
  // 多账号歧义时
  ambiguousCandidates?: Array<{ studentIdHash: string; nick: string }>;
  // 决策树降级动作
  fallback?: 'P-LANDING' | 'P00';
}

// POST /api/auth/device-refresh 请求体
interface DeviceRefreshReq {
  deviceFp: string;
  oauthPayload: {
    provider: 'wechat' | 'apple';
    code: string;
  };
}

interface DeviceRefreshResp {
  jwt: string;
  studentId: string;
  homeBootstrap?: {
    todayTotal: number;
    welcomeToast: string; // "欢迎回来！今天有 14 题待复习"
  };
}
```

> 类型不一致直接 fail。

---

## §5 API 触点

| Method | Path | 用途 | P95 预算 | 失败降级 |
|---|---|---|---|---|
| POST | `/api/session/resolve` | 设备指纹解析 + 摘要 | ≤ 300ms | 节点 3 未命中 → 跳 P-LANDING |
| POST | `/api/auth/device-refresh` | 一键回登（OAuth + 指纹双因子） | ≤ 500ms | OAuth 失败 → toast "登录失败，请稍后再试" + 保留页面 |

> [AI 推测]：业务文档 §2B.15 未列具体 ms 预算，沿用 §2A.3.2 表格中"指纹命中 ≤300ms"。

---

## §6 状态机

| State | 触发 | UI 表现 |
|---|---|---|
| `LOADING` | 进入页面 | hero skeleton + 信息区骨架（pending 数字位置 "—"）|
| `READY` | `/session/resolve` 200 + `fingerprintMatched=true` | 完整渲染，pending 数字 streak-bump 动画 1 次 |
| `OAUTH_LAUNCHING` | 用户点"一键回登" | CTA 按钮内嵌 spinner + 文案 "正在恢复你的错题本..." · disabled |
| `OAUTH_OK` | `/auth/device-refresh` 200 | 短暂 success state → 立即跳 P-HOME |
| `DEVICE_MISMATCH` | `ambiguousCandidates.length >= 2` | 替换 hero 为"请选择账号"列表降级 P00（实际跳 P00 路由） |
| `STUDENT_DELETED` | 后端返回 `student.status=DELETED` | 整页降级 P-LANDING |
| `TIMEOUT_60S` | 60s 无操作 | 整页淡出 + 跳 P-LANDING（不泄露 masked_account） |
| `ERROR` | API non-2xx OR 网络失败 | toast + 保留 hero · CTA 文案变 "重试一键回登" |

---

## §7 跳转图

```
[入口]
  bootstrap 决策树节点 3 命中 ─┐
  深链 wb://welcome-back ─────┤──→ P-WELCOMEBACK
  从 P00 "我曾登录过" ─────────┘ [AI 推测]
        │
        ├──[一键回登成功]──────→ P-HOME (toast "欢迎回来！今天有 14 题待复习")
        ├──[换个账号登录]──────→ P00
        ├──[60s 无操作]────────→ P-LANDING (auto-exit)
        ├──[设备指纹漂移多账户]→ P00 (DEVICE_MISMATCH)
        └──[student.deleted]──→ P-LANDING (STUDENT_DELETED)
```

---

## §8 AC 覆盖表（与 business-analysis.yml 双向锚定）

| AC ID | 验收点 | 涉及 Block | testid 验证点 |
|---|---|---|---|
| `AC-WB-001` | 指纹命中 → 显示脱敏问候 "欢迎回来，<张\*>" | B2 | `welcomeback-hero-name` 文本匹配 `^欢迎回来，[一-龥]\*$` |
| `AC-WB-002` | 显示"还剩 N 个待复习"，N 用 display-hero 字号且应用 streak-bump 动画 | B3 | `welcomeback-pending-pulse-number` 计算样式 fontSize ≥ 56px · animation-name=streak-bump |
| `AC-WB-003` | "一键回登" CTA 触发 POST `/api/auth/device-refresh` 并在 ≤500ms 跳 P-HOME | B6 | `welcomeback-oauth-cta` click 后 fetch 命中且 200 后路由 = `/` |
| `AC-WB-004` | "看看新功能"灰色链接跳 P-LANDING（不调任何 auth API） | B7 | `welcomeback-switch-account` href 包含 `/landing` 或 click 后路由 = `/landing` |
| `AC-WB-005` | 60s 无操作自动跳 P-LANDING，DOM 中 `welcomeback-account-summary` 元素被销毁（不泄露 masked） | B8 | `welcomeback-auto-exit` 倒计时 60→0；切换后 querySelector 不命中 |
| `AC-WB-006` | 设备指纹多账号命中 → 不渲染问候，直接路由 P00 | B2 | `/session/resolve` 返回 `ambiguousCandidates.length>=2` 时 `welcomeback-hero` 元素不存在 |
| `AC-WB-007` | [AI 推测] MemoryCurve preview 变体所有节点灰色，无任何脉冲 | B4 | `memory-curve` 内 6 个 `memory-curve-node-T*` 全部 `data-status=future` |
| `AC-WB-008` | [AI 推测] 接口响应必须 `Cache-Control: no-store`（合规） | B2 | network response header `Cache-Control` 包含 `no-store` |

> **fe-accept-mock 用法**：用这张表逐条断言；缺 testid = 验收 fail。
> AC-WB-001..006 来自业务文档 §2B.15 SC-14 的 F01-F06 + 关键断言点；AC-WB-007/008 标 `[AI 推测]` 待业务方 review。

---

## §9 异常路径

| 触发 | UI | 系统行为 |
|---|---|---|
| 指纹命中 ≥ 2 账号 | hero 不展示 | 路由 P00（携带 candidates 列表 query） |
| `student.status=DELETED` | 整页淡出 | 路由 P-LANDING |
| 60s 无操作 | 整页淡出 + 提示"已自动退出" | 路由 P-LANDING + 清 `device_fp` 软绑定 |
| OAuth 失败（用户拒绝授权） | CTA 复位 + toast "登录失败" | 保留页面，可重试 |
| 网络断开 | 顶部黄条 system-info | 重试一次后 fallback P-LANDING |
| 指纹未命中 | 不渲染本页 | bootstrap 直接路由 P-LANDING（本页不会进入） |

---

## §10 埋点事件

| 事件名 | 触发 Block | 必带属性 |
|---|---|---|
| `anon_welcomeback_resolve` | bootstrap | `device_fp_hash`, `ms` |
| `anon_welcomeback_view` | B2 | `device_fp_hash`, `elapsed_days`, `pending_review_count` |
| `anon_welcomeback_oauth_launch` | B6 | `device_fp_hash`, `provider` |
| `anon_welcomeback_oauth_success` | B6 | `device_fp_hash`, `elapsed_days`, `ms` |
| `anon_welcomeback_switch_account` | B7 | `device_fp_hash` |
| `anon_welcomeback_timeout` | B8 | `device_fp_hash`, `idle_seconds` |

> 所有事件经 `packages/analytics` 包；必须携带 `device_fp_hash`，禁带原始 PII（包括 `nick`、`student_id`）。

---

## §11 性能预算

- TTI ≤ 1000ms（hero 文字优先，pending 数字渐入）
- LCP ≤ 1200ms（hero 渐变 + 数字 = LCP 元素）
- CLS < 0.05（pending 数字位预留固定高度避免抖动）
- API P95：`/session/resolve` ≤ 300ms · `/auth/device-refresh` ≤ 500ms
- 骨架屏与数据切换 < 100ms 渐入
- streak-bump 动画总时长 = `--tkn-motion-celebrate-streak-bump`（400ms），仅播放 1 次

---

## §12 A11y

- Landmarks: `<main role="main" aria-labelledby="welcomeback-hero-name">` · hero 是 `<header role="banner">` · CTA 区 `<section role="region" aria-label="账号摘要与回登">`
- 焦点顺序: `welcomeback-oauth-cta` → `welcomeback-switch-account` → 状态栏导航元素（无返回）
- 屏幕阅读器优先级: 进入页面立即 `aria-live="polite"` 朗读 "欢迎回来，张星 · 还剩 14 个待复习 · 双击一键回登"
- `prefers-reduced-motion`: 关闭 streak-bump 弹跳，pending 数字直接显示终态
- 数字节点 `aria-live="polite"` 但仅在数字变化时朗读
- `welcomeback-hero-aurora-particles` `aria-hidden="true"`（装饰）

---

## §13 Mockup 锚定

- **权威**: `design/mockups/wrongbook/17_welcomeback.html` (v1)
- **历史变体**: `design/mockups/wrongbook/_archive/17_welcomeback_v0.html`（v0 日落渐变方案，token 不合规已废弃）
- **截图**: `design/system/screenshots/P-WELCOMEBACK-v1-light.png`（fe-preflight 自动生成）
- **反向锚定**: mockup HTML `<head>` 中含 `<meta name="design-spec" content="P-WELCOMEBACK.spec.md@v1">`

---

## §14 Tokens 清单（grep 校验用）

> mockup HTML 的 CSS 中**每一处** hex / rgb / px 数值必须能映射回这张清单中的 token。grep 不命中 = lint fail。

```
L1 (Apple base):
  --tkn-color-primary-DEFAULT
  --tkn-color-primary-link
  --tkn-color-text-on-dark
  --tkn-color-system-info-DEFAULT
  --tkn-font-display
  --tkn-font-text
  --tkn-type-display-hero
  --tkn-type-sub-heading
  --tkn-type-body
  --tkn-type-caption
  --tkn-spacing-2
  --tkn-spacing-xs
  --tkn-spacing-sm
  --tkn-spacing-12
  --tkn-spacing-md
  --tkn-spacing-lg
  --tkn-spacing-xl
  --tkn-spacing-2xl
  --tkn-radius-sm
  --tkn-radius-md
  --tkn-radius-lg
  --tkn-radius-pill
  --tkn-radius-circle
  --tkn-shadow-card
  --tkn-motion-ease-standard
  --tkn-motion-dur-base

L2 (warmth · 仅 mood=warm/celebrate 区段使用):
  --tkn-color-bg-light
  --tkn-color-card
  --tkn-color-text-primary
  --tkn-color-text-secondary
  --tkn-color-sep
  --tkn-color-glass-white-18
  --tkn-color-glass-white-08
  --tkn-gradient-hero-home
  --tkn-shadow-card-deep
  --tkn-shadow-hero-card

L3 (celebration · 仅庆祝白名单时刻):
  --tkn-motion-celebrate-streak-bump
  --tkn-ease-celebrate-bounce-out

EXCEPTION (subject):
  (none — 本页不引用学科色)
```

---

## 模板使用清单

- [x] frontmatter 全部字段填写
- [x] §1 一句话目的写完
- [x] §2 ASCII 布局图绘制
- [x] §3 Block 清单 testid 与 §8 AC 表交叉对账
- [x] §4 TS interface 与业务文档 §10.11 设备刷新一致
- [x] §5 API 路径与业务文档 §2A.3.2 / §2B.15 一致
- [x] §6 状态机覆盖 LOADING/READY/EMPTY/ERROR + 业务专用 OAUTH/TIMEOUT 态
- [x] §7 跳转图入口出口都列全
- [x] §8 AC 表 8 条；缺失的标 `[AI 推测]`（2 条）
- [x] §10 埋点事件名与业务文档 §2A.8 字典一致
- [x] §14 Tokens 清单与本页用到的 token 完全一致

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
