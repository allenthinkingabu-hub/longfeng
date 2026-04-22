# Longfeng Wrong-Answer Notebook · Design System Master

<!--
source: getdesign.md/apple/design-md
saved_local: design/system/inspiration/apple.design.md
generated_by: ui-ux-pro-max-skill v2.5.0
human_overrides: [subject-palette, miniprogram-saturation]
style: Minimalism & Swiss Style — Apple.com specialization
code_as_design: true  (no Figma, no .fig)
project_cn: AI wrong-answer notebook (H5 + WeChat miniprogram)
-->

## 1. Visual Identity

**Philosophy**: Minimalism & Swiss Style, Apple-specialized. Every pixel serves content; the interface retreats until invisible. Reverence for the learning artifact — the wrong-answer card — mirrors Apple's reverence for the product.

**Tone**: Precise · Calm · Encouraging. High-contrast black-and-white cinematic rhythm punctuated by four subject accent colors and one interactive blue.

**Non-negotiable rules**:
- No emoji as icons — SVG only (Lucide / custom subset)
- No Tailwind arbitrary values (e.g. `width: 37px` inline — forbidden) — all values via tokens
- No hardcoded Chinese strings in UI code — all copy via `i18n/zh-CN.json`
- Apple Blue `#0071e3` is the **only** chromatic accent in the core palette; subject colors are permitted exceptions

---

## 2. Color System

Token namespace: `--tkn-color-*` (core) · `--tkn-subject-*` (exceptions)

### 2.1 Core Palette (from apple.design.md)

| Token | Hex | Usage |
|-------|-----|-------|
| `--tkn-color-black` | `#000000` | Hero dark backgrounds, immersive sections |
| `--tkn-color-white` | `#ffffff` | Text on dark, button text on CTAs |
| `--tkn-color-bg-light` | `#f5f5f7` | Informational section backgrounds (slight blue-gray tint) |
| `--tkn-color-text-primary` | `#1d1d1f` | Body text on light backgrounds |
| `--tkn-color-text-secondary` | `rgba(0,0,0,0.80)` | Nav items, secondary text on light |
| `--tkn-color-text-tertiary` | `rgba(0,0,0,0.48)` | Disabled, captions |
| `--tkn-color-surface-dark-1` | `#272729` | Card backgrounds in dark sections |
| `--tkn-color-surface-dark-2` | `#262628` | Subtle dark surface variation |
| `--tkn-color-surface-dark-3` | `#28282a` | Elevated card on dark |
| `--tkn-color-surface-dark-4` | `#2a2a2d` | Highest dark elevation |
| `--tkn-color-overlay` | `rgba(210,210,215,0.64)` | Media control scrims |

### 2.2 Interactive / Semantic

| Token | Hex | `contrast_on_light` | `contrast_on_dark` | Note |
|-------|-----|---------------------|--------------------|------|
| `--tkn-color-primary-DEFAULT` | `#0071e3` | 4.70:1 ✓ | 4.47:1 ⚠ | Apple Blue; use dark-variant on dark bg |
| `--tkn-color-primary-dark` | `#2997ff` | 3.02:1 (large) | 6.96:1 ✓ | Interactive links on dark sections |
| `--tkn-color-primary-link` | `#0066cc` | 5.57:1 ✓ | 3.77:1 (large) | Inline text links on light |
| `--tkn-color-success-DEFAULT` | `#1A7D34` | 5.21:1 ✓ | 4.03:1 (large) | Success states |
| `--tkn-color-warning-DEFAULT` | `#B45309` | 5.02:1 ✓ | 4.18:1 (large) | Warning states |
| `--tkn-color-danger-DEFAULT` | `#C0392B` | 5.44:1 ✓ | 3.86:1 (large) | Error / destructive |
| `--tkn-color-info-DEFAULT` | `#005C99` | 7.02:1 ✓ | 2.99:1 ⚠ | Info banners |

> ⚠ `primary-DEFAULT` on black: 4.47:1 — passes WCAG AA for large text (≥18pt/24px or 14pt bold). For small body text on pure black, use `primary-dark` instead.
> ⚠ `info-DEFAULT` on black: 2.99:1 — use only for large decorative text on dark surfaces.

### 2.3 Subject Palette — Exception 1 (human override)

