---
page_id: P-SHARED
name: 分享只读预览
name_en: Shared Read-Only Preview
route_h5: /s/:shareToken
route_miniprogram: pages/shared/view
deeplink: wb://s/:shareToken
auth_state: anonymous
persona:
  - 分享链接收方
scenarios:
  - SC-13
mockup_canonical: design/mockups/wrongbook/16_shared.html
mockup_version: v1
last_reviewed: 2026-05-02
status: spec-draft
sprint: S3
---

# P-SHARED · 分享只读预览

> **使用说明**：本页通过 HS256 签名令牌渲染脱敏只读内容，触发"注册查看完整"的升级转化（≥15% 漏斗）。

---

## §1 页面目的（why · 1 句话）

让分享链接收方看到"分享者想让我看什么"的脱敏预览，并以"拥有自己的错题本"驱动注册转化。

---

## §2 ASCII 布局图

```
┌─────────────────────────────────────┐
│  [B1 statusbar]                      │
│  [B2 sharer-banner · mood=warm]      │  ← warm-bg 米白
│   头像 + "@小明** 同学 分享了一道错题给你"
│                                      │
├─────────────────────────────────────┤
│  [B3 masked-question · mood=warm]    │
│   QuestionListCard 脱敏版              │
│   · 题干：前 12 字清晰 + 后续模糊       │
│   · 缩略图：打码                       │
│   · 学生答案/错因/记录：模糊覆盖层      │
│   · 覆盖层文案："注册查看完整内容"       │
│                                      │
│  [B4 memory-curve-preview · warm]    │
│   MemoryCurve(variant=preview)       │
│   全部灰色节点 · 副标题"已被复习 N 次"  │
│                                      │
│  [B5 share-meta · warm]              │
│   分享时间 · ttl 倒计时 · 来源应用       │
│                                      │
├─────────────────────────────────────┤
│  [B6 upgrade-cta-fixed · warm]       │  ← sticky bottom
│   "注册查看 + 拥有自己的错题本"        │
│   蓝 pill 大按钮（铁律 1 主蓝）        │
└─────────────────────────────────────┘
```

---

## §3 Block 清单

| Block ID | 名称 | Section | Mood | Component | testid root | Tokens 用到 |
|---|---|---|---|---|---|---|
| B1 | 状态栏 | shell | — | (atom) | `p-shared-statusbar` | `--tkn-color-text-primary` |
| B2 | 分享者横幅 | info | warm | SharedBanner (Avatar + Text) | `sharer-banner` | `--tkn-color-bg-light` / `--tkn-color-text-primary` / `--tkn-color-text-secondary` / `--tkn-radius-circle` |
| B3 | 脱敏题目卡 | info | warm | MaskedQuestionCard (QuestionListCard.readonly + 模糊覆盖层) | `masked-question` | `--tkn-color-card` / `--tkn-subject-math` / `--tkn-color-text-primary` / `--tkn-color-sep` / `--tkn-shadow-card-deep` / `--tkn-color-bg-light` |
| B4 | 复习曲线预览 | info | warm | MemoryCurve (M8 · variant=preview) | `memory-curve-preview` | `--tkn-color-text-secondary` / `--tkn-color-sep` |
| B5 | 分享元信息 | info | warm | (Card + 文字) | `share-meta` | `--tkn-color-text-secondary` / `--tkn-type-caption` / `--tkn-type-micro` |
| B6 | 升级 CTA（吸底） | info | warm | Button (primary, fixed bottom) | `upgrade-cta-fixed` | `--tkn-color-primary-DEFAULT` / `--tkn-color-white` / `--tkn-radius-pill` / `--tkn-type-body-emphasis` |

---

## §4 数据契约

```typescript
interface SharedResp {
  type: 'EXAM_DAY' | 'QUESTION' | 'REVIEW_NODE';
  signature_valid: boolean;
  ttl_sec: number;            // 剩余有效时间
  sharer_nick: string;        // 已脱敏 "小明** 同学"
  sharer_avatar_url: string;
  shared_at: string;          // ISO
  masked_payload: {
    // QUESTION 类型
    qid_hash: string;         // 不暴露原始 qid
    subject: 'math' | 'physics' | 'chemistry' | 'english';
    stem_preview: string;     // 前 12 字 · 后续 *** 模糊
    thumbnail_url_masked: string;
    review_count: number;     // "已被复习 N 次"
    node_stage_preview: number; // 0-6，仅显示位置不显示状态
    // 隐藏字段：student_answer, error_reason, personal_notes
  };
  upgrade_cta: {
    text: string;             // "注册查看 + 拥有自己的错题本"
    target_route: string;     // /auth?redirect=/s/:token
    can_claim: boolean;       // 接收方注册后是否可一键 claim
  };
}

interface SharedTokenError {
  error: 'TOKEN_EXPIRED' | 'TOKEN_INVALID' | 'TOKEN_REVOKED';
  fallback_route: string;     // /welcome
}
```

---

## §5 API 触点

