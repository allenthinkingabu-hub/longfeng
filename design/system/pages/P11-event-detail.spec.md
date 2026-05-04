---
page_id: P11
name: 事件详情双形态
name_en: Event Detail (3 Morphs)
route_h5: /event/:eventId
route_miniprogram: pages/event/detail
deeplink: wb://event/:eventId
auth_state: authenticated
persona:
  - P1-K12
scenarios:
  - SC-05
  - SC-06
  - SC-09
mockup_canonical: design/mockups/wrongbook/11_event_detail.html
mockup_version: v2
last_reviewed: 2026-05-02
status: spec-draft
sprint: S4
---

# P11 · 事件详情双形态（实为三形态：STUDY / FAMILY / EXAM）

> **使用说明**：Sprint 4 辅助页之一。P11 是日历的"事件锚点页"，承载 SC-05（学习形态）/ SC-06（家庭形态）/ SC-09（考试形态）三个分支。
> **核心约束**：DESIGN.md §1 铁律 6（页面节奏二分）—— 顶部一条形态色 12px 条带 + 下方信息流卡片。条带本身视为 mood=warm 内的高光区，**不算 hero**（铁律 6 例外：信息流到底）。
> **共享与差异**：3 形态共享"事件名 / 日期 / 时间 / 描述 / 形态徽章 / 顶部返回 / 底部 CTA"骨架；差异仅在中段 `B3 关联区` 与底部 CTA 文案 + 颜色。
> **观察者态**：复用 STUDY/EXAM 视觉，FAMILY 编辑按钮置灰；分享只读态（来源 SC-09）EXAM 形态加 "分享人 妈妈" 行。

---

## §1 页面目的（why · 1 句话）

学生从日历或消息卡进入后，**用最少视觉切换看清"这是什么事 / 跟我哪些错题相关 / 我现在能做什么"**，并在该形态下一键完成主操作。

---

## §2 ASCII 布局图

```
┌─────────────────────────────────────┐
│  B0 顶部状态条 + 返回（"4月" / "通知"）│
├─────────────────────────────────────┤
│  ▓▓▓▓▓▓▓▓▓▓ B1 形态色条带 12px ▓▓▓▓ │   ← STUDY 学科色 / EXAM 红 / FAMILY 橙
├─────────────────────────────────────┤
│  B2 EventHeroCard                    │
│   · 形态徽章（"复习节点"/"考试"/"家庭")│
│   · 标题（事件名 · 一行）             │
│   · 日期 · 时间 · 时长（行内三段）    │
│   · 描述（最多 3 行 · 可展开）        │
├─────────────────────────────────────┤
│  B3 关联区（按形态切）                │
│   STUDY → QuestionListCard(关联错题) │
│       + MemoryCurve(预告 6 节点)     │
│   FAMILY → 备忘文字卡（参与人/重复规则）│
│   EXAM → 科目 chip + 地点行 + 分享人  │
├─────────────────────────────────────┤
│  B4 元信息行（提醒 / 重复 / 来源）     │
├─────────────────────────────────────┤
│  B5 底部 CTA（按形态切）               │
│   STUDY → "立即复习" 蓝 pill          │
│   FAMILY → "编辑" 灰 pill             │
│   EXAM → "加入日历" 蓝 pill           │
└─────────────────────────────────────┘
```

> 视觉节奏：信息流到底（无 hero 区分），mood=warm 全程；3 形态用同一 ASCII 骨架，B1 / B3 / B5 切色与切内容。

---

## §3 Block 清单（每块独立可实现单元）

