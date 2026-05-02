---
page_id: P-LANDING
name: 访客落地页
name_en: Visitor Landing Page
route_h5: /welcome
route_miniprogram: pages/landing/welcome
deeplink: wb://welcome
auth_state: anonymous
persona:
  - 陌生访客
  - 犹豫期潜在用户
scenarios:
  - SC-11
mockup_canonical: design/mockups/wrongbook/14_landing.html
mockup_version: v1
last_reviewed: 2026-05-02
status: spec-draft
sprint: S3
---

# P-LANDING · 访客落地页

> **使用说明**：本 spec 对应 DESIGN.md §5.3 Sprint 3 增长漏斗第一站。
> **特殊说明**：本页是铁律 6 的合法变体——landing pages 第二段允许包含价值数字 + 二次 CTA，仍属"两段式"（dark hero + warm content）。

---

## §1 页面目的（why · 1 句话）

让陌生访客在 30 秒内明白"AI 错题本是什么"，并做出"试一试 / 直接登录"的决断，承载访客→游客 ≥35% 的转化漏斗。

---

## §2 ASCII 布局图

```
┌─────────────────────────────────────┐
│  [B1 status-bar]                     │
│                                      │
│  [B2 landing-hero · mood=cool]       │  ← Section 1 (dark)
│   --tkn-color-bg-camera 全屏暗色       │
│   · LogoMark 左上 · 登录右上          │
│   · display-hero "AI 帮你拍下错题"    │
│   · "不再做无用功"副标题                │
│   · 拍题动效占位（Lottie/GIF）        │
│   · 双 CTA: "试一试(白pill)" + "登录(蓝pill)" │
│                                      │
├─────────────────────────────────────┤  ← Mood 切换 cool → warm
│  [B3 landing-three-step · mood=warm] │  ← Section 2 (warm)
│   warm-bg #FAF8F4 米白底              │
│   · "三步搞定" section title           │
│   · 拍 → 分析 → 排进日历 三步漫画卡    │
│                                      │
│  [B4 landing-samples · mood=warm]    │
│   · "看看真实样例" section title       │
│   · 3 张样例卡横滚 (math/physics/eng) │
│   · 点击展开看 AI 分析 mock           │
│                                      │
│  [B5 landing-kpi · mood=warm]        │
│   · 价值数字 banner                   │
│   · "已分析 100w+ 错题 · 7 日留存 47%" │
│                                      │
│  [B6 landing-cta-bottom · mood=warm] │
│   · 二次 CTA "立即开始"               │
│   · 底部小字 家长/老师入口            │
└─────────────────────────────────────┘
```

---

## §3 Block 清单（每块独立可实现单元）

| Block ID | 名称 | Section | Mood | Component | testid root | Tokens 用到 |
|---|---|---|---|---|---|---|
| B1 | 状态栏 | shell | — | (atom · time + icons) | `p-landing-statusbar` | `--tkn-color-text-on-dark` |
| B2 | Landing Hero（暗底主视觉） | hero | cool | LogoMark + display-hero + DemoMedia + DualCTA | `landing-hero` | `--tkn-color-bg-camera` / `--tkn-type-display-hero` / `--tkn-color-text-on-dark` / `--tkn-color-primary-DEFAULT` / `--tkn-radius-pill` |
| B3 | 三步漫画 | info | warm | (Card + Icon × 3) | `landing-three-step` | `--tkn-color-bg-light` / `--tkn-color-card` / `--tkn-color-text-primary` / `--tkn-shadow-card-deep` / `--tkn-radius-lg` |
| B4 | 样例卡横滚 | info | warm | SampleCardCarousel (Card + 学科色条) | `landing-samples` | `--tkn-color-card` / `--tkn-subject-math` / `--tkn-subject-physics` / `--tkn-subject-english` / `--tkn-shadow-card-deep` |
| B5 | 价值数字 banner | info | warm | (Card + 大数字) | `landing-kpi` | `--tkn-color-encouragement-soft` / `--tkn-color-encouragement-DEFAULT` / `--tkn-type-display-hero` |
| B6 | 二次 CTA + 家长入口 | info | warm | Button + LinkText | `landing-cta-bottom` | `--tkn-color-primary-DEFAULT` / `--tkn-radius-pill` / `--tkn-color-text-secondary` |

> **fe-preflight 用法**：把 mockup HTML 切成这些块；fe-builder 按块逐个实现并跑 lint。

---

## §4 数据契约（page-level interface）

```typescript
interface LandingResp {
  bucket: 'default' | 'hero_copy_v1' | 'hero_copy_v2';
  hero: {
    headline: string;        // "AI 帮你拍下错题，不再做无用功"
    subheadline: string;     // "拍一张照，3 秒分析 · 自动排好艾宾浩斯复习"
    demoMediaUrl: string;    // Lottie/APNG/WebP, ≤300KB
  };
  samples: Array<{
    id: string;
    subject: 'math' | 'physics' | 'english' | 'chemistry';
    stemPreview: string;     // 题干前 60 字
    thumbnailUrl: string;
    aiAnalysisMock: {
      reason: string;
      stepsCount: number;
    };
  }>;
  kpi: {
    totalQuestionsAnalyzed: number;   // 1_080_000
    retention7d: number;               // 0.47
    headline: string;                  // "已分析 100w+ 错题"
  };
  parentEntryUrl: string;    // → P-OBSERVER 说明
}
```

