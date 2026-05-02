# Apple-base Decision Summary

> **⚠️ 已废 v1.0 · reference-only（2026-05-02 标注）**：本决策基于纯 Apple HIG（`#0071e3`），但 archive 真相是 iOS HIG（`#007AFF`）。当前权威 = `design/system/STYLE-TRUTH.md`。本文仅作历史决策记录。

**Sd Task**: sd-t02-tokens-from-apple-design-md
**Author**: Design Agent (Claude Sonnet 4.6)
**Date**: 2026-04-22 · **Superseded**: 2026-05-02 by STYLE-TRUTH.md

## Why Apple

Apple.com's design system is the reference for Minimalism & Swiss Style executed at product scale. Its binary black/light-gray section rhythm, SF Pro optical sizing, and single-accent-color discipline solve the same problem AI错题本 faces: making a complex, data-dense educational tool feel premium and calm on both H5 and WeChat miniprogram. The existing 19 HTML mockups already reference Apple aesthetics; this decision codifies rather than pivots.

## Two Mandatory Exceptions

**Exception 1 — Subject Palette**: The business requires four visually distinct subject identifiers (math / physics / chemistry / english) for chips, left-bars, and radar charts. Apple's mono-blue palette cannot encode four semantic categories. The four chosen saturated colors (`#C41E3A` / `#0057B7` / `#1A6B3A` / `#9C4F00`) each achieve ≥ 5.84:1 contrast on white and produce ≥ 5.84:1 for white text on the color itself — both WCAG AA compliant.

**Exception 2 — Miniprogram Saturation**: Apple cold-white (`#f5f5f7`) renders as dirty gray under WeChat's gamma curve. A `+12%` saturation delta and `+0.04` shadow alpha offset, declared in `color.json._meta.platform_overrides`, are applied by Style Dictionary at wxss build time. Only the wxss output is affected; the JSON source remains platform-neutral.

## Risks & Mitigation

- **SF Pro unavailability in miniprogram**: Fallback chain `-apple-system, PingFang SC` preserves legibility; no visual regression expected.
- **backdrop-filter absent in miniprogram**: NavBar falls back to solid `#1c1c1e`; glass effect is H5-only.
- **Subject color calibration**: Four colors chosen for contrast compliance; hue distinctiveness should be eyeball-reviewed by User before Sd Review Gate.

## Follow-up Observation Items (Sd Review Gate)

1. Verify subject chip colors render correctly in both iPhone Safari and WeChat DevTool simulator at 100% and 150% brightness.
2. Confirm `primary-DEFAULT` (#0071e3) on black body text is avoided in code (flagged `_warning_dark` in color.json).
3. Validate SF Pro Display availability on target H5 devices; if unavailable, consider loading via web font.
