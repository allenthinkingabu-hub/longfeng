---
page_id: P-OBSERVER
name: 只读观察者会话
name_en: Observer Session
route_h5: /observer
route_miniprogram: pages/observer/home
deeplink: wb://observer/:code
auth_state: observer
persona:
  - P4-Parent
  - P5-Teacher
scenarios:
  - SC-15
mockup_canonical: design/mockups/wrongbook/18_observer.html
mockup_version: v1
last_reviewed: 2026-05-02
status: spec-draft
sprint: S5
---

# P-OBSERVER · 只读观察者会话

> **使用说明**：本 spec 对应 DESIGN.md §4.2 的 14 段标准结构。匿名态 P1 页面，业务文档 §2A.3.2 与 §2B.16 SC-15 为权威来源。AI 推测条目均显式标注。

---

## §1 页面目的（why · 1 句话）

让家长/班主任用一次性邀请码进入只读观察视图，3 秒内看到学生最近 7 天进度，并被 banner 永久提醒"你只能看，不能动"，永不污染学生数据。

---

## §2 ASCII 布局图

```
┌─────────────────────────────────────┐
│  [statusbar · mood=warm]
│  [observer-banner · mood=warm]
│   ┌─ system-info 黄底 banner ───────────────┐
│   │ ⓘ 你正在以观察者身份查看 @张* · 仅可读   │
│   │                              [撤销 →]    │
│   └──────────────────────────────────────────┘
│  [student-summary-card · mood=warm]
│   ┌────────────────────────────┐
│   │ Avatar · @张* · PARENT      │
│   │ 近 7 日已复习 18 题          │
│   │ 掌握度 68%                   │
│   │ 学科分布：数 8 · 物 5 · 化 3 │
│   └────────────────────────────┘
├─────────────────────────────────────┤
│  [recent-question-list · mood=warm]
│   · 最近错题时间线（QuestionListCard readonly）
│     ┌─ readonly watermark "仅可读" 半透明覆盖 ─┐
│     │ [4px 学科色] 题干前 50 字 · KP chips     │
│     │ T2 节点 · 下次到期 2026-05-04            │
│     │ ⊘ 立即复习（按钮置灰，不可点）           │
│     └────────────────────────────────────────┘
│   ... ×N 条
└─────────────────────────────────────┘
```

---

## §3 Block 清单（每块独立可实现单元）

| Block ID | 名称 | Section | Mood | Component | testid root | Tokens 用到 |
|---|---|---|---|---|---|---|
| `B1` | StatusBar | hero | warm | (atom) | `observer-statusbar` | `--tkn-color-text-primary` |
| `B2` | ReadOnlyBanner | hero | warm | (atom · Banner system-info) | `observer-banner` | `--tkn-color-system-info-DEFAULT`, `--tkn-color-card`, `--tkn-color-text-primary`, `--tkn-radius-md`, `--tkn-spacing-md`, `--tkn-type-caption-bold` |
| `B3` | StudentSummaryCard | info | warm | (atom · Card · 含 Avatar) | `observer-student-summary` | `--tkn-color-card`, `--tkn-color-text-primary`, `--tkn-color-text-secondary`, `--tkn-color-sep`, `--tkn-shadow-card-deep`, `--tkn-radius-lg`, `--tkn-type-display-hero`, `--tkn-type-body`, `--tkn-spacing-md`, `--tkn-spacing-lg`, `--tkn-subject-math`, `--tkn-subject-physics`, `--tkn-subject-chemistry`, `--tkn-subject-english` |
| `B4` | RecentQuestionList | info | warm | M.QuestionListCard(variant=readonly)[] | `observer-question-list` | `--tkn-color-bg-light`, `--tkn-color-card`, `--tkn-color-text-primary`, `--tkn-color-text-secondary`, `--tkn-color-bg-light`, `--tkn-color-sep`, `--tkn-shadow-card-deep`, `--tkn-radius-lg`, `--tkn-radius-sm`, `--tkn-subject-math`, `--tkn-subject-physics`, `--tkn-subject-chemistry`, `--tkn-subject-english`, `--tkn-color-mastery-mastered`, `--tkn-color-encouragement-DEFAULT` |
| `B5` | ReadOnlyWatermark | info | warm | (overlay) | `observer-readonly-watermark` | `--tkn-color-text-secondary`, `--tkn-type-caption` |
| `B6` | RevokeRedirectModal | overlay | warm | (atom · Modal) | `observer-revoke-modal` | `--tkn-color-system-danger-DEFAULT`, `--tkn-color-card`, `--tkn-color-primary-DEFAULT`, `--tkn-radius-lg`, `--tkn-shadow-card-deep`, `--tkn-type-body` |

