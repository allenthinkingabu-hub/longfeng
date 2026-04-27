# Token 映射 Review — ListPage

> 生成于：2026-04-27 · 工具：`fe-preflight` skill · parse_mockup.py
> **Builder 开工前必须完成本文档所有决策**

---

## 🚨 核心设计冲突（必须 User 决策，影响全页）

### 冲突 A：iOS 系统蓝 vs Apple Web 蓝

| 项目 | mockup 值 | design token | 色差 |
|---|---|---|---|
| 主色（蓝） | `#007AFF`（iOS UIKit Blue） | `--tkn-color-primary-default: #0071e3` | Δ≈29 |
| 浅蓝（渐变起点） | `#5FA8FF` | 无 token | — |
| 蓝透明 10% | `rgba(0,122,255,.10)` | 无 token | — |
| 蓝透明 25% | `rgba(0,122,255,.25)` | 无 token | — |
| 蓝透明 35% | `rgba(0,122,255,.35)` | 无 token | — |

**影响范围**：筛选图标、学科 chip active、掌握度 active 状态、FAB 按钮渐变、艾宾浩斯进度 now 节点、TabBar active、"AI 语义" badge

**可选方案**：
- **A1** ✅ 统一用 `--tkn-color-primary-default: #0071e3`（保持设计系统一致性，视觉略偏深）
- **A2** 沿用 mockup `#007AFF`，将其加入 tokens 为 `--tkn-color-primary-ios`
- **A3** 接受 Δ29 的视觉差异，Builder 直接用 `var(--tkn-color-primary-default)`

**User 决策**：[x] A1 — 统一用 `--tkn-color-primary-default: #0071e3`

---

### 冲突 B：语义色 iOS vs Token（危险/警告/成功）

| 角色 | mockup 值 | design token | 色差 |
|---|---|---|---|
| 危险/未掌握（红） | `#FF3B30`（iOS Red） | `--tkn-color-danger-default: #c0392b` | 非常大 |
| 警告/部分掌握（橙） | `#FF9500`（iOS Orange） | `--tkn-color-warning-default: #b45309` | 非常大 |
| 成功/已掌握（绿） | `#34C759`（iOS Green） | `--tkn-color-success-default: #1a7d34` | 非常大 |

**影响范围**：掌握度三格数值颜色、left-bar 竖条、掌握度 pill badge、艾宾浩斯 done 节点

**可选方案**：
- **B1** ✅ 统一用现有 token（`--tkn-color-danger/warning/success-default`），视觉与 mockup 差异明显但系统一致
- **B2** 沿用 iOS 系统色，将其覆盖 token 值（更改 tokens.css）
- **B3** 新增平行 token：`--tkn-color-danger-bright / --tkn-color-warning-bright / --tkn-color-success-bright`

**User 决策**：[x] B1 — 统一用现有 token（`--tkn-color-danger/warning/success-default`）

---

### 冲突 C：Subject 颜色体系完全不同

| 学科 | mockup 颜色 | design token | 说明 |
|---|---|---|---|
| 数学 | `#007AFF`（蓝） | `--tkn-subject-math: #c41e3a`（红） | 完全相反 |
| 物理 | `#FF9500`（橙） | `--tkn-subject-physics: #0057b7`（蓝） | 完全相反 |
| 化学 | `#5856D6`（紫） | `--tkn-subject-chemistry: #1a6b3a`（绿） | 完全相反 |
| 英语 | `#34C759`（绿） | `--tkn-subject-english: #9c4f00`（琥珀） | 完全相反 |
| 紫色 `#5856D6` | 化学标签颜色 | 无 token（无紫色 token） | — |

**影响范围**：卡片 header 学科标签颜色、学科 chip 色（如果按学科上色）

**可选方案**：
- **C1** 采用设计系统 `--tkn-subject-*` token（与当前 ListPage 实现不同，需重写）
- **C2** 保持 mockup 颜色方案（mockup > 设计系统），更新 `--tkn-subject-*` token 值
- **C3** List 页面学科标签统一不上色（纯文字，避免冲突）

**User 决策**：[x] C1 — 采用设计系统 `--tkn-subject-*` token

---

## 近似匹配（approx）· 请确认可接受