| Block ID | 名称 | Section | Mood | Component | testid root | Tokens 用到 |
|---|---|---|---|---|---|---|
| `B0` | TopBar 顶部返回栏 | info | warm | (custom block · NavBar 变体) | `p11-top-bar` | `--tkn-color-bg-light`, `--tkn-color-text-primary`, `--tkn-color-primary-DEFAULT`, `--tkn-type-body` |
| `B1` | MorphRibbon 形态色条带 | info | warm | (custom block · 12px high) | `p11-morph-ribbon` | `--tkn-subject-math`, `--tkn-subject-physics`, `--tkn-subject-chemistry`, `--tkn-subject-english`, `--tkn-color-system-danger-DEFAULT`, `--tkn-color-encouragement-DEFAULT` |
| `B2` | EventHeroCard 事件卡 | info | warm | (custom block · Card 变体) | `p11-event-hero-card` | `--tkn-color-card`, `--tkn-color-text-primary`, `--tkn-color-text-secondary`, `--tkn-color-sep`, `--tkn-shadow-card-deep`, `--tkn-radius-lg`, `--tkn-type-tile-heading`, `--tkn-type-body`, `--tkn-type-caption` |
| `B3a` | RelatedQuestion (STUDY 形态) | info | warm | M.QuestionListCard + M.MemoryCurve | `p11-related-study` | `--tkn-color-card`, `--tkn-subject-{sub}`, `--tkn-color-mastery-mastered`, `--tkn-color-encouragement-DEFAULT`, `--tkn-radius-lg`, `--tkn-shadow-card-deep` |
| `B3b` | FamilyMemo (FAMILY 形态) | info | warm | (custom block · 文本卡) | `p11-related-family` | `--tkn-color-card`, `--tkn-color-encouragement-soft`, `--tkn-color-text-primary`, `--tkn-color-text-secondary`, `--tkn-radius-lg` |
| `B3c` | ExamMeta (EXAM 形态) | info | warm | (custom block) | `p11-related-exam` | `--tkn-color-card`, `--tkn-color-system-danger-DEFAULT`, `--tkn-color-text-primary`, `--tkn-color-text-secondary`, `--tkn-subject-math`, `--tkn-subject-physics`, `--tkn-subject-chemistry`, `--tkn-subject-english`, `--tkn-radius-lg`, `--tkn-radius-pill` |
| `B4` | MetaRow 元信息行 | info | warm | (custom block) | `p11-meta-row` | `--tkn-color-card`, `--tkn-color-text-secondary`, `--tkn-color-sep`, `--tkn-type-caption` |
| `B5` | BottomCta 形态 CTA | info | warm | L0 Button | `p11-bottom-cta` | `--tkn-color-primary-DEFAULT`, `--tkn-color-card`, `--tkn-color-text-primary`, `--tkn-color-text-on-dark`, `--tkn-radius-pill`, `--tkn-shadow-card-deep` |

> **fe-preflight 用法**：B3 三选一渲染（按 `relationType`）；其余 6 块共享。

---

## §4 数据契约（page-level interface）

```typescript
type RelationType = 'STUDY' | 'EXAM' | 'FAMILY';

interface EventDetailResp {
  eventId: string;
  relationType: RelationType;
  title: string;
  startAt: string;          // ISO timestamp
  endAt?: string;           // ISO timestamp（可选）
  durationMin?: number;     // 计算属性
  description?: string;     // 最多 200 字
  reminder?: { offsetMin: number; channel: 'PUSH' | 'IN_APP' };
  recurrence?: { rule: string; count?: number };  // RRULE
  source: 'SELF' | 'AI' | 'PARENT' | 'TEACHER';
  fromUser?: { name: string; role: 'PARENT' | 'TEACHER' | 'SELF' };

  // STUDY 形态独占
  study?: {
    subject: 'math' | 'physics' | 'chemistry' | 'english';
    questionId: string;
    questionStem: string;
    thumbnailUrl: string;
    nodeId: string;
    tLevel: 'T0' | 'T1' | 'T2' | 'T3' | 'T4' | 'T5' | 'T6';
    nodes: Array<{ tLevel: string; status: 'done' | 'now' | 'future'; dueAt: string }>;
  };

  // EXAM 形态独占
  exam?: {
    subject: 'math' | 'physics' | 'chemistry' | 'english';
    location: string;
    countdownDays: number;
  };

  // FAMILY 形态独占
  family?: {
    participants: string[];
    note: string;
  };
}
```

> 类型不一致直接 fail。

---

## §5 API 触点

| Method | Path | 用途 | P95 预算 | 失败降级 |
|---|---|---|---|---|
| GET | `/api/calendar/events/{eventId}` | 主聚合接口 · 一次拿 envelope + 形态字段 | 600ms | ERROR 态 |
| GET | `/api/wb/questions/{qid}` | STUDY 形态并发拉题目摘要 | 400ms | 题目区显示骨架不阻断 |
| GET | `/api/review/nodes/{nid}` | STUDY 形态并发拉节点态 | 400ms | 曲线区显示 fallback "节点已取消" |
| POST | `/api/review/nodes/{nid}/open` | "立即复习" CTA → P08 | 500ms | toast "请稍后" |
| PATCH | `/api/calendar/events/{eventId}` | FAMILY 编辑保存（参考 SC-06 步 7） | 500ms | toast "保存失败 重试" |
| POST | `/api/calendar/events/{eventId}/subscribe` | EXAM "加入日历" | 400ms | toast "加入失败" |
| PATCH | `/api/events/{eventId}/ack` | EXAM 形态进入即标记已查看（SC-09 步 4） | 200ms | 仅本地态 |

---

## §6 状态机

