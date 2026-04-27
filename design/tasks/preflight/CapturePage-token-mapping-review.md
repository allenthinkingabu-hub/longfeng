# Token 映射 Review — CapturePage

> 生成于：2026-04-27 · 工具：`fe-preflight` skill · parse_mockup.py
> **色值冲突 A1/B1/C1/D1 决策已继承自 ListPage，本文档只列 CapturePage 特有问题**

---

## 已确认继承（无需再决策）

| 决策 | 规则 | 应用场景 |
|---|---|---|
| A1 | `#007AFF` → `--tkn-color-primary-default (#0071e3)` | subject-selector active 文字、shutter 渐变终点 |
| B1 | `#FF3B30/#FF9500/#34C759` → `--tkn-color-danger/warning/success-default` | paper pen 色 |
| D1 | border-radius 优先最近 token | 各圆角 |

---

## CapturePage 特有：相机专属色（允许自定义）

CapturePage 是深色相机 UI，有一套与普通页面完全不同的色彩体系。以下颜色**明确允许裸色值**，不强制 token：

| 颜色 | 用途 | 处理方式 |
|---|---|---|
| `#FFCC00`（相机黄） | 扫描线、四角框、模式 tab indicator、detect pulse | ✅ 允许 custom 裸色值（相机 UI 专属装饰色） |
| `rgba(255,204,0,.45/.7/.95/.18)` | 黄色发光效果 | ✅ 同上 |
| `#0B0F1A`（深色背景） | 相机主屏幕 | ✅ 允许裸色值（相机 UI 专属） |
| `rgba(11,15,26,0/.85)` | dock 渐变 | ✅ 同上 |
| `rgba(0,0,0,.45/.55/.35)` | 半透明遮罩 | ✅ 允许（无法 token 化透明值） |
| `rgba(255,255,255,.10/.18/.12/.15/.95/.85)` | 磨砂玻璃效果 | ✅ 允许（无法 token 化透明值） |
| `rgba(255,80,80,.55)` | 错题划线装饰 | ✅ 允许（装饰性，不影响功能色） |

**User 确认**：[x] 以上相机专属色允许裸色值，不要求 token 引用

---

## 近似匹配（approx）· 请确认可接受

| mockup 原始值 | 最近 token | 色差 | 出现次数 | 用于区块 | 建议 |
|---|---|---|---|---|---|
| `#0B0F1A` | `--tkn-color-text-primary (#1d1d1f)` | Δ23.3 | 3 | 相机背景（渐变参数） | ✅ 不走 token，允许裸色值（相机专属） |
| `#1C1C1E` | `--tkn-color-text-primary (#1d1d1f)` | Δ1.7 | 1 | 主文字色 | ✅ 接受 |
| `#222` | `--tkn-color-surface-dark-5` | Δ4.9 | 2 | 纸张 paper 文字 | ✅ 接受（装饰内容） |
| `#1D2433` | `--tkn-color-surface-dark-2` | Δ14.4 | 1 | viewfinder 背景渐变 | ✅ 允许裸色值（相机专属） |
| `#FBF8F0 / #F1ECE0` | `--tkn-color-bg-light` | Δ9.7/18.5 | 各1 | 纸张背景渐变 | ✅ custom（装饰性纸张） |
| `#C9302C` | `--tkn-color-danger-default (#c0392b)` | Δ12.8 | 1 | 纸张 pen 颜色 | ✅ 接受（B1 决策） |
| `#FDE9E9` | `--tkn-color-button-active` | Δ18.8 | 1 | 题号 badge 背景 | ✅ custom（装饰性） |
| `#E8EEFB / #F5E8FB / #EEF2F8 / #E6ECF5` | `--tkn-color-button-active / --tkn-color-bg-light` | Δ7-13 | 各1 | 页面装饰背景（phone frame 外） | ✅ 接受（手机 frame 装饰，非业务元素） |

**User 确认**：[x] 以上 approx 条目均可接受

---

## 缺失 Token（none）· CapturePage 特有

| mockup 值 | 出现次数 | 用于区块 | 建议处理 |
|---|---|---|---|
| `#3C3C43` | 1 | 文字次级色 | 用 `--tkn-color-text-secondary` |
| `#8E8E93` | 1 | 文字三级色 | 用 `--tkn-color-text-tertiary` |
| `rgba(60,60,67,.14)` | 1 | 分割线 | 用 `--tkn-color-border-subtle` |
| `#5E6B82` | 1 | 页面 caption 文字（phone frame 外） | ✅ 忽略（非业务元素） |
| `#A78A3C 、#7A3B3B、#A02828` | 各1 | 纸张装饰（lbl/qno 颜色） | ✅ custom 裸色值（装饰性纸张） |
| `rgba(30,40,80,.35)、rgba(20,30,60,.12)` | 各1 | phone frame 阴影 | ✅ 忽略（phone frame 装饰） |
| `#5FA8FF` | 1 | shutter 渐变起点 | custom 裸色值（无 token，渐变起点） |

**User 确认**：[x] 以上 none 条目均已指定处理方式

---

## 间距 / 圆角

| 项目 | mockup | 最近 token | 差值 | 建议 |
|---|---|---|---|---|
| icon-btn border-radius | `50%` | `--tkn-radius-circle (50%)` | 0 | ✅ exact |
| subj chip border-radius | `10px` | `--tkn-radius-md: 11px` | Δ1 | ✅ 接受 |
| tip 卡片 border-radius | `14px` | `--tkn-radius-lg: 12px` | Δ2 | ✅ 接受（D1）|
| shutter 外圈 | `78px 圆` | `--tkn-radius-circle` | 0 | ✅ exact |
| side-btn border-radius | `14px` | `--tkn-radius-lg: 12px` | Δ2 | ✅ 接受 |
| 主要间距 18px | `18px` | `--tkn-spacing-8: 16px` | Δ2 | ✅ 接受（高频水平 padding） |

---

## User 最终确认

- [x] 相机专属色允许裸色值（黄色系 + 深色背景系）
- [x] 所有 approx 条目均可接受
- [x] 所有 none 条目已指定处理方式
- [x] `CapturePage-build-spec.json` 已 review
- [x] **Builder 可开工** ✅ 2026-04-27
