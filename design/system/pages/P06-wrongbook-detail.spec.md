---
page_id: P06
name: 错题详情
name_en: Wrongbook Detail
route_h5: /wrongbook/:qid
route_miniprogram: pages/wrongbook/detail
deeplink: wb://wrongbook/:qid
auth_state: authenticated
persona:
  - P1-K12
scenarios:
  - SC-05
  - SC-06
  - SC-10
mockup_canonical: design/mockups/wrongbook/06_wrongbook_detail.html
mockup_version: v1
last_reviewed: 2026-05-02
status: spec-locked
sprint: S2
---

# P06 · 错题详情

> **使用说明**：本 spec 对应 DESIGN.md §4.2 的 14 段标准结构。Sprint 2 单题档案页。
> **核心约束**：DESIGN.md §1 铁律 6（节奏二分）—— 单段信息流到底，无 hero（顶部原图卡作为视觉锚点而非 hero）。

---

## §1 页面目的（why · 1 句话）

学生在单题"档案页"中看到这道题的全部学习历史（分析 / 复习记录 / 变式），并随时点"立即复习"开练。

---

## §2 ASCII 布局图

```
┌─────────────────────────────────────┐
│  [< 返回]            [⋯ 更多]        │  ← NavBar
├─────────────────────────────────────┤
│  B1 OriginImageCard 原图卡          │  ← mood=warm 暖米白底
│   ┌─────────────────────────┐       │
│   │   错题原图（170px 高）    │       │
│   │   [点击放大 ⤢]            │       │
│   └─────────────────────────┘       │
│                                     │
│  B2 SegmentTab                      │
│   [分析 ▼] [复习记录] [变式]        │
├─────────────────────────────────────┤
│   ▼ 当 tab=分析                     │
│  B3 AIBriefCard                     │
│   ┌───────────────────────┐         │
│   │ │ 错因（红条 4px）    │         │
│   │   ▌ AI 简报 markdown  │         │
│   │   KP chips · ★ 难度    │         │
│   └───────────────────────┘         │
│                                     │
│   ▼ 当 tab=复习记录                 │
│  B4 MemoryCurve（progress 变体）    │
│   遗忘曲线 + 6 节点                  │
│  B5 RecordsTimeline 时间线          │
│   T0 ─┬─ T1 ─┬─ T2 ─┬─ ...          │
│       2026-04-30 已掌握 ✓           │
│                                     │
│   ▼ 当 tab=变式                     │
│  B6 VariantsEmpty "敬请期待"        │
│                                     │
│  B7 RadarChart 五维能力雷达         │
│   运算/概念/方法/速度/准确          │
│                                     │
├─────────────────────────────────────┤
│  B8 BottomActions                   │  ← sticky 底部
│   [归档（灰）] [立即复习（蓝）]      │
└─────────────────────────────────────┘
```

---

## §3 Block 清单（每块独立可实现单元）