| Method | Path | 用途 | P95 预算 | 失败降级 |
|---|---|---|---|---|
| GET | `/api/share/:shareToken` | 拉取脱敏内容 | 300ms | 410 → 挡板页 + 跳 P-LANDING |
| POST | `/api/share/:shareToken/claim` | 注册后一键加入 (QUESTION 类型 only) | 600ms | Toast + 重试 |

> Cache-Control: no-store · 后端不允许 GET 幂等暴露敏感数据

---

## §6 状态机

| State | 触发 | UI 表现 |
|---|---|---|
| `LOADING` | 进入页面 | 骨架屏 (banner + 模糊卡 + 灰色曲线) |
| `READY` | API 200 + signature_valid=true | 完整渲染（含 B6 sticky CTA） |
| `TOKEN_EXPIRED` | 410 | 挡板"这个分享已过期" + CTA "去看产品介绍" → P-LANDING |
| `TOKEN_INVALID` | 403 | 挡板"分享链接无效" |
| `TOKEN_REVOKED` | Bloom Filter 命中 | 挡板"分享者已撤销" |
| `EMPTY` | masked_payload 全空 | 显示"暂无可预览内容" + CTA |
| `ERROR` | 网络失败 | Toast + 重试按钮 |

---

## §7 跳转图

```
[入口]
  微信分享点击 ─┐
  扫码进入 ────┤──→ P-SHARED
  wb://s/:token ─┘
        │
        ├──[B6 "注册查看" CTA]──→ P00 (?redirect=/s/:token)
        ├──[B3 模糊覆盖层点击]──→ P00 (同上)
        ├──[B5 ttl 0]──────────→ P-LANDING (挡板自动跳转)
        ├──[TOKEN_EXPIRED]─────→ P-LANDING
        └──[关注公众号 P1]──────→ 外部微信授权页
```

---

## §8 AC 覆盖表

| AC ID | 验收点 | 涉及 Block | testid 验证点 |
|---|---|---|---|
| `AC-SHARED-001` | 顶部分享者头像 + 名字 + "分享了一道错题给你" | B2 | `sharer-banner-avatar` 可见 · `sharer-banner-text` 文本含 "分享了" |
| `AC-SHARED-002` | 题干前 12 字清晰，后续模糊（CSS filter blur 或字符替换） | B3 | `masked-question-stem-clear` 字符长 ≤ 12 · `masked-question-stem-blurred` 存在 |
| `AC-SHARED-003` | 学生答案 / 错因 / 个人记录全部覆盖"注册查看完整内容"覆盖层 | B3 | `masked-question-overlay` 可见 · 覆盖至少 3 个 region |
| `AC-SHARED-004` | MemoryCurve preview 模式 · 全部节点灰色 · 显示 "已被复习 N 次" | B4 | `memory-curve-preview-svg` 内 6 个 node fill = `rgba(0,0,0,0.16)` |
| `AC-SHARED-005` | 底部固定 CTA 蓝 pill · 主蓝色 · 全宽 | B6 | `upgrade-cta-fixed-btn` background = `--tkn-color-primary-DEFAULT` · width = 100% |
| `AC-SHARED-006` | TOKEN_EXPIRED → 挡板 + 跳 P-LANDING | — | `token-expired-screen` 可见 · `token-expired-cta` click 后路由 = `/welcome` |
| `AC-SHARED-007` | [AI 推测] 分享 → 注册 转化率 ≥15%（增长漏斗 KPI） | B6 | `anon_share_upgrade_cta` / `anon_share_view` 比值 |
| `AC-SHARED-008` | 写操作（评论 / 收藏）调用一律 403 | B3 | 模拟 fetch `/api/share/:token/comment` 应返回 403 ANONYMOUS_WRITE_FORBIDDEN |

---

## §9 异常路径

| 触发 | UI | 系统行为 |
|---|---|---|
| token 过期 (410) | 挡板"这个分享已过期" + 跳 P-LANDING | 不展示任何 payload |
| token 无效签名 (403) | 挡板"链接无效" | 不重试 |
| token 已撤销 (Bloom hit) | 挡板"分享者已撤销" | 通知监控 |
| 网络失败 | 骨架屏 + 重试按钮 | 最多重试 2 次 |
| 用户尝试拼接 /comment 等写接口 | 网关 403 ANONYMOUS_WRITE_FORBIDDEN | 不返回任何业务字段 |
| sharer 已删除账号 | 显示"分享者账号已注销" + 自动跳 P-LANDING | 1.5s 倒计时 |

---

## §10 埋点事件

| 事件名 | 触发 Block | 必带属性 |
|---|---|---|
| `anon_share_view` | 进入页面 + signature_valid=true | `device_fp`, `type`, `sharer_id_hash` |
| `anon_share_upgrade_cta` | B6 CTA 点击 | `device_fp`, `type`, `cta_position` |
| `anon_share_token_expired` | 进入但 410 | `device_fp`, `share_token_jti_hash` |
| `anon_share_forward` | 用户分享当前页（再分享） | `device_fp`, `target_app` |

---

## §11 性能预算

- TTI ≤ 1000ms
- LCP ≤ 1500ms（脱敏图 + 文字）
- API P95 ≤ 300ms
- CLS < 0.05
- 首屏渲染禁止任何 raw qid / student_id 出现在 DOM

