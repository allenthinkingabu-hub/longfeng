# test-plan.json · Schema 与 TC 原型库

本文档定义 fe-testplan 输出的完整数据契约。所有字段必须严格遵守命名与类型，下游 fe-accept-* 依此执行。

## 顶层结构

```json
{
  "page": "ReviewTodayPage",
  "phase": "s8",
  "version": "1.0",
  "generated_at": "2026-04-28T10:00:00Z",
  "source": {
    "build_spec": "design/tasks/preflight/ReviewTodayPage-build-spec.json",
    "business_analysis": "design/analysis/s8-business-analysis.yml"
  },
  "ac_coverage": [
    { "ac": "SC-08.AC-1", "tc_count": 4, "categories": ["happy_path", "error_path", "boundary", "visual"], "p0_count": 3 }
  ],
  "test_cases": [ /* TC[] · 见下 */ ]
}
```

字段说明：

| 字段 | 必填 | 说明 |
|---|---|---|
| `page` | ✅ | 页面名，与文件名 `<PAGE>-test-plan.json` 一致 |
| `phase` | ✅ | 来源 phase（`s7` / `s8` 等），用于反查 business-analysis.yml |
| `version` | ✅ | 文档自身版本，每次 fe-testplan 重跑 +0.1 |
| `generated_at` | ✅ | ISO8601 UTC |
| `source` | ✅ | 输入文件路径，用于审阅时溯源 |
| `ac_coverage` | ✅ | 覆盖率快照，方便 review 一眼看出每条 AC 的 TC 数 |
| `test_cases` | ✅ | TC 数组，按生成顺序排列，id 单调递增 |

## TC 字段定义

```json
{
  "id": "TC-FE-S8-001",
  "ac": "SC-08.AC-1",
  "category": "happy_path",
  "title": "今日复习列表 · 时段分组渲染",
  "priority": "P0",
  "tracks": ["A", "B"],
  "setup_group": "review-today-loaded",
  "testids": [
    "review.today.root",
    "review.today.hero",
    "review.today.slot-now",
    "review.today.item-card"
  ],
  "preconditions": [
    "已登录用户",
    "X-User-Timezone=Asia/Shanghai"
  ],
  "fixture": {
    "endpoint": "GET /review-plans?date=today",
    "response_ref": "fixtures/review-plans-8-nodes.json",
    "notes": "8 个 active 节点 · 现在 2 / 上午 0 / 下午 4 / 晚上 2"
  },
  "steps": [
    "导航到 /review/today",
    "等待 review.today.root 出现"
  ],
  "expected": [
    "review.today.hero 可见且文本含 '8 题'",
    "review.today.slot-now 下的 review.today.item-card 数量 == 2",
    "review.today.slot-afternoon 下的 review.today.item-card 数量 == 4",
    "review.today.slot-evening 下的 review.today.item-card 数量 == 2",
    "review.today.empty 不可见"
  ],
  "source": {
    "matrix_ref": "happy_path.0",
    "user_journey": "P-REVIEW-TODAY → 时段分组 → 用户点卡片"
  }
}
```

字段规范：

| 字段 | 类型 | 必填 | 取值约束 |
|---|---|---|---|
| `id` | string | ✅ | `TC-FE-<PHASE>-<NNN>`，PHASE 大写，NNN 三位数字 |
| `ac` | string | ✅ | 必须存在于 business-analysis.yml.ac_coverage |
| `category` | enum | ✅ | `happy_path` / `error_path` / `boundary` / `visual` / `observable` |
| `title` | string | ✅ | ≤ 50 字 |
| `priority` | enum | ✅ | `P0` / `P1` / `P2` |
| `tracks` | array | ✅ | `A` / `B` / `C` 子集，至少 1 个 |
| `setup_group` | string | ✅ | kebab-case `<page>-<state>`，同 setup 的 TC 共桶 |
| `testids` | array<string> | ✅ | 必须出现在 ux_anchor 或 build-spec.blocks[].testids |
| `preconditions` | array<string> | ⚪ | 环境/认证条件（不影响 setup_group 分桶） |
| `fixture` | object | ⚪ | B 轨 MSW fixture 引用，A 轨可省略 |
| `steps` | array<string> | ✅ | 至少 1 条，每条以动词开头：导航/点击/输入/等待/滚动 |
| `expected` | array<string> | ✅ | 至少 1 条原子断言，禁止"渲染正常"等笼统语 |
| `source.matrix_ref` | string | ✅ | `happy_path.0` / `error_paths.1` / `boundary.0` / `visual.0` 等 |
| `source.user_journey` | string | ⚪ | 取自 ac_coverage[].user_journey 摘要 |

