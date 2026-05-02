---
name: fe-testplan
description: >
  AI 前端高保真开发的 Stage 1.5 测试用例编写工作流。读取 business-analysis.yml 的
  ac_coverage[] 与 build-spec.json 的 blocks[]，把 verification_matrix（happy_path /
  error_paths / boundary / visual）结构化转译为 test-plan.json（每条 TC 含 id / ac /
  testids / tracks（A/B/C）/ setup_group / steps / expected），并把 testid 清单回填到
  build-spec.json，最终输出 test-plan-review.md（覆盖率统计 + 漏覆盖警示）供 User 确认。
  下游 fe-builder 只读 build-spec.json，下游 fe-accept-* 只读 test-plan.json，作者与
  执行者职责彻底切开。
  触发场景：用户说 "/fe-testplan 页面名"、"开始 testplan"、"生成测试用例"、"写 test-plan"、
  "做测试编排"，或在 fe-preflight 完成 + token-mapping-review 确认后、fe-builder 启动前。
---

# fe-testplan · 测试用例编写（Stage 1.5）

## 目标

把 `business-analysis.yml` 的 `ac_coverage[]` + `build-spec.json` 的 `blocks[]` 结构化转译为：

- **`test-plan.json`** — 给 fe-accept-* 执行的测试契约（每条 TC 含 setup/steps/expected/tracks）
- **回填 testids** — 在 build-spec.json `blocks[].testids` 中补齐 verification 引用的 testid，让 fe-builder 知道"必须打哪些 testid"
- **`test-plan-review.md`** — 覆盖率统计 + 漏覆盖告警，供 User 确认

## 职责硬铁律

| 角色 | 能看 | 不能看 |
|---|---|---|
| fe-builder（下游） | build-spec.json（含回填的 testid 清单） | test-plan.json |
| fe-accept-*（下游） | test-plan.json | （无新增限制，但严禁执行时新增 TC） |

- fe-builder 看不到 TC 的 expected → 避免针对测试编程
- fe-accept-* 严禁运行时增 TC → 漏了必须回头改 plan，不允许临时加检查

## 输入

| 参数 | 说明 | 示例 |
|---|---|---|
| `<PAGE>` | 页面名称 | ReviewTodayPage / ListPage / DetailPage |
| `--analysis` | business-analysis.yml 路径（可选，自动按 phase 推断） | design/analysis/s8-business-analysis.yml |
| `--build-spec` | build-spec.json 路径（可选，自动按 PAGE 推断） | design/tasks/preflight/ReviewTodayPage-build-spec.json |
| `--ac` | 可选的 AC 过滤（逗号分隔） | SC-08.AC-1,SC-08.AC-2 |

页面 → 默认输入推断（与 fe-preflight 对齐）：

| 页面 | business-analysis | build-spec |
|---|---|---|
| ListPage / CapturePage / DetailPage | `design/analysis/s7-business-analysis.yml` | `design/tasks/preflight/<PAGE>-build-spec.json` |
| ReviewTodayPage / ReviewExecPage / InsightPage / ParentObserverPage | `design/analysis/s8-business-analysis.yml` | `design/tasks/preflight/<PAGE>-build-spec.json` |

如缺少其一，HALT 并提示先运行 `/fe-preflight <PAGE>`。

## 执行步骤（严格按序）

### Step 1 · 加载施工图与业务文档

```bash
cat design/tasks/preflight/<PAGE>-build-spec.json
cat design/analysis/<phase>-business-analysis.yml
```

提取：
- `business-analysis.yml.ac_coverage[*]` — 每条 AC 的 verification_matrix + ux_anchor
- `build-spec.json.blocks[*]` — 区块到 testid 的映射 + AC 关联

如果 build-spec.json 缺失，HALT：
```
⛔ build-spec.json 未找到。
   请先运行 /fe-preflight <PAGE> 生成施工图后再编写 test-plan。
```

### Step 2 · 过滤本页面相关的 AC

依据 `build-spec.json.blocks[*].ac` 字段汇总本页面涉及的 AC ID 集合。
仅处理 ac_coverage 中匹配该集合的条目。

