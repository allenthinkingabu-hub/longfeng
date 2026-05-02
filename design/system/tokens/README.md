# Design Token Single Source

> **⚠️ v2.0 重大变更（2026-05-02）**：原"warm/cool/celebrate 三分法 + aurora 极光 + #2C2A26 暖棕字 + #0071e3 macOS 蓝"体系**已废弃**。当前真相来源：`design/system/STYLE-TRUTH.md`，反推自 `design/mockups/wrongbook/_archive/` 19 张原版 mockup。
>
> 新体系 = **完整 iOS HIG 9 色 + 深蓝 Hero 渐变 + 多色 blob + Conic 彩虹 + 玻璃态**。

<!--
source: design/system/STYLE-TRUTH.md (v1.0 · archive-aligned)
generated_by: archive reverse-engineering 2026-05-02
human_overrides: [subject-palette, miniprogram-saturation, brand-wechat, archive-iOS-HIG-realignment]
-->

## 单一真相来源

本目录是所有设计 token 的单一真相来源。前端源代码或 mockup HTML **不可硬编码**任何设计值（颜色、间距、圆角、字体、阴影、动效、渐变），必须引用从这些 JSON 生成的 `--tkn-*` CSS 自定义属性。

**冲突时优先级**：`STYLE-TRUTH.md` > `tokens/*.json` > `DESIGN.md` > 各 spec。

## 体系架构（v2.0）

| Layer | File | Purpose | Trigger |
|---|---|---|---|
| **L1 · iOS HIG base + Archive 真相** | `color.json` (主调色板 · iOS HIG 9 色 · Hero stops · Blob · Em 渐变 · KP 棕字 · Glass 白透 · 学科色 · 微信品牌) · `typography.json` · `spacing.json` · `radius.json` · `shadow.json` · `motion.json` | 页面骨架 + iOS HIG 节奏 + archive 装饰系统 | 永远默认 |
| **L2 · ⛔️ DEPRECATED** | `warmth.json` | warm/aurora 体系已废 · 仅保留作 migration map | 不再使用 |
| **L3 · Celebration** | `celebration.json` | Mastery 状态 · Confetti · Streak fire · 庆祝 motion | 4 个白名单庆祝时刻 |
| EXCEPTION 1 | `color.json#tkn.subject` | 4 学科颜色身份 | SubjectChip / left-bar / icon |

## Token 文件 v2.0

| 文件 | 状态 | 主要内容 | 关键 token |
|---|---|---|---|
| `color.json` | ✅ v2.0 archive-aligned | iOS HIG 9 色 / 灰阶 / Hero stops 7 档 / Blob 7 色 / Em 渐变 / KP 棕字 / Glass 白透 14 档 / 学科色 / 微信 | `--tkn-color-primary-DEFAULT #007AFF` · `--tkn-color-text-primary #1C1C1E` · `--tkn-color-bg-light #F2F2F7` · `--tkn-color-system-{red\|orange\|green\|indigo\|yellow\|purple\|teal\|pink}` · `--tkn-color-hero-stop-{1-7}` · `--tkn-color-blob-{purple\|cyan\|pink\|coral\|mint\|gold}` · `--tkn-color-em-{from-gold\|to-coral\|to-amber}` · `--tkn-color-glass-white-{08\|10\|12\|14\|16\|18\|22\|24\|30\|35\|78\|86\|92}` |
| `shadow.json` | ✅ v2.0 archive-aligned | archive 实测阴影全集 18 类 | `--tkn-shadow-card 0 1px 2px rgba(0,0,0,.04)` (基线) · `--tkn-shadow-hero-card 0 10px 30px rgba(31,60,140,.25)` (上限) · `--tkn-shadow-shutter` (三层环) · `--tkn-shadow-cta-deep` (landing) · `--tkn-shadow-focus 0 0 0 2px #007AFF` (修正) |
| `radius.json` | ✅ v2.0 archive-aligned | archive 实测 13 档 + 2 别名 | `--tkn-radius-{phone\|hero-card\|card-lg\|card-sm\|btn\|cell\|ic-md\|ic-sm\|sm\|xs\|micro\|tiny\|pill\|circle}` |
| `warmth.json` | ⛔️ DEPRECATED | 仅保留 migration map · `_deprecated_*` 命名空间标注每个老 token 的新对应 | 已无新 token · 老引用按 `_replaced_by` 字段迁移 |
| `celebration.json` | ⏳ 待 v2 同步 | Mastery 3 状态 + Confetti + Streak | `--tkn-color-mastery-*` · `--tkn-color-celebrate-confetti-{1-5}` |
| `typography.json` | ⏳ 待 v2 同步（Apple HIG 字号体系仍可用 · 需补 archive 字号） | 字体族 + 14 档字号（Apple HIG）· archive 实际还用 32/28/24/22/15/13.5/13/12/11/10/9px | `--tkn-font-*` · `--tkn-type-*` |
| `spacing.json` | ✅ 4pt/8pt 通用 · 与 archive 兼容 | 4pt/8pt 网格 (2-96px) | `--tkn-spacing-*` |
| `motion.json` | ✅ 与 archive 一致 | Apple HIG ease + 90/150/250/400ms | `--tkn-motion-*` |