| Block ID | 名称 | Section | Mood | Component | testid root | Tokens 用到 |
|---|---|---|---|---|---|---|
| `B1` | OriginImageCard 原图卡 | info | warm | (custom block) | `p06-origin-image` | `--tkn-color-card`, `--tkn-shadow-card-deep`, `--tkn-radius-lg`, `--tkn-color-text-secondary` |
| `B2` | SegmentTab 分段 tab | info | warm | (custom block) | `p06-segment-tab` | `--tkn-color-bg-light`, `--tkn-color-card`, `--tkn-color-primary-DEFAULT`, `--tkn-color-text-primary`, `--tkn-radius-md` |
| `B3` | AIBriefCard AI 简报卡 | info | warm | (custom block) | `p06-ai-brief` | `--tkn-color-card`, `--tkn-color-mastery-forgot`, `--tkn-color-text-primary`, `--tkn-color-bg-light`, `--tkn-radius-lg`, `--tkn-shadow-card-deep` |
| `B4` | MemoryCurve 遗忘曲线 | info | warm | M.MemoryCurve(variant=progress) | `memory-curve` | `--tkn-color-mastery-mastered`, `--tkn-color-mastery-partial`, `--tkn-color-encouragement-DEFAULT`, `--tkn-color-text-secondary` |
| `B5` | RecordsTimeline 复习时间线 | info | warm | (custom block) | `p06-records-timeline` | `--tkn-color-card`, `--tkn-color-mastery-mastered`, `--tkn-color-mastery-forgot`, `--tkn-color-sep`, `--tkn-radius-md` |
| `B6` | VariantsEmpty 变式空态 | info | warm | (custom block) | `p06-variants-empty` | `--tkn-color-card`, `--tkn-color-text-secondary`, `--tkn-color-bg-light` |
| `B7` | RadarChart 五维能力雷达 | info | warm | (custom block) | `p06-radar-chart` | `--tkn-color-card`, `--tkn-color-primary-DEFAULT`, `--tkn-color-text-secondary`, `--tkn-radius-lg`, `--tkn-shadow-card-deep` |
| `B8` | BottomActions sticky CTA | info(sticky) | warm | (custom block) | `p06-bottom-actions` | `--tkn-color-card`, `--tkn-color-sep`, `--tkn-color-primary-DEFAULT`, `--tkn-color-text-secondary`, `--tkn-radius-pill`, `--tkn-shadow-hero-card` |

---

## §4 数据契约（page-level interface）

```typescript
interface WrongbookDetailResp {
  question: {
    qid: string;
    subject: 'math' | 'physics' | 'chemistry' | 'english';
    stem: string;
    formula?: string;
    myAnswer?: string;
    correctAnswer: string;
    thumbnailUrl: string;
    originImageUrl: string;       // 高清原图
    difficulty: 1 | 2 | 3 | 4 | 5;
    knowledgePoints: string[];
    createdAt: string;
    archived: boolean;
  };
  analysis: {
    reasonMarkdown: string;       // AI 错因简报
    stepsMarkdown: string;        // 3 步解法
    confidence: number;           // 0-100
    modelInfo: { model: string; version: string };
  };
  nodes: Array<{
    nid: string;
    tLevel: 'T0'|'T1'|'T2'|'T3'|'T4'|'T5'|'T6';
    status: 'done' | 'now' | 'future';
    dueAt?: string;
    gradedAt?: string;
    grade?: 'forgot' | 'partial' | 'mastered';
  }>;                              // length = 7（T0-T6）
  records: Array<{
    rid: string;
    timestamp: string;
    grade: 'forgot' | 'partial' | 'mastered';
    durationSec: number;
    tLevel: string;
  }>;
  radar: {
    axes: ['运算', '概念', '方法', '速度', '准确'];
    values: [number, number, number, number, number]; // 0-100
  };
  variants: Array<{ vid: string; stem: string }>;     // MVP 为 []
}
```

---

## §5 API 触点

| Method | Path | 用途 | P95 预算 | 失败降级 |
|---|---|---|---|---|
| GET | `/api/wb/questions/{qid}` | 题目主体 + analysis | 500 ms | retry 按钮 + 缓存兜底 |
| GET | `/api/wb/questions/{qid}/nodes` | 复习节点 7 条 | 300 ms | 仅显示 MemoryCurve preview 态 |
| GET | `/api/wb/questions/{qid}/records` | 复习记录 | 400 ms | timeline 显示 EMPTY |
| POST | `/api/wb/questions/{qid}/archive` | 归档 → 返回 P05 | 300 ms | 失败 toast |
| POST | `/api/review/nodes/{nid}/start` | "立即复习" → 跳 P08 | 300 ms | 失败 toast |

---

## §6 状态机

