---
page_id: P05
name: 错题本列表
name_en: Wrongbook List
route_h5: /wrongbook
route_miniprogram: pages/wrongbook/list
deeplink: wb://wrongbook
auth_state: authenticated
persona:
  - P1-K12
scenarios:
  - SC-04
  - SC-10
mockup_canonical: design/mockups/wrongbook/05_wrongbook_list.html
mockup_version: v1
last_reviewed: 2026-05-02
status: spec-locked
sprint: S2
---

# P05 · 错题本列表

> **使用说明**：本 spec 对应 DESIGN.md §4.2 的 14 段标准结构。Sprint 2 错题管理核心。
> **核心约束**：DESIGN.md §1 铁律 6（页面节奏二分）—— 单段信息流到底，无 hero。

---

## §1 页面目的（why · 1 句话）

学生管理所有历史错题，支持"以知识点找题、以掌握度找题、以语义找题"，并在右下角随时再拍一张新题。

---

## §2 ASCII 布局图

```
┌─────────────────────────────────────┐
│  B1 PageHeader                      │   ← mood=warm 暖米白底 #FAF8F4
│   "错题本" (display-hero 56px)      │
│   [搜索框 warm-sunken 凹陷底]       │
│   [AI 语义 Badge]                   │
│                                     │
│  B2 SubjectChips（横滚 + 计数）     │
│   [全部 138][数学 52][物理 32]...   │
│                                     │
│  B3 MasteryStatusCards（3 横排）    │
│   ┌──────┬──────┬──────┐            │
│   │未掌握│部分  │已掌握│            │
│   │ 42   │ 35   │ 51   │            │
│   └──────┴──────┴──────┘            │
│                                     │
│  B4 SortBar 排序选择                │
│   [最近添加 ▾]  [AI 语义 Badge]     │
│                                     │
│  B5 QuestionListCard 列表           │
│   ┌────┬─────────────────┐          │
│   │学科│ 缩略图 + 题干    │ 80×80   │
│   │色  │ KP chips · 6 段 │ stage   │
│   │条  │ 下次到期         │ dots    │
│   └────┴─────────────────┘          │
│   ┌────┬─────────────────┐          │
│   │... │ ...              │          │
│   └────┴─────────────────┘          │
│                                  ●  │   ← B6 FAB 蓝色拍题
└─────────────────────────────────────┘
[Tab Bar: 首页 错题本 拍题 复习 我的]
```

---

## §3 Block 清单（每块独立可实现单元）

| Block ID | 名称 | Section | Mood | Component | testid root | Tokens 用到 |
|---|---|---|---|---|---|---|
| `B1` | PageHeader 大标题+搜索 | info | warm | (custom block) | `p05-page-header` | `--tkn-color-bg-light`, `--tkn-color-bg-light`, `--tkn-color-text-primary`, `--tkn-type-display-hero`, `--tkn-radius-md` |
| `B2` | SubjectChips 学科横滚 | info | warm | M.SubjectChip[] | `p05-subject-chips` | `--tkn-subject-math`, `--tkn-subject-physics`, `--tkn-subject-chemistry`, `--tkn-subject-english`, `--tkn-radius-pill`, `--tkn-type-caption-bold` |
| `B3` | MasteryStatusCards 掌握度 | info | warm | M.MasteryStatusCard | `mastery-status-card` | `--tkn-color-mastery-forgot`, `--tkn-color-mastery-partial`, `--tkn-color-mastery-mastered`, `--tkn-color-card`, `--tkn-shadow-card-deep`, `--tkn-radius-lg` |
| `B4` | SortBar 排序条 | info | warm | (custom block) | `p05-sort-bar` | `--tkn-color-text-primary`, `--tkn-color-text-secondary`, `--tkn-color-primary-DEFAULT`, `--tkn-color-encouragement-soft`, `--tkn-radius-pill` |
| `B5` | QuestionList 错题卡列表 | info | warm | M.QuestionListCard[] | `question-list-card-{index}` | `--tkn-color-card`, `--tkn-shadow-card-deep`, `--tkn-subject-*`, `--tkn-color-mastery-mastered`, `--tkn-color-encouragement-DEFAULT`, `--tkn-color-bg-light`, `--tkn-radius-lg` |
| `B6` | FAB 拍题悬浮按钮 | info(fixed) | warm | (custom block) | `p05-fab-capture` | `--tkn-color-primary-DEFAULT`, `--tkn-color-white`, `--tkn-radius-circle`, `--tkn-shadow-hero-card` |

---

## §4 数据契约（page-level interface）