> 类型不一致直接 fail。

---

## §5 API 触点

| Method | Path | 用途 | P95 预算 | 失败降级 |
|---|---|---|---|---|
| GET | `/api/landing/samples?bucket={ab}` | 3 组预置样本 | 200ms（CDN 强缓存 1h） | DEGRADED 态：只露 hero + CTA |
| GET | `/api/landing/kpi` | 社区脱敏数据 | 200ms（CDN 强缓存） | 隐藏 B5 banner |
| POST | `/api/auth/wechat-login` | 登录 CTA 跳出 | 800ms | Toast 提示 + 重试 |

---

## §6 状态机

| State | 触发 | UI 表现 |
|---|---|---|
| `LOADING` | 进入页面 (<150ms) | hero 静态海报 + 骨架样例卡 |
| `READY` | 双接口 200 + 数据 | 完整渲染（hero 动图 + 样例 + KPI） |
| `DEGRADED` | samples / kpi 任一接口失败 | 只露 hero + 双 CTA · 隐藏 B4/B5 |
| `EMPTY` | samples 数组为空（不应发生） | 显示静态文案"敬请期待样例" |
| `ERROR` | 网络完全失败 | 全屏静态海报 + "无网络也能登录"链接 |

---

## §7 跳转图

```
[入口]
  冷启动决策树节点 3 未命中 ─┐
  wb://welcome 深链 ────────┤──→ P-LANDING
  P00 "没有账号？先看看" ────┤
  P-SHARED 令牌过期降级 ────┘
        │
        ├──[B2 "试一试" 白 pill]──→ P-GUEST-CAPTURE
        ├──[B2 "登录" 蓝 pill]────→ P00
        ├──[B4 样例卡点击]────────→ 内嵌弹层 / 不离开本页
        ├──[B6 "立即开始"]────────→ P-GUEST-CAPTURE
        └──[B6 家长入口小字]──────→ P-OBSERVER 说明页
```

---

## §8 AC 覆盖表（与 business-analysis.yml 双向锚定）

| AC ID | 验收点 | 涉及 Block | testid 验证点 |
|---|---|---|---|
| `AC-LANDING-001` | 首屏 TTI ≤ 1.0s · hero headline 完整可见 | B2 | `landing-hero-headline` 文本 = "AI 帮你拍下错题，不再做无用功" |
| `AC-LANDING-002` | 双 CTA 视觉层级正确（白 pill 试一试，蓝 pill 登录主操作） | B2 | `landing-hero-cta-try` 背景 = white · `landing-hero-cta-login` 背景 = `--tkn-color-primary-DEFAULT` |
| `AC-LANDING-003` | 点击「试一试」跳转 P-GUEST-CAPTURE 并记录 `anon_landing_cta_try` | B2 | `landing-hero-cta-try` click 后路由 = `/guest/capture` |
| `AC-LANDING-004` | 样例卡 3 个 · 学科 4px 左色条命中 subject 色 token | B4 | `landing-samples-card-1` 左条 background = `--tkn-subject-math` |
| `AC-LANDING-005` | KPI banner 显示 "已分析 100w+ 错题" · 7 日留存数字渲染 | B5 | `landing-kpi-total` 文本包含 "100w" · `landing-kpi-retention` 包含 "47%" |
| `AC-LANDING-006` | DEGRADED 态：samples 接口 500 时只露 hero + CTA 不卡白屏 | B2 | `landing-samples` 不可见 / `landing-hero-cta-try` 仍可点 |
| `AC-LANDING-007` | [AI 推测] 访客 → 试一试 转化率 ≥35%（增长漏斗 KPI） | B2 | `anon_landing_cta_try` / `anon_landing_view` 比值 |
| `AC-LANDING-008` | A11y: hero `<header role="banner">` · 双 CTA tabIndex 顺序正确 | B2 | `landing-hero` role = banner · keyboard tab 顺序 try → login |

> **fe-accept-mock 用法**：用这张表逐条断言；缺 testid = 验收 fail。
> **AC 缺失处理**：`AC-LANDING-007` 标 [AI 推测]，需业务方在 business-analysis.yml 确认 KPI 量化口径。

---

## §9 异常路径