| mockup 原始值 | 最近 token | 色差 | 出现次数 | 用于区块 | 建议 |
|---|---|---|---|---|---|
| `#1C1C1E` | `--tkn-color-text-primary (#1d1d1f)` | Δ1.7 | 1 | 文字主色 | ✅ 接受 |
| `#F2F2F7` | `--tkn-color-bg-light (#f5f5f7)` | Δ4.2 | 1 | 页面背景色 | ✅ 接受 |
| `#111` | `--tkn-color-text-primary (#1d1d1f)` | Δ22 | 9 | 状态栏文字/手机边框 | ✅ 接受（手机 frame 装饰元素，非业务） |
| `rgba(242,242,247,.78)` | `--tkn-color-bg-light` | Δ4.2 | 1 | nav 背景毛玻璃 | ✅ 接受 |
| `#E5E5EA` | `--tkn-color-button-active (#ededf2)` | Δ13.9 | 1 | 艾宾浩斯空节点 | ⚠️ 偏差较大，请确认 |

**User 确认**：[x] 以上 approx 条目均可接受

---

## 缺失 Token（none）· 需新增或指定替代

| mockup 值 | 出现次数 | 用于区块 | 建议处理 |
|---|---|---|---|
| `#3C3C43`（text2 次级文字） | 多处 | 卡片内容 / 排序提示 | 新增 `--tkn-color-text-secondary-strong` 或用 `--tkn-color-text-secondary` |
| `#8E8E93`（text3 三级文字） | 多处 | 标签颜色 / 时间 / TabBar inactive | 新增 `--tkn-color-text-tertiary-strong` 或用 `--tkn-color-text-tertiary` |
| `rgba(60,60,67,.14)`（separator） | 多处 | 边框 / 分割线 / TabBar 上边框 | 新增 `--tkn-color-separator` 或 `--tkn-color-border-subtle` |
| `#5856D6`（iOS Indigo） | 2 | 化学学科色 / kp 标签 | 取决于冲突 C 决策；如 C1 则用 `--tkn-subject-chemistry` |
| `#FFCC00`（iOS Yellow） | 1 | 装饰性（mockup stage 环境） | 可忽略（不在核心页面元素中） |
| `#fde9e9`（题目缩略图题号背景） | 1 | thumb 区 qno badge | 新增 token 或纯 custom（缩略图装饰） |
| `#7a3b3b`（题目缩略图题号字色） | 1 | thumb 区 qno badge | 同上 |
| `#fbf8f0 → #f1ece0`（thumb 渐变背景） | 1 | card thumb 缩略图区背景 | 纯 custom（装饰性缩略图，非功能色） |

**User 确认**：[x] 以上 none 条目均用 design token 替代或标 custom（见 build-spec.json 各 CONFLICT 项均采用对应 --tkn-* token）

---

## 间距 / 圆角 · 近似问题

| 项目 | mockup | 最近 token | 差值 | 影响 |
|---|---|---|---|---|
| card border-radius | `16px` | `--tkn-radius-lg: 12px` | Δ4px | 卡片圆角偏小 |
| chip padding-x | `11px` | `--tkn-spacing-5: 10px` | Δ1px | 可接受 |
| search bar border-radius | `10px` | `--tkn-radius-md: 11px` | Δ1px | 可接受 |

**card border-radius 决策**：
- **D1** 用 `--tkn-radius-lg: 12px`（系统一致，比 mockup 略小）
- **D2** 临时写 `border-radius: 16px`（与 token 系统不一致）
- **D3** 新增 token `--tkn-radius-xl: 16px`

**User 决策**：[x] D1 — 用 `--tkn-radius-lg: 12px`（系统一致）

---

## User 最终确认

> 完成上方 4 个冲突决策（A/B/C/D）+ approx 确认 + none 处理方式后，勾选以下项：

- [x] 冲突 A（主色蓝）已决策 → A1：`--tkn-color-primary-default`
- [x] 冲突 B（语义色）已决策 → B1：`--tkn-color-danger/warning/success-default`
- [x] 冲突 C（Subject 颜色）已决策 → C1：`--tkn-subject-*` token
- [x] 冲突 D（card border-radius）已决策 → D1：`--tkn-radius-lg: 12px`
- [x] 所有 approx 条目已确认可接受
- [x] 所有 none 条目已指定处理方式
- [x] `ListPage-build-spec.json` 已 review
- [x] **Builder 可开工** ✅ 2026-04-27