> These four colors break the Apple mono-blue rule. Rationale: `tag_taxonomy` requires ≥ 4 visually distinct subject identifiers. Each achieves ≥ 4.5:1 contrast on `#f5f5f7` and `#ffffff`; white text on any of them also meets ≥ 4.5:1.

| Token | Hex | `contrast_on_light` | White text on it | Subject association |
|-------|-----|---------------------|-------------------|---------------------|
| `--tkn-subject-math` | `#C41E3A` | 5.84:1 ✓ | 5.84:1 ✓ | Carmine red — algebra / equations |
| `--tkn-subject-physics` | `#0057B7` | 6.89:1 ✓ | 6.89:1 ✓ | Cobalt blue — physical world |
| `--tkn-subject-chemistry` | `#1A6B3A` | 6.55:1 ✓ | 6.55:1 ✓ | Forest green — lab / organic |
| `--tkn-subject-english` | `#9C4F00` | 5.95:1 ✓ | 5.95:1 ✓ | Amber — language / literature |

### 2.4 Platform Override — Exception 2 (human override)

WeChat miniprogram renders Apple cold-white as "dirty gray" due to system-level gamma correction. Style Dictionary transform applies at build time:

```
wechat.saturation_delta: +12%
wechat.shadow_alpha_delta: +0.04
```

Declared in `color.json._meta.platform_overrides`. Applied in `sd.config.js` wxss platform transform.

### 2.5 Focus Ring

All interactive elements: `outline: 2px solid #0071e3; outline-offset: 2px;`

---

## 3. Typography

### 3.1 Font Stack

```css
--tkn-font-display: 'SF Pro Display', 'SF Pro Icons', 'Helvetica Neue', Helvetica, Arial, sans-serif;
--tkn-font-text:    'SF Pro Text',    'SF Pro Icons', 'Helvetica Neue', Helvetica, Arial, sans-serif;
```

Rule: SF Pro Display at **20px and above**; SF Pro Text at **19px and below**. Never mix within one element.

### 3.2 Type Scale

| Token | Size | Weight | Line-height | Letter-spacing | Usage |
|-------|------|--------|-------------|----------------|-------|
| `--tkn-type-display-hero` | 56px / 3.5rem | 600 | 1.07 | -0.28px | Product launch headlines |
| `--tkn-type-section-heading` | 40px / 2.5rem | 600 | 1.10 | normal | Feature section titles |
| `--tkn-type-tile-heading` | 28px / 1.75rem | 400 | 1.14 | +0.196px | Product tile headlines |
| `--tkn-type-card-title` | 21px / 1.31rem | 700 | 1.19 | +0.231px | Bold card headings |
| `--tkn-type-sub-heading` | 21px / 1.31rem | 400 | 1.19 | +0.231px | Regular card headings |
| `--tkn-type-body` | 17px / 1.06rem | 400 | 1.47 | -0.374px | Standard reading text |
| `--tkn-type-body-emphasis` | 17px / 1.06rem | 600 | 1.24 | -0.374px | Emphasized body, labels |
| `--tkn-type-button-lg` | 18px / 1.13rem | 300 | 1.00 | normal | Large button text |
| `--tkn-type-button` | 17px / 1.06rem | 400 | 2.41 | normal | Standard button |
| `--tkn-type-link` | 14px / 0.88rem | 400 | 1.43 | -0.224px | Inline links |
| `--tkn-type-caption` | 14px / 0.88rem | 400 | 1.29 | -0.224px | Descriptions |
| `--tkn-type-caption-bold` | 14px / 0.88rem | 600 | 1.29 | -0.224px | Emphasized captions |
| `--tkn-type-micro` | 12px / 0.75rem | 400 | 1.33 | -0.12px | Fine print, footnotes |
| `--tkn-type-nano` | 10px / 0.63rem | 400 | 1.47 | -0.08px | Legal text, smallest |

### 3.3 Typography Rules

- Negative letter-spacing at **all** sizes — Apple tracks tight universally
- Body text left-aligned; headlines center-aligned only in hero sections
- Weight range: 300 (light, decorative large only) → 700 (bold, rare)
- Minimum body size on mobile: 17px (avoids iOS auto-zoom)

---

## 4. Spacing System

Base unit: **8px** (4pt/8pt grid)