| 触发 | UI | 系统行为 |
|---|---|---|
| `/api/landing/samples` 返回 500 | DEGRADED 态：只露 hero + CTA | 监控告警 · 不阻塞用户 |
| `/api/landing/kpi` 返回超时 | 隐藏 B5 banner | silently fail |
| 弱网 | hero 动图退为静态海报 | 减小请求 |
| 海外地区 (GDPR) | 顶部 ConsentBar 提示 | 强制同意才显示动图 |
| 用户同时点双 CTA | 取第一次点击的目标，禁用第二次 | 防误触 |
| 微信浏览器无法播放 Lottie | fallback 静态 PNG hero | 前端探测后切换 |

---

## §10 埋点事件

| 事件名 | 触发 Block | 必带属性 |
|---|---|---|
| `anon_landing_view` | 进入页面 | `entry_source`, `device_fp`, `experiment_bucket` |
| `anon_landing_demo_play` | B2 hero 动图自动播放完一遍 | `device_fp`, `sec` |
| `anon_landing_sample_open` | B4 点击样例卡 | `device_fp`, `subject`, `sample_id` |
| `anon_landing_cta_try` | B2/B6 "试一试" 点击 | `device_fp`, `cta_position`(hero/bottom) |
| `anon_landing_cta_login` | B2 "登录" 点击 | `device_fp` |
| `anon_landing_parent_entry` | B6 家长入口点击 | `device_fp` |

> 所有事件经 `packages/analytics` 包；必须携带 `device_fp`，禁带原始 PII。

---

## §11 性能预算

- TTI ≤ 1000ms（首屏 hero 文字 + CTA 必须可见）
- LCP ≤ 1500ms（hero 动图）
- CLS < 0.05
- API P95 ≤ 200ms（CDN 命中）
- 总包 ≤ 180KB（不含动图）· hero 动图 ≤ 300KB
- 骨架屏与数据切换 < 100ms 渐入

---

## §12 A11y

- Landmarks: `<header role="banner">` (B2), `<main role="main">` (B3-B6), `<footer role="contentinfo">` (B6 家长入口)
- 焦点顺序: `landing-hero-logo` → `landing-hero-cta-try` → `landing-hero-cta-login` → `landing-samples-card-1` → ... → `landing-cta-bottom-btn`
- 屏幕阅读器朗读优先级: B2 headline 优先，aria-label="访客落地 · AI 帮你拍下错题"
- `prefers-reduced-motion`: 关闭 hero 动图自动播放 + 关闭样例卡入场 stagger，仅静态展示

---

## §13 Mockup 锚定

- **权威**: `design/mockups/wrongbook/14_landing.html` (v1)
- **历史变体**: 旧版 `14_landing.html` 已归档至 `_archive/`（v0 · 2026-04-24）
- **截图**: `design/system/screenshots/P-LANDING-v1-light.png`（fe-preflight 自动生成）
- **反向锚定**: mockup HTML `<head>` 中含 `<meta name="design-spec" content="P-LANDING.spec.md@v1">`

---

## §14 Tokens 清单（grep 校验用）

```
L1 (Apple base):
  --tkn-color-black
  --tkn-color-white
  --tkn-color-text-on-dark
  --tkn-color-primary-DEFAULT
  --tkn-font-display
  --tkn-font-text
  --tkn-type-display-hero
  --tkn-type-sub-heading
  --tkn-type-body
  --tkn-type-caption
  --tkn-type-micro
  --tkn-spacing-xs
  --tkn-spacing-sm
  --tkn-spacing-12
  --tkn-spacing-md
  --tkn-spacing-lg
  --tkn-spacing-xl
  --tkn-spacing-2xl
  --tkn-radius-sm
  --tkn-radius-md
  --tkn-radius-lg
  --tkn-radius-pill
  --tkn-shadow-card
  --tkn-motion-ease-standard
  --tkn-motion-dur-base

L2 (warmth · 仅 B3-B6 warm 区段使用):
  --tkn-color-bg-light
  --tkn-color-card
  --tkn-color-text-primary
  --tkn-color-text-secondary
  --tkn-color-sep
  --tkn-color-encouragement-DEFAULT
  --tkn-color-encouragement-soft
  --tkn-color-bg-camera
  --tkn-color-glass-white-08
  --tkn-shadow-card-deep

EXCEPTION (subject · 仅 B4 样例卡 4px 左色条):
  --tkn-subject-math
  --tkn-subject-physics
  --tkn-subject-english
```

---

## 模板使用清单

- [x] frontmatter 全部字段填写
- [x] §1 一句话目的写完
- [x] §2 ASCII 布局图绘制
- [x] §3 Block 清单 testid 与 §8 AC 表交叉对账
- [x] §4 TS interface 与业务文档 §10 API 契约一致
- [x] §5 API 路径与业务文档 §10 一致
- [x] §6 状态机覆盖 LOADING/READY/EMPTY/ERROR/DEGRADED 五态
- [x] §7 跳转图入口出口都列全
- [x] §8 AC 表 8 条；`AC-LANDING-007` 标 [AI 推测]
- [x] §10 埋点事件名与业务文档 §2A.8 字典一致
- [x] §14 Tokens 清单与本页用到的 token 完全一致
