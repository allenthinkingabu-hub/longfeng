# Token 映射 Review — ReviewTodayPage

> 以下是无法精确匹配的颜色/间距，需要 User 确认处理方式。
> 仅列 approx 偏差较大 + none 条目；exact match 不在此列。

---

## ⚠️ 高优先级：学科色冲突（4 项）

mockup 使用 iOS 系统调色板，与 `--tkn-subject-*` 设计 token 存在色相冲突。
**建议：以设计 token 为准（design system authority），忽略 mockup 原始色值。**

| 学科 | mockup 色值 | 设计 token | 色差性质 | 出现区域 |
|---|---|---|---|---|
| 物理 | `#FF9500`（橙）| `--tkn-subject-physics: #0057b7`（蓝）| 色相完全不同 | 左侧竖条 · 学科文字 |
| 化学 | `#5856D6`（靛紫）| `--tkn-subject-chemistry: #1a6b3a`（绿）| 色相完全不同 | 左侧竖条 · 学科文字 |
| 英语 | `#34C759`（绿）| `--tkn-subject-english: #9c4f00`（琥珀）| 色相完全不同 | 左侧竖条 · 学科文字 |
| 数学 | `#FF3B30`（红）| `--tkn-subject-math: #c41e3a`（胭脂红）| 同色系 · 可接受 | 左侧竖条 · 学科文字 |

> **User 决策**：
> - [ ] A · 以 `--tkn-subject-*` 为准（推荐 · 设计一致性）
> - [ ] B · 以 mockup 色值为准（新增学科 token 或允许 custom CSS 裸色值）

---

## ⚠️ 中优先级：缺失语义 token（需逐项确认）

### 1 · 无 indigo（靛紫）token

mockup 大量使用 `#5856D6` 作为"通用标签"色，但 ui-kit 无对应 indigo token。

| 用途 | mockup 色值 | 出现次数 | 建议 |
|---|---|---|---|
| 标签背景 | `rgba(88,86,214,.08)` | 1 | 新增 `--tkn-color-accent-indigo` 或 custom |
| 标签边框 | `rgba(88,86,214,.18)` | 1 | 同上 |
| 标签文字 | `#5856D6` | 1 | 同上 |
| 时段图标背景（晚上）| `rgba(88,86,214,.14)` | 1 | 同上 |
| 时段图标文字（晚上）| `#5856D6` | 1 | 同上 |

> **User 决策**：
> - [ ] A · 新增 `--tkn-color-accent-indigo: #5856D6` token（需改 tokens.css）
> - [ ] B · custom CSS 硬编码（标注 mockup-only 值）
> - [ ] C · 统一替换为 `--tkn-color-primary-default`（色相不同但可接受）

### 2 · 无 orange token

mockup 使用 `#FF9500` 作为橙色（物理学科 + 倒计时"快到了"状态）。

| 用途 | mockup 色值 | 出现次数 | 建议 |
|---|---|---|---|
| 倒计时"soon"文字 | `#FF9500` | 1 | `--tkn-color-warning-default` 但色差大 |
| 倒计时"soon"背景 | `rgba(255,149,0,.15)` | 1 | 无 token |
| 难度标签背景 | `rgba(255,149,0,.10)` | 1 | 无 token |
| 难度标签边框 | `rgba(255,149,0,.22)` | 1 | 无 token |

> **User 决策**：
> - [ ] A · 新增 `--tkn-color-accent-orange: #FF9500` token
> - [ ] B · 使用 `--tkn-color-warning-default`（`#b45309` · 视觉偏差较大）
> - [ ] C · custom CSS 硬编码

### 3 · 无 yellow token

mockup 使用 `#FFCC00` 作为"现在"时段图标 + live dot 颜色。

