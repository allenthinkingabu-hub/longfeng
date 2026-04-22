# Sd · Step 5 — 首页页面规格 + 实施 Prompt

> 基于 Step 4 产出的 10 个组件 + `01_home_v3.html` 视觉参考，产出首页的 **页面规格文档 + H5 页面实现 + 小程序页面骨架**。本步是 Sd 阶段的第一个"真页面"落地，验证 tokens → 组件 → 页面整条链路。

**Task ID**: `sd-t05-page-spec-home`
**上游依赖**: `sd-t04-components-base` (must be `status: done`)
**下游依赖**: `sd-t06-additional-pages`（其他四个首屏入口页会复用本步的页面骨架约定）
**预计 tool calls**: ≤ 50 | **预计 tokens**: ≤ 55% window
**Reset 触发**: tool_call > 50 且 DoD 未全勾 | 发现需要自造新组件（表示 sd-t04 scope 遗漏）

---

## ===== PROMPT START =====

你是 **只做组件拼装 + 规格文档** 的 Builder。不做设计改动、不加组件、不写测试、不连真实 API（所有数据用类型明确的 mock）。

### 冷启动 5 步

1. **读项目不变量**: `落地实施计划_v1.0_AI自动执行.md` §1.8 + §9.2 + §10（如有页面规格模板章节）
2. **读架构**: `frontend/` 目录，确认 `apps/h5/` 和 `apps/mini/` 存在
3. **读 state**: `state/phase-sd.yml` → 确认 `sd-t04.status == "done"`
4. **读接口契约**: `state/interfaces.yml` → 读 `sd-t04 → sd-t05`；验证 10 个组件 + `index.ts` 的 symbols 与 sha256
5. **读前任 scratch**: `design-system/scratch_summary_sd_t04.md` + `design-system/scratch_summary_sd_t03.md` + `design-system/components.md`

---

### 硬性输入（只读）

```
frontend/packages/ui-kit/src/components/h5/index.ts   ← 10 个组件来源
frontend/packages/ui-kit/src/tokens.ts                ← 类型与常量
frontend/packages/ui-kit/src/tokens.css               ← app 层全局引入
frontend/packages/ui-kit/src/mini/vant-overrides.wxss ← 小程序 app.wxss 引入
design/01_home_v3.html                                ← 视觉 Ground Truth
design-system/components.md                           ← 组件 props 契约
design-system/scratch_summary_sd_t04.md               ← 已知"ProgressRing-mini 等后补"
state/phase-sd.yml
state/interfaces.yml
```

---

### 产物（6 个文件）

```
frontend/apps/h5/src/pages/home/
  index.tsx                       ← React 页面，纯组件拼装 + mock data
  page-spec.md                    ← 页面规格文档（见下节结构）
  mock-data.ts                    ← 明确类型的假数据，供 index.tsx 消费

frontend/apps/mini/pages/home/
  index.wxml                      ← Vant Weapp 组件 + 自定义 Canvas 组件位
  index.wxss                      ← 只写布局，不写颜色（颜色走 tokens.wxss）
  index.ts                        ← Page({}) 生命周期 + 假数据
```

不产出:
- 真实 API 调用 / axios / requests
- 路由配置（route 名以字符串常量形式列在 page-spec.md，接线留 sd-t07）
- 测试 / e2e
- 新增组件（缺组件即标 blocked 回退 sd-t04）

---

### 页面规格契约（page-spec.md 必备章节）

1. **页面目的**: 一句话 + 用户任务
2. **视觉 Ground Truth**: 引用 `design/01_home_v3.html` 并贴截图描述
3. **区域划分**（7 个 region，每个 region 有 `data-region` 值）:
   - `status-bar`（系统状态栏占位，实际由系统处理，高 54px）
   - `greeting`（ambient + 日期 + 名字 + streak chip + avatar）
   - `review-hero`（黑底 bento，8 题 + ring + subject chips + CTA）
   - `bento-weekly`（掌握率 / streak / 新增 三方块）
   - `week-strip`（7 日 dots）
   - `insight`（AI 洞察 dark card）
   - `msgs + quicks`（消息 + 4 快捷入口）
   - `tab-bar`（底部导航）
