# AI 错题本 · Design System v2.0 (archive-aligned)

> **⚠️ v2.0 重大对齐**：v1.0 的 "warm/cool/celebrate 三分法 + aurora 极光 + #2C2A26 暖棕字 + #0071e3 macOS 蓝" 体系**已废**。当前真相 = `design/system/STYLE-TRUTH.md`（反推自 `design/mockups/wrongbook/_archive/` 19 张原版 mockup）。
>
> **角色定位**：本文档是 tokens / components / molecules / pages 四级体系的总宪法 · STYLE-TRUTH.md 是其权威依据。
> **服务对象**：UI/UX 设计师 + AI 实施代理（fe-preflight / fe-builder / fe-accept skill 链）+ 前端工程师 + QA。
> **使用约束**：与本文档冲突的任何 mockup / 组件 / 代码均视为缺陷。**本文档与 STYLE-TRUTH.md 冲突时，以 STYLE-TRUTH.md 为准**。
> **修订原则**：本文档变更 = 大版本变更，需重新走"设计评审 → 全量 mockup 同步 → spec 同步"三步。
> **版本**：v2.0 · 2026-05-02 · archive 对齐 + iOS HIG 真相反推

---

## 0. 设计哲学

### 0.1 一句话哲学

> **冷静学习、暖意鼓励、永不打扰。**

错题本是工具不是玩具。学生要的是被看见和被尊重，不是被哄；家长要的是这看起来像个学习软件，不是游戏。Apple 给了我们「克制」的语言，学生侧给了我们「温度」的允许，两者通过**冷暖分层**共存而不打架。

### 0.2 三个紧箍咒（产品基因）

| 紧箍咒 | 翻译 | 落地约束 |
|---|---|---|
| **学习先于美学** | 任何视觉决策必须服务于「学生看清错题、记住知识点、按时复习」 | 错因红条不能为了好看而柔化；倒计时不能为了节制而隐藏；公式不能因为字体优雅而读不清 |
| **温度先于个性** | 我们不做"独特的视觉风格"，做"可信任的学习伴侣" | 不上吉祥物 IP、不上品牌 emoji、不上插画师签名风格。情感靠**节奏**和**留白**给，不靠装饰 |
| **机器先于灵感** | 所有视觉元素必须能被 AI 1:1 翻译成代码 | 没有 token 引用的颜色禁止入图；没有 testid 的元素禁止入图；没有 a11y 角色的交互禁止入图 |

### 0.3 与 Apple 原版的偏离声明（透明记账）

**保留**：光学排版 / 单一蓝交互色 / 4pt 间距网格 / 5–12px + 980px 圆角双轨 / 单层柔影 / Section 节奏二分 / 负字距全场景 / focus ring 强制

**有意识偏离**：

| 偏离点 | Apple 原版 | 我们的版本 | 偏离理由 |
|---|---|---|---|
| **底色** | `#f5f5f7` 蓝灰 | 信息流用 **`#FAF8F4` 暖米白** | K12 学生长时间盯屏，蓝灰冷调易疲劳；暖米白和纸质书桌一致，降低焦虑 |
| **学科色** | 不存在（Apple 单蓝） | 4 学科 4 色（红/蓝/绿/琥珀） | 业务必须 —— 错题归类、列表筛选、左色条都依赖学科识别 |
| **庆祝时刻** | 不存在 | 4 个白名单庆祝时刻 | K12 留存核心是即时反馈；Apple 卖产品不需要给用户打鸡血 |
| **几何插画** | 极简零装饰 | 允许**几何 SVG 插画**（无角色、无吉祥物、单/双色） | 错题本要有空状态、首次引导、错题入库成就，纯文字过冷漠 |
| **暗色模式** | 频繁切换 | MVP **不做** | 学生侧暗色实测留存无显著提升，但维护成本翻倍；P1 再做 |
| **登录 CTA 颜色** | 单一 Apple Blue | 微信一键登录用微信绿 | 微信品牌色是产品语境的一部分；唯一例外，单点声明 |

---

## 1. 八条铁律（AI Lint 必跑）

> 每条配 ✅ 应做 / ❌ 禁做 / 🤖 AI 校验点。fe-accept-mock skill 自动跑这 8 条 lint，违反 = 验收 fail。