| State | 触发 | UI 表现 |
|---|---|---|
| `LOADING_GENERIC` | 进入 + relationType 未知 | 整页骨架（条带 + 卡 + CTA 骨架） |
| `LOADING_STUDY` / `LOADING_FAMILY` / `LOADING_EXAM` | 拿到 envelope 后并发拉子接口 | B3 区骨架，其余已渲染 |
| `READY_STUDY` / `READY_FAMILY` / `READY_EXAM` | 全部 200 | 完整渲染 |
| `EXIT_REVIEW` | STUDY "立即复习" 触发 | 跳 P08，本页 fade out |
| `EDIT` | FAMILY "编辑" 打开抽屉 | Bottom Sheet 上滑 |
| `SAVING` | FAMILY 保存中 | CTA spinner |
| `ERROR` | 关键 API 非 2xx | 整页错误态 + 重试 |
| `CANCELLED_NODE` | STUDY 形态 nodeId 返回 404（被另一端 FORGOT 取消） | B3 占位 "该复习节点已取消，下次排期已更新" + CTA "查看新排期" |

---

## §7 跳转图

```
[入口]
  P10 cell tap                 ──┐
  P-HOME 消息卡                  ┤
  P12 通知 tap                   ┤── → P11
  深链 wb://event/:eventId       ┤
  分享链 P-SHARED                ┘
                                │
        ├──[B0 返回]──→ 来源页（from=CAL → P10 / from=HOME → P-HOME / from=NOTIF → P12）
        ├──[B5 STUDY 立即复习]──→ P08 (跨越 P07)
        ├──[B5 FAMILY 编辑]──→ Bottom Sheet (内嵌不离开)
        ├──[B5 EXAM 加入日历]──→ Toast 成功 + B5 文案变"已加入"
        └──[B3a 题目缩略图 tap (STUDY)]──→ P06 错题详情
```

---

## §8 AC 覆盖表（与 business-analysis.yml 双向锚定）

| AC ID | 验收点 | 涉及 Block | testid 验证点 |
|---|---|---|---|
| `AC-P11-001` | STUDY 形态：B1 条带颜色 = 学科色 token · B2 形态徽章文字 = "复习节点" | B1, B2 | `p11-morph-ribbon` 含 `data-relation-type="STUDY"` 且背景色命中 `--tkn-subject-{sub}`；`p11-event-hero-card-badge` 文本 = "复习节点" |
| `AC-P11-002` | FAMILY 形态：B1 条带 = encouragement 橙 · B5 CTA 文字 = "编辑" 灰 pill | B1, B5 | `p11-morph-ribbon[data-relation-type="FAMILY"]` 背景色命中 `--tkn-color-encouragement-DEFAULT`；`p11-bottom-cta-edit` 可见 |
| `AC-P11-003` | EXAM 形态：B1 条带 = system-danger 红 · B5 CTA = "加入日历" 蓝 pill | B1, B5 | `p11-morph-ribbon[data-relation-type="EXAM"]` 背景命中 `--tkn-color-system-danger-DEFAULT`；`p11-bottom-cta-add-calendar` 可见 |
| `AC-P11-004` | STUDY 形态：B3a 渲染 QuestionListCard + MemoryCurve · 不渲染 FAMILY/EXAM 节点 | B3a | `p11-related-study` 可见 + `p11-related-family` / `p11-related-exam` 不在 DOM |
| `AC-P11-005` | FAMILY 形态：B3b 渲染备忘文本卡 · 不渲染曲线 | B3b | `p11-related-family` 可见 + `p11-related-study-memory-curve` 不在 DOM |
| `AC-P11-006` | EXAM 形态：B3c 渲染 学科 chip + 地点 + 倒计时 · 来源="妈妈分享" 行 | B3c | `p11-related-exam-subject-chip`, `p11-related-exam-location`, `p11-related-exam-countdown`, `p11-related-exam-from` 各 1 |
| `AC-P11-007` | STUDY 立即复习 tap → 跳 P08 + 埋点 `event_review_now{nid}` (`SC-05` 步 7) | B5 | `p11-bottom-cta-review-now` click → 路由 `/review/exec/:nid` |
| `AC-P11-008` | 顶部返回按钮文案随 from 变化 (`SC-06`, `SC-09`) | B0 | `p11-top-bar-back` 文本 ∈ {"4月", "首页", "通知"} |
| `AC-P11-009` | STUDY 节点被另一端取消 → B3a 显示 "该复习节点已取消" 占位 + CTA "查看新排期" (SC-05 TC-05.04) | B3a | `p11-related-study-cancelled` 可见 |
| `AC-P11-010` | [AI 推测] EXAM 形态进入立即调 `PATCH /api/events/:eid/ack` 标记已查看（SC-09 步 4） | B2 | network log 含 ack PATCH（验收时通过 MSW 断言） |

> **fe-accept-mock 用法**：B 轨同时跑 3 个 fixture（STUDY/FAMILY/EXAM）；缺一个变体即 fail。
> **AC 缺失处理**：业务文档 §2A.4 P11 仅给"双形态"概念，本 spec 拆为 3 形态（STUDY/EXAM/FAMILY）以匹配业务文档 §2B SC-05/06/09 实际。AC-P11-010 标 `[AI 推测]` 供业务方 review。