| Token | Value | Usage |
|-------|-------|-------|
| `--tkn-spacing-2xs` | 2px | Icon micro-alignment |
| `--tkn-spacing-xs` | 4px | Dense internal padding |
| `--tkn-spacing-sm` | 8px | Component internal gap |
| `--tkn-spacing-md` | 16px | Card padding, section internal |
| `--tkn-spacing-lg` | 24px | Component separation |
| `--tkn-spacing-xl` | 32px | Section padding |
| `--tkn-spacing-2xl` | 48px | Major section separation |
| `--tkn-spacing-3xl` | 64px | Hero vertical rhythm |
| `--tkn-spacing-4xl` | 96px | Between major page sections |

---

## 5. Border Radius

| Token | Value | Usage |
|-------|-------|-------|
| `--tkn-radius-xs` | 5px | Small containers, link tags |
| `--tkn-radius-sm` | 8px | Buttons, product cards, image containers |
| `--tkn-radius-md` | 11px | Search inputs, filter buttons |
| `--tkn-radius-lg` | 12px | Feature panels, lifestyle image containers |
| `--tkn-radius-pill` | 980px | CTA links ("Learn more", "Shop") |
| `--tkn-radius-circle` | 50% | Media controls (play/pause, avatars) |

---

## 6. Shadow & Elevation

| Token | Value | Usage |
|-------|-------|-------|
| `--tkn-shadow-card` | `rgba(0,0,0,0.22) 3px 5px 30px 0px` | Product cards, floating elements |
| `--tkn-shadow-nav` | `backdrop-filter: saturate(180%) blur(20px)` applied to `rgba(0,0,0,0.80)` bg | Sticky navigation glass effect |
| `--tkn-shadow-focus` | `0 0 0 2px #0071e3` | Focus ring on interactive elements |

**Philosophy**: Shadow is rare. Elevation comes from background color contrast. Only one soft diffused shadow (`card`) + the nav glass effect.

---

## 7. Motion

| Token | Value | Usage |
|-------|-------|-------|
| `--tkn-motion-ease-standard` | `cubic-bezier(0.25, 0.46, 0.45, 0.94)` | Apple HIG standard — ease-out entering |
| `--tkn-motion-ease-enter` | `cubic-bezier(0.0, 0.0, 0.2, 1.0)` | Elements entering viewport |
| `--tkn-motion-ease-exit` | `cubic-bezier(0.4, 0.0, 1.0, 1.0)` | Elements exiting (faster than enter) |
| `--tkn-motion-duration-fast` | `150ms` | Micro-interactions: hover, focus ring |
| `--tkn-motion-duration-base` | `250ms` | Standard transitions: page elements |
| `--tkn-motion-duration-slow` | `400ms` | Complex transitions: modals, sheets |

**Rules**:
- Exit animations at ~60% of enter duration (`150ms` vs `250ms`)
- Only animate `transform` and `opacity` — never `width` / `height` / `top` / `left`
- Always include `prefers-reduced-motion` override (disable or reduce)
- Spring physics preferred for sheet/modal entrance on mobile

---

## 8. Component Patterns

### Button

```html
<!-- Primary CTA (filled blue) -->
<button class="btn-primary">
  Buy
</button>

<!-- Pill Link (Apple signature) -->
<a class="btn-pill-link" href="#">
  Learn more
</a>

<!-- Dark variant -->
<button class="btn-dark">
  Secondary CTA
</button>
```

Key rules:
- Padding: `8px 15px` (creates ≥44px touch height)
- Focus ring: `outline: 2px solid #0071e3; outline-offset: 2px`
- Hover: background brightens or opacity decreases
- Loading: `disabled` + spinner, no layout shift
- Border-radius: `--tkn-radius-sm` (8px) for filled; `--tkn-radius-pill` (980px) for pill links

### Input / Search

```html
<label for="search">Search</label>
<input id="search" type="search" class="input-filter" placeholder="Search questions..." autocomplete="off">
```

Key rules:
- Background: `#fafafc`; border: `3px solid rgba(0,0,0,0.04)`; radius: `--tkn-radius-md` (11px)
- Visible `<label>` — never placeholder-only
- Focus: `outline: 2px solid #0071e3`
- Min height: 44px

### Card

```html
<article class="card-product">
  <!-- image top 60-70% -->
  <!-- title + description + links below -->
</article>
```

Key rules:
- Background: `#f5f5f7` (light) or `#272729` (dark section)
- Border-radius: `--tkn-radius-sm` (8px); no visible border
- Shadow: `--tkn-shadow-card` only on elevated cards
- Hover: static — only links inside are interactive

### NavBar

