# FE-06 · Anon Pages · UI Plan

> Generated: 2026-05-02
> Agent: fe-06-anon-pages Sub-agent
> Branch: agent/fe-06-anon-pages

---

## 页面矩阵

| 页面 | 路由 | Mood | Archive 参考 | 状态 |
|---|---|---|---|---|
| P-LANDING | `/welcome` | A (hero+overlap) | `_archive/14_landing.html` | ✅ 实现 |
| P-GUEST-CAPTURE | `/guest/capture` | C (dark-camera) | `_archive/15_guest_capture.html` | ✅ 实现 |
| P-SHARED | `/s/:shareToken` | E (teal-observer) | `_archive/16_shared.html` | ✅ 实现 |

---

## Hooks

| Hook | 文件 | 用途 | 状态 |
|---|---|---|---|
| `useDeviceFingerprint` | `hooks/useDeviceFingerprint.ts` | 5来源指纹 hash (Canvas/WebGL/AudioContext/UA/Accept-Language) | ✅ 实现 |

---

## P-LANDING Block 实现状态

| Block ID | 名称 | testid | AC 覆盖 |
|---|---|---|---|
| B1 | StatusBar | `p-landing-statusbar` | — |
| B2 | Landing Hero | `landing-hero`, `landing-hero-headline`, `landing-hero-cta-try`, `landing-hero-cta-login`, `landing-hero-logo` | AC-LANDING-001, 002, 003, 006, 008 |
| B3 | Feature rows | `landing-three-step` | — |
| B4 | 样例卡 | `landing-samples`, `landing-samples-card-1..3` | AC-LANDING-004 |
| B5 | KPI banner | `landing-kpi`, `landing-kpi-total`, `landing-kpi-retention` | AC-LANDING-005 |
| B6 | 底部 CTA | `landing-cta-bottom`, `landing-cta-bottom-btn` | AC-LANDING-003, 008 |

---

## P-GUEST-CAPTURE Block 实现状态

| Block ID | 名称 | testid | AC 覆盖 |
|---|---|---|---|
| B1 | StatusBar | `p-guest-capture-statusbar` | — |
| B2 | Guest Quota Banner | `guest-quota-banner`, `guest-quota-banner-text`, `guest-quota-banner-cta` | AC-GUEST-001, 002 |
| B3 | Camera Preview | `camera-preview` | AC-GUEST-003 |
| B4 | Subject Chip Row | `subject-chip-row`, `subject-chip-math`, `subject-chip-physics`, `subject-chip-chemistry`, `subject-chip-english` | AC-GUEST-004 |
| B5 | Capture Controls | `capture-controls`, `capture-controls-shutter` | AC-GUEST-005 |
| — | Quota Exhausted Screen | `quota-exhausted-screen`, `quota-exhausted-cta-register` | AC-GUEST-006 |

---

## P-SHARED Block 实现状态

| Block ID | 名称 | testid | AC 覆盖 |
|---|---|---|---|
| B1 | StatusBar | `p-shared-statusbar` | — |
| B2 | Sharer Banner | `sharer-banner`, `sharer-banner-avatar`, `sharer-banner-text` | AC-SHARED-001 |
| B3 | Masked Question | `masked-question`, `masked-question-stem-clear`, `masked-question-stem-blurred`, `masked-question-overlay` | AC-SHARED-002, 003 |
| B4 | Memory Curve Preview | `memory-curve-preview`, `memory-curve-preview-svg` | AC-SHARED-004 |
| B5 | Share Meta | `share-meta` | — |
| B6 | Upgrade CTA | `upgrade-cta-fixed-btn` | AC-SHARED-005 |
| — | Token Error Screens | `token-expired-screen`, `token-expired-cta`, `token-invalid-screen`, `token-revoked-screen` | AC-SHARED-006 |

---

## 设计铁律合规

| 铁律 | 状态 | 备注 |
|---|---|---|
| Mood A hero+overlap (P-LANDING) | ✅ | 380px 深蓝 hero + 3 blob + 26px overlap |
| Mood C dark-camera (P-GUEST-CAPTURE) | ✅ | #0B0F1A 全屏 + 黄色 bracket + 蓝色 shutter core |
| Mood E teal-observer (P-SHARED) | ✅ | 深蓝→teal header + 26px overlap |
| 防写动词 (P-SHARED C4) | ✅ | aria-disabled + onClick intercepted (comment/bookmark) |
| C3 guest session | ✅ | 写 `guest_session_token` · 不写 user-id |
| prefers-reduced-motion | ✅ | 各页面均有 @media 兜底 |
| testid 覆盖 spec.§8 | ✅ | 所有 AC 对应 testid 均已实现 |
| 无硬编码废弃 token (warm-*/aurora) | ✅ | 均使用 archive-aligned 本地变量 |
| data-mood v2.0 (A/B/C/D/E) | ✅ | P-LANDING=A, P-GUEST-CAPTURE=C, P-SHARED=E |

---

## 路由注册

App.tsx 已更新：
- `/welcome` → `LandingPage`
- `/guest/capture` → `GuestCapturePage`
- `/s/:shareToken` → `SharedPage`