---

## §9 异常路径

| 触发 | UI | 系统行为 |
|---|---|---|
| eventId 不存在 (404) | 整页占位 "事件不存在或已删除" + CTA 返回 | 不消费埋点 ack |
| 观察者会话过期 (403) | 顶部 banner 红 + 跳 P-LANDING | JWT 黑名单生效 |
| STUDY nodeId 404 | B3a 占位 + "查看新排期"链接 → P05 qid | 不阻塞其他区域 |
| FAMILY 编辑保存失败 | toast "保存失败 · 重试" | 抽屉保持打开 |
| EXAM 加入日历失败 (subscribe 4xx) | toast "加入失败" + B5 文案不变 | 重试按钮保留 |
| 跨设备并发删除 | 进入 P11 立即 404 → 兜底"已被另一端删除" | 落用户感知 |

---

## §10 埋点事件

| 事件名 | 触发 Block | 必带属性 |
|---|---|---|
| `calendar_event_tap` | 入口 from=CAL | `eventId, relationType, cellIndex` |
| `event_view` | B2 渲染完成 | `variant=STUDY\|FAMILY\|EXAM, nid?, eventId` |
| `event_review_now` | B5 STUDY CTA | `nid, T` |
| `event_edit_open` | B5 FAMILY CTA | `eventId` |
| `event_edit_save` | FAMILY 抽屉保存 | `eventId, fields_changed[]` |
| `event_add_calendar` | B5 EXAM CTA | `eventId, subject` |
| `event_exit` | B0 返回 | `returnTo=calendar\|home\|notif` |
| `obs_event_readonly_view` | 观察者进入 | `eventId, student_id_hash` |

> 所有事件经 `packages/analytics` 包；`obs_*` 事件携带 `student_id_hash`。

---

## §11 性能预算

- TTI ≤ 700ms（envelope 200 即视为 TTI）
- LCP ≤ 600ms（B2 EventHeroCard 视为 LCP）
- CLS < 0.05
- API P95 ≤ 600ms（envelope）+ 400ms（子接口并发）
- LOADING_X → READY_X < 250ms 渐入
- B5 CTA 点击响应 < 100ms 视觉反馈（scale 0.97）

---

## §12 A11y

- Landmarks: `<main role="main">` 包裹 B1-B5；B0 用 `<nav role="navigation">`
- 焦点顺序: 返回 → 形态徽章 → 标题 → 描述 → 关联区第一个可交互项 → 元信息 → 底部 CTA
- 屏幕阅读器朗读优先级: B2 标题 + 形态徽章合并 `aria-label="复习节点 · 韦达定理 D7 回顾"`；B5 CTA `aria-label` 完整化（"立即复习这道二次函数题"）
- `prefers-reduced-motion`: 关闭 B2 卡片入场 fade；编辑抽屉 slide 改为瞬切

---

## §13 Mockup 锚定

- **权威**: `design/mockups/wrongbook/11_event_detail.html` (v2)
- **历史变体**: 早期 v1（仅 STUDY 形态）已废弃，本次 spec 触发 v2 三形态同框
- **截图**: `design/system/screenshots/P11-v2-{study,family,exam}.png`（fe-preflight 自动生成 3 个）
- **反向锚定**: mockup HTML `<head>` 含 `<meta name="design-spec" content="P11-event-detail.spec.md@v2">`

---

## §14 Tokens 清单（grep 校验用）

> mockup HTML 的 CSS 中**每一处** hex / rgb / px 数值必须能映射回这张清单中的 token。grep 不命中 = lint fail。

```
L1 (Apple base):
  --tkn-color-primary-DEFAULT
  --tkn-color-primary-dark
  --tkn-color-system-danger-DEFAULT
  --tkn-color-system-info-DEFAULT
  --tkn-color-text-on-dark
  --tkn-font-display
  --tkn-font-text
  --tkn-type-display-hero
  --tkn-type-tile-heading
  --tkn-type-sub-heading
  --tkn-type-body
  --tkn-type-body-emphasis
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
  --tkn-shadow-focus
  --tkn-motion-duration-fast
  --tkn-motion-duration-base
  --tkn-motion-ease-standard

L2 (warmth · 整页 mood=warm):
  --tkn-color-bg-light
  --tkn-color-card
  --tkn-color-bg-light
  --tkn-color-text-primary
  --tkn-color-text-secondary
  --tkn-color-sep
  --tkn-color-encouragement-DEFAULT
  --tkn-color-encouragement-soft
  --tkn-shadow-card-deep

L3 (celebration · 仅 STUDY 形态 MemoryCurve 引用 mastered):
  --tkn-color-mastery-mastered

EXCEPTION (subject):
  --tkn-subject-math
  --tkn-subject-physics
  --tkn-subject-chemistry
  --tkn-subject-english
```

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