| State | 触发 | UI 表现 |
|---|---|---|
| `LOADING` | 进入页面 | 全页骨架（图卡 + tab + 内容区 + radar） |
| `READY` (`VIEW`) | 三接口齐 | 默认 tab=分析；底部双按钮可点击 |
| `ARCHIVED` | archive 成功 | 全页 opacity 0.6 + 底部按钮替换为 "已归档"；返回 P05 |
| `ERROR` | 主接口失败 | 全页 retry |
| `IMAGE_VIEWER` | 点击 B1 图片 | 全屏 modal 放大原图 |

---

## §7 跳转图

```
[入口]
  P05 卡片点击 ─┐
  P04 查看详情 ─┤
  P-OBSERVER 详情 ─┘──→ P06
        │
        ├──[B1 原图点击]──────→ ImageViewer modal（原页内）
        ├──[B2 切换 tab]──────→ 内容区切换
        ├──[B8 立即复习]──────→ P08（reviewExec, nid=当前 currentT）
        ├──[B8 归档]──────────→ P05（return， list 自动过滤）
        └──[NavBar 返回]──────→ 来源页
```

---

## §8 AC 覆盖表（与 business-analysis.yml 双向锚定）

| AC ID | 验收点 | 涉及 Block | testid 验证点 |
|---|---|---|---|
| `AC-WB-DETAIL-001` | 顶部原图卡 170px 高，点击进 ImageViewer 放大 | B1 | `p06-origin-image` `height: 170px` · `p06-origin-image-zoom` 可点击 |
| `AC-WB-DETAIL-002` | SegmentTab 三段：分析 / 复习记录 / 变式，默认选中分析 | B2 | `p06-segment-tab-analysis[aria-selected="true"]` 默认；切换后 `aria-selected` 同步 |
| `AC-WB-DETAIL-003` | tab=分析 时 AIBriefCard 含 4px mastery-forgot 红条 + KP chips + 难度 ★ | B3 | `p06-ai-brief` 内 `p06-ai-brief-reason-bar` 颜色 = `--tkn-color-mastery-forgot` · `p06-ai-brief-kp-chip-{n}` · `p06-ai-brief-difficulty` |
| `AC-WB-DETAIL-004` | tab=复习记录 时显示 MemoryCurve（progress 变体）+ Timeline | B4, B5 | `memory-curve` 可见 + `memory-curve-node-{T0..T6}` 7 个节点 + `p06-records-timeline-item-{n}` |
| `AC-WB-DETAIL-005` | tab=变式 显示"敬请期待"空态（MVP） | B6 | `p06-variants-empty` 可见 + 文本含"敬请期待" |
| `AC-WB-DETAIL-006` | RadarChart 显示 5 个轴：运算/概念/方法/速度/准确 | B7 | `p06-radar-chart-axis-{1..5}` 5 个 + 文本对应 axes |
| `AC-WB-DETAIL-007` | 底部 sticky 双按钮：归档（灰）+ 立即复习（蓝），蓝按钮命中 primary token | B8 | `p06-bottom-actions-archive-btn` bg = warm-divider · `p06-bottom-actions-review-btn` bg = `--tkn-color-primary-DEFAULT` |
| `AC-WB-DETAIL-008` | 立即复习点击 调 `POST /review/nodes/{currentT}/start` 后跳 P08 | B8 | `p06-bottom-actions-review-btn` 触发 POST + 跳 `/review/exec/{nid}` |
| `AC-WB-DETAIL-009` | [AI 推测] ARCHIVED 态全页 opacity 0.6 + 底部按钮替换 "已归档" | B8 | `p06-bottom-actions[data-archived="true"]` + `p06-bottom-actions-review-btn` disabled |
| `AC-WB-DETAIL-010` | [AI 推测] MemoryCurve 当前节点 (status=now) 应用 sse-pulse 脉冲，prefers-reduced-motion 时关闭 | B4 | `memory-curve-node-{currentT}` 应用 `@keyframes sse-pulse` |

---

## §9 异常路径