## TC 原型库（按 category）

### happy_path · 数据加载完成态主流程

```json
{
  "id": "TC-FE-S8-001",
  "ac": "SC-08.AC-1",
  "category": "happy_path",
  "title": "今日复习列表 · 时段分组渲染",
  "priority": "P0",
  "tracks": ["A", "B"],
  "setup_group": "review-today-loaded",
  "testids": ["review.today.root", "review.today.hero", "review.today.slot-now", "review.today.item-card"],
  "preconditions": ["已登录用户", "X-User-Timezone=Asia/Shanghai"],
  "fixture": { "endpoint": "GET /review-plans?date=today", "response_ref": "fixtures/review-plans-8-nodes.json" },
  "steps": ["导航到 /review/today", "等待 review.today.root 出现"],
  "expected": [
    "review.today.hero 可见且文本含 '8 题'",
    "review.today.slot-now 下的 review.today.item-card 数量 == 2"
  ],
  "source": { "matrix_ref": "happy_path.0" }
}
```

### error_path · 后端错误降级

```json
{
  "id": "TC-FE-S8-002",
  "ac": "SC-08.AC-1",
  "category": "error_path",
  "title": "/review-plans 返回 500 · 显示重试态",
  "priority": "P0",
  "tracks": ["B"],
  "setup_group": "review-today-error",
  "testids": ["review.today.error", "review.today.error-retry"],
  "fixture": { "endpoint": "GET /review-plans?date=today", "response_ref": "fixtures/error-500.json" },
  "steps": ["导航到 /review/today", "等待 review.today.error 出现"],
  "expected": [
    "review.today.error 可见",
    "review.today.error-retry 可见且可点击",
    "review.today.hero 不可见",
    "review.today.item-card 不存在"
  ],
  "source": { "matrix_ref": "error_paths.0" }
}
```

### boundary · 空态/边界值

```json
{
  "id": "TC-FE-S8-003",
  "ac": "SC-08.AC-1",
  "category": "boundary",
  "title": "今日 0 节点 · 空态展示",
  "priority": "P0",
  "tracks": ["B"],
  "setup_group": "review-today-empty",
  "testids": ["review.today.empty", "review.today.cta-start-all"],
  "fixture": { "endpoint": "GET /review-plans?date=today", "response_ref": "fixtures/review-plans-empty.json" },
  "steps": ["导航到 /review/today", "等待 review.today.empty 出现"],
  "expected": [
    "review.today.empty 可见且文本含 '今日已完成'",
    "review.today.cta-start-all 不可见",
    "review.today.item-card 不存在"
  ],
  "source": { "matrix_ref": "boundary.0" }
}
```

### visual · pixel diff

```json
{
  "id": "TC-FE-S8-005",
  "ac": "SC-08.AC-1",
  "category": "visual",
  "title": "P-REVIEW-TODAY 整页 pixel diff vs mockup 07",
  "priority": "P1",
  "tracks": ["B", "C"],
  "setup_group": "review-today-loaded",
  "testids": ["review.today.root"],
  "fixture": { "endpoint": "GET /review-plans?date=today", "response_ref": "fixtures/review-plans-8-nodes.json" },
  "steps": [
    "导航到 /review/today",
    "等待 review.today.root 出现",
    "Playwright 截图 1440×900"
  ],
  "expected": [
    "vs design/mockups/wrongbook/07_review_today.html baseline",
    "整页 pixel diff ≤ 3%"
  ],
  "source": { "matrix_ref": "visual.0" }
}
```