对每条 AC 校验：
- `ux_anchor` 是否含 testid 列表？没有 → 标记缺口（告警，不阻塞）
- `verification_matrix` 是否至少有 1 条 happy_path？没有 → 标记缺口（**阻塞**，HALT）

### Step 3 · 转译 verification_matrix → test_cases

参考 `references/test-plan-schema.md` 的完整 schema 与 TC 原型示例。
轨道分配的边界判断见 `references/track-assignment.md`。

逐条 AC，对每个 verification 类别按以下规则转译为 TC：

| verification 类别 | 默认 tracks | 默认 setup_group 推断 | category 字段 |
|---|---|---|---|
| `happy_path.*` | `["A", "B"]`（如涉及 OCR/SSE 加 A，纯 UI 用 B） | 数据加载完成态：`<page>-loaded` | `happy_path` |
| `error_paths.*` | `["B"]`（需 mock 后端错误） | 错误态：`<page>-error` | `error_path` |
| `boundary.*` | `["B"]`（需精心 fixture） | 边界态：`<page>-empty` / `<page>-boundary-<k>` | `boundary` |
| `visual.*` | `["B", "C"]`（截图 diff 都能跑） | 数据加载完成态：`<page>-loaded` | `visual` |
| `observable.*` | `["A"]`（需真实后端行为） | 真实后端态：`<page>-real-backend` | `observable` |

每条 TC 必须含的字段（强制）：
- `id`：`TC-FE-<PHASE>-<NNN>` 三位序号（按生成顺序）
- `ac`：来源 AC ID（`SC-08.AC-1` 等）
- `category`：上表对应类别
- `title`：人类可读标题（取自 verification_matrix 条目的 then 或 statement 摘要）
- `priority`：默认 `P0` 给 happy_path / error_paths.0 / boundary.0；其余 `P1`
- `tracks`：上表默认值，可根据 risks 调整
- `setup_group`：上表推断
- `testids`：从 `ux_anchor` 抽取该 AC 涉及的全部 testid（无差别全列）
- `steps`：把 verification 的 `given + when` 翻译成可执行操作序列（导航/点击/输入/等待）
- `expected`：把 `then` 拆成可断言的检查点列表（DOM 可见性 / 文本匹配 / URL / 数量 / 网络调用）
- `source.matrix_ref`：来源标记（如 `happy_path.0`），用于溯源

**翻译规则（given/when/then → setup/steps/expected）**：

```
given: "已登录用户 · 当日有 8 个 active 节点 · X-User-Timezone=Asia/Shanghai"
  ↓ 数据条件归 setup_group + setup 备注；环境条件归 expected 前置说明

when: "进入 P-REVIEW-TODAY"
  ↓ 翻译为 steps 第一条："导航到 /review/today，等待 review.today.root 出现"

then: "渲染 Hero 卡（8 题）+ 3 stat 卡 + 时段分组列表（现在 2 / 上午 0 / 下午 4 / 晚上 2）"
  ↓ 拆为 expected 多条：
    - "review.today.hero 可见且文本含 '8 题'"
    - "review.today.stats-done / stats-progress / stats-pending 三者均可见"
    - "review.today.slot-now 下 review.today.item-card 数量 == 2"
    - "review.today.slot-afternoon 下 review.today.item-card 数量 == 4"
```

**断言原子化原则**：每条 expected 是一个独立可机器判定的断言，不写"渲染正常"这种笼统语。

### Step 4 · 回填 testids 到 build-spec.json

收集 step 3 中所有 TC 引用的 testid，按 `blocks[].ac` 反向 join 回各区块：

```bash
python3 .claude/skills/fe-testplan/scripts/backfill_testids.py \
  --build-spec design/tasks/preflight/<PAGE>-build-spec.json \
  --test-plan design/tasks/testplan/<PAGE>-test-plan.json \
  --inplace
```

回填规则：
- 对每个区块 `block`，查找 `test_cases` 中 `ac == block.ac` 的所有 TC
- 把这些 TC 的 testids 取并集，merge 到 `block.testids`
- 不删除原有 testid，只补充新的

