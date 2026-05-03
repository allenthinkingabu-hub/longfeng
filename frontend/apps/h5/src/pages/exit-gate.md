# FE-06 · Anon Pages · Exit Gate Report

> Date: 2026-05-02
> Agent: fe-06-anon-pages Sub-agent
> Branch: agent/fe-06-anon-pages

---

## ✅ 完成判据对照

### 判据 1: Write 3 page + 1-2 hook

| 产出物 | 路径 | 状态 |
|---|---|---|
| P-LANDING 页面 | `frontend/apps/h5/src/pages/Landing/index.tsx` | ✅ |
| P-LANDING CSS module | `frontend/apps/h5/src/pages/Landing/Landing.module.css` | ✅ |
| P-GUEST-CAPTURE 页面 | `frontend/apps/h5/src/pages/GuestCapture/index.tsx` | ✅ |
| P-GUEST-CAPTURE CSS module | `frontend/apps/h5/src/pages/GuestCapture/GuestCapture.module.css` | ✅ |
| P-SHARED 页面 | `frontend/apps/h5/src/pages/Shared/index.tsx` | ✅ |
| P-SHARED CSS module | `frontend/apps/h5/src/pages/Shared/Shared.module.css` | ✅ |
| useDeviceFingerprint hook | `frontend/apps/h5/src/hooks/useDeviceFingerprint.ts` | ✅ |
| App.tsx 路由注册 | `frontend/apps/h5/src/App.tsx` | ✅ |

### 判据 2: ui-plan.md + exit-gate.md

| 文档 | 路径 | 状态 |
|---|---|---|
| ui-plan.md | `frontend/apps/h5/src/pages/ui-plan.md` | ✅ |
| exit-gate.md（本文件） | `frontend/apps/h5/src/pages/exit-gate.md` | ✅ |

---

## AC 覆盖验证

### P-LANDING

| AC ID | testid 验证点 | 代码实现 | 状态 |
|---|---|---|---|
| AC-LANDING-001 | `landing-hero-headline` 文本 = "拍一张错题 AI 给你一条记忆曲线" | `<h1 data-testid="landing-hero-headline">` | ✅ |
| AC-LANDING-002 | `landing-hero-cta-try` 白底深蓝 · `landing-hero-cta-login` 透明 | CSS `.ctaTry` grad-cta-deep · `.signin` glass | ✅ |
| AC-LANDING-003 | `landing-hero-cta-try` click 后路由 = `/guest/capture` | `onClick={() => nav('/guest/capture')}` | ✅ |
| AC-LANDING-004 | `landing-samples-card-1` 学科主题色背景 | `.sampleThumb.math` / `.physics` / `.english` | ✅ |
| AC-LANDING-005 | `landing-kpi-total` 含 "w+" · `landing-kpi-retention` 含 "%" | 动态渲染 KPI 数据 | ✅ |
| AC-LANDING-006 | DEGRADED 态 `landing-samples` 不可见 | `{pageState !== 'DEGRADED' && ...}` | ✅ |
| AC-LANDING-007 | [AI 推测] 转化率 KPI | 埋点事件 `anon_landing_cta_try` 已实现 | ⏳ 业务待确认 |
| AC-LANDING-008 | `landing-hero` role=banner · tabIndex 顺序 | `<header role="banner">` · tabIndex=0 on CTAs | ✅ |

### P-GUEST-CAPTURE

| AC ID | testid 验证点 | 代码实现 | 状态 |
|---|---|---|---|
| AC-GUEST-001 | `guest-quota-banner-text` 含 "1 次" · `guest-quota-banner-cta` → /auth | data-testid 均已实现，onClick → nav('/auth') | ✅ |
| AC-GUEST-002 | `guest-quota-banner` background encouragement-soft 半透明 | `background: linear-gradient(135deg, rgba(255,180,84,.18), rgba(255,90,79,.14))` | ✅ |
| AC-GUEST-003 | `camera-preview` height ≈ 70vh · background bg-camera | `position:absolute;inset:0` fills viewport; Mood C colours | ✅ |
| AC-GUEST-004 | 4 chip 可见 · 选中 aria-pressed=true | `aria-pressed={selectedSubject === value}` | ✅ |
| AC-GUEST-005 | `capture-controls-shutter` 宽高 = 78px | `.shutter { width:78px; height:78px; }` | ✅ |
| AC-GUEST-006 | `quota-exhausted-screen` + `quota-exhausted-cta-register` → /auth | 429 → setCaptureState('QUOTA_EXHAUSTED') + redirect | ✅ |
| AC-GUEST-007 | [AI 推测] 转化率 KPI | 埋点 `anon_guest_register_cta` 已实现 | ⏳ 业务待确认 |
| AC-GUEST-008 | 整页 0 暖色 0 庆祝色 | Mood C dark-camera · 无 warm/celebrate token | ✅ |