---

## §12 A11y

- Landmarks: `<header role="banner">` (B2), `<main role="main">` (B3-B5), `<footer role="contentinfo">` (B6 sticky CTA)
- 焦点顺序: `sharer-banner-avatar` → `masked-question-overlay` → `upgrade-cta-fixed-btn`
- 屏幕阅读器朗读优先级: B2 优先 aria-label="分享者：小明 · 分享了一道数学错题"
- `prefers-reduced-motion`: 关闭模糊覆盖层入场 fade · 关闭曲线节点 stagger
- 模糊覆盖层 aria-label="注册后查看完整内容 · 点击跳转登录"

---

## §13 Mockup 锚定

- **权威**: `design/mockups/wrongbook/16_shared.html` (v1)
- **历史变体**: 旧版 v0 已归档
- **截图**: `design/system/screenshots/P-SHARED-v1-light.png`
- **反向锚定**: mockup HTML `<head>` 中含 `<meta name="design-spec" content="P-SHARED.spec.md@v1">`

---

## §14 Tokens 清单（grep 校验用）

```
L1 (Apple base):
  --tkn-color-white
  --tkn-color-primary-DEFAULT
  --tkn-color-text-primary
  --tkn-font-display
  --tkn-font-text
  --tkn-type-display-hero
  --tkn-type-sub-heading
  --tkn-type-body
  --tkn-type-body-emphasis
  --tkn-type-caption
  --tkn-type-micro
  --tkn-spacing-xs
  --tkn-spacing-sm
  --tkn-spacing-12
  --tkn-spacing-md
  --tkn-spacing-lg
  --tkn-spacing-xl
  --tkn-radius-sm
  --tkn-radius-md
  --tkn-radius-lg
  --tkn-radius-pill
  --tkn-radius-circle
  --tkn-motion-ease-standard
  --tkn-motion-dur-base

L2 (warmth · 全页 warm):
  --tkn-color-bg-light
  --tkn-color-card
  --tkn-color-bg-light
  --tkn-color-text-primary
  --tkn-color-text-secondary
  --tkn-color-sep
  --tkn-shadow-card-deep

EXCEPTION (subject · 仅 B3 4px 左色条):
  --tkn-subject-math
  --tkn-subject-physics
  --tkn-subject-chemistry
  --tkn-subject-english
```

---

## 模板使用清单

- [x] frontmatter 完整
- [x] §1-§3 完成
- [x] §4 SharedResp 与业务文档 §2A.3.2 / §10 一致
- [x] §6 状态机含 4 种 token 异常态
- [x] §8 AC 表 8 条；`AC-SHARED-007` 标 [AI 推测]
- [x] §10 埋点事件 anon_ 前缀
- [x] §14 Tokens 清单完整

---

## §15 实现边界（Implementation Boundaries · F 机制 · MUST）

> **背景**：mockup HTML 含 iPhone chrome 装饰（边框 / 状态栏 / home indicator / notch）· 这些是 mockup 展示用 · **不是**真实页面应实现的元素。
>
> **完整方案**：`docs/DESIGN-AUDIT-SYSTEM-PLAN.md` · F 机制 + H 机制 + design-reviewer agent

### 实施前检查（per CLAUDE.md §2.0 · 强制）

```bash
grep -nE 'data-mockup-chrome' design/mockups/wrongbook/_archive/<file>.html
```

### Chrome 边界表（通用 · 各页 mockup 实际含哪些以 `data-mockup-chrome` attr 为准）

| 元素 | 实现否 | 备注 |
|---|---|---|
| `[data-mockup-chrome="iphone-frame"]` (phone wrapper · 黑色边框 + radius:54px + box-shadow inset) | ❌ 不实现 | mockup 装饰 · 改 `width:100% min-height:100vh` |
| `[data-mockup-chrome="iphone-statusbar"]` (9:41 + 信号 + wifi + 电池) | ❌ 不实现 | 浏览器/系统原生提供 |
| `[data-mockup-chrome="iphone-homebar"]` (底部 home indicator 横条) | ❌ 不实现 | iOS chrome |
| `[data-mockup-chrome="iphone-notch"]` (顶部凹槽 / Dynamic Island) | ❌ 不实现 | iOS chrome |
| 其他（hero / nav / cta / list / cards / 等所有未标 chrome 的元素） | ✅ 实现 | 真实页面内容 · 100% viewport 自适应 |

### 自检（per CLAUDE.md §2.11 · 实施完 commit 前必做）

```bash
pnpm e2e:mockup-diff -- --grep <page_id>      # B 机制 · 容差 5%
pnpm e2e:vrt-multi   -- --grep <page_id>      # A 机制 · 4 viewport baseline
# 派 design-reviewer agent (subagent_type: design-reviewer · 输入 page_id)
```

verdict 处理：
- ✅ `PASS` → 可 commit
- ❌ `FAIL` → 修复循环 · 不交付（按 issues[].suggested_fix 改 · 重跑直到 PASS）
- ⚠️ `AMBIGUOUS` → ask user 决策 · 不假设
