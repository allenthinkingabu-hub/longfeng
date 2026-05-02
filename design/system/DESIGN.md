# AI 错题本 · Design System v1.0

> **角色定位**：本文档是 tokens / components / molecules / pages 四级体系的总宪法。
> **服务对象**：UI/UX 设计师 + AI 实施代理（fe-preflight / fe-builder / fe-accept skill 链）+ 前端工程师 + QA。
> **使用约束**：与本文档冲突的任何 mockup / 组件 / 代码均视为缺陷。
> **修订原则**：本文档变更 = 大版本变更，需重新走"设计评审 → 全量 mockup 同步 → spec 同步"三步。
> **版本**：v1.0 · 2026-05-02 · 作者 Longfeng 设计组 · 评审通过

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
**所有可点击元素**用 `--tkn-color-primary-DEFAULT` (#0071e3) 或深底变体 `--tkn-color-primary-dark` (#2997ff)。

- ✅ 主 CTA 蓝底白字、链接 #0066cc 下划线、Tab Bar 激活态蓝、复选框选中蓝、focus ring 蓝
- ❌ 学科色当 CTA、暖色当 CTA、庆祝色当 CTA、4 种颜色区分按钮重要性
- 🤖 grep 所有 `<button>` / `role="button"`，背景必须命中 `--tkn-color-primary-*` 或透明 pill。出现其他色 = fail（除非命中下方注册例外）
- ⚠️ **已注册例外**（共 2 项 · 必须 `data-iron-rule-1-exception` 显式标注）：
  - `wechat-brand` —— P00 登录"微信一键登录"按钮使用 `--tkn-color-brand-wechat` (#07C160)，因微信品牌色是产品语境的一部分
  - `self-grading` —— P08 复习执行底部三按钮使用 `--tkn-color-mastery-{forgot|partial|mastered}`，因语义是"自评判断"非"前进操作"
- 任何新增例外必须在本铁律节追加并经设计 lead 批准

### 铁律 2 · 冷暖分层不混用
默认走 Apple 冷调（白 / 浅灰 / 黑）。**只在情感时刻**切换到暖中性 + 庆祝色，且**整段一致**。

- ✅ P-HOME hero 极光蓝紫渐变（情感时刻），下方信息区切到暖米白；P09 庆祝页全屏暖绿渐变
- ❌ 一个卡片里同时出现冷灰背景 + 暖橙文字、Apple 蓝按钮和庆祝绿按钮并排、暖色卡片配冷灰边
- 🤖 每个 section 标 `data-mood="cool|warm|celebrate"`，子元素颜色必须与 mood 一致（用映射表校验）

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

## 2. Token 三层架构

### 2.1 三层结构总览

```
┌────────────────────────────────────────────────────────────────┐
│  Layer 1 · Apple 基础层（保留 · 不动）                          │
│  --tkn-color-* (Apple Blue / Light gray / Dark surfaces)       │
│  --tkn-font-* / --tkn-type-* / --tkn-spacing-* / --tkn-radius-*│
│  --tkn-shadow-* / --tkn-motion-*                               │
│  → 决定页面骨架与系统语言（Apple HIG 节奏）                     │
└────────────────────────────────────────────────────────────────┘
       ↑ 共存 · 不污染 · 通过 mood 切换
┌────────────────────────────────────────────────────────────────┐
│  Layer 2 · 学生暖意层（新增 · warmth.json）                     │
│  --tkn-color-warm-* (暖米白 / 暖文字 / 暖分隔线)                │
│  --tkn-color-encouragement-* (鼓励橙)                          │
│  --tkn-gradient-* (4 种 hero 渐变)                             │
│  --tkn-shadow-warm-*                                           │
│  → 决定情感时刻的色温与节奏                                     │
└────────────────────────────────────────────────────────────────┘
       ↑ 仅在 mood ≠ cool 时触发
┌────────────────────────────────────────────────────────────────┐
│  Layer 3 · 庆祝语义层（新增 · celebration.json）                │
│  --tkn-color-mastery-* (掌握三态色)                            │
│  --tkn-color-celebrate-confetti-* (5 色粒子)                   │
│  --tkn-color-streak-fire (火焰)                                │
│  --tkn-motion-celebrate-* (粒子动效专用时长)                    │
│  → 仅在 4 个白名单庆祝时刻调用                                  │
└────────────────────────────────────────────────────────────────┘
       ↑ 独立命名空间
┌────────────────────────────────────────────────────────────────┐
│  EXCEPTION · 学科色板（保留 · 独立）                            │
│  --tkn-subject-{math|physics|chemistry|english}                │
│  → 仅 chip / 左色条 / icon 三种用法                             │
└────────────────────────────────────────────────────────────────┘
```

### 2.2 Layer 1 · Apple 基础层（保留）

现有 7 份 token JSON 全部保留：`color.json` / `typography.json` / `spacing.json` / `radius.json` / `shadow.json` / `motion.json` + 学科色作为 EXCEPTION。

维护清单：
1. 在 `color.json` 加 `_meta.layered = "L1-apple-base"` 标记
2. 把现有 `success` / `warning` / `danger` / `info` 重命名为 `system-success` / `system-warning` / `system-danger` / `system-info`（防止与新增 `mastery-*` / `encouragement-*` 混淆）
3. 在 `color.json` 新增 `brand` 子组：`--tkn-color-brand-wechat = #07C160`，仅供 P00 登录页"微信一键登录"按钮使用（铁律 1 唯一例外项的 token 化承载）

### 2.3 Layer 2 · 学生暖意层（新增 18 个 token · `warmth.json`）

#### 暖中性面板（6 个）

| Token | 值 | 用途 |
|---|---|---|
| `--tkn-color-warm-bg` | `#FAF8F4` | 信息流页面默认底色（P05 列表、P06 详情、P-HOME 信息区） |
| `--tkn-color-warm-elevated` | `#FFFFFF` | 暖底上的卡片纯白 |
| `--tkn-color-warm-sunken` | `#F2EDE3` | 暖底里的凹陷区（搜索框、输入框背景） |
| `--tkn-color-warm-text-primary` | `#2C2A26` | 暖底主文字（对比 16.2:1） |
| `--tkn-color-warm-text-secondary` | `rgba(44,42,38,0.72)` | 暖底次文字 |
| `--tkn-color-warm-divider` | `rgba(44,42,38,0.08)` | 暖底分隔线、卡片细边 |

#### 鼓励橙（情感激励，3 个）

| Token | 值 | 用途 |
|---|---|---|
| `--tkn-color-encouragement-DEFAULT` | `#E8741C` | 倒计时 soon 状态、连击数字、薄弱 KP 高亮、复习"部分掌握"按钮 |
| `--tkn-color-encouragement-soft` | `rgba(232,116,28,0.10)` | 鼓励背景色 |
| `--tkn-color-encouragement-on` | `#FFFFFF` | 鼓励底上的文字 |

#### Hero 渐变（4 个，仅情感时刻）

| Token | 值 | 用途 |
|---|---|---|
| `--tkn-gradient-aurora` | `linear-gradient(135deg, #4F6FFF 0%, #7B5FFF 50%, #B85FFF 100%)` | P-HOME / P-WELCOMEBACK 顶部极光问候 |
| `--tkn-gradient-focus-night` | `linear-gradient(180deg, #0A0E1A 0%, #1A1F2E 100%)` | P00 / P02 / P03 / P-LANDING 第一段 全屏专注暗色 |
| `--tkn-gradient-result-warm` | `linear-gradient(180deg, #FFF8E7 0%, #FAF8F4 100%)` | P04 AI 结果页温暖入场 |
| `--tkn-gradient-today-blue` | `linear-gradient(160deg, #1F3A93 0%, #4A6FE3 100%)` | P07 今日复习 hero（沉稳深蓝→明亮蓝） |

#### Hero 装饰透明度（2 个）

| Token | 值 | 用途 |
|---|---|---|
| `--tkn-color-aurora-particle` | `rgba(255,255,255,0.18)` | hero 上漂浮的极光粒子 |
| `--tkn-color-aurora-blur` | `rgba(255,255,255,0.06)` | hero 模糊光斑 |

#### 暖底卡阴影（3 个）

| Token | 值 | 用途 |
|---|---|---|
| `--tkn-shadow-warm-card` | `0 1px 2px rgba(44,42,38,0.04), 0 4px 12px rgba(44,42,38,0.06)` | 暖底卡片标准浮起 |
| `--tkn-shadow-warm-card-pressed` | `0 0 0 transparent` | 卡片按压瞬间，配合 scale(0.97) |
| `--tkn-shadow-warm-hero` | `0 8px 24px rgba(31,58,147,0.20)` | hero 区下方过渡阴影 |

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

### 5.0 19 张页面 Mockup 主控矩阵

| # | Page ID | 页面名 | Mood | Hero | 关键 Molecules | 情感锚点 | Sprint |
|---|---|---|---|---|---|---|---|
| 1 | P02 | 拍题相机 | cool 全屏沉浸 | `gradient-focus-night` | 取景器 + SubjectChip | 专注 → 期待 | **S1** |
| 2 | P03 | AI 分析中 | cool 暗底等待 | 同 P02 顺接 | AnalyzingPipeline | 略焦虑 → 被看见 | **S1** |
| 3 | P04 | AI 分析结果 | warm 温和入场 | `gradient-result-warm` | QuestionCard + 3-step + MemoryCurve | 惊喜 → 满足 | **S1** |
| 4 | P08 | 复习执行 | cool 专注白底 | 无 hero | 题干 Hero 卡 + Mastery 三按钮 | 挑战 → 自评 | **S1** |
| 5 | P09 | 复习完成 | celebrate 庆祝 | `gradient-celebrate-green` + Confetti | CelebrateHero + MemoryCurve | 成就 → 持续动力 | **S1** |
| 6 | P-HOME | 今日聚合首页 | celebrate hero + warm 信息流 | `gradient-aurora` 极光 | GreetingHero + TodayReviewCard + WeekStrip | 期待 → 控制感 | **S2** |
| 7 | P05 | 错题本列表 | warm 卡片信息流 | 无 hero · 大标题 | SubjectChip + MasteryStatusCard + QuestionListCard | 掌控感 | **S2** |
| 8 | P06 | 错题详情 | warm 档案页 | 原图缩略图卡 | MemoryCurve + RadarChart | 沉淀 → 进步可见 | **S2** |
| 9 | P07 | 今日待复习 | celebrate hero + warm 时段卡 | `gradient-today-blue` | TodayReviewCard + 时段分组卡 | 整装 → 出发 | **S2** |
| 10 | P-LANDING | 访客落地 | warm 价值橱窗 | 双段 hero | HeroProductShot + 3 样例 + 双 CTA | 好奇 → 信任 | **S3** |
| 11 | P-GUEST-CAPTURE | 游客拍题 | cool 同 P02 + 配额 banner | 同 P02 | P02 + GuestQuotaBanner | 试探 → 体验 | **S3** |
| 12 | P-SHARED | 分享只读 | warm 脱敏卡 | 无 hero · 顶部分享者 | QuestionCard(脱敏) + UpgradeCTA | 好奇 → 升级欲 | **S3** |
| 13 | P00 | 登录 | cool Apple 黑底 | `gradient-focus-night` | LogoMark + 微信一键 + 协议 | 干净 → 决断 | **S3** |
| 14 | P10 | 日历月视图 | warm 网格 | 无 hero | MonthGrid + LegendBar | 全局 → 计划 | **S4** |
| 15 | P11 | 事件详情双形态 | warm 卡片 | 形态条带 | EventHeroCard + 关联项列表 | 上下文 → 行动 | **S4** |
| 16 | P12 | 通知中心 | warm 列表 | 无 hero | TimelineGroup + NotificationCard | 整理 | **S4** |
| 17 | P13 | 设置 / 我的 | warm 列表 + 头像区 | 暖色头像区 | AvatarBlock + SettingRow + DangerZone | 控制 | **S4** |
| 18 | P-WELCOMEBACK | 回流唤起 | celebrate hero · 数字脉冲 | `gradient-aurora` | WelcomeBackHero + 数字脉冲 + CTA | 被惦记 → 回归 | **S5** |
| 19 | P-OBSERVER | 观察者会话 | warm 只读 | 无 hero · 顶部观察者 banner | ReadOnlyBanner + 错题列表(只读) | 信任 → 安心 | **S5** |

### 5.1 Sprint 1 · 学习闭环 5 张

学生完成"一道错题"端到端的 5 张关键页面。情感曲线：专注 → 被看见 → 满足 → 挑战 → 成就。

#### P02 · 拍题相机
- 全屏 `gradient-focus-night` 暗底，70% 高度相机预览
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
- **从 P03 暗底切到 `gradient-result-warm` 米黄米白**——情感曲线最关键转折
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
- 顶部 hero `gradient-aurora` 蓝紫极光 + 漂浮粒子
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
- **第一段 hero**：暗底 `gradient-focus-night`，display-hero 大字"AI 帮你拍下错题，不再做无用功"，下方真实拍题 Lottie / GIF loop
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
- 全屏 `gradient-focus-night` 暗底
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
- 复用 P-HOME 的 `gradient-aurora` hero
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

## 附录 A · 完整 Token 清单（Layer 2 + 3 新增）

### Layer 2 新增（18 个 · `warmth.json`）

```
--tkn-color-warm-bg                 #FAF8F4
--tkn-color-warm-elevated           #FFFFFF
--tkn-color-warm-sunken             #F2EDE3
--tkn-color-warm-text-primary       #2C2A26
--tkn-color-warm-text-secondary     rgba(44,42,38,0.72)
--tkn-color-warm-divider            rgba(44,42,38,0.08)

--tkn-color-encouragement-DEFAULT   #E8741C
--tkn-color-encouragement-soft      rgba(232,116,28,0.10)
--tkn-color-encouragement-on        #FFFFFF

--tkn-gradient-aurora               linear-gradient(135deg, #4F6FFF 0%, #7B5FFF 50%, #B85FFF 100%)
--tkn-gradient-focus-night          linear-gradient(180deg, #0A0E1A 0%, #1A1F2E 100%)
--tkn-gradient-result-warm          linear-gradient(180deg, #FFF8E7 0%, #FAF8F4 100%)
--tkn-gradient-today-blue           linear-gradient(160deg, #1F3A93 0%, #4A6FE3 100%)

--tkn-color-aurora-particle         rgba(255,255,255,0.18)
--tkn-color-aurora-blur             rgba(255,255,255,0.06)

--tkn-shadow-warm-card              0 1px 2px rgba(44,42,38,0.04), 0 4px 12px rgba(44,42,38,0.06)
--tkn-shadow-warm-card-pressed      0 0 0 transparent
--tkn-shadow-warm-hero              0 8px 24px rgba(31,58,147,0.20)
```

### Layer 3 新增（12 个 · `celebration.json`）

```
--tkn-color-mastery-forgot          #C0392B
--tkn-color-mastery-partial         #E8741C
--tkn-color-mastery-mastered        #34A853

--tkn-color-celebrate-confetti-1    #FFD60A
--tkn-color-celebrate-confetti-2    #FF375F
--tkn-color-celebrate-confetti-3    #34A853
--tkn-color-celebrate-confetti-4    #5E5CE6
--tkn-color-celebrate-confetti-5    #FF9500

--tkn-color-streak-fire             #FF6B35

--tkn-motion-celebrate-confetti     1200ms
--tkn-motion-celebrate-checkmark    800ms
--tkn-motion-celebrate-streak-bump  400ms
```

### Layer 1 重命名 + 新增清单（兼容性变更）

```
旧                              →  新
--tkn-color-success-DEFAULT     →  --tkn-color-system-success-DEFAULT
--tkn-color-warning-DEFAULT     →  --tkn-color-system-warning-DEFAULT
--tkn-color-danger-DEFAULT      →  --tkn-color-system-danger-DEFAULT
--tkn-color-info-DEFAULT        →  --tkn-color-system-info-DEFAULT

新增（brand 子组 · 1 个）：
--tkn-color-brand-wechat        #07C160   仅 P00 登录"微信一键登录"按钮
```

迁移：一次性 sed 替换所有 mockup CSS + components.md + 现有代码。

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