```html
<nav class="navbar-glass" aria-label="Main navigation">
  <!-- logo | links | actions -->
</nav>
```

Key rules:
- Background: `rgba(0,0,0,0.80)` + `backdrop-filter: saturate(180%) blur(20px)`
- Height: 48px; position: sticky top-0
- Nav links: 12px SF Pro Text, `#ffffff`, weight 400
- Mobile: collapses to hamburger + full-screen overlay

### TabBar (mobile)

```html
<nav class="tab-bar" aria-label="App navigation">
  <!-- max 5 items; icon + label always -->
</nav>
```

Key rules:
- Maximum 5 items; icon + text label always (never icon-only)
- Active: colored accent; inactive: `rgba(0,0,0,0.48)`
- Safe area bottom: `padding-bottom: env(safe-area-inset-bottom)`

### Toast

```html
<div role="status" aria-live="polite" class="toast">
  Message text
</div>
```

Key rules:
- Auto-dismiss: 3–5 seconds
- Does not steal focus
- `aria-live="polite"` for screen reader announcement

### Modal / Sheet

Key rules:
- Background scrim: `rgba(0,0,0,0.50)` minimum
- Animate from trigger source: scale+fade or slide-up
- Always offer dismiss affordance (× button + swipe-down on mobile)
- Confirm before dismissing if unsaved changes
- Duration: `--tkn-motion-duration-slow` (400ms) enter; 240ms exit

### Skeleton (loading)

Key rules:
- Use for operations >300ms; never blank screen
- Animated shimmer: `opacity: 0.5 → 1.0` at 1.5s loop
- Match shape of actual content exactly

---

## 9. Accessibility Baseline

- Contrast ≥ 4.5:1 normal text · ≥ 3:1 large text (18px+/24px or 14px bold)
- Focus ring on all interactive elements: `2px solid #0071e3`, offset 2px
- Touch targets ≥ 44×44pt
- All icons + icon-only buttons: `aria-label` required
- Tab order matches visual order
- `prefers-reduced-motion`: disable or reduce all animations
- Form labels: visible `<label>` elements, errors below fields
- Skip-to-content link as first focusable element

---

## 10. Responsive Breakpoints

| Name | Width | Key changes |
|------|-------|-------------|
| `mobile-sm` | < 360px | Minimum supported, single column |
| `mobile` | 360–480px | Standard mobile; TabBar navigation |
| `mobile-lg` | 480–640px | Wider single column |
| `tablet-sm` | 640–834px | 2-column product grids begin |
| `tablet` | 834–1024px | Full tablet layout |
| `desktop-sm` | 1024–1070px | Standard desktop, max-content-width kicks in |
| `desktop` | 1070–1440px | Full layout, 980px max-content |
| `desktop-lg` | > 1440px | Centered with generous margins |

Headline responsive: 56px → 40px → 28px (tight line-height maintained proportionally)

---

## 11. Platform Notes — WeChat Miniprogram

- All CSS custom properties compile to `--tkn-*` variables in `tokens.wxss` via Style Dictionary
- `saturation_delta: +12%` applied at SD transform time (see `color.json._meta.platform_overrides`)
- `shadow_alpha_delta: +0.04` on all shadow tokens
- No `backdrop-filter` support → NavBar fallback: solid `#1c1c1e` background
- `rpx` values derived from `px` tokens × 2 (750rpx base)
- Font stack: system `-apple-system, PingFang SC` (SF Pro unavailable in miniprogram)
- Input: `type="text"` only (miniprogram input types differ)
- Touch targets enforced via `min-height: 88rpx` (44pt × 2)

---

## 12. Anti-patterns (Hard NO)

| Violation | Correct |
|-----------|---------|
| Additional accent colors beyond subject palette | Only `#0071e3` + 4 subject colors |
| Heavy or multiple shadow layers | One `--tkn-shadow-card` or nothing |
| Visible borders on cards/containers | Background contrast creates separation |
| Wide letter-spacing on SF Pro | Always tight/negative at every size |
| Tailwind arbitrary inline values | Token-driven: `--tkn-spacing-*` |
| Hardcoded Chinese in UI | All copy via `i18n/zh-CN.json` |
| Emoji as icons | SVG only (Lucide subset) |
| Gradient/texture on backgrounds | Solid colors only |
| Opaque navigation bar | Glass blur effect non-negotiable |
| Center-aligned body text | Left-aligned body; center only for hero headlines |