> **fe-preflight 用法**：把 mockup HTML 切成这些块；fe-builder 按块逐个实现并跑 lint。
> **铁律 5 备注**：B3/B4 中学科色 ONLY 用于 chip / 4px 左色条 / icon 三种用法，禁大面积色块。

---

## §4 数据契约（page-level interface）

```typescript
// POST /api/observer/exchange 请求
interface ObserverExchangeReq {
  inviteCode: string;        // 6 位大写字母+数字
  purpose?: 'PARENT_VIEW' | 'TEACHER_VIEW';
}
interface ObserverExchangeResp {
  observerJwt: string;       // role=OBSERVER · scope=READ
  studentIdHash: string;
  role: 'PARENT' | 'TEACHER';
  ttlSeconds: number;        // 30d=2592000 / 90d=7776000
  maskedNick: string;        // "张*"
}

// GET /api/observer/overview 响应
interface ObserverOverviewResp {
  student: {
    nickMasked: string;      // "张*"
    avatarSeed: string;      // monogram 字母
    role: 'PARENT' | 'TEACHER';
  };
  weeklyReport: {
    rangeDays: 7;
    reviewedCount: number;
    masteryRate: number;     // 0-1
    subjectDist: Array<{
      subject: 'math' | 'physics' | 'chemistry' | 'english';
      count: number;
    }>;
  };
}

// GET /api/observer/timeline 响应
interface ObserverTimelineResp {
  items: Array<{
    qid: string;
    subject: 'math' | 'physics' | 'chemistry' | 'english';
    stemSnippet: string;     // 前 50 字（脱敏后）
    kpList: string[];        // 最多 3 个
    nodeStage: 0 | 1 | 2 | 3 | 4 | 5 | 6;
    nextDueAt: string;       // ISO
    mastery: 'forgot' | 'partial' | 'mastered';
    thumbnailMaskedUrl: string; // 始终脱敏，不返回原图
  }>;
  hasMore: boolean;
  cursor?: string;
}
```

> 类型不一致直接 fail。原图字段（`originalImageUrl`、`studentEmail`、`chatId`）不允许出现在 observer 接口响应。

---

## §5 API 触点

| Method | Path | 用途 | P95 预算 | 失败降级 |
|---|---|---|---|---|
| POST | `/api/observer/exchange` | 邀请码兑换 OBSERVER JWT | ≤ 400ms | 410 INVITE_EXPIRED → 挡板页"邀请码已过期，请联系学生重发" |
| GET | `/api/observer/overview` | 学生周报聚合（脱敏） | ≤ 600ms | 部分降级：subjectDist 空时仅显示总数 |
| GET | `/api/observer/timeline?limit=20&cursor=` | 最近错题时间线（脱敏） | ≤ 600ms | 上拉加载失败 toast；首屏失败 → empty state |

> 网关强制 `OBSERVER` JWT `scope=READ`；任何 `POST/PUT/DELETE/PATCH`（除 `/observer/exchange`）直接 403。

---

## §6 状态机

| State | 触发 | UI 表现 |
|---|---|---|
| `LOADING` | 进入页面 | banner skeleton + summary 骨架 + 列表 3 行骨架 |
| `EXCHANGE_PENDING` | 邀请码兑换中 | 整页 spinner "正在准备观察视图..." |
| `READY` | overview + timeline 200 | 完整渲染（banner + summary + 列表）|
| `EMPTY` | timeline.items 空 | empty 插画 + 文案 "学生最近还没有错题" |
| `INVITE_EXPIRED` | exchange 410 | 挡板页 + CTA "联系学生重发" |
| `INVITE_REVOKED` | exchange 403 / overview 403 | 同上挡板页 |
| `OBSERVER_REVOKED` | 任何 API 返回 403 OBSERVER_REVOKED | RevokeRedirectModal 弹窗 → 跳 P-LANDING |
| `ERROR` | 网络/服务降级 | 顶部黄条 + 列表显示骨架 |

---

## §7 跳转图

```
[入口]
  扫码 wb://observer/:code ─┐
  邀请链接点击 ─────────────┤──→ P-OBSERVER (exchange)
  P13 学生页"管理观察者"────┘ [AI 推测]
        │
        ├──[tap 时间线条目]──→ P06 (只读模式 · observer-token header)
        ├──[tap 撤销链接]────→ P-LANDING (主动退出)
        ├──[INVITE_EXPIRED]──→ 挡板页
        └──[OBSERVER_REVOKED]→ P-LANDING (强制)
```