4. **数据绑定契约**: 每个 region 列出 `props: TypeScriptInterface`，字段必须在 `mock-data.ts` 有实例
5. **交互状态**:
   - Loading: bento 区 Skeleton（本步用 Card + 中灰背景占位即可）
   - Empty: streak=0 时 flame chip 隐藏；AI insight 无信号时整块不渲染
   - Error: 顶部 toast（本步不实现，留占位注释 `// TODO: sd-t08 error-states`）
6. **导航路由表**:
   ```
   CTA "开始复习" → route: '/review/next'
   "月视图 ›"    → route: '/calendar/month'
   quick "错题本" → route: '/wrongbook'
   quick "拍新题" → route: '/capture'
   quick "日历"   → route: '/calendar/month'
   quick "偏好"   → route: '/settings'
   tab 切换       → route: '/home' | '/wrongbook' | '/capture' | '/review' | '/me'
   ```
7. **Safe Area 与滚动**:
   - H5: `padding-top: env(safe-area-inset-top)` 包住 `greeting`；`padding-bottom: env(safe-area-inset-bottom) + 84px` 给 TabBar 留
   - 滚动区: `greeting` 和 `tab-bar` 固定，中间 `scroll` 区可滚
8. **testid 表**（必须与 index.tsx 里的 `data-testid` 一一对应）:
   ```
   home-greeting-streak
   home-greeting-avatar
   home-review-hero-cta-primary
   home-review-hero-cta-add
   home-review-hero-ring
   home-weekly-mastery
   home-weekly-streak
   home-weekly-newadd
   home-weekstrip-day-{YYYYMMDD}
   home-weekstrip-expand
   home-insight-cta-primary
   home-insight-cta-secondary
   home-msg-item-{id}
   home-quick-{key}
   home-tab-{key}
   ```
9. **A11y 表**:
   - 页面 `<main aria-label="首页">`；每个 region `<section aria-labelledby="...">` + 隐藏 h2
   - Hero 大数字 "8 题" 用 `<h1>` 或 `aria-level=1`
   - WeekStrip 每个 day 是 `<button>`，`aria-label="4 月 22 日，今日 8 题复习"`
   - Insight dark card 不能只靠颜色传达紧迫度；CTA 文字"立即专练"保留
10. **i18n seed**: 列出所有字符串键（`home.greeting.morning`、`home.review.hero.unit` …）；本步不接入 i18n 引擎，只保证每个硬编码中文串前加 `// i18n: home.xxx` 注释，为 sd-t08 做 ready
11. **性能预算**: 首次渲染 JS < 80 KB（不含 tokens.css + React），无图片 > 40 KB（hero 渐变全部 CSS 生成）
12. **已知待办**: `ProgressRing-mini / Sparkline-mini / WeekStrip-mini 未实现`（引用 sd-t04 scratch）

---

### 技术契约

#### H5 (React 18) 侧

- `index.tsx` ≤ 250 行；如超 → 拆子文件（同目录下 `region-*.tsx`）
- 只 import from `@lf/ui-kit`；不得直接从 `@lf/ui-kit/src/*` 深导
- 所有 event handler 用 `useCallback`，避免 WeekStrip 这类 re-render 开销
- mock-data.ts 导出 `export const homeMockData: HomeData`，类型 `HomeData` 也在同文件导出
- 字符串本地化: 直接写中文 + 行内 `// i18n: key` 注释；等 sd-t08 替换
- 页面入口只消费 10 个组件；缺什么立即退回 sd-t04

#### 小程序侧