```typescript
interface WrongbookListResp {
  total: number;
  items: Array<{
    qid: string;
    subject: 'math' | 'physics' | 'chemistry' | 'english';
    thumbnailUrl: string;
    stemSnippet: string;     // 前 50 字
    kpList: string[];        // 知识点名 (最多 3 个)
    nodeStage: 0 | 1 | 2 | 3 | 4 | 5 | 6;
    nextDueAt?: string;      // ISO
    mastery: 'forgot' | 'partial' | 'mastered';
    archived: boolean;
    createdAt: string;       // ISO
  }>;
  filter: {
    subject: 'all' | 'math' | 'physics' | 'chemistry' | 'english';
    mastery: 'all' | 'forgot' | 'partial' | 'mastered';
    kp?: string;
    q?: string;              // 搜索关键字
    qMode?: 'kw' | 'sem';    // 关键字 / 语义
  };
  sort: 'created_desc' | 'created_asc' | 'due_asc' | 'mastery_asc';
  page: { current: number; size: number; hasMore: boolean };
  subjectCounts: Array<{ subject: string; count: number }>;
  masterySummary: { forgot: number; partial: number; mastered: number };
}
```

---

## §5 API 触点

| Method | Path | 用途 | P95 预算 | 失败降级 |
|---|---|---|---|---|
| GET | `/api/wb/questions?subject=&mastery=&kp=&q=&qMode=&page=&sort=` | 错题列表（关键字 + 语义混合排序 RRF） | 600 ms | 关键字降级（无 pgvector）；空态渲染 EMPTY |
| POST | `/api/wb/questions/{qid}/archive` | 归档错题（左滑手势触发） | 300 ms | 失败 toast 回滚 |

---

## §6 状态机

| State | 触发 | UI 表现 |
|---|---|---|
| `LOADING` | 进入页面 / 切 filter / 切 sort | B5 渲染 8 个卡片骨架；B1-B4 立即可见 |
| `READY` (`LIST`) | API 200 + items.length > 0 | 完整渲染列表 |
| `EMPTY` | filter 后 items.length === 0 | B5 区域显示空态插画 + 文案 "没有匹配的错题，[拍一张试试 →]" |
| `ERROR` | API non-2xx | 顶部 system-warning 黄条 + 列表区显示 retry 按钮 |
| `FILTERED` | 任何 filter 改变 | 顶部 chips 高亮 + 列表过渡淡入 250ms |
| `HIGHLIGHTED` | 来自 P04 保存后跳转，highlight={qid} | 第 1 张卡片 mastery-mastered 绿光圈 3s |

---

## §7 跳转图

```
[入口]
  Tab 2 「错题本」 ─┐
  P04 保存成功 ─┤── highlight=qid
  P-HOME B7 快捷 ─┘──→ P05
        │
        ├──[B5 卡片点击]──────→ P06（错题详情）
        ├──[B5 左滑归档]──────→ POST archive 后留在 P05（列表自动过滤）
        ├──[B6 FAB]──────────→ P02（拍题）
        ├──[B2 学科 chip]────→ 过滤 + 当前页（FILTERED 态）
        ├──[B3 mastery 卡]───→ 过滤 + 当前页（FILTERED 态）
        └──[B1 搜索]─────────→ q + qMode 过滤
```

---

## §8 AC 覆盖表（与 business-analysis.yml 双向锚定）

| AC ID | 验收点 | 涉及 Block | testid 验证点 |
|---|---|---|---|
| `AC-WB-LIST-001` | 大标题 "错题本" 用 display-hero 56px + 搜索框凹陷底 | B1 | `p05-page-header-title` font-size = 56px · `p05-page-header-search` 背景命中 `--tkn-color-bg-light` |
| `AC-WB-LIST-002` | 学科 chips 横滚显示计数 "数学 52"，选中后 list 过滤 | B2 | `subject-chip-math` 文本含 "52"；点击后请求 URL 含 `subject=math` |
| `AC-WB-LIST-003` | 3 张 MasteryStatusCard 横排，数字 = forgot/partial/mastered | B3 | `mastery-status-card-forgot/partial/mastered` 文本 = 对应数字 |
| `AC-WB-LIST-004` | mastery card 点击切换筛选，selected 态高亮 | B3 | 点击后 `aria-checked="true"` + 列表请求 URL 含 `mastery=` |
| `AC-WB-LIST-005` | QuestionListCard 必含：4px 学科色左条 + 缩略图 + 题干 + 6 段进度 + nextDueAt | B5 | `question-list-card-1-thumbnail` 80×80 · `question-list-card-1-stage-{0..6}` 6 个 dot · `question-list-card-1-due` 文本含日期 |
| `AC-WB-LIST-006` | 列表卡 4px 左色条颜色 = `--tkn-subject-{subject}`（铁律 5） | B5 | `question-list-card-1` 的 `border-left-color` 命中 subject token |
| `AC-WB-LIST-007` | FAB 蓝色 right-bottom fixed，size ≥ 56×56，命中 `--tkn-color-primary-DEFAULT` | B6 | `p05-fab-capture` `position: fixed` + bg = primary 蓝 + 跳 `/capture` |
| `AC-WB-LIST-008` | EMPTY 态：filter 后 0 题显示空态文案 + 拍题入口 | B5 | EMPTY 态 `p05-empty-state` 可见 + 含 `p05-empty-capture-btn` |
| `AC-WB-LIST-009` | [AI 推测] 来自 P04 highlight 时，第 1 卡片 3s mastery-mastered 绿光圈 | B5 | `question-list-card-1[data-highlight="true"]` 应用 `outline` 命中 mastery-mastered |
| `AC-WB-LIST-010` | [AI 推测] 搜索框右侧 "AI 语义" Badge 切换 qMode=sem 时高亮 | B1 | `p05-page-header-semantic-badge[data-active="true"]` |