| 触发 | UI | 系统行为 |
|---|---|---|
| 主接口 4xx | 全页错误页 + 返回按钮 | 跳回 P05 |
| 主接口 5xx | retry 按钮 + 缓存渲染 | 后台静默重试 |
| 原图加载失败 | 显示缩略图占位 + "加载失败" | 不阻塞页面其他渲染 |
| nodes 接口失败 | MemoryCurve 显示 preview 态（全灰） | 不阻断主流程 |
| records 接口失败 | timeline EMPTY 态 + "暂无复习记录" | 不阻断 |
| archive 失败 | toast 回滚 + 状态复位 | 后端事务回滚 |

---

## §10 埋点事件

| 事件名 | 触发 Block | 必带属性 |
|---|---|---|
| `wb_detail_view` | 页面 ready | `qid`, `subject` |
| `wb_detail_tab` | B2 切换 tab | `tab`(analysis/records/variants) |
| `wb_detail_zoom` | B1 放大 | `qid` |
| `wb_detail_archive` | B8 归档点击 | `qid` |
| `wb_detail_review_now` | B8 立即复习 | `qid`, `nid` |
| `obs_detail_readonly_view` | 观察者态 ready（P-OBSERVER 跳进来时） | `qid`, `student_id_hash` |

---

## §11 性能预算

- TTI ≤ 1500 ms
- LCP ≤ 1800 ms（B1 原图为 LCP）
- CLS < 0.05
- API P95 ≤ 500 ms（主接口）
- tab 切换 < 100ms

---

## §12 A11y

- Landmarks:
  - `<main role="main">` 包裹 B1-B7
  - `<header role="banner">` NavBar
  - 底部 sticky 双按钮区 `<footer role="contentinfo">`
- 焦点顺序: `NavBar 返回 → B1 zoom → B2 tab1 → tab2 → tab3 → 当前 tab 内容首元素 → B8 归档 → B8 立即复习`
- 屏幕阅读器朗读优先级:
  - B2 SegmentTab 用 `<div role="tablist">` 与 `<button role="tab" aria-selected>`
  - B3 AIBriefCard 用 `<article role="article" aria-label="AI 错因简报">`
  - B4 MemoryCurve 用 `<figure role="img" aria-label="艾宾浩斯遗忘曲线 · T2 已完成 · T3 进行中">`
- `prefers-reduced-motion: reduce` 兜底:
  - 关闭 MemoryCurve current 节点脉冲
  - tab 切换无过渡

---

## §13 Mockup 锚定

- **权威**: `design/mockups/wrongbook/06_wrongbook_detail.html` (v1)
- **历史变体**: 无
- **截图**: `design/system/screenshots/P06-v1-light.png`
- **反向锚定**: mockup HTML `<head>` 含 `<meta name="design-spec" content="P06-wrongbook-detail.spec.md@v1">`

---

## §14 Tokens 清单（grep 校验用）

```
L1 (Apple base):
  --tkn-color-primary-DEFAULT
  --tkn-color-text-primary
  --tkn-color-white
  --tkn-color-system-warning-DEFAULT
  --tkn-font-display
  --tkn-font-text
  --tkn-type-tile-heading
  --tkn-type-card-title
  --tkn-type-sub-heading
  --tkn-type-body
  --tkn-type-body-emphasis
  --tkn-type-caption
  --tkn-type-caption-bold
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
  --tkn-shadow-focus
  --tkn-motion-duration-base
  --tkn-motion-duration-slow
  --tkn-motion-ease-apple-standard

L2 (warmth):
  --tkn-color-bg-light
  --tkn-color-card
  --tkn-color-bg-light
  --tkn-color-text-primary
  --tkn-color-text-secondary
  --tkn-color-sep
  --tkn-color-encouragement-DEFAULT
  --tkn-shadow-card-deep
  --tkn-shadow-hero-card

L3 (celebration):
  --tkn-color-mastery-forgot
  --tkn-color-mastery-partial
  --tkn-color-mastery-mastered

EXCEPTION (subject):
  --tkn-subject-math
  --tkn-subject-physics
  --tkn-subject-chemistry
  --tkn-subject-english
```