---

## §8 AC 覆盖表（与 business-analysis.yml 双向锚定）

| AC ID | 验收点 | 涉及 Block | testid 验证点 |
|---|---|---|---|
| `AC-OBS-001` | banner 文案包含"观察者身份"+"仅可读"+ 学生脱敏昵称 | B2 | `observer-banner-text` 文本匹配 `观察者身份查看.*\*.*仅可读` |
| `AC-OBS-002` | banner 右侧"撤销"链接可点击 → 跳 P-LANDING 并清除 OBSERVER JWT | B2 | `observer-banner-revoke-link` click 后 localStorage 无 observer JWT 且路由 = `/landing` |
| `AC-OBS-003` | 学生摘要卡显示 "近 7 日已复习 N 题 / 掌握度 X%" | B3 | `observer-student-summary-reviewed-count` + `observer-student-summary-mastery-rate` 文本匹配 weekly_report 数据 |
| `AC-OBS-004` | QuestionListCard readonly 变体所有按钮置灰且不可点击 | B4 | `observer-question-list-1-action-btn` `aria-disabled=true` · click 后无路由变化 |
| `AC-OBS-005` | 列表卡覆盖只读 watermark "仅可读"，opacity ≤ 0.7 | B5 | `observer-readonly-watermark` 存在且 computed opacity ≤ 0.7 |
| `AC-OBS-006` | 任何 API 返回 403 OBSERVER_REVOKED 时弹窗后跳 P-LANDING | B6 | mock 注入 403 → `observer-revoke-modal` 出现 → 关闭后路由 = `/landing` |
| `AC-OBS-007` | timeline 数据中不存在 originalImageUrl / studentEmail / chatId 字段 | B4 | network response body JSON.stringify 不包含上述 key |
| `AC-OBS-008` | [AI 推测] 网关侧 OBSERVER JWT 写请求被 403 拦截 | (gateway) | mock 提交 POST /api/wb/questions → 网关返 403 OBSERVER_FORBIDDEN_WRITE |
| `AC-OBS-009` | [AI 推测] 邀请码过期 → 挡板页 + CTA "联系学生重发" 而非自动跳转 | B6 | mock 注入 410 → `observer-revoke-modal-cta` 文案包含"联系学生重发" |
| `AC-OBS-010` | tap 列表条目跳 P06 时携带 observer-token header | B4 | `observer-question-list-1` click → next request headers 含 `observer-token` |

> **fe-accept-mock 用法**：用这张表逐条断言；缺 testid = 验收 fail。
> AC-OBS-001..007/010 来自业务文档 §2B.16 SC-15 的 F01-F07 + 关键断言点；AC-OBS-008/009 标 `[AI 推测]` 待业务方 review。

---

## §9 异常路径

| 触发 | UI | 系统行为 |
|---|---|---|
| `INVITE_EXPIRED` | 挡板页 + CTA "联系学生重发邀请" | 不路由，留在挡板页 |
| `INVITE_REVOKED` | 同上 | 同上 |
| 学生撤销 → 任意 API 403 | RevokeRedirectModal "学生已撤销你的查看权限" | 关闭后路由 P-LANDING + 清 JWT |
| 网络断开 | 顶部黄条 system-info | 退避重试 1 次 |
| 观察者尝试写操作（误操作） | toast "观察者不可编辑 · 如需操作请联系 @张*" | 网关 403 + 埋点 `obs_write_blocked` |
| 观察者尝试看原图 | 缩略图占位 | 接口响应 thumbnailMaskedUrl 即终态，不放行点击放大 |
| TTL 到期（30d/90d） | RevokeRedirectModal "会话已过期" | 同 OBSERVER_REVOKED 路径 |

---

## §10 埋点事件

| 事件名 | 触发 Block | 必带属性 |
|---|---|---|
| `obs_enter` | bootstrap | `code_hash`, `entry_source` |
| `obs_exchange_success` | B2 (兑换成功后) | `student_id_hash`, `role`, `ms` |
| `obs_overview_view` | B3 | `student_id_hash`, `role`, `ms` |
| `obs_timeline_tap` | B4 | `student_id_hash`, `subject`, `qid_hash` |
| `obs_detail_readonly_view` | (跨页 P06) | `student_id_hash`, `qid_hash` |
| `obs_write_blocked` | (网关侧) | `student_id_hash`, `action` |
| `obs_revoked_by_student` | B6 | `student_id_hash`, `revoked_at_ms` |
| `obs_invite_expired` | B6 | `code_hash` |