### 铁律 1 · 单一蓝是唯一可点击色
**所有可点击元素**用 `--tkn-color-primary-DEFAULT` (iOS Blue #007AFF) 或深底变体 `--tkn-color-primary-dark` (#2997ff)。

- ✅ 主 CTA 蓝底白字、链接 #0066cc 下划线、Tab Bar 激活态蓝、复选框选中蓝、focus ring 蓝
- ❌ 学科色当 CTA、暖色当 CTA、庆祝色当 CTA、4 种颜色区分按钮重要性
- 🤖 grep 所有 `<button>` / `role="button"`，背景必须命中 `--tkn-color-primary-*` 或透明 pill。出现其他色 = fail（除非命中下方注册例外）
- ⚠️ **已注册例外**（共 2 项 · 必须 `data-iron-rule-1-exception` 显式标注）：
  - `wechat-brand` —— P00 登录"微信一键登录"按钮使用 `--tkn-color-brand-wechat` (#07C160)，因微信品牌色是产品语境的一部分
  - `self-grading` —— P08 复习执行底部三按钮使用 `--tkn-color-mastery-{forgot|partial|mastered}`，因语义是"自评判断"非"前进操作"
- 任何新增例外必须在本铁律节追加并经设计 lead 批准

### 铁律 2 · Mood 5 类一致性（v2.0 替代旧 cool/warm/celebrate 三分法）
每个 section 必须明确归类 5 类 mood 之一（详见 STYLE-TRUTH.md §3）：

| Mood | 适用页 | 视觉特征 |
|---|---|---|
| **A** `hero+overlap` | P-HOME / P-LANDING / P-GUEST-CAPTURE / P-WELCOMEBACK | 深蓝 hero 240-380px (`hero-stop-2/4/5` 渐变) + 米白 scroll overlap (24-26px 圆角) + 3 层 radial blob (purple/cyan/pink) blur 18-28px |
| **B** `pure-warm` | P04 / P05 / P06 / P07 / P10 / P11 / P12 / P13 | 米白底 #F2F2F7 + 白卡 + iOS nav 玻璃态 (`backdrop-filter blur(22px) saturate(180%)`) |
| **C** `dark-camera` | P02 / P15 / P03 (analyzing 沿用) | 全屏 #0B0F1A 实色（不是渐变）+ viewfinder 内模拟纸面 + 黄色检测元素（brackets / scan / detect badge） |
| **D** `celebrate-green` | P09 | 绿渐变 hero `linear-gradient(175deg, #0F7F3E 0%, #1FAE5C 40%, #34C759 100%)` + confetti 粒子 + pulse 脉冲环 |
| **E** `teal-observer` | P11 / P16 / P18 | 青绿主题 + identity card · 深蓝 hero 变体 |

- ✅ 一个 section 数据归到一个 mood · 子元素颜色严格按本 mood 的 token 池
- ❌ 一个卡片里混 hero+overlap 的深蓝按钮 + pure-warm 的白按钮、Mood A 内出现 `--tkn-color-warm-*` 旧 token、Mood B 内出现 `linear-gradient` aurora
- 🤖 每个 section 标 `data-mood="A|B|C|D|E"`（v2.0 命名），grep 校验：
  - Mood A 必须有 hero-stop + blob token
  - Mood B 禁止 hero gradient 全屏
  - Mood C 必须用 `--tkn-color-bg-camera` 而非 `gradient-focus-night`
  - Mood D 必须含 confetti motion
  - 旧 `data-mood="cool|warm|celebrate"` 视为遗留，需迁移

### 铁律 3 · 庆祝有节制
仅 4 个白名单时刻允许"高强度庆祝"：
1. P04 拍题首次入库成功
2. P09 单题复习完成
3. P09 今日全部完成
4. P-HOME / P09 连续打卡 7 / 30 / 100 天里程碑

- ✅ confetti ≤ 1.2 秒、对勾粒子 ≤ 800ms、连击数字弹跳 ≤ 400ms、全部用 `prefers-reduced-motion` 兜底关掉
- ❌ 每次自评点击都放粒子、滚动经过卡片就闪光、加载 spinner 用爱心动画
- 🤖 所有 `@keyframes` 名称必须在白名单 `[confetti, checkmark-pop, streak-bump, hero-aurora, sse-pulse, skeleton-shimmer]` 内；超出 = fail

### 铁律 4 · 字体光学切换 + 中文 PingFang SC
- ✅ ≥20px → SF Pro Display + PingFang SC Semibold；<20px → SF Pro Text + PingFang SC Regular；负字距 -0.374px (17px) / -0.224px (14px) 全场景生效
- ❌ 中文用思源黑体、英文用 Inter、混用粗细超过 4 档（300/400/600/700）
- 🤖 CSS `font-family` 必须命中 `var(--tkn-font-display)` 或 `var(--tkn-font-text)`，硬编码字体名 = fail

### 铁律 5 · 学科即色彩身份
数学 `#C41E3A` 红 / 物理 `#0057B7` 蓝 / 化学 `#1A6B3A` 绿 / 英语 `#9C4F00` 琥珀。**严格 3 种用法**：

- ✅ ① SubjectChip 背景（白字）② 错题卡 4px 左色条 ③ 学科 SVG icon 填色
- ❌ 学科色当 CTA、当 hero 背景、当大面积色块、当文字色（除非在 chip 内）
- 🤖 `--tkn-subject-*` 仅允许出现在 `border-left-color` / `background-color`（width ≤ 80px 即 chip 尺寸） / `fill`（仅 SVG icon）。其他位置 = fail

### 铁律 6 · 页面节奏二分（沉浸 + 信息）
每个页面**最多两段**：上半沉浸 hero（深色或渐变，全宽到边）+ 下半信息流（米白卡片）。

- ✅ P-HOME 顶部极光 hero + 下方暖米白卡片；P02 全屏取景器（一段沉浸到底）；P05 错题列表（一段信息流到底）
- ❌ 一个页面 4 段不同背景色、hero 中间穿插小信息卡又再 hero、信息流中间插入广告 banner
- 🤖 根 `<main>` 直接子节点 ≤ 2 个 section，且第二个 section 是信息流（含至少一个 list/grid）

### 铁律 7 · 移动手势优先
为微信小程序 + H5 移动端设计，hover 状态可有可无，但**所有交互必须触摸可达**。

- ✅ 错题列表下拉刷新 + 上拉加载 + 左滑归档、底部 Sheet 拖拽柄可下拉关闭、Tab Bar 长按显示二级菜单
- ❌ "鼠标悬停才能看到删除按钮"、"hover 才显示工具提示"、点击区 < 44×44px
- 🤖 所有可交互元素 `min-height: 44px && min-width: 44px`（icon-only button 例外但需 padding 拉到 44）；hover 样式必须有对应的 `:active` 或 `aria-pressed` 等价

### 铁律 8 · AI 三件套强制
任何元素入 mockup 前必须满足 testid + token + a11y 三件套：

- ✅ `data-testid="kebab-case-name"` 存在且页面内唯一
- ✅ 颜色/字号/间距/圆角/阴影/动效**全部**通过 `var(--tkn-*)` 引用，CSS 中出现一个 hex 色值 = fail（白名单除外，见 §4.4）
- ✅ 交互元素有 `role` + `aria-label`（如必要）+ keyboard 可达
- 🤖 lint 三件套，**这条是其他 7 条的兜底机制**。fe-accept-mock 直接跑这条作为 P0 阻断

---

## 2. Token 三层架构（v2.0 archive-aligned）

> **⚠️ 与 v1.0 的关键差异**：v1.0 用"L2 暖意层"承载 hero gradients + warm 棕字；v2.0 把所有 archive 真相 token 收编到 L1 (color.json)，**L2 warmth.json 已 deprecated**（仅保留 migration map）。

### 2.1 v2.0 双层结构总览

```
┌────────────────────────────────────────────────────────────────┐
│  Layer 1 · iOS HIG + archive 真相（v2.0 收编完整体系）          │
│  --tkn-color-* (iOS HIG 9 色 + 灰阶 + Hero stops + Blob       │
│                + Em 渐变 + KP 棕字 + Glass 白透 + 学科色)       │
│  --tkn-font-* / --tkn-type-* / --tkn-spacing-* / --tkn-radius-*│
│  --tkn-shadow-* (archive 实测 18 类 · 几乎无阴影特征)           │
│  --tkn-motion-* (Apple HIG ease + 90/150/250/400ms)            │
│  → 决定页面骨架 + 系统语言 + archive 装饰系统                   │
└────────────────────────────────────────────────────────────────┘
       ↑ 唯一主层 · 所有视觉值都在这里
┌────────────────────────────────────────────────────────────────┐
│  Layer 2 · ⛔️ DEPRECATED (warmth.json)                         │
│  原 --tkn-color-warm-* / --tkn-gradient-aurora / ...           │
│  → 仅保留 migration map · 不允许新代码引用                      │
└────────────────────────────────────────────────────────────────┘
       ↑ 已废 · 详见 warmth.json#_meta.migration_map
┌────────────────────────────────────────────────────────────────┐
│  Layer 3 · 庆祝语义层（保留 · celebration.json v2.0）           │
│  --tkn-color-mastery-* (掌握三态色)                            │
│  --tkn-color-celebrate-confetti-* (5 色粒子)                   │
│  --tkn-color-celebrate-celebrate-green-stop-{1,2,3} (P09 hero) │
│  --tkn-gradient-celebrate-hero (P09 175deg 实测)               │
│  --tkn-color-streak-fire (火焰)                                │
│  --tkn-motion-celebrate-* (粒子动效专用时长)                    │
│  → 仅在 4 个白名单庆祝时刻调用                                  │
└────────────────────────────────────────────────────────────────┘
       ↑ 独立命名空间
┌────────────────────────────────────────────────────────────────┐
│  EXCEPTION · 学科色板 + 微信品牌（保留 · 独立）                 │
│  --tkn-subject-{math|physics|chemistry|english}                │
│  --tkn-color-brand-wechat                                      │
│  → 仅 chip / 左色条 / icon / 微信按钮 4 种用法                  │
└────────────────────────────────────────────────────────────────┘
```

### 2.2 Layer 1 · iOS HIG + archive 真相（v2.0 主层）

现有 token JSON：`color.json` (v2.0 重写) / `typography.json` / `spacing.json` / `radius.json` (v2.0 重写) / `shadow.json` (v2.0 重写) / `motion.json`。完整定义详见各 JSON · 也可在 `STYLE-TRUTH.md §2` 一览。

**v2.0 关键修正**（vs v1.0）：

| Token | v1.0 错值 | v2.0 archive 真相 |
|---|---|---|
| `--tkn-color-primary-DEFAULT` | `#0071e3` (macOS) | `#007AFF` (iOS HIG) |
| `--tkn-color-text-primary` | `#1d1d1f` 或 `#2C2A26` | `#1C1C1E` (iOS HIG) |
| `--tkn-color-text-secondary` | `rgba(0,0,0,.80)` 或 `rgba(44,42,38,.72)` | `#636366` (iOS HIG) |
| `--tkn-color-text-tertiary` | `rgba(0,0,0,.48)` | `#8E8E93` (iOS HIG) |
| `--tkn-color-bg-light` | `#f5f5f7` (Apple HIG) | `#F2F2F7` (iOS HIG) |
| `--tkn-color-sep` | (无) | `rgba(60,60,67,.14)` (iOS HIG · 新增) |

### 2.3 Layer 1 关键 token 清单（v2.0 收编 archive 真相）

#### iOS HIG 9 色（color.json#tkn.color.system）

| Token | 值 | 用途 |
|---|---|---|
| `--tkn-color-primary-DEFAULT` | `#007AFF` | 主交互色 / CTA / 焦点环 |
| `--tkn-color-system-red` | `#FF3B30` | 错误 / forgot / wrong / exam |
| `--tkn-color-system-orange` | `#FF9500` | 警告 / partial-mastery / weak-KP |
| `--tkn-color-system-green` | `#34C759` | 成功 / mastered / streak / sparkline |
| `--tkn-color-system-indigo` | `#5856D6` | KP / message-icon / blob-purple base |
| `--tkn-color-system-yellow` | `#FFCC00` | 02_capture 检测 brackets / scan / mode tab |
| `--tkn-color-system-purple` | `#AF52DE` | settings / preferences |
| `--tkn-color-system-teal` | `#30B0C7` | 系统通知 / 16_shared / 18_observer |
| `--tkn-color-system-pink` | `#FF2D55` | 考试事件 / family share / blob-pink |

#### 灰阶（color.json#tkn.color.text + bg）

| Token | 值 | 用途 |
|---|---|---|
| `--tkn-color-text-primary` | `#1C1C1E` | 主文字 |
| `--tkn-color-text-secondary` | `#636366` | 次文字 |
| `--tkn-color-text-tertiary` | `#8E8E93` | 三级 / 禁用 |
| `--tkn-color-bg-light` | `#F2F2F7` | iOS Light bg / Mood B 默认底 |
| `--tkn-color-bg-camera` | `#0B0F1A` | Mood C dark-camera 实色（替代旧 gradient-focus-night） |
| `--tkn-color-card` | `#FFFFFF` | 卡片纯白 |
| `--tkn-color-sep` | `rgba(60,60,67,.14)` | iOS 分割线 |

#### Hero 渐变 stops（color.json#tkn.color.hero-stop · v2.0 archive 真相）

| Token | 值 | 用途 |
|---|---|---|
| `--tkn-color-hero-stop-1` | `#0F1A3D` | reviewhero card 起点 / landing 起点 |
| `--tkn-color-hero-stop-2` | `#1E3A8A` | 01_home hero 起点（推荐） |
| `--tkn-color-hero-stop-3` | `#1F3C8C` | reviewhero 中段 / landing 中段 |
| `--tkn-color-hero-stop-4` | `#3B5BDB` | 01_home hero 中段 |
| `--tkn-color-hero-stop-5` | `#5B8DEF` | 01_home hero 终点（亮） |
| `--tkn-color-hero-stop-6` | `#5F5BDB` | reviewhero 终点 / landing 紫色段 |
| `--tkn-color-hero-stop-7` | `#8B87F6` | landing 终点（最亮紫） |

**Hero 渐变组装示例**（不再用单一 `--tkn-gradient-aurora` token，改为 css 直接组装 stops）：

```css
/* Mood A · 01_home hero 240px */
.hero-home {
  background: linear-gradient(180deg, var(--tkn-color-hero-stop-2) 0%, var(--tkn-color-hero-stop-4) 45%, var(--tkn-color-hero-stop-5) 100%);
}

/* Mood A · 14_landing hero 380px */
.hero-landing {
  background: linear-gradient(170deg, var(--tkn-color-hero-stop-1) 0%, var(--tkn-color-hero-stop-3) 40%, var(--tkn-color-hero-stop-6) 80%, var(--tkn-color-hero-stop-7) 100%);
}

/* Mood A · reviewhero card */
.reviewhero {
  background: linear-gradient(135deg, var(--tkn-color-hero-stop-1) 0%, var(--tkn-color-hero-stop-3) 60%, var(--tkn-color-hero-stop-6) 100%);
}
```

#### Hero 装饰 blob（color.json#tkn.color.blob · 灵魂级别 · 必须 3 层）

| Token | 值 | 用途 |
|---|---|---|
| `--tkn-color-blob-purple` | `rgba(88,86,214,.55)` | hero `::before` 紫右上 · blur 20-28px |
| `--tkn-color-blob-cyan` | `rgba(88,214,255,.55)` | hero `::after` 青左下 · blur 20-24px |
| `--tkn-color-blob-cyan-soft` | `rgba(88,214,255,.45)` | 01_home hero ::after |
| `--tkn-color-blob-pink` | `rgba(255,45,85,.35)` | hero `.blob` 粉中央偏左 · blur 18px |
| `--tkn-color-blob-coral` | `rgba(255,107,107,.45)` | reviewhero / 14 landing top-right · blur 28px |
| `--tkn-color-blob-mint` | `rgba(79,209,217,.35)` | reviewhero ::after / 04 hero card 内底 |
| `--tkn-color-blob-gold` | `rgba(255,209,102,.40)` | 14 landing 中央 · blur 22px |

#### Em 强调字渐变（color.json#tkn.color.em）

| Token | 值 | 用途 |
|---|---|---|
| `--tkn-color-em-from-gold` | `#FFD166` | 渐变起点 gold |
| `--tkn-color-em-to-coral` | `#FF6B6B` | landing hero title em 终点 |
| `--tkn-color-em-to-amber` | `#FFB454` | 01_home hero name em 终点（柔和） |

```css
/* Em 渐变字示例 */
em {
  background: linear-gradient(90deg, var(--tkn-color-em-from-gold) 0%, var(--tkn-color-em-to-coral) 100%);
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
}
```

#### KP 鼓励卡（color.json#tkn.color.kp · 替代旧 encouragement-soft 单色）

| Token | 值 | 用途 |
|---|---|---|
| `--tkn-color-kp-bg-from` | `#FFF4E6` | KP 卡渐变起点（暖米） |
| `--tkn-color-kp-bg-to` | `#FFE0C2` | KP 卡渐变终点（暖橙） |
| `--tkn-color-kp-border` | `rgba(255,149,0,.25)` | KP 卡 border |
| `--tkn-color-kp-text-title` | `#8B4513` | KP 卡标题棕色 |
| `--tkn-color-kp-text-body` | `#A0522D` | KP 卡正文棕色 |
| `--tkn-color-kp-text-em` | `#6B2C0F` | KP 卡强调棕色 |

#### 玻璃态白透（color.json#tkn.color.glass · 14 档透明度）

| Token | 值 | 用途 |
|---|---|---|
| `--tkn-color-glass-white-08` | `rgba(255,255,255,.08)` | hero 装饰极淡 |
| `--tkn-color-glass-white-10` | `rgba(255,255,255,.10)` | 02 subj 默认 / rh-sub-chip |
| `--tkn-color-glass-white-12` | `rgba(255,255,255,.12)` | tip / eyebrow |
| `--tkn-color-glass-white-14` | `rgba(255,255,255,.14)` | mchip metric / rh-btn2 |
| `--tkn-color-glass-white-16` | `rgba(255,255,255,.16)` | streak flame pill / signin pill |
| `--tkn-color-glass-white-18` | `rgba(255,255,255,.18)` | shutter outer ring layer 1 |
| `--tkn-color-glass-white-22` | `rgba(255,255,255,.22)` | mchip border |
| `--tkn-color-glass-white-78` | `rgba(255,255,255,.78)` | tabbar 04 result / nav blur |
| `--tkn-color-glass-white-86` | `rgba(242,242,247,.86)` | tabbar 01 home (bg-light tint) |
| `--tkn-color-glass-white-92` | `rgba(255,255,255,.92)` | cta-dock fade / 02 subj.on |
| `--tkn-color-glass-black-{35,40,45,55}` | `rgba(0,0,0,.35-.55)` | 02 camera 黑半透按钮 |

#### 阴影体系（shadow.json · v2.0 archive 实测 18 类）

阴影哲学：**几乎无阴影是关键特征**。卡片基线极轻 (0 1px 2px) · hero card 上限 (0 10px 30px) · 不允许 `0 12px 32%` 之类中等深阴影。

| Token | 值 | 用途 |
|---|---|---|
| `--tkn-shadow-card` | `0 1px 2px rgba(0,0,0,.04)` | 卡片基线 |
| `--tkn-shadow-card-deep` | `0 1px 2px rgba(0,0,0,.04), 0 8px 22px rgba(40,50,90,.06)` | 04 hero / 14 sample 双层 |
| `--tkn-shadow-hero-card` | `0 10px 30px rgba(31,60,140,.25)` | **本系统阴影上限** · reviewhero |
| `--tkn-shadow-cta-blue` | `0 10px 24px rgba(0,122,255,.28)` | primary CTA 蓝色发光 |
| `--tkn-shadow-cta-deep` | `0 12px 30px -6px rgba(31,60,140,.4), inset 0 1px 0 rgba(255,255,255,.25)` | landing 深蓝主 CTA |
| `--tkn-shadow-shutter` | `0 0 0 4px rgba(255,255,255,.18), 0 0 0 8px rgba(255,255,255,.10), 0 14px 28px rgba(0,0,0,.35)` | 02_capture 三层环 |
| `--tkn-shadow-focus` | `0 0 0 2px #007AFF` | 焦点环（修正自 #0071e3） |
| (其他完整列表) | | 详见 shadow.json |

### 2.4 Layer 3 · 庆祝语义层（新增 12 个 token · `celebration.json`）

#### 掌握三态语义色（3 个，**学习状态专用**）

| Token | 值 | 用途 | 与 system 色的差异 |
|---|---|---|---|
| `--tkn-color-mastery-forgot` | `#C0392B` | "未掌握"按钮、未掌握节点圆点 | 与 system-danger 同值，但语义是"忘了"不是"错了" |
| `--tkn-color-mastery-partial` | `#E8741C` | "部分掌握"按钮、半透明节点 | 复用 encouragement，传达"还差一点" |
| `--tkn-color-mastery-mastered` | `#34A853` | "已掌握"按钮、完成节点圆点、徽章 | 比 system-success #1A7D34 更亮更友好 |

#### 庆祝粒子色（5 个，仅 confetti SVG 内）

| Token | 值 | 用途 |
|---|---|---|
| `--tkn-color-celebrate-confetti-1` | `#FFD60A` | 金色粒子 |
| `--tkn-color-celebrate-confetti-2` | `#FF375F` | 粉红粒子 |
| `--tkn-color-celebrate-confetti-3` | `#34A853` | 薄荷粒子 |
| `--tkn-color-celebrate-confetti-4` | `#5E5CE6` | 紫色粒子 |
| `--tkn-color-celebrate-confetti-5` | `#FF9500` | 橙色粒子 |

#### 连击专用色（1 个）

| Token | 值 | 用途 |
|---|---|---|
| `--tkn-color-streak-fire` | `#FF6B35` | 连续打卡 🔥 火焰 SVG icon 填色（不用 emoji） |

#### 庆祝动效时长（3 个）

| Token | 值 | 用途 |
|---|---|---|
| `--tkn-motion-celebrate-confetti` | `1200ms` | P09 全部完成 confetti 总持续 |
| `--tkn-motion-celebrate-checkmark` | `800ms` | P08 → P09 单题完成对勾粒子 |
| `--tkn-motion-celebrate-streak-bump` | `400ms` | 连击数字弹跳缓动 |

### 2.5 学科色板（保留 · EXCEPTION 1）

| Token | 值 | 用途约束 |
|---|---|---|
| `--tkn-subject-math` | `#C41E3A` | 仅 SubjectChip / 左色条 / icon |
| `--tkn-subject-physics` | `#0057B7` | 同上 |
| `--tkn-subject-chemistry` | `#1A6B3A` | 同上 |
| `--tkn-subject-english` | `#9C4F00` | 同上 |

### 2.6 系统状态 vs 学习状态：**双轨制**

> 关键差异化决策。

| 状态语义 | 系统轨（Apple 克制） | 学习轨（学生暖意） |
|---|---|---|
| **用途** | 系统级提示：网络错误 toast、登录过期 banner、表单验证、AI 服务降级黄条 | 学习场景：复习自评三按钮、掌握度卡片、节点状态、庆祝时刻 |
| **Token 前缀** | `--tkn-color-system-{success/warning/danger/info}` | `--tkn-color-mastery-*` / `--tkn-color-encouragement-*` |
| **视觉特征** | 较冷、对比度高、克制 | 较暖、饱和度略高、友好 |
| **出现频率** | 偶发 | 高频 |

**追加铁律**：学习状态色禁止用于系统提示（避免学生把"已掌握绿"误读为"网络成功绿"），反之亦然。

### 2.7 文件组织

```
design/system/tokens/
├── README.md                 (更新 · 标注三层架构)
├── color.json                (L1 · 已有 · 重命名 success/warning/danger/info → system-*)
├── typography.json           (L1 · 已有 · 不动)
├── spacing.json              (L1 · 已有 · 不动)
├── radius.json               (L1 · 已有 · 不动)
├── shadow.json               (L1 · 已有 · 不动)
├── motion.json               (L1 · 已有 · 不动)
├── warmth.json               (L2 · 新建 · 18 token)
└── celebration.json          (L3 · 新建 · 12 token)
```

Style Dictionary 配置（`frontend/packages/ui-kit/sd.config.js`）增加两条 source 路径，输出仍是单一 `tokens.css` / `tokens.wxss` / `tokens.ts`，**对消费端透明**。

---

## 3. 组件三层架构

### 3.1 三层关系

```
tokens (--tkn-*)
    ↓
components.md (L0 · 通用原语 · 20 个)
    ↓
molecules.md  (L1 · 产品分子组件 · 11 个)
    ↓
pages/*.spec.md (L2 · 页面规格卡 · 19 张)
```

### 3.2 L0 通用原语（保留 20 个，仅做维护）

现有 Button / Input / Card / Toast / Modal / Sheet / TabBar / NavBar / Skeleton / Empty / Badge / Avatar / Divider / Banner / Tag / Picker / DatePicker / Stepper / Switch / Progress 全部保留。

**维护清单**：
1. 每个组件的 `token usage` 段追加 mood 适配说明
2. Button 增加 `mood-celebrate` variant（用于 P09 "继续下一题"按钮 mint 绿底）
3. Toast / Banner 仅允许使用 `system-*` 色族（与铁律 6 双轨制一致）

### 3.3 L1 产品分子组件（新增 11 个 · `molecules.md`）

| # | Molecule | 用在哪些页 | 由哪些原语组合 | 关键 props |
|---|---|---|---|---|
| 1 | **GreetingHero** | P-HOME, P-WELCOMEBACK | Aurora 背景 + Avatar + 文字 + StreakBar | `studentName`, `streakDays`, `mood="celebrate"` |
| 2 | **StreakBar** | P-HOME, P09 | 火焰 SVG + 数字 + 进度网格 | `days`, `milestone` |
| 3 | **TodayReviewCard** | P-HOME, P07 | Card + CircleProgress + SubjectChip[] + Button | `total`, `done`, `subjectDist[]`, `estMin` |
| 4 | **WeekStrip** | P-HOME | 7 个日期 cell + T-level 色点 + 考试红点 | `days[]`, `today` |
| 5 | **SubjectChip** | P-HOME, P05, P06, P08 | Tag + 学科色 + 计数 | `subject`, `count`, `selected` |
| 6 | **MasteryStatusCard** | P05, P06 | Card + 3 状态色条 + 计数 | `forgot`, `partial`, `mastered` |
| 7 | **QuestionListCard** | P05, P-OBSERVER | Card + 学科左色条 + 缩略图 + KP chips + 6 段进度 | `question`, `nodeStage`, `nextDueAt` |
| 8 | **MemoryCurve** | P04, P06, P09 | SVG 曲线 + T1–T6 节点 + 三态色 | `nodes[]`, `currentT` |
| 9 | **CelebrateHero** | P09 | 渐变背景 + 大对勾 + ConfettiBurst | `variant="single\|all-done\|streak"` |
| 10 | **ConfettiBurst** | P09 | SVG 粒子动画（5 色循环） | `duration`, `particleCount` |
| 11 | **AnalyzingPipeline** | P03 | 4 步流水线 + JSON 流式区 + Cancel | `steps[]`, `partialJson`, `model` |

每个 molecule 在 `molecules.md` 按 `components.md` 的 schema 写一节（variants / states / props / a11y / h5 / miniprogram / token usage）。

---

## 4. 页面规格卡 (spec.md) Schema

### 4.1 文件命名与位置

```
design/system/pages/
├── _template.spec.md        (空白模板)
├── P00.spec.md
├── P-LANDING.spec.md
├── P-GUEST-CAPTURE.spec.md
├── P-SHARED.spec.md
├── P-WELCOMEBACK.spec.md
├── P-OBSERVER.spec.md
├── P-HOME.spec.md
├── P02-capture.spec.md
├── P03-analyzing.spec.md
├── P04-result.spec.md
├── P05-wrongbook-list.spec.md
├── P06-wrongbook-detail.spec.md
├── P07-review-today.spec.md
├── P08-review-exec.spec.md
├── P09-review-done.spec.md
├── P10-calendar-month.spec.md
├── P11-event-detail.spec.md
├── P12-notifications.spec.md
└── P13-settings.spec.md
```

每个页面**一个 .md 文件**。文件名 = 页面 ID。

### 4.2 单页 spec 14 段标准结构

````markdown
---
page_id: P-HOME
name: 今日聚合首页
route_h5: /
route_miniprogram: pages/home/today
deeplink: wb://home
auth_state: authenticated
persona: [P1-K12]
scenarios: [SC-01, SC-03, SC-05]
mockup_canonical: design/mockups/wrongbook/01_home.html
mockup_version: v4
last_reviewed: 2026-05-02
status: spec-locked          # spec-draft | spec-locked | implemented | shipped
sprint: S2
---

# P-HOME · 今日聚合首页

## §1 页面目的（why · 1 句话）
学生打开 App 第一眼知道："今天有多少题要复习、现在点一下就开始"。

## §2 ASCII 布局图
（人类秒懂结构 · 详见每页 spec.md）

## §3 Block 清单
| Block ID | 名称 | Section | Mood | Component | testid root | Tokens 用到 |
（每行一个独立可实现单元 · fe-preflight 按此切 mockup）

## §4 数据契约
（TypeScript interface · 单一聚合 API 响应）

## §5 API 触点
| Method | Path | 用途 | P95 预算 | 失败降级 |

## §6 状态机
| State | 触发 | UI 表现 |

## §7 跳转图
（入口 → 出口 ASCII 流程图）

## §8 AC 覆盖表（与 business-analysis.yml 双向锚定）
| AC ID | 验收点 | 涉及 Block | testid 验证点 |

## §9 异常路径
| 触发 | UI | 系统行为 |

## §10 埋点事件
| 事件名 | 触发 Block | 必带属性 |

## §11 性能预算
TTI / LCP / CLS / API P95

## §12 A11y
landmarks / focus order / 屏幕阅读器优先级

## §13 Mockup 锚定
权威路径 + 历史变体归档 + 截图

## §14 Tokens 清单（grep 校验用）
（完整列出本页用到的 --tkn-* · 多一个少一个 = lint fail）
````

### 4.3 testid 命名规范（强制）

```
{block-id-kebab}[-{role}][-{index}]
```

例：
- `greeting-hero` (block root)
- `greeting-hero-name` (子元素)
- `today-review-card-circle-progress`
- `week-strip-day-1-tlevel-T2`

**规则**：
1. **kebab-case**，不用驼峰、下划线、中文
2. block root testid = 该 block 在 §3 表中的 testid root
3. 子元素拼 `-role`
4. 列表项拼 `-index`（从 1 起）
5. 全局唯一（同一页面内）
6. 与 AC 表 §8 双向锚定（每条 AC 至少绑定 1 个 testid）

### 4.4 Token 引用强制规则

mockup CSS 中**任何一处**：颜色（hex/rgb/hsl）/ 字号（px/rem/em）/ 间距 / 圆角 / 阴影 / 动效 —— **必须**通过 `var(--tkn-*)` 引用。

**白名单**（lint 跳过）：
- `0` 数值（margin: 0、padding: 0）
- `100%` / `50%` / `auto` / `inherit` 等 CSS 关键字
- `transparent` / `currentColor`
- `keyframes` 中粒子初始 transform 的随机值（confetti 专用）
- 微信小程序 `rpx` 单位（特定平台单位）

### 4.5 Mockup 与 Spec 双向锚定

- spec frontmatter 的 `mockup_canonical` 字段指向权威 HTML
- mockup HTML `<head>` 中加 `<meta name="design-spec" content="P-HOME.spec.md@v4">` 反向锚定
- spec 改大版本（schema 变化）时 mockup 需重新生成；mockup 改视觉时 spec 的 `last_reviewed` 必须更新

---

## 5. 19 页面设计意图与 Sprint 计划

### 5.0 19 张页面 Mockup 主控矩阵（v2.0 archive-aligned · Mood 5 类）

| # | Page ID | 页面名 | Mood (v2.0) | Hero | 关键 Molecules | 情感锚点 | Sprint |
|---|---|---|---|---|---|---|---|
| 1 | P02 | 拍题相机 | **C** dark-camera 全屏 #0B0F1A 实色 | viewfinder 内模拟纸面 + 黄色检测 brackets/scan | 取景器 + SubjectChip 玻璃态 | 专注 → 期待 | **S1** |
| 2 | P03 | AI 分析中 | **C** dark-camera 沿用 #0B0F1A | 同 P02 顺接 | AnalyzingPipeline | 略焦虑 → 被看见 | **S1** |
| 3 | P04 | AI 分析结果 | **B** pure-warm 米白 #F2F2F7 | 题干缩略图卡（无 hero gradient） | QuestionCard + 答案对错卡 + 3-step + MemoryCurve | 惊喜 → 满足 | **S1** |
| 4 | P08 | 复习执行 | **B** pure-warm 米白 | 无 hero | 题干 Hero 卡 + Mastery 三按钮 | 挑战 → 自评 | **S1** |
| 5 | P09 | 复习完成 | **D** celebrate-green | `linear-gradient(175deg, #0F7F3E, #1FAE5C, #34C759)` + Confetti | CelebrateHero + MemoryCurve | 成就 → 持续动力 | **S1** |
| 6 | P-HOME | 今日聚合首页 | **A** hero+overlap 240px | 深蓝 `linear-gradient(180deg, hero-stop-2/4/5)` + 3 blob (purple/cyan/pink) | GreetingHero + TodayReviewCard + WeekStrip | 期待 → 控制感 | **S2** |
| 7 | P05 | 错题本列表 | **B** pure-warm 米白 | 无 hero · 大标题 | SubjectChip + MasteryStatusCard + QuestionListCard | 掌控感 | **S2** |
| 8 | P06 | 错题详情 | **B** pure-warm 档案页 | 原图缩略图卡 | MemoryCurve + RadarChart | 沉淀 → 进步可见 | **S2** |
| 9 | P07 | 今日待复习 | **A** hero+overlap | 深蓝渐变变体（today-blue 待 archive 详查）+ 气泡粒子 | TodayReviewCard + 时段分组卡 | 整装 → 出发 | **S2** |
| 10 | P-LANDING | 访客落地 | **A** hero+overlap 380px | 深蓝 `linear-gradient(170deg, hero-stop-1/3/6/7)` + 3 blob (coral/cyan/gold) blur 22-28px | HeroProductShot + 3 样例 + 双 CTA | 好奇 → 信任 | **S3** |
| 11 | P-GUEST-CAPTURE | 游客拍题 | **C** dark-camera + 配额 banner | 同 P02 | P02 + GuestQuotaBanner | 试探 → 体验 | **S3** |
| 12 | P-SHARED | 分享只读 | **B** pure-warm 脱敏卡 | 无 hero · 顶部分享者 | QuestionCard(脱敏) + UpgradeCTA | 好奇 → 升级欲 | **S3** |
| 13 | P00 | 登录 | **A** hero+overlap (archive 缺 · 按 STYLE-TRUTH §6 建议) | 深蓝 hero + 微信品牌按钮 + 玻璃态登录卡 | LogoMark + 微信一键 + 协议 | 干净 → 决断 | **S3** |
| 14 | P10 | 日历月视图 | **B** pure-warm 网格 | 无 hero | MonthGrid + LegendBar | 全局 → 计划 | **S4** |
| 15 | P11 | 事件详情双形态 | **E** teal-observer / **B** pure-warm | 形态条带 | EventHeroCard + 关联项列表 | 上下文 → 行动 | **S4** |
| 16 | P12 | 通知中心 | **B** pure-warm 列表 | 无 hero | TimelineGroup + NotificationCard | 整理 | **S4** |
| 17 | P13 | 设置 / 我的 | **B** pure-warm 列表 + 头像区 | 暖色头像区 | AvatarBlock + SettingRow + DangerZone | 控制 | **S4** |
| 18 | P-WELCOMEBACK | 回流唤起 | **A** hero+overlap 460px · 数字脉冲 | 深蓝 hero `linear-gradient(135deg, #4F6FFF, #7B5FFF, #B85FFF)` + memory curve preview SVG | WelcomeBackHero + 数字脉冲 + CTA | 被惦记 → 回归 | **S5** |
| 19 | P-OBSERVER | 观察者会话 | **E** teal-observer 只读 | 无 hero · 顶部观察者 banner | ReadOnlyBanner + 错题列表(只读) | 信任 → 安心 | **S5** |

### 5.1 Sprint 1 · 学习闭环 5 张

学生完成"一道错题"端到端的 5 张关键页面。情感曲线：专注 → 被看见 → 满足 → 挑战 → 成就。

#### P02 · 拍题相机
- 全屏 `var(--tkn-color-bg-camera)` #0B0F1A 实色，viewfinder 70% 高度内含模拟纸面 + 黄色 4 角检测 brackets + 黄色扫描线
- 顶部 SubjectChip 横滚（4 学科色），当前学科 1px 内描边
- 底部 78px 圆形快门，左右图库 / 闪光灯小图标
- **整页禁止暖色 / 庆祝色**

#### P03 · AI 分析中
- **沿用 P02 暗底**（视觉无缝衔接，学生体感"还在同一个动作里"）
- 顶部题目缩略图 + 模型 Badge
- 中部 4 步流水线（图像预处理 / OCR / 错因诊断 / 生成解法），每步 wait → now → done 三态
- 等宽字体流式 JSON 滚动区
- 取消按钮灰色 pill，最下方

#### P04 · AI 分析结果
- **从 P03 暗底切到 Mood B pure-warm 米白 #F2F2F7**——情感曲线最关键转折（无 hero gradient · 直接 page-level 米白底）
- Hero 题干卡白底 + 公式 chip 高亮
- 错解 / 正解双列：左卡 mastery-forgot 红 1px 内描边，右卡 mastery-mastered 绿 1px 内描边（**不用大色块**）
- 错因区左侧 4px mastery-forgot 红条 + warm-text-primary 文字
- 3 步解法 Stepper + 6 节点 MemoryCurve 灰色未来态预览
- 保存 CTA 蓝色 pill 全宽

#### P08 · 复习执行
- 白底（`bg-warm-elevated`）**无任何渐变 hero** —— 做题时视觉装饰是噪音
- 顶部 4px 线状蓝色进度条 + "第 2/8 题" + ×
- 题干卡复用 P04 QuestionCard 样式
- 揭示按钮**点击前**只显示题干，**点击后**绿色高亮 + 3 步解法滑入（800ms checkmark）
- 6 节点 MemoryCurve 揭示后展开
- 底部 3 等宽按钮：未掌握(`mastery-forgot`) / 部分(`mastery-partial`) / 已掌握(`mastery-mastered`)
- ⚠️ **铁律 1 例外**：自评三按钮使用学习状态色而非 CTA 蓝，因为语义是"自评"不是"前进"。需 `data-iron-rule-1-exception="self-grading"` 标注

#### P09 · 复习完成
- 全屏 `gradient-celebrate-green` 顶部 hero（160deg 深绿→薄荷）
- 中央大白色对勾粒子 800ms（celebrate-checkmark）
- ConfettiBurst 1200ms 5 色粒子（**仅"今日全部完成"才有，单题完成不放**）
- MemoryCurve 完整 6 节点，刚做的节点 mastery-mastered 绿色脉冲，下一节点 encouragement 橙色脉冲
- AI Advance Banner 暖橙底
- 下次复习 + 加日历按钮组（蓝 pill）
- 3 statistics 卡（已掌握 / 部分 / 遗忘）
- KP 掌握度变化条形图（学科色横向）

### 5.2 Sprint 2 · 主页与列表 4 张

学生每日开启的高频心智入口。情感曲线：期待 → 掌控 → 沉淀 → 出发。

#### P-HOME · 今日聚合首页
- 顶部 hero **深蓝渐变** `linear-gradient(180deg, var(--tkn-color-hero-stop-2) 0%, var(--tkn-color-hero-stop-4) 45%, var(--tkn-color-hero-stop-5) 100%)` + 3 层 radial blob (purple/cyan/pink) blur 18-20px
- TodayReviewCard 焦点：圆环进度大占 40%，学科 chip 横排，"全部开始"蓝 pill
- 切到暖米白后 6 个 block 平铺（WeeklySparkline / WeekStrip / MessagesList / WeakKPHint / QuickEntries）
- 每个 block 独立 skeleton，互不阻塞

#### P05 · 错题本列表
- 无 hero。顶部大标题"错题本"（display-hero 56px）+ 搜索框（`warm-sunken` 凹陷底）
- 学科 SubjectChip 横滚带计数
- 3 张 MasteryStatusCard 横排（未掌握 / 部分 / 已掌握），点击切筛选
- 错题卡列表，每卡：4px 学科色条 + 缩略图 + 题干 2 行 + KP chips + 6 段进度 dot + 下次到期
- FAB 拍题悬浮按钮右下蓝色

#### P06 · 错题详情
- 无 hero。顶部原图缩略图大卡（170px 高，可放大）
- Segment Tab：分析 / 复习记录 / 变式
- 分析 tab 复用 P04 QuestionCard
- 复习记录 tab：MemoryCurve + 时间线
- 变式 tab：MVP 显示"敬请期待"
- 五维 RadarChart（运算 / 概念 / 方法 / 速度 / 准确）
- 底部"归档(灰)" + "立即复习(蓝)"双按钮

#### P07 · 今日待复习
- 顶部 `gradient-today-blue` hero（160deg #1F3A93 → #4A6FE3）+ 气泡粒子（比 P-HOME 极光更"沉稳"）
- Hero 内容：今日总数 + 已完成 + 估计时间 + 进度条 + 掌握度 %
- 切到暖底后**时段分组**：「现在·上午」「下午」「晚上」每段一组卡
- 每卡：HH:MM + T-level pill + 学科色条 + 题干 2 行 + 倒计时（now=red / soon=orange / wait=gray）
- 底部"全部开始"蓝 pill 全宽 sticky

### 5.3 Sprint 3 · 匿名态 + 登录 4 张

冷启动 + 增长漏斗，三条 KPI（访客→游客 ≥35% / 游客→注册 ≥25% / 分享→注册 ≥15%）的视觉承载。

#### P-LANDING · 访客落地页
- **第一段 hero**：深蓝 `linear-gradient(170deg, hero-stop-1/3/6/7)` 380px + 3 blob (coral/cyan/gold) blur 22-28px，display-hero 32px 大字"AI 帮你拍下错题，不再做无用功"（em 用 gold→coral 渐变），下方真实拍题 Lottie / GIF loop
- 双 CTA："试一试（白 pill 突出）" + "登录（蓝 pill 主操作）"
- **第二段切到 warm 米白**：3 张样例卡横滚（数学应用题 / 物理力学 / 英语完形），点击展开看完整 AI 分析
- 底部价值数字（"已分析 100w+ 错题 / 7 日留存 47%"）+ 二次 CTA

#### P-GUEST-CAPTURE · 游客拍题
- 视觉 95% 复用 P02
- 顶部多一条 `encouragement-soft` 半透明 banner："今天还可试用 1 次 · [注册后不限次 →]"
- 完成拍题进 P03 → P04 时，P04 顶部多一条蓝色 banner："这次分析将保留 24h，[立即注册保存到错题本 →]"

#### P-SHARED · 分享只读
- 顶部分享者头像 + 名字 + "分享了一道错题给你"
- QuestionCard 脱敏版：题干清晰，**学生答案 / 错因 / 个人记录全部模糊化**（带"注册查看完整内容"覆盖层）
- 底部固定 CTA："注册查看 + 拥有自己的错题本"蓝 pill 大按钮

#### P00 · 登录
- 全屏 `var(--tkn-color-bg-camera)` #0B0F1A 实色暗底
- 顶部 1/3 区域中心 LogoMark（白色 SVG）+ 1 句 slogan（display-hero）
- 中部"微信一键登录"按钮使用 `--tkn-color-brand-wechat` (#07C160) 微信绿（铁律 1 唯一例外，需 `data-iron-rule-1-exception="wechat-brand"` 标注）+ "其他方式"灰色链接
- 底部协议勾选 + micro 文字

### 5.4 Sprint 4 · 辅助页 4 张

#### P10 · 日历月视图
- 月份导航 + Mon-Sun 表头 + 7 列日期网格
- 每格右上角 T-level 色点（4 学科色 + 考试红 + 家庭橙）
- 今日格子蓝色边框
- 点格子进 P11

#### P11 · 事件详情双形态
- 顶部一条**形态色条带**：学习形态=学科色 / 家庭=橙 / 考试=红
- 卡片化展示事件信息 + 关联项列表
- 底部按钮根据形态切换（学习→"立即复习"蓝 / 家庭→"编辑"灰 / 考试→"加日历"蓝）

#### P12 · 通知中心
- 时间分组（今天 / 昨天 / 本周 / 更早）
- 每条 NotificationCard：左侧类型 icon（学科色或系统色）+ 标题 + 时间 + 已读/未读 dot
- 滑动归档手势

#### P13 · 设置 / 我的
- 顶部头像 + 学生名 + 编辑链接（暖色头像区，非渐变 hero）
- 设置分组：账户 / 复习 / 推送 / 隐私 / 关于
- 危险区单独 DangerZone block 红色边框（注销 / 清除数据）

### 5.5 Sprint 5 · P1 复合功能 2 张

#### P-WELCOMEBACK · 回流唤起
- 复用 P-HOME 的深蓝 hero 渐变（`linear-gradient(180deg, hero-stop-2/4/5)`）
- 中央数字脉冲："还剩 N 个待复习"（数字使用 display-hero 字号，N 数字应用 streak-bump 弹跳）
- 双 CTA："一键回登（蓝 pill）" + "看看新功能（灰链接）"

#### P-OBSERVER · 观察者会话
- 顶部 `system-info` 黄底 banner："你正在以观察者身份查看 · 仅可读"
- 错题列表 = P05 但所有按钮置灰 + 增加只读水印
- 详情页 = P06 但底部按钮替换为"我已查看"

---

## 6. AI 实施链路

### 6.1 Spec → 实现的自动化链条

```
[人写 spec]
  design/system/pages/{ID}.spec.md (本设计的产出物)
       ↓
[AI 写 mockup] · fe-preflight skill
  读 spec + tokens + business-analysis.yml
  生成 design/mockups/wrongbook/{n}.html
       ↓
[AI 解析 mockup] · fe-preflight skill
  把 mockup 切成 build-spec.json
  （color/spacing/component/testid 抽取 + token 映射）
  输出 design/tasks/preflight/{ID}-build-spec.json
       ↓
[AI 写代码] · fe-builder skill
  读 build-spec.json + tokens.css
  逐 block 写 React 组件 + CSS Module
       ↓
[AI 验收] · fe-accept-mock skill (B 轨)
  读 spec.§8 AC 表 + spec.§14 token 清单
  跑 Playwright + grep + a11y 校验
       ↓
[人最终签字] / 不通过则回 fe-builder
```

**关键性质**：
1. spec.md 是**唯一的人工设计输入**，其余三段全部自动化
2. testid + token + AC 三件套是 AI 自动化的**契约面**，缺一不可
3. fe-accept-mock 不通过**绝不停下**（前端铁律），必须 1:1 对齐才放过

### 6.2 Sprint 内单页生命周期

```
Day 1: 设计师锁定 spec.md（本文档已锁的页面跳过此步）
Day 1: fe-preflight 生成 mockup HTML + token-mapping-review.md
       人工审 token-mapping（确认近似项无歧义）
Day 2: fe-testplan 生成 test-plan.json（绑定 AC × testid）
Day 2-3: fe-builder 按 block 逐层实现 React 代码
Day 4: fe-accept-mock B 轨自动跑（必过）
Day 4: fe-accept-diff C 轨像素级对比（参考）
Day 5: fe-accept-e2e A 轨真实后端联调（每 sprint 末跑一次）
```

---

## 7. 治理与变更流程

### 7.1 谁能改什么

| 变更类型 | 需要谁批 | 影响范围 | 走哪个流程 |
|---|---|---|---|
| Token 值修改（如改色号） | 设计 lead + 工程 lead | 所有 mockup + 所有代码 | 大版本 v2.0，全量重做 mockup + grep 替换代码 |
| 新增 Layer 2/3 token | 设计 lead | 仅新引用页 | 小版本 v1.x，新页 spec.md 引用即可 |
| Molecule 新增 | 设计 lead | 仅新引用页 | 在 molecules.md 加章节 + spec.md 引用 |
| 单页 spec.md 修改 | 设计师 + PM | 仅本页 | spec.md `last_reviewed` 更新 + mockup 重生 |
| 铁律修改 | 设计 lead + 工程 lead + PM | 全部 | **重新走设计评审会** |

### 7.2 审计与回归

- 每个 sprint 末跑一次**全局 grep 审计**：
  - 所有 mockup CSS 中是否还有硬编码 hex（白名单除外）
  - 所有 testid 是否在 spec.§8 AC 表中有引用
  - 所有 spec.md 的 mockup_canonical 路径是否有效
- 任意一项失败 = sprint 收尾 fail，回去补齐

### 7.3 与业务文档的关系

本文档**不重复**业务需求（`design/业务与技术解决方案_AI错题本_基于日历系统.md`）。两者职责切分：

| 文档 | 内容 | 谁维护 |
|---|---|---|
| 业务文档 §1-§4 | 业务流 / 数据模型 / API 契约 | 后端 + 业务 |
| 业务文档 §2A | 页面路由 / 用户故事 / Persona | 业务 + UX |
| **本设计文档** | 视觉系统 / 组件库 / 页面规格卡格式 | 设计 + 前端 |
| business-analysis.yml | AC 列表 / testid 验收点 | QA + 前端 |
| 19 张 spec.md | 单页详细规格（数据 / 状态 / 异常 / 埋点） | 设计 + 前端 + QA |

---

## 附录 A · 完整 Token 清单（v2.0 · archive-aligned）

> **权威清单见 `STYLE-TRUTH.md §2`** + 各 token JSON 文件。本附录列关键 v2.0 变更点。

### v2.0 修正：Layer 1 实际值（替代 v1.0 错值）

```
变更项                           v1.0 错值                       v2.0 archive 真相
--tkn-color-primary-DEFAULT      #0071e3 (macOS)                  #007AFF (iOS HIG)
--tkn-color-text-primary         #1d1d1f                          #1C1C1E (iOS HIG)
--tkn-color-text-secondary       rgba(0,0,0,.80)                  #636366 (iOS HIG)
--tkn-color-text-tertiary        rgba(0,0,0,.48)                  #8E8E93 (iOS HIG)
--tkn-color-bg-light             #f5f5f7                          #F2F2F7 (iOS HIG)
--tkn-color-sep                  (无)                              rgba(60,60,67,.14) (iOS HIG · 新增)
--tkn-shadow-focus               0 0 0 2px #0071e3                0 0 0 2px #007AFF
```

### Layer 1 新增（archive 收编 · color.json v2.0）

```
# iOS HIG 9 色系统
--tkn-color-system-red               #FF3B30
--tkn-color-system-orange            #FF9500
--tkn-color-system-green             #34C759
--tkn-color-system-indigo            #5856D6
--tkn-color-system-yellow            #FFCC00     (02_capture 检测用)
--tkn-color-system-purple            #AF52DE
--tkn-color-system-teal              #30B0C7
--tkn-color-system-pink              #FF2D55
--tkn-color-system-danger-DEFAULT    #C0392B     (重命名自 danger)

# Hero 渐变 stops（深蓝 hero 7 档）
--tkn-color-hero-stop-{1..7}         #0F1A3D / #1E3A8A / #1F3C8C / #3B5BDB / #5B8DEF / #5F5BDB / #8B87F6

# Hero 装饰 blob（7 色 · 灵魂级别 · 必须 3 层）
--tkn-color-blob-purple              rgba(88,86,214,.55)
--tkn-color-blob-cyan                rgba(88,214,255,.55)
--tkn-color-blob-cyan-soft           rgba(88,214,255,.45)
--tkn-color-blob-pink                rgba(255,45,85,.35)
--tkn-color-blob-coral               rgba(255,107,107,.45)
--tkn-color-blob-mint                rgba(79,209,217,.35)
--tkn-color-blob-gold                rgba(255,209,102,.40)

# Em 强调字渐变（gold→coral / amber）
--tkn-color-em-from-gold             #FFD166
--tkn-color-em-to-coral              #FF6B6B
--tkn-color-em-to-amber              #FFB454

# KP 鼓励卡（暖米橙 + 棕字 · 替代旧 encouragement-soft 单色）
--tkn-color-kp-bg-from               #FFF4E6
--tkn-color-kp-bg-to                 #FFE0C2
--tkn-color-kp-border                rgba(255,149,0,.25)
--tkn-color-kp-text-title            #8B4513
--tkn-color-kp-text-body             #A0522D
--tkn-color-kp-text-em               #6B2C0F

# 答案对错卡（04_result）
--tkn-color-ans-{wrong,right}-from   #FFE8E6 / #E4F7EA
--tkn-color-ans-{wrong,right}-to     #FFFFFF

# 玻璃态白透 14 档
--tkn-color-glass-white-{08|10|12|14|16|18|22|24|30|35|78|86|92}
--tkn-color-glass-black-{35|40|45|55}

# Camera 暗底（替代旧 gradient-focus-night）
--tkn-color-bg-camera                #0B0F1A   (Mood C 实色 · 不是 linear gradient)
```

### Layer 1 阴影（shadow.json v2.0 · 18 类 archive 实测）

```
--tkn-shadow-card-light              0 1px 0 rgba(0,0,0,.03)        L0 极轻
--tkn-shadow-card                    0 1px 2px rgba(0,0,0,.04)      L0 卡片基线（最常用）
--tkn-shadow-card-deep               0 1px 2px rgba(0,0,0,.04), 0 8px 22px rgba(40,50,90,.06)
--tkn-shadow-hero-card               0 10px 30px rgba(31,60,140,.25)   ← 系统阴影上限
--tkn-shadow-phone                   inset 0 0 0 6px #111, 0 24px 64px rgba(0,0,0,.22)
--tkn-shadow-phone-deep              0 40px 100px -20px rgba(30,40,80,.35), 0 8px 24px rgba(20,30,60,.12), inset 0 0 0 6px #111
--tkn-shadow-cta-blue                0 10px 24px rgba(0,122,255,.28)
--tkn-shadow-cta-deep                0 12px 30px -6px rgba(31,60,140,.4), inset 0 1px 0 rgba(255,255,255,.25)
--tkn-shadow-cta-orange              0 4px 10px rgba(255,149,0,.28)
--tkn-shadow-step-num                0 4px 10px rgba(0,122,255,.3)
--tkn-shadow-rh-btn                  0 6px 18px rgba(255,255,255,.2)
--tkn-shadow-shutter                 0 0 0 4px rgba(255,255,255,.18), 0 0 0 8px rgba(255,255,255,.10), 0 14px 28px rgba(0,0,0,.35)
--tkn-shadow-glass-active            0 6px 18px rgba(0,0,0,.18)
--tkn-shadow-glow-yellow             0 0 8px #FFD166
--tkn-shadow-glow-pulse              0 0 0 6px rgba(255,204,0,.18)
--tkn-shadow-avatar                  0 0 0 2px rgba(255,255,255,.35), inset 0 0 0 2px rgba(0,0,0,.08)
--tkn-shadow-logo                    inset 0 0 0 1.5px rgba(255,255,255,.25), 0 4px 12px rgba(0,0,0,.25)
--tkn-shadow-paper                   0 30px 70px rgba(0,0,0,.55), 0 4px 12px rgba(0,0,0,.4)
--tkn-shadow-focus                   0 0 0 2px #007AFF
--tkn-shadow-nav-glass               backdrop-filter: saturate(180%) blur(20px)
```

### Layer 1 圆角（radius.json v2.0 · 13 档 archive 实测）

```
--tkn-radius-phone                   54px
--tkn-radius-phone-camera            55px
--tkn-radius-scroll-overlap          26px       landing scroll
--tkn-radius-scroll-overlap-sm       24px       home / welcomeback scroll
--tkn-radius-hero-card               22px       reviewhero / hero card
--tkn-radius-card-lg                 18px       weekly / weekcard / msgs / kpcard ← 主卡片
--tkn-radius-card-sm                 16px       sample / qcard / cta-try / btn 主按钮
--tkn-radius-btn                     14px       rh-btn / kpbtn
--tkn-radius-cell                    12px       weekstrip wd / how-step
--tkn-radius-ic-md                   11px       qcard ic
--tkn-radius-ic-sm                   10px       msg ic / 02 nav icon-btn / logo 方块
--tkn-radius-sm                      8px        Apple 标准按钮
--tkn-radius-xs                      6px        sample chip / qno
--tkn-radius-micro                   4px        mini sep
--tkn-radius-tiny                    3px        home indicator
--tkn-radius-pill                    999px      streak / chip / signin
--tkn-radius-circle                  50%        avatar / shutter / dots
```

### Layer 3（celebration.json v2.0）

```
--tkn-color-mastery-forgot          #C0392B
--tkn-color-mastery-partial         #E8741C
--tkn-color-mastery-mastered        #34A853

--tkn-color-celebrate-confetti-{1..5}    #FFD60A / #FF375F / #34A853 / #5E5CE6 / #FF9500
--tkn-color-celebrate-celebrate-green-stop-{1,2,3}  #0F7F3E / #1FAE5C / #34C759
--tkn-gradient-celebrate-hero       linear-gradient(175deg, #0F7F3E 0%, #1FAE5C 40%, #34C759 100%)

--tkn-color-streak-fire             #FF6B35

--tkn-motion-celebrate-confetti     1200ms
--tkn-motion-celebrate-checkmark    800ms
--tkn-motion-celebrate-streak-bump  400ms
```

### Layer 2 ⛔️ DEPRECATED · warmth.json

> 原 v1.0 token 名（`--tkn-color-warm-bg / -elevated / -text-primary / -text-secondary / -divider` + `--tkn-gradient-aurora / -focus-night / -result-warm` + `--tkn-shadow-warm-*`）**全部已废**。
> 详见 `warmth.json#_meta.migration_map` 字段。
> 下游引用迁移规则见 README.md。

### EXCEPTION 命名空间

```
--tkn-subject-math                   #C41E3A
--tkn-subject-physics                #0057B7
--tkn-subject-chemistry              #1A6B3A
--tkn-subject-english                #9C4F00
--tkn-color-brand-wechat             #07C160     (铁律 1 例外 wechat-brand)
```

迁移：一次性 sed 替换所有 mockup CSS + components.md + 现有代码 + 19 page spec.md（Loop 2 待执行）。

---

## 附录 B · testid 命名规范（强制）

### B.1 格式
`{block-id-kebab}[-{role}][-{index}]`

### B.2 命名规则
1. kebab-case，不允许驼峰、下划线、中文、特殊字符
2. block root testid = spec §3 表中的 testid root
3. 子元素拼 `-role`（如 `-name`, `-icon`, `-button`, `-input`, `-progress`）
4. 列表项拼 `-{index}`（从 1 起，不从 0）
5. 复合层次允许多级拼接：`week-strip-day-1-tlevel-T2`
6. 同一页面内全局唯一
7. 与 AC 表 §8 双向锚定，每条 AC 至少绑定 1 个 testid

### B.3 命名示例

| Block | testid root | 子元素示例 |
|---|---|---|
| GreetingHero | `greeting-hero` | `greeting-hero-name`, `greeting-hero-streak-icon` |
| TodayReviewCard | `today-review-card` | `today-review-card-total`, `today-review-card-circle-progress`, `today-review-card-start-all-btn` |
| WeekStrip | `week-strip` | `week-strip-day-{1..7}`, `week-strip-day-3-tlevel-T2` |
| QuestionListCard | `question-list-card-{index}` | `question-list-card-1-thumbnail`, `question-list-card-1-stage-3` |
| MasteryStatusCard | `mastery-status-card` | `mastery-status-card-forgot`, `mastery-status-card-partial`, `mastery-status-card-mastered` |
| ConfettiBurst | `confetti-burst` | `confetti-burst-particle-{1..N}` |

---

## 附录 C · 文件清单（本设计完成后产出）

### C.1 已存在 · 仅维护

```
design/system/components.md                  (重命名 system-* token + 加 mood-celebrate variant)
design/system/tokens/color.json              (重命名 success/warning/danger/info)
design/system/tokens/typography.json         (不动)
design/system/tokens/spacing.json            (不动)
design/system/tokens/radius.json             (不动)
design/system/tokens/shadow.json             (不动)
design/system/tokens/motion.json             (不动)
design/system/tokens/README.md               (更新 · 标注三层架构)
```

### C.2 新建文件

```
design/system/DESIGN.md                      (本文档)
design/system/molecules.md                   (11 个产品分子组件规格)
design/system/tokens/warmth.json             (Layer 2 · 18 token)
design/system/tokens/celebration.json        (Layer 3 · 12 token)
design/system/pages/_template.spec.md        (空白模板)
design/system/pages/P00.spec.md              (Sprint 3)
design/system/pages/P-LANDING.spec.md        (Sprint 3)
design/system/pages/P-GUEST-CAPTURE.spec.md  (Sprint 3)
design/system/pages/P-SHARED.spec.md         (Sprint 3)
design/system/pages/P-WELCOMEBACK.spec.md    (Sprint 5)
design/system/pages/P-OBSERVER.spec.md       (Sprint 5)
design/system/pages/P-HOME.spec.md           (Sprint 2)
design/system/pages/P02-capture.spec.md      (Sprint 1)
design/system/pages/P03-analyzing.spec.md    (Sprint 1)
design/system/pages/P04-result.spec.md       (Sprint 1)
design/system/pages/P05-wrongbook-list.spec.md   (Sprint 2)
design/system/pages/P06-wrongbook-detail.spec.md (Sprint 2)
design/system/pages/P07-review-today.spec.md (Sprint 2)
design/system/pages/P08-review-exec.spec.md  (Sprint 1)
design/system/pages/P09-review-done.spec.md  (Sprint 1)
design/system/pages/P10-calendar-month.spec.md   (Sprint 4)
design/system/pages/P11-event-detail.spec.md (Sprint 4)
design/system/pages/P12-notifications.spec.md    (Sprint 4)
design/system/pages/P13-settings.spec.md     (Sprint 4)
```

### C.3 mockup HTML（19 张，按 sprint 在后续 fe-preflight 阶段生成）

```
design/mockups/wrongbook/
├── _archive/                          (现有 5 张 home 变体归档到此)
├── 00_login.html                      (Sprint 3 · 重做)
├── 01_home.html                       (Sprint 2 · 重做)
├── 02_capture.html                    (Sprint 1 · 重做)
├── 03_analyzing.html                  (Sprint 1 · 重做)
├── 04_result.html                     (Sprint 1 · 重做)
├── 05_wrongbook_list.html             (Sprint 2 · 重做)
├── 06_wrongbook_detail.html           (Sprint 2 · 重做)
├── 07_review_today.html               (Sprint 2 · 重做)
├── 08_review_exec.html                (Sprint 1 · 重做)
├── 09_review_done.html                (Sprint 1 · 重做)
├── 10_calendar_month.html             (Sprint 4 · 重做)
├── 11_event_detail.html               (Sprint 4 · 重做)
├── 12_notifications.html              (Sprint 4 · 重做)
├── 13_settings.html                   (Sprint 4 · 重做)
├── 14_landing.html                    (Sprint 3 · 重做)
├── 15_guest_capture.html              (Sprint 3 · 重做)
├── 16_shared.html                     (Sprint 3 · 重做)
├── 17_welcomeback.html                (Sprint 5 · 重做)
└── 18_observer.html                   (Sprint 5 · 重做)
```

---

## 文档元信息

- **版本**：v1.0
- **创建日期**：2026-05-02
- **作者**：Longfeng 设计组
- **评审**：与用户在交互式 brainstorming 中分 4 章节（§A 哲学 / §B Token / §C 组件与规格卡 / §D 19 页面意图）逐节点头通过
- **下一步**：本文档评审通过后进入 writing-plans 阶段，按 Sprint 1 → 5 拆分 5 个独立实施计划，每个 Sprint 走 fe-preflight → fe-testplan → fe-builder → fe-accept 三段式 skill 链
- **变更跟踪**：本文档修改 → 必须更新 frontmatter `version` + 在底部追加变更日志
