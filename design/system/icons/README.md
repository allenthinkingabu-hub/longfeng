# Sd.5 Handoff · Icons

> 主文档 §16.2 Sd.5 · 图标资产包

## 当前状态（minimal viable）

S7 H5 三页（List / Capture / Detail）已直接 inline SVG · 无外部图标依赖。本目录用于：
- 提取 inline SVG 为可复用 .svg 文件
- 为 S8/S10/S11 等后续 Phase 的图标统一来源

## 来源

S7 H5 当前 inline 图标（引自 Sd.3 高保真 mockup）：

| 图标 | 来源页 | 用途 |
|---|---|---|
| `back-chevron.svg` | List/Detail | 返回箭头（蓝色 chevron） |
| `filter-lines.svg` | List | 筛选三横线 icon |
| `search-magnifier.svg` | List | 搜索放大镜 |
| `camera-lens.svg` | List FAB / Capture | 拍照 |
| `tab-home.svg` | TabBar | 首页 |
| `tab-wrongbook.svg` | TabBar | 错题本 |
| `tab-review.svg` | TabBar | 复习 |
| `tab-me.svg` | TabBar | 我的 |
| `flash.svg` | Capture | 闪光灯 |
| `gallery.svg` | Capture | 相册 |
| `lightning.svg` | Capture | 提示 tip |

## 待 User 提供

- `logo.svg` · 应用 logo（Sd.3 mockup index.html 用了 conic-gradient + AI 文字 placeholder）
- `app-icon/` · iOS / Android / web favicon 多尺寸 · 1024×1024 base
- `og-image.png` · 1200×630 社交分享 · Sd.3 美学

## 接入约束

- icon 必须 SVG（矢量 · 可 currentColor 主题）
- 命名 kebab-case · `<category>-<name>.svg`
- viewBox 24×24（统一）· stroke-width 1.8
- 颜色用 `currentColor` · CSS 控制

## sd-done 范围说明

本 handoff 包以 minimal viable 形态合并 sd-done · S7 H5 已不阻塞（inline SVG 工作正常）。User 提供 logo/app-icon/og 后落到 Sd.5 二期补充包。