| 用途 | mockup 色值 | 出现次数 | 建议 |
|---|---|---|---|
| live dot 填充 | `#FFCC00` | 1 | `--tkn-color-warning-default` 色差大 |
| live dot 光晕 | `rgba(255,204,0,.28)` | 1 | 无 token |
| 时段图标背景（现在）| `rgba(255,204,0,.28)` | 1 | 无 token |

> **User 决策**：
> - [ ] A · 新增 `--tkn-color-accent-yellow: #FFCC00` token
> - [ ] B · 使用 `--tkn-color-warning-default`（视觉大幅差异）
> - [ ] C · custom CSS 硬编码

### 4 · 无 text-tertiary token

mockup 大量使用 `#8E8E93`（iOS 三级文字色）：标签数量、未开始倒计时文字、非激活 Tab 图标。

| 用途 | mockup 色值 | 出现次数 | 建议 |
|---|---|---|---|
| 时段计数文字 | `#8E8E93` | ~3 | 无 text-tertiary token |
| 非激活 Tab 图标 | `#8E8E93` | ~4 | 同上 |
| 倒计时"wait"文字 | `#8E8E93` | 1 | 同上 |

> **User 决策**：
> - [ ] A · 新增 `--tkn-color-text-tertiary: #8E8E93` token
> - [ ] B · 使用 `--tkn-color-text-primary` + opacity（行为不同）
> - [ ] C · custom CSS 硬编码

### 5 · 无 border/separator token

mockup 使用 `rgba(60,60,67,.14)` 作为分割线（NavBar 底部边框、Tab 顶部边框、卡片内竖分割线）。

| 用途 | 出现次数 | 建议 |
|---|---|---|
| NavBar border-bottom | 1 | 新增 `--tkn-color-border-subtle` |
| TabBar border-top | 1 | 同上 |
| 卡片内竖分割线 | 1 | 同上 |
| 时段标签分割线 | 1 | 同上 |

> **User 决策**：
> - [ ] A · 新增 `--tkn-color-border-subtle: rgba(60,60,67,.14)` token
> - [ ] B · custom CSS 硬编码

---

## 近似匹配（approx）· 请确认可接受

| mockup 原始值 | 最近 token | 色差 Δ | 出现次数 | 用于区块 |
|---|---|---|---|---|
| `#F2F2F7` | `--tkn-color-bg-light (#f5f5f7)` | 4.2 | 2 | 倒计时"wait"背景 · nav glass 背景 |
| `#1C1C1E` | `--tkn-color-text-primary (#1d1d1f)` | 1.7 | 1 | 时间列文字 |
| `#C7C7CC` | `--tkn-color-overlay-media` | 19.1 | 1 | 箭头 chevron 颜色 |
| `rgba(242,242,247,.78)` | `--tkn-color-bg-light` | 4.2 | 1 | NavBar glass 背景 |

> **#C7C7CC → --tkn-color-overlay-media** 偏差较大。建议：
> - [ ] 接受 approx（箭头颜色宽容度高）
> - [ ] 新增 `--tkn-color-icon-disabled` token

---

## 特殊处理：Hero 卡渐变色

Hero 汇总卡三色渐变（`#0F1A3D → #1F3C8C → #5F5BDB`）无对应 token，属页面专属设计。

> **User 决策**：
> - [ ] A · custom CSS 硬编码（只此页面使用，无需 token）
> - [ ] B · 新增 `--tkn-chart-deep-navy` / `--tkn-chart-royal-blue` / `--tkn-chart-indigo` 系列 token

---

## User 确认

- [ ] 学科色冲突：选 A 或 B
- [ ] indigo token：选 A / B / C
- [ ] orange token：选 A / B / C
- [ ] yellow token：选 A / B / C
- [ ] text-tertiary token：选 A / B / C
- [ ] border-subtle token：选 A / B
- [ ] C7C7CC arrow color：接受 approx 或新增 token
- [ ] Hero 渐变：选 A 或 B
- [ ] build-spec.json 已 review，Builder 可开工
