# Token 映射 Review — DetailPage

> 生成于：2026-04-27 · 工具：`fe-preflight` skill · parse_mockup.py
> **色值冲突 A1/B1/C1/D1 决策已继承自 ListPage，本文档只列 DetailPage 特有问题**

---

## 已确认继承（无需再决策）

| 决策 | 规则 | 应用场景 |
|---|---|---|
| A1 | `#007AFF` → `--tkn-color-primary-default (#0071e3)` | back/编辑/更多图标、雷达图数据、CTA 主按钮 |
| B1 | `#FF3B30/#FF9500/#34C759` → `--tkn-color-danger/warning/success-default` | pill badge、答案区颜色、错因区、节点、统计数值 |
| C1 | subject colors → `--tkn-subject-*` | 雷达图图例中的学科 dot |
| D1 | border-radius 优先最近 token | 各区块圆角 |

---

## DetailPage 特有问题

### 问题 E：Segment Control 背景色 `rgba(118,118,128,.12)`

| 项目 | mockup 值 | 最近 token | 情况 |
|---|---|---|---|
| segment track bg | `rgba(118,118,128,.12)` | 无精确匹配 | none |
| CTA ghost btn bg | `rgba(118,118,128,.12)` | 同上 | none |

**可选方案**：
- **E1** 新增 `--tkn-color-segment-track: rgba(118,118,128,.12)`（通用 iOS 分段控件背景）
- **E2** 用 `--tkn-color-button-active (#ededf2)` 替代（色差较大，视觉偏白）
- **E3** 允许裸色值（detail 内 custom 区块）

**User 决策**：[x] E3 — 允许裸色值（仅用于 custom 区块，不影响设计系统全局）

---

### 问题 F：`#FF2D55`（pink，iOS 粉色）

| 项目 | mockup 值 | 最近 token | 出现次数 |
|---|---|---|---|
| （CSS 变量 `--pink`，未实际使用） | `#FF2D55` | 无 | 定义未使用 |

**处理**：变量定义在 CSS :root 中但 mockup 无实际用途，实现时忽略。

---

## 近似匹配（approx）· 请确认可接受

| mockup 原始值 | 最近 token | 色差 | 出现次数 | 用于区块 | 建议 |
|---|---|---|---|---|---|
| `#E5E5EA` | `--tkn-color-button-active (#ededf2)` | Δ13.9 | 10 | 雷达图 ring/axis、节点空状态 | ✅ 接受（近似，视觉差异可忽略） |
| `#111` | `--tkn-color-text-primary (#1d1d1f)` | Δ22 | 9 | 状态栏文字 · phone frame | ✅ 接受（装饰元素） |
| `#F2F2F7` | `--tkn-color-bg-light (#f5f5f7)` | Δ4.2 | 2 | 页面背景、nav 背景 | ✅ 接受 |
| `#222` | `--tkn-color-surface-dark-5` | Δ4.9 | 2 | 纸张文字（装饰性） | ✅ 接受（装饰） |
| `#1C1C1E` | `--tkn-color-text-primary` | Δ1.7 | 1 | 主文字色 | ✅ 接受 |
| `#E8EEFB / #F5E8FB / #EEF2F8 / #E6ECF5` | `--tkn-color-button-active / --tkn-color-bg-light` | Δ7-13 | 各1 | phone frame 装饰背景 | ✅ 接受（非业务元素） |
| `#FBF8F0` | `--tkn-color-bg-light` | Δ9.7 | 1 | image-card 背景渐变起点 | ✅ custom 裸色值（装饰性） |
| `#FDE9E9` | `--tkn-color-button-active` | Δ18.8 | 1 | 纸张题号 badge 背景 | ✅ custom（装饰） |
| `#C9302C` | `--tkn-color-danger-default` | Δ12.8 | 1 | 纸张 pen 颜色 | ✅ 接受（B1 决策） |
| `#C7C7CC` | `--tkn-color-overlay-media` | Δ19.1 | 1 | 时间线曲线渐变终点（已掌握后段灰色） | ✅ 接受（曲线装饰色） |
| `rgba(242,242,247,.78/.95/0)` | `--tkn-color-bg-light` | Δ4.2 | 各1 | CTA 渐变遮罩、nav 背景 | ✅ 接受 |

**User 确认**：[x] 以上 approx 条目均可接受

---

## 缺失 Token（none）· DetailPage

| mockup 值 | 出现次数 | 用于区块 | 建议处理 |
|---|---|---|---|
| `#3C3C43` | 6 | 次级文字（答案区标签、雷达图 label） | 用 `--tkn-color-text-secondary` |
| `#5FA8FF` | 3 | 时间线 now 节点渐变起点、CTA 主按钮渐变起点 | custom 裸色值（渐变起点，无对应 token） |
| `rgba(118,118,128,.12)` | 2 | segment control、CTA ghost btn | E3 决策：允许裸色值 |
| `#8E8E93` | 1 | 三级文字 | 用 `--tkn-color-text-tertiary` |
| `rgba(60,60,67,.14)` | 1 | 分割线、tabbar border | 用 `--tkn-color-border-subtle` |
| `#5E6B82` | 1 | phone frame caption（非业务） | ✅ 忽略 |
| `#ECE5D3` | 1 | image-card 背景渐变终点 | custom 裸色值（装饰） |
| `#A78A3C / #7A3B3B / #A02828` | 各1 | 纸张装饰色 | ✅ custom（装饰性纸张） |
| `rgba(30,40,80,.35)、rgba(20,30,60,.12)` | 各1 | phone frame 阴影 | ✅ 忽略（phone frame） |
| `rgba(40,50,90,.1)` | 1 | image-card 阴影 | custom（盒阴影，无 token） |
| `rgba(0,122,255,.2/.28/.18)` | 各1 | 节点 ring、雷达数据填充、CTA 阴影 | 基于 A1 primary 计算：`rgba(0,113,227,.2/.28/.18)` |
| `rgba(255,59,48,.06/.08/.10/.2)` | 各1 | 错因区/答案区透明 danger | 基于 B1：`rgba(color-danger-default-rgb, .06/.08/.10/.2)` |
| `rgba(52,199,89,.08/.2)` | 各1 | 正确答案区透明 success | 基于 B1：`rgba(color-success-default-rgb, .08/.2)` |

**User 确认**：[x] 以上 none 条目均已指定处理方式

---

## 间距 / 圆角

| 项目 | mockup | 最近 token | 差值 | 建议 |
|---|---|---|---|---|
| image-card border-radius | `18px` | `--tkn-radius-lg: 12px` | Δ6 | ✅ 接受（D1） |
| analysis/timeline/radar 卡片 | `14px` | `--tkn-radius-lg: 12px` | Δ2 | ✅ 接受 |
| stab track border-radius | `10px` | `--tkn-radius-md: 11px` | Δ1 | ✅ 接受 |
| CTA btn border-radius | `14px` | `--tkn-radius-lg: 12px` | Δ2 | ✅ 接受 |
| content top | `152px` | 无 token | — | 固定值（状态栏 54px + nav 约 98px） |

---

## User 最终确认

- [x] 问题 E 已决策 → E3：允许裸色值（segment/ghost btn）
- [x] `#FF2D55` 忽略（未使用变量）
- [x] 所有 approx 条目均可接受
- [x] 所有 none 条目已指定处理方式
- [x] `DetailPage-build-spec.json` 已 review
- [x] **Builder 可开工** ✅ 2026-04-27