- `index.wxml` 使用 Vant Weapp 组件（`van-cell-group` / `van-tabbar` / `van-badge` 等）+ 自定义 `<canvas>` 为 ProgressRing / Sparkline / WeekStrip 占位（canvas id 写对，Canvas 逻辑 sd-t06 填）
- `index.wxss` 只布局：flex / grid / position / padding；**禁止写颜色 / 阴影 / 边框样式**（全部走 `vant-overrides.wxss` 或 inline var）
- `index.ts` Page lifecycle：`onLoad` 塞假数据，`onPullDownRefresh` 留空 callback
- 同一份 testid 表以 `data-testid` 绑定在 WXML 节点（小程序支持 `data-*` 属性）

---

### 工具白名单

允许:
- `Read`（tokens.* / 10 组件 / 01_home_v3.html / state/*.yml / components.md）
- `Write` / `Edit`（仅限 6 个产物路径）
- `Bash`:
  - `npx tsc --noEmit -p frontend/apps/h5`
  - `ls frontend/packages/ui-kit/src/components/h5/`
  - `grep -r 'data-testid' frontend/apps/h5/src/pages/home/`

禁止:
- 在本页面新建任何可复用组件（抽象单元属于 sd-t04 scope）
- 引入 axios / swr / react-query / zustand / redux
- Import 任何 CSS 文件（tokens.css 已在 app root 引入）
- 写真实 API；mock 数据固定
- 改动 `@lf/ui-kit` 内部任何文件

---

### 执行序列（14 步）

1. 冷启动 5 步
2. 读 `01_home_v3.html` 全文，按照 7 个 region 切分结构清单
3. 读 `@lf/ui-kit/src/components/h5/index.ts`，列出可用 10 个组件及其 props 类型
4. 反查：7 个 region 里每个视觉单元映射到哪个组件 + 哪些 props；**命中缺组件就停**（写 scratch 标 blocked，退回 sd-t04）
5. 设计 `HomeData` 类型（对应 page-spec §4 数据绑定契约）
6. 写 `mock-data.ts`，确保每个字段有真实样例值（streak=12, reviewDueCount=8, subjects=[math:3, physics:2, english:3], weekDays=[…7 天…]）
7. 写 `index.tsx`，按 region 顺序组装；每个交互元素贴 `data-testid`
8. 写 `page-spec.md`，12 个章节全齐
9. 写小程序 `index.wxml / .wxss / .ts`
10. 跑 `tsc --noEmit -p frontend/apps/h5`，退出码必须 0
11. grep 校验 testid 表与 page-spec §8 完全一致（无漏无多）
12. grep 校验 `index.tsx` 无非 `@lf/ui-kit` 的视觉性 import（禁止 `import './foo.css'` / `import styled from ...`）
13. 更新 `state/phase-sd.yml` + `state/interfaces.yml`（新增 sd-t05 → sd-t06 条目，列出 page-spec 路径、testid 清单、route 常量）
14. 写 `design-system/scratch_summary_sd_t05.md`

---

### DoD 清单

- [ ] 6 个产物文件齐
- [ ] `tsc --noEmit` 退出码 0（H5 侧）
- [ ] `page-spec.md` 12 个章节全部存在，§8 testid 表与 `index.tsx` grep 结果一致
- [ ] `index.tsx` ≤ 250 行，无硬编码 hex、无直接 style 属性里写颜色
- [ ] `index.wxml` 所有颜色来自 `vant-overrides.wxss` 或 token 变量
- [ ] `mock-data.ts` 导出 `HomeData` 类型 + `homeMockData` 实例
- [ ] 路由常量集中，易于 sd-t07 接线
- [ ] `state/phase-sd.yml` sd-t05 = done；`state/interfaces.yml` 新增条目
- [ ] `scratch_summary_sd_t05.md` 已写

---

### 失败回滚

- **Case A**: 某 region 视觉依赖缺失组件 → 停；scratch 标 `blocked`，指明缺哪个组件，请求 sd-t04 扩展
- **Case B**: testid 表与代码不一致且 3 次修补仍漂移 → 停；scratch 标 `in_progress`，保留现状，列出具体差异
- **Case C**: mock-data 里字段形状与 `HomeData` 类型对不上 → 修类型不修 mock（mock 是 ground truth，类型要跟得上）；如需改组件 props → 退回 sd-t04

---

### `scratch_summary_sd_t05.md` 格式

```markdown
# scratch_summary_sd_t05

## 本任务状态
status: done | blocked | in_progress

## 产出清单（6 条）
- frontend/apps/h5/src/pages/home/index.tsx      sha256=…  LOC=…
- frontend/apps/h5/src/pages/home/page-spec.md   sha256=…  章节数=12
- frontend/apps/h5/src/pages/home/mock-data.ts   sha256=…
- frontend/apps/mini/pages/home/index.wxml       sha256=…
- frontend/apps/mini/pages/home/index.wxss       sha256=…
- frontend/apps/mini/pages/home/index.ts         sha256=…

## 区域-组件映射表
| region | H5 组件 | Mini 节点 | testid 前缀 |
|---|---|---|---|
| status-bar     | (system)                | (system)             | home-status |
| greeting       | Chip + Avatar + div     | view + van-icon      | home-greeting-* |
| review-hero    | Card(dark) + Button ... | van-row + canvas     | home-review-hero-* |
| bento-weekly   | Card × 3 + Sparkline    | view × 3 + canvas    | home-weekly-* |
| week-strip     | WeekStrip               | canvas               | home-weekstrip-* |
| insight        | Card(dark) + Button × 2 | van-cell + van-button| home-insight-* |
| msgs + quicks  | 自写 list + 4 Card      | van-cell-group × 2   | home-msg-*, home-quick-* |
| tab-bar        | TabBar                  | van-tabbar           | home-tab-* |

## testid 合规
- page-spec §8 表 vs index.tsx grep: 全一致（共 N 条）

## 对 sd-t06 的交接要点
- 下一步实现小程序端 canvas：ProgressRing / Sparkline / WeekStrip
- 下一步需要实现的其他首屏页：/wrongbook, /capture, /calendar/month, /settings
- 路由常量已集中在 page-spec §6，接线时从这里读

## 未解决的坑
- i18n 引擎未接入，所有硬编码中文仅加注释；等 sd-t08
- 错误/空态 toast 未实现；等 sd-t08
- 真数据未接；等 sd-t07
```

---

### Reset Protocol

- tool_call > 50 且 DoD 未全勾 → 停，标 `in_progress`，按 region 列出 done/undone
- tsc 连续 3 次不过 → 停，标 `in_progress`，scratch 附错误栈
- 永不 rm -rf / git reset

---

### 禁止事项

- 禁止在本步新增任何 `@lf/ui-kit` 组件（即使只差一点点）
- 禁止写 `useEffect` 拉数据
- 禁止 import 任何 CSS 文件
- 禁止给页面起第二个名字（如 HomePage / DashboardPage 两个组件）
- 禁止把 mock-data 混在 index.tsx 里（两文件分开是刚性要求）
- 禁止为 i18n 提前接入引擎（本步只留注释）

## ===== PROMPT END =====

---

## 父 Agent 元信息

- **为什么页面规格文档要 12 章**: 这是 sd-t06 之后所有页面的模板。一旦首页立起标准（region / testid / 路由 / A11y / i18n seed / 性能预算），后续 4 个首屏入口页按模板填空即可，不必每次重新发明。
- **为什么 testid 要写在规格里再落代码**: testid 是 e2e 的公共接口，容易散落在代码里无人维护；规格文档作为唯一事实源，代码 grep 对齐。
- **为什么 H5 和 Mini 同一步实现**: 两端页面骨架需要共用数据模型 (`HomeData`)、testid 表、route 表；分两步做会让这些公共契约漂移。
- **为什么小程序不做 canvas 实现**: Canvas 在小程序里有 `<canvas type="2d">` 独立 API，实现量 ≥ 200 行，混在本步会把 LOC 打爆；独立成 sd-t06。
- **为什么 mock-data 要严格类型**: mock-data.ts 是页面 → 服务端契约的第一版，sd-t07 接真数据时直接把 `HomeData` 换成 API 响应类型即可，接口形状不漂。