### P-SHARED

| AC ID | testid 验证点 | 代码实现 | 状态 |
|---|---|---|---|
| AC-SHARED-001 | `sharer-banner-avatar` 可见 · `sharer-banner-text` 含 "分享了" | data-testid 均已实现 | ✅ |
| AC-SHARED-002 | `masked-question-stem-clear` 字符长 ≤12 · `masked-question-stem-blurred` 存在 | `stemClear = stem_preview.slice(0, 12)` | ✅ |
| AC-SHARED-003 | `masked-question-overlay` 可见 · 覆盖至少 3 region | `.maskedOverlay` 绝对定位覆盖整卡 · aria-label 说明 | ✅ |
| AC-SHARED-004 | `memory-curve-preview-svg` 6 node fill=rgba(0,0,0,0.16) | SVG 6个 circle fill="rgba(0,0,0,0.16)" | ✅ |
| AC-SHARED-005 | `upgrade-cta-fixed-btn` primary 色 · width=100% | `width:100%` · `background: var(--sh-grad-cta-deep)` | ✅ |
| AC-SHARED-006 | `token-expired-screen` · `token-expired-cta` → /welcome | `pageState=TOKEN_EXPIRED` render + nav('/welcome') | ✅ |
| AC-SHARED-007 | [AI 推测] 转化率 KPI | 埋点 `anon_share_upgrade_cta` 已实现 | ⏳ 业务待确认 |
| AC-SHARED-008 | 写动词 403 拦截 | `aria-disabled="true"` + `interceptWriteVerb()` onClick | ✅ |

---

## 设计铁律合规

| 铁律 | 验证 | 状态 |
|---|---|---|
| 无 `#0071e3` | CSS 中均使用 `#007AFF` 或本地变量 | ✅ |
| 无 `#2C2A26` | 文字色均使用 `#1C1C1E` | ✅ |
| 无 `#FAF8F4` | 背景使用 `#F2F2F7` | ✅ |
| 无废弃 v1.0 token | 未使用 `--tkn-color-warm-*`/`--tkn-gradient-aurora` 等 | ✅ |
| data-mood v2.0 | A/C/E 三类均已正确标注 | ✅ |
| 3 blob hero (P-LANDING) | ::before coral / ::after cyan / .blob gold | ✅ |
| 黄色 bracket + 蓝 shutter core (P-GUEST-CAPTURE) | `.edges` `#FFD166` / `.shutterCore` linear-gradient(180deg, #5FA8FF, #007AFF) | ✅ |
| 防写动词 (P-SHARED C4) | comment/bookmark buttons: aria-disabled + interceptWriteVerb | ✅ |
| C3 guest session token | localStorage key = `guest_session_token`; no user-id written | ✅ |
| prefers-reduced-motion | 各页均有 @media 兜底 | ✅ |

---

## 遗留事项

1. **AC-*-007 [AI 推测]**: 3 页面各有 1 条转化率 KPI AC，标记为 AI 推测，需业务方在 business-analysis.yml 确认量化口径
2. **E2E A 轨**: 依赖后端 `/api/guest/analyze` / `/api/share/:token` 等接口实装，sprint 末联调
3. **useObserverGuard.ts**: 任务说明中指定跳过 (由 FE-01 实现)，已遵循
4. **像素对齐 C 轨**: 需 Playwright + archive 截图 diff，等待 CI 环境就绪
5. **conic logo 彩虹渐变**: Landing 页 logo 方块已按 `_archive/14_landing.html` 原版实现 `conic-gradient(from 210deg, ...)` 7 色彩虹

---

## 不 commit 说明

按任务指令，代码不自动 commit，Orchestrator 代做。