**已知 limitation（v0.1）**：当多个区块共用同一 AC 时，TC testid 会同时合并到这些区块——下游 fe-builder 看见冗余 testid 不会出错（每个 testid 在 TSX 中只会出现在它语义对应的位置），但 build-spec 会有视觉冗余。User 可在 review 阶段根据 testid 命名前缀手动分到正确区块。

### Step 5 · 生成 test-plan.json

输出路径：`design/tasks/testplan/<PAGE>-test-plan.json`

完整 schema 见 `references/test-plan-schema.md`。

`mkdir -p design/tasks/testplan` 后写入。

### Step 6 · 运行 schema + 覆盖率校验

```bash
python3 .claude/skills/fe-testplan/scripts/validate_test_plan.py \
  design/tasks/testplan/<PAGE>-test-plan.json \
  design/analysis/<phase>-business-analysis.yml \
  --page <PAGE>
```

校验项：
- JSON schema 完整性（所有强制字段存在）
- 每条 AC 至少 1 条 P0 TC
- 每条 AC 的 happy_path / error_paths / boundary 三类至少各 1 条 TC（visual / observable 可选）
- testid 引用是否在 ux_anchor 中存在（无则告警）
- tracks 字段值在 `["A", "B", "C"]` 子集
- setup_group 命名规范（`<page>-<state>` kebab-case）

任何"阻塞"项（schema 不完整 / P0 缺失）→ HALT，提示修复。

### Step 7 · 生成 test-plan-review.md

输出路径：`design/tasks/testplan/<PAGE>-test-plan-review.md`

模板见 `references/test-plan-schema.md` 末尾"review 模板"。

**只列出需要 User 决策的内容**：
- 覆盖率统计（每条 AC 的 TC 数 + category 分布）
- 漏覆盖告警（缺 P0 / 缺类别 / 没 visual TC 等）
- 跨 AC 共用的 setup_group 总览（确认 fe-accept 的执行分桶合理）
- testid 缺口（被 TC 引用但 ux_anchor 未声明，可能是 ux_anchor 漏写或 TC 多写）
- 三轨 TC 数量分布（A / B / C 各多少 TC）

### Step 8 · 汇总并等待 User 确认

```
✅ Test Plan Authoring 完成 · <PAGE>
   AC 数：N · 总 TC 数：M（P0:x / P1:y / P2:z）
   轨道分布：A=a / B=b / C=c
   build-spec.json 已回填 testid 清单（+K 个新 testid）
   输出：
     design/tasks/testplan/<PAGE>-test-plan.json
     design/tasks/testplan/<PAGE>-test-plan-review.md
     design/tasks/preflight/<PAGE>-build-spec.json（已更新）

⏸  请 User review test-plan-review.md 后确认覆盖率，fe-builder 方可开工。
```

## 硬约束

1. **不写任何页面代码** — 只产 test-plan，不动 frontend/apps 任何文件
2. **不臆造 testid** — 所有 testid 必须来自 build-spec.json 或 business-analysis.yml.ux_anchor，未声明的标为告警让 User 决策
3. **不臆造 AC** — 仅处理 build-spec 当前页面涉及的 AC 集合，不跨页面
4. **必须保留 verification_matrix 来源痕迹** — 每条 TC 在 metadata 中记录 `source.matrix_ref`（`happy_path.0` 等）
5. **回填 build-spec.json 必须保留原有 testid** — 只 merge 不替换
6. **等 User 确认 review 才声明完成** — 不自行跳过

## 与下游 skill 的契约

- **fe-builder**：在新一轮启动时检查 `design/tasks/testplan/<PAGE>-test-plan.json` 是否存在；不存在 → 提示先跑 fe-testplan
- **fe-accept-mock / e2e / diff**：从 test-plan.json 读 `tracks` 含自轨道字母的 TC，按 `setup_group` 分桶执行，逐条 ✅/❌ 报告

## 详细参考

- **test-plan.json 完整 schema 与 TC 原型**：`references/test-plan-schema.md`
- **track 分配决策树**：`references/track-assignment.md`