---

## §9 异常路径

| 触发 | UI | 系统行为 |
|---|---|---|
| 列表请求失败 | 顶部 system-warning 黄条 + 中部 retry 按钮 | retry 重新发起请求 |
| 网络离线 | 渲染本地缓存最近一次列表 + 离线 banner | 缓存 TTL 6h |
| 搜索无结果 | EMPTY 态 + "没有匹配，[换个关键词]" | 不发起后续请求 |
| pgvector 服务不可用 | 自动降级到 pg_trgm 关键字搜索 + Badge 灰态 | 后端透明降级 |
| 归档失败 | toast 回滚 + item 复位 | 后端事务回滚 |

---

## §10 埋点事件

| 事件名 | 触发 Block | 必带属性 |
|---|---|---|
| `wb_list_view` | 页面 ready | `total`, `subject`, `mastery` |
| `wb_list_filter` | B2/B3 chip/card 点击 | `subject`, `mastery` |
| `wb_list_search` | B1 搜索回车 | `q`, `mode`(kw/sem), `resultCount` |
| `wb_list_tap` | B5 卡片点击 | `qid`, `index` |
| `wb_list_archive` | B5 左滑归档 | `qid` |
| `wb_list_fab_tap` | B6 FAB | (no extra) |

---

## §11 性能预算

- TTI ≤ 1500 ms
- LCP ≤ 1800 ms（首屏 3 张卡片为 LCP）
- CLS < 0.05
- API P95 ≤ 600 ms
- 滚动列表 60fps（virtualization 必要）

---

## §12 A11y

- Landmarks:
  - `<header role="banner">` 包裹 B1
  - `<main role="main">` 包裹 B2-B5
  - `<nav role="navigation" aria-label="底部导航">` Tab Bar
- 焦点顺序: `B1 搜索框 → B2 第 1 chip → B3 forgot 卡 → B5 第 1 卡片 → B6 FAB`
- 屏幕阅读器朗读优先级:
  - B5 列表使用 `<ol role="list">`，每卡 `<article role="article" aria-label="数学错题 · 二次函数 · T2 · 明日 18:00 复习">`
  - 搜索结果数变化用 `aria-live="polite"` 朗读 "找到 N 道题"
- `prefers-reduced-motion: reduce` 兜底:
  - 关闭 highlight 绿光圈脉冲（直接显示 outline 后立刻移除）
  - 关闭列表过渡淡入

---

## §13 Mockup 锚定

- **权威**: `design/mockups/wrongbook/05_wrongbook_list.html` (v1)
- **历史变体**: 无（第一版）
- **截图**: `design/system/screenshots/P05-v1-light.png`
- **反向锚定**: mockup HTML `<head>` 含 `<meta name="design-spec" content="P05-wrongbook-list.spec.md@v1">`

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
  --tkn-type-display-hero
  --tkn-type-card-title
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
  --tkn-radius-circle
  --tkn-shadow-focus
  --tkn-motion-duration-base
  --tkn-motion-ease-apple-standard

L2 (warmth):
  --tkn-color-bg-light
  --tkn-color-card
  --tkn-color-bg-light
  --tkn-color-text-primary
  --tkn-color-text-secondary
  --tkn-color-sep
  --tkn-color-encouragement-DEFAULT
  --tkn-color-encouragement-soft
  --tkn-shadow-card-deep
  --tkn-shadow-NONE-pressed-use-transform-scale
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