### observable · 仅 A 轨可验

```json
{
  "id": "TC-FE-S8-006",
  "ac": "SC-08.AC-3",
  "category": "observable",
  "title": "POST /review-plans/:id/complete 后服务端真实重算 next_review_at",
  "priority": "P0",
  "tracks": ["A"],
  "setup_group": "review-today-real-backend",
  "testids": ["review.exec.rating-perfect"],
  "preconditions": ["真实 s5 后端已启动", "DB 已 seed 一个 active 节点"],
  "steps": [
    "导航到 /review/exec/<planId>",
    "点击 review.exec.rating-perfect",
    "等待跳转到 done 页"
  ],
  "expected": [
    "POST /review-plans/<planId>/complete 返回 200",
    "DB 中该节点 next_review_at 大于当前时间",
    "DB 中该节点 ease_factor 增加（>= 2.6）"
  ],
  "source": { "matrix_ref": "observable.0" }
}
```

## setup_group 命名约定

`<page-slug>-<state>` 全小写 kebab-case：

- `<page-slug>`：页面短名（review-today / list / detail / capture / insight / parent-observer）
- `<state>`：数据/UI 状态（loaded / empty / error / loading / boundary-{n} / real-backend）

举例：

```
review-today-loaded
review-today-empty
review-today-error
list-loaded
list-empty
detail-loaded
detail-loading-stream
capture-permission-denied
parent-observer-real-backend
```

同 setup_group 的 TC 在 fe-accept 中共用一次 setup 步骤（同一份 fixture / 同一次浏览器初始化），桶内逐条断言。

## review 模板（test-plan-review.md）

```markdown
# Test Plan Review · <PAGE>

> 本文件由 fe-testplan 自动生成。请在每项「决策」处填写后方可进入 fe-builder。

## 概览

| 维度 | 值 |
|---|---|
| AC 总数 | N |
| TC 总数 | M（P0:x · P1:y · P2:z） |
| 轨道分布 | A=a · B=b · C=c |
| setup_group 数 | K |

## AC 覆盖矩阵

| AC | TC 数 | happy | error | boundary | visual | observable | P0 | 阻塞 |
|---|---|---|---|---|---|---|---|---|
| SC-08.AC-1 | 4 | 1 | 1 | 1 | 1 | 0 | 3 | — |
| SC-08.AC-3 | 3 | 1 | 1 | 0 | 0 | 1 | 2 | ⚠️ 缺 boundary |

## 漏覆盖告警

- ⚠️ **SC-08.AC-3 缺 boundary 类 TC** — verification_matrix.boundary 为空，可能漏写
  - **决策**：A) 补 boundary TC（指定场景）/ B) 接受空覆盖（业务无边界条件）/ C) 改 business-analysis.yml

## testid 缺口

| testid | 来源 TC | 在 ux_anchor 中？ | 建议 |
|---|---|---|---|
| review.today.error-retry | TC-FE-S8-002 | ❌ 未声明 | 添加到 ux_anchor 或改 TC |

## setup_group 总览

| setup_group | TC 数 | 共用 TC |
|---|---|---|
| review-today-loaded | 5 | TC-001/002/005/008/011 |
| review-today-empty | 1 | TC-003 |
| review-today-error | 1 | TC-002 |

## User 确认

- [ ] 所有漏覆盖告警已决策
- [ ] 所有 testid 缺口已补全或允许例外
- [ ] setup_group 分桶合理（不存在数据状态污染）
- [ ] 三轨分布与本页面适用范围一致
- [ ] test-plan.json 已 review，fe-builder 可开工
```