> 所有事件经 `packages/analytics`，必须携带 `student_id_hash`，禁带原始 PII（学生 email / chat_id / 真实昵称）。
> 观察者埋点写独立 ClickHouse 表 `obs_events`，不进入学生维度统计。

---

## §11 性能预算

- TTI ≤ 1200ms（banner 优先渲染）
- LCP ≤ 1500ms（学生摘要卡为 LCP 元素）
- CLS < 0.05（列表卡固定高度 96px）
- API P95：`/observer/exchange` ≤ 400ms · `/observer/overview` ≤ 600ms · `/observer/timeline` ≤ 600ms
- 撤销实时性：学生撤销后 ≤ 1s 内 redis 黑名单生效
- 骨架屏与数据切换 < 100ms 渐入

---

## §12 A11y

- Landmarks: `<header role="banner">` (banner) · `<main role="main">` · `<section role="region" aria-label="学生周报">` · `<section role="feed" aria-label="最近错题时间线（只读）">`
- 焦点顺序: `observer-banner-revoke-link` → `observer-question-list-1` → `observer-question-list-2` → ... → 状态栏
- 屏幕阅读器优先级: 进入页面立即朗读 "你正在以家长身份查看张星的错题本，仅可读"（`aria-live="assertive"`，确保用户首先听到角色声明）
- `prefers-reduced-motion`: 关闭列表渐入 stagger，立即显示
- readonly watermark `aria-hidden="true"`（装饰）；按钮 `aria-disabled="true"` + tooltip "观察者不可操作"
- banner 类型 `role="status"` + `aria-label="只读观察者横幅"`

---

## §13 Mockup 锚定

- **权威**: `design/mockups/wrongbook/18_observer.html` (v1)
- **历史变体**: `design/mockups/wrongbook/_archive/18_observer_v0.html`（v0 大色块紫底，违反铁律 5 已废弃）
- **截图**: `design/system/screenshots/P-OBSERVER-v1-light.png`（fe-preflight 自动生成）
- **反向锚定**: mockup HTML `<head>` 中含 `<meta name="design-spec" content="P-OBSERVER.spec.md@v1">`

---

## §14 Tokens 清单（grep 校验用）

> mockup HTML 的 CSS 中**每一处** hex / rgb / px 数值必须能映射回这张清单中的 token。grep 不命中 = lint fail。

```
L1 (Apple base):
  --tkn-color-primary-DEFAULT
  --tkn-color-primary-link
  --tkn-color-system-info-DEFAULT
  --tkn-color-system-danger-DEFAULT
  --tkn-color-text-on-dark
  --tkn-font-display
  --tkn-font-text
  --tkn-type-display-hero
  --tkn-type-sub-heading
  --tkn-type-body
  --tkn-type-caption
  --tkn-type-caption-bold
  --tkn-spacing-2
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
  --tkn-shadow-card
  --tkn-motion-ease-standard
  --tkn-motion-dur-base

L2 (warmth · 仅 mood=warm/celebrate 区段使用):
  --tkn-color-bg-light
  --tkn-color-card
  --tkn-color-bg-light
  --tkn-color-text-primary
  --tkn-color-text-secondary
  --tkn-color-sep
  --tkn-color-encouragement-DEFAULT
  --tkn-color-encouragement-soft
  --tkn-shadow-card-deep

L3 (celebration · 本页仅作三态色复用 · 无庆祝时刻):
  --tkn-color-mastery-forgot
  --tkn-color-mastery-partial
  --tkn-color-mastery-mastered

EXCEPTION (subject):
  --tkn-subject-math
  --tkn-subject-physics
  --tkn-subject-chemistry
  --tkn-subject-english
```

---

## 模板使用清单

- [x] frontmatter 全部字段填写
- [x] §1 一句话目的写完
- [x] §2 ASCII 布局图绘制
- [x] §3 Block 清单 testid 与 §8 AC 表交叉对账
- [x] §4 TS interface 与业务文档 §10.10 观察者会话一致
- [x] §5 API 路径与业务文档 §2A.3.2 / §2B.16 一致
- [x] §6 状态机覆盖 LOADING/READY/EMPTY/ERROR + 业务专用兑换/撤销态
- [x] §7 跳转图入口出口都列全
- [x] §8 AC 表 10 条；缺失的标 `[AI 推测]`（2 条）
- [x] §10 埋点事件名与业务文档 §2A.8 字典一致
- [x] §14 Tokens 清单与本页用到的 token 完全一致

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