## 命名约定

所有生成的 CSS 自定义属性遵循模式：

```
--tkn-<group>-<name>-<variant>
```

**v2.0 关键 token 示例**（修正自 v1.0）：
- `--tkn-color-primary-DEFAULT` → iOS Blue `#007AFF`（**v1.0 错值 #0071e3 已废**）
- `--tkn-color-text-primary` → iOS Near Black `#1C1C1E`（**v1.0 错值 #1d1d1f / #2C2A26 已废**）
- `--tkn-color-text-secondary` → iOS Sec `#636366`
- `--tkn-color-text-tertiary` → iOS Ter `#8E8E93`
- `--tkn-color-bg-light` → iOS Light bg `#F2F2F7`
- `--tkn-color-sep` → iOS Separator `rgba(60,60,67,.14)`
- `--tkn-color-system-red` → `#FF3B30`
- `--tkn-color-system-green` → `#34C759`
- `--tkn-color-system-indigo` → `#5856D6`
- `--tkn-color-system-yellow` → `#FFCC00` (02_capture 检测)
- `--tkn-color-hero-stop-2` → `#1E3A8A` (深蓝 hero 起点)
- `--tkn-color-blob-purple` → `rgba(88,86,214,.55)` (hero 装饰)
- `--tkn-color-em-from-gold` → `#FFD166` (强调字渐变)
- `--tkn-color-glass-white-16` → `rgba(255,255,255,.16)` (玻璃 pill)
- `--tkn-color-brand-wechat` → 微信绿 `#07C160` (铁律 1 例外)
- `--tkn-shadow-focus` → `0 0 0 2px #007AFF` (**修正自 #0071e3**)
- `--tkn-shadow-hero-card` → 单页面阴影上限
- `--tkn-radius-hero-card` → 22px
- `--tkn-radius-card-lg` → 18px (主卡片)
- `--tkn-subject-math` → 数学胭脂红 `#C41E3A`（铁律 1 例外）

## ⛔️ 已废 v1.0 token（不可在新代码中使用）

下列 token 在 v1.0 与 archive 不符，**必须按 migration_map 迁移**（详见 `warmth.json#_meta.migration_map`）：

```
--tkn-color-warm-bg            → --tkn-color-bg-light #F2F2F7
--tkn-color-warm-elevated      → --tkn-color-card #FFFFFF
--tkn-color-warm-text-primary  → --tkn-color-text-primary #1C1C1E
--tkn-color-warm-text-secondary→ --tkn-color-text-secondary #636366
--tkn-color-warm-divider       → --tkn-color-sep rgba(60,60,67,.14)
--tkn-gradient-aurora          → 深蓝 hero 渐变（用 hero-stop-* tokens 组装）
                                · linear-gradient(180deg, #1E3A8A, #3B5BDB, #5B8DEF) [01_home]
                                · linear-gradient(170deg, #0F1A3D, #1F3C8C, #5F5BDB, #8B87F6) [14_landing]
                                · linear-gradient(135deg, #0F1A3D, #1F3C8C, #5F5BDB) [reviewhero]
--tkn-gradient-focus-night     → --tkn-color-bg-camera #0B0F1A (实色非渐变)
--tkn-gradient-result-warm     → --tkn-color-bg-light #F2F2F7
--tkn-shadow-warm-card         → --tkn-shadow-card-deep
--tkn-shadow-warm-hero         → --tkn-shadow-hero-card
```

## Mood 体系（v2.0 · 5 类）

> **v1.0 三分法（cool/warm/celebrate）已废**

| Mood | 适用页 | 视觉特征 | 关键 token |
|---|---|---|---|
| **A** `hero+overlap` | P-HOME / P-LANDING / P-GUEST-CAPTURE / P-WELCOMEBACK | 深蓝 hero 240-380px + 米白 scroll overlap 24-26px 圆角 | `--tkn-color-hero-stop-*` + `--tkn-color-blob-*` + `--tkn-color-bg-light` |
| **B** `pure-warm` | P04 / P05 / P06 / P07 / P10 / P11 / P12 / P13 | 米白底 + 白卡 + iOS nav 玻璃态 | `--tkn-color-bg-light` + `--tkn-color-card` + `--tkn-color-glass-white-78` |
| **C** `dark-camera` | P02 / P15 | 全屏 #0B0F1A + viewfinder 内模拟纸面 + 黄色检测 | `--tkn-color-bg-camera` + `--tkn-color-system-yellow` + `--tkn-color-glass-black-*` |
| **D** `celebrate-green` | P09 | 绿渐变 hero + confetti + pulse | `linear-gradient(175deg, #0F7F3E, #1FAE5C, #34C759)` + `--tkn-motion-celebrate-*` |
| **E** `teal-observer` | P11 / P16 / P18 | 青绿主题 + identity card | `--tkn-color-system-teal` + 深蓝 hero 变体 |

## System vs Learning State (Dual-Track)

关键区分（DESIGN.md §2.6）：

- **System track**（iOS 克制 · `--tkn-color-system-*`）：网络错误、登录失效、表单验证、AI 服务降级 banner
- **Learning track**（学生友好 · `--tkn-color-mastery-*` / `--tkn-color-encouragement-*`）：自评分按钮、掌握度卡、节点状态、庆祝

两条轨道可能 hex 接近（如 `system-danger` 和 `mastery-forgot` 都是 `#C0392B`），但**语义不可混用**。toast 用 mastery 色 / 自评按钮用 system 色 = 铁律违反。

## 铁律 1 例外注册

铁律 1 = "蓝色是唯一交互色"。已注册例外：

| Exception ID | Token | 出现处 | 必须标记 |
|---|---|---|---|
| `wechat-brand` | `--tkn-color-brand-wechat` | P00 login 微信按钮 | `data-iron-rule-1-exception="wechat-brand"` |
| `self-grading` | `--tkn-color-mastery-{forgot\|partial\|mastered}` | P08 自评分按钮 | `data-iron-rule-1-exception="self-grading"` |
| `subject-palette` | `--tkn-subject-{math\|physics\|chemistry\|english}` | 学科 chip / left-bar / icon | `data-iron-rule-1-exception="subject-palette"` |

新增例外需 Design Lead 批准并加入此注册表。

## Style Dictionary Build

Style Dictionary 4.x 配置在 `frontend/packages/ui-kit/sd.config.js`。实施阶段构建目标：

| Output | Path | Platform |
|---|---|---|
| `tokens.css` | `frontend/packages/ui-kit/src/tokens.css` | H5 web |
| `tokens.wxss` | `frontend/packages/ui-kit/src/tokens.wxss` | WeChat miniprogram |
| `tokens.ts` | `frontend/packages/ui-kit/src/tokens.ts` | TypeScript constants |

**source 路径必须包含所有 layer**（warmth.json 仍纳入构建以提供 deprecated 兼容引用）：
```js
source: [
  'design/system/tokens/color.json',
  'design/system/tokens/typography.json',
  'design/system/tokens/spacing.json',
  'design/system/tokens/radius.json',
  'design/system/tokens/shadow.json',
  'design/system/tokens/motion.json',
  'design/system/tokens/warmth.json',       // L2 · DEPRECATED · backwards-compat
  'design/system/tokens/celebration.json'    // L3
]
```

## 平台覆盖

WeChat miniprogram 需要饱和度和阴影修正（gamma 渲染差异）：
- `color.json#_meta.platform_overrides.wechat`
- `shadow.json#tkn.shadow.*.wechat_override`

JSON 源保持平台中立 —— 差异仅在 Style Dictionary transform 后的 `tokens.wxss` 中体现。

## A11y 契约

每个语义色都带 `contrast_on_*` 字段（WCAG 公式）。带 `_warning` 标记的色表明哪些表面配对应避免。低于 AA 的 token 必须文档化为 accent / 背景专用，不可作为主文字。

## 文件修改历史

| Date | Version | Change |
|---|---|---|
| 2026-04-21 | 0.1 | 初始 Apple-mirror tokens |
| 2026-05-02 | 1.0 | 三层架构：rename success/warning/danger/info → system-* · 加 brand.wechat · 加 warmth.json (L2) · 加 celebration.json (L3) |
| 2026-05-02 | **2.0** | **archive 真相对齐**：废弃 warmth.json L2 · 重写 color.json (添加 iOS HIG 9 色 / Hero stops / Blob / Em / KP / Glass / 修 #0071e3→#007AFF · #1d1d1f→#1C1C1E) · 重写 shadow.json (修 focus / 添加 archive 阴影 18 类) · 重写 radius.json (添加 archive 13 档) · 写 STYLE-TRUTH.md 作权威源 · 后续待同步 typography / celebration / DESIGN.md / 20 page spec |
