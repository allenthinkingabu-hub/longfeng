# AI 前端高保真开发三段式方案

> 版本：v1.0 · 2026-04-27
> 背景：针对 S7 Phase 开发结果与高保真 mockup 差距过大的问题，设计一套 AI 可执行的三阶段工作流，覆盖开发前 · 开发中 · 开发后三个环节。

---

## 问题定性

诊断出的根本原因分两类：

**A 类 · AI 行为约束问题**（可被规范化解决）
- 没有消费 design tokens / ui-kit，自写 CSS 走阻力最小路径
- 任务粒度太粗，AI 一次性写完整页面导致细节漂移
- AC 只管功能正确性，视觉细节无约束

**B 类 · 能力边界问题**（只能缓解，无法根治）
- AI 无法渲染页面，没有视觉反馈回路
- Mockup HTML → React 的转换天生有损

Skill 的定位：**让 AI 在 A 类问题上有明确轨道可走，同时对 B 类问题保持诚实（显式标记差距，不假装解决了）**。

---

## 当前信息缺口

S7 + Sd 现有信息：

| 已有 | 作用 |
|---|---|
| Mockup HTML 文件（18-20KB/个） | 视觉基准，但 AI 只能读代码，看不到渲染结果 |
| `@longfeng/ui-kit` 组件（Card/Sheet/NavBar 等） | 可复用，但 AI 不知道"哪个 mockup 区块用哪个组件" |
| `@longfeng/design-tokens`（`--tkn-*` 变量完整列表） | 可引用，但 AI 不知道"mockup 里的 `#007AFF` 对应哪个 token" |
| S7 business-analysis.yml（AC + user journey） | 业务约束 |
| `@longfeng/api-contracts` 类型 | API 层约束 |

信息缺口：

| 缺口 | 影响 |
|---|---|
| Mockup 区块 → ui-kit 组件的映射关系不存在 | AI 不知道该用哪个组件，默认自写 |
| Mockup 色值/间距 → token 名的映射不存在 | AI 见到 `#007AFF` 不知道对应 `--tkn-color-primary-default` |
| Mockup 是 HTML，AI 读代码而非渲染结果 | 空间布局/层叠关系只能靠推断 |
| 没有 Sd.10 baseline 截图 | 视觉门禁无法对比真值 |

**结论**：开发前必须做一步"信息对齐"，把缺口补出来，否则 AI 还会走"自写 CSS"路径。

---

## 三阶段落地方案

### 阶段一：开发前 · Pre-flight Agent

**目标**：把 mockup + design system 转成 AI 能直接执行的结构化 spec。

```
输入：mockup HTML + tokens.css + ui-kit 组件列表 + business-analysis.yml
输出：build-spec.json（每个 UI 区块的"施工图"）
```

**执行动作**：

1. 解析 mockup HTML，提取所有视觉区块（NavBar / 筛选栏 / 卡片 / FAB / TabBar 等）
2. 对每个区块，从 ui-kit 组件列表中匹配最近的组件（找不到则标记"自定义"）
3. 提取区块内所有 CSS 色值 / 间距数值，与 `--tkn-*` token 做映射（找不到精确匹配则选最近值并标记"近似"）
4. 关联 AC：每个区块标注对应的 SC-XX.AC-Y 和 testid
5. 产出 `build-spec.json` + `token-mapping-review.md`（仅含"近似处理"条目）供 User 确认

**build-spec.json 示例结构**：

```json
{
  "page": "ListPage",
  "mockup_source": "design/mockups/wrongbook/05_wrongbook_list.html",
  "blocks": [
    {
      "id": "nav-bar",
      "label": "顶部导航栏",
      "ui_kit_component": "NavBar",
      "props": { "title": "错题本", "rightIcon": "filter" },
      "tokens": {
        "background": "--tkn-color-white",
        "title_color": "--tkn-color-text-primary"
      },
      "testids": ["wrongbook.list.filter-toggle"],
      "ac": "SC-08.AC-1",
      "custom_css_needed": false
    },
    {
      "id": "subject-chips",
      "label": "学科筛选 chip 栏",
      "ui_kit_component": "Tag（复用）",
      "tokens": {
        "chip_active_bg": "--tkn-color-primary-default",
        "chip_inactive_bg": "--tkn-color-bg-light"
      },
      "testids": ["wrongbook.list.filter-bar", "wrongbook.list.subject-chip-{value}"],
      "ac": "SC-08.AC-1",
      "custom_css_needed": false
    },
    {
      "id": "card-item",
      "label": "错题卡片",
      "ui_kit_component": "Card",
      "tokens": {
        "left_bar_low": "--tkn-color-danger-default",
        "left_bar_mid": "--tkn-color-warning-default",
        "left_bar_high": "--tkn-color-success-default",
        "subject_math": "--tkn-subject-math",
        "subject_physics": "--tkn-subject-physics"
      },
      "testids": ["wrongbook.list.item-card"],
      "ac": "SC-08.AC-1",
      "custom_css_needed": false
    }
  ]
}
```

**User 交互节点**：Pre-flight Agent 完成后，User 审阅 `token-mapping-review.md` 中的"近似处理"条目，确认可接受 → 开工。

---

### 阶段二：开发中 · Builder 分区块执行

**目标**：每次只做一个区块，做完即验，再做下一个。

**区块粒度示例（List 页面）**：

```
ListPage 拆成 6 个区块：
  ① NavBar（标题 + 搜索框）
  ② Subject chip 筛选栏
  ③ 掌握度三格筛选（未掌握 / 部分 / 已掌握）
  ④ CardItem（错题卡片）
  ⑤ FAB 拍照按钮
  ⑥ TabBar 底部导航
```

**每个区块的执行流程**：

```
1. Builder 读 build-spec.json 对应区块的施工图
   → 只注入该区块的 spec 摘录 + 相关 ui-kit 组件 API
   → 禁止注入其他区块代码（避免 context 膨胀）

2. 写 TSX：
   → 优先复用 ui-kit 组件（按 spec 指定）
   → CSS Module 中只用 var(--tkn-*) 引用 token
   → 所有可点击元素带 data-testid

3. 区块级本地验证（三条 grep 门禁）：
   ① ! grep -E '#[0-9a-fA-F]{3,6}|rgb\(' 区块文件  # 零硬编码色值
   ② grep "from '@longfeng/ui-kit'" 区块文件       # ui-kit 组件被引用
   ③ grep "data-testid" 区块文件 | wc -l           # testid 存在

4. 区块 commit（带 [SC-XX-ACY] 前缀）→ 进下一个区块
```

**关键约束**：每个区块的 Builder prompt context **只包含该区块的 spec 摘录**，避免上下文过长导致 AI 跳过约束走捷径。

---

### 阶段三：开发后 · Acceptance Agent

**目标**：独立 AI 从三个维度验收，产出有证据的 gap report，不修代码。

**关键突破 · 视觉对比可自动化**：

Playwright 可以同时渲染两个页面做截图对比：

```
对比源 A：file:///design/mockups/wrongbook/05_wrongbook_list.html（原始高保真）
对比源 B：http://localhost:4173/wrongbook（实际实现）
→ 截图 pixel diff → 客观量化视觉偏差
```

这绕开了"AI 无法看渲染结果"的限制，用工具代替人眼。

**三个验收维度**：

| 维度 | 执行方式 | 产出 |
|---|---|---|
| **视觉保真** | Playwright 截图 mockup vs 实现，pixel diff | diff 截图 + 偏差百分比（列表页阈值 5%，富交互页阈值 10%） |
| **设计系统合规** | grep 代码：零硬编码色值 / ui-kit 覆盖率 / token 引用率 | 合规评分报告 |
| **业务完整性** | Verifier AI 对照 S7 business-analysis.yml 逐 AC 检查 testid + 交互路径 | AC 覆盖矩阵（✅/⚠️/❌） |

**gap report 结构**：

```markdown
## 视觉保真 Gap
| 区块 | 偏差 % | 截图 diff | 疑似原因 |
|---|---|---|---|
| CardItem | 12% | diff/card-item.png | 左侧彩条颜色偏浅，应用 --tkn-subject-math |
| TabBar | 3% | diff/tabbar.png | ✅ 在阈值内 |

## 设计系统合规
- 硬编码色值：0 处（✅）
- ui-kit 组件覆盖：5/6 区块（⚠️ FAB 未用 ui-kit Button）
- token 引用率：94%（✅）

## 业务完整性
| AC | testid | 交互路径 | 状态 |
|---|---|---|---|
| SC-08.AC-1 | wrongbook.list.item-card ✅ | 点击 → /wrongbook/:id ✅ | ✅ |
| SC-08.AC-1 | wrongbook.list.filter-bar ✅ | 筛选 → 列表刷新 ✅ | ✅ |
```

**User 交互节点**：Acceptance Agent 完成后，User 审阅 gap report，决定：接受当前偏差 or 打回 Builder 修复指定区块。

---

### 阶段三补充：验收后修复回路（Repair Loop）

Acceptance Agent 发现问题后，修复路径按问题类型分三类：

#### M 类 · 机械修复（Mechanical）

**触发条件**：设计系统合规检查失败（硬编码色值、缺失 testid、使用旧 iOS 变量等）。

```
路径：
  Acceptance Agent 报告合规失败
       ↓
  fe-builder 定向修复（只改失败区块）
       ↓
  re-run check-compliance.sh（全页面四门禁）
       ↓
  全部通过 → commit → 结束
  仍失败   → 重试（上限 3 次）→ 超限 → 升级 User
```

特点：无需人工判断，AI 可以自主完成整个修复 → 验证 → commit 循环。

---

#### V 类 · 视觉修复（Visual）

**触发条件**：Playwright pixel diff 超过阈值（列表页 >8%，富交互页 >15%，相机页 >20%）。

```
路径：
  Acceptance Agent 报告偏差超阈值 + 输出 diff 截图
       ↓
  User 查看 diff 截图 → 决策分支：
    ├── 接受偏差（字体渲染差异等不可避免因素）→ 标注"已知偏差"→ 通过
    └── 不接受 → 指定区块 + 描述期望 → 打回 fe-builder
           ↓
       fe-builder 定向重写（最多 3 次）
           ↓
       每次修复后重跑 pixel diff
           ↓
       在阈值内 → commit → 结束
       3 次后仍超阈值 → 升级 User（需人工干预或接受偏差）
```

特点：**V 类问题 AI 不能自主决定"接受"**。偏差是否可接受属于视觉审美判断，必须由人决定。AI 的职责是提供 diff 证据，执行 User 指定的修复方向。

---

#### B 类 · 业务修复（Business）

**触发条件**：AC 验收失败（testid 不存在、交互路径不可达、状态变化未发生）。

```
路径：
  Acceptance Agent 报告 AC 失败
       ↓
  自动归因三连判：
    ① testid 在 DOM 中是否存在？
    ② 对应 API 请求是否发送？
    ③ 后端是否响应（HTTP 状态码）？
       ↓
  归因结果：
    ├── 前端 bug（testid 缺失 / 路由错误 / 状态未更新）
    │     → fe-builder 定向修复 TSX（最多 3 次）
    │     → 修复后重跑 AC 验证
    │     → 通过 → commit
    │
    └── 后端 bug（API 500 / 字段缺失 / 业务逻辑错误）
          → 创建后端 issue（标注 AC + 失败现象）
          → 退出前端修复流程（不在前端兜底后端问题）
```

特点：归因正确是 B 类修复的关键。前端 bug AI 可以修，后端 bug 必须创建 issue 交后端处理，**禁止在前端加 fallback 掩盖后端问题**。

---

#### 修复回路全局守则

1. **最多重试 3 次**：任何类型的修复，同一区块累计 3 次仍不过 → 强制升级 User，不再自动重试。
2. **每次修复后全页回归**：不只验改动区块，必须重跑全页面 check-compliance.sh（防止修 A 破 B）。
3. **修复结果记录到 gap report**：每次重试在 gap report 追加记录，形成可追溯的修复历史。
4. **V 类和 B 类超限后不自动合并**：未经 User 明确接受的视觉/业务问题，不进入主分支。

---

#### fe-repair Skill（规划）

未来可将上述流程封装为 `/fe-repair <page> <issue-type> <block-id>` skill：

| 参数 | 说明 |
|---|---|
| `issue-type` | `m`（机械）/ `v`（视觉）/ `b`（业务） |
| `block-id` | 来自 gap report 的区块 ID |

M 类自主执行，V 类等待 User 决策后执行，B 类先归因再决定是修前端还是开 issue。

---

## 整体流程

```
[Mockup HTML + tokens.css + ui-kit 组件 + business-analysis.yml]
          ↓
  Pre-flight Agent（解析 + 映射 + 产 build-spec.json）
          ↓
  User 审阅 token-mapping-review.md → 确认 → 开工
          ↓
  Builder 区块循环（每块：写 → grep 验 → commit）
    ① NavBar  ② Chips  ③ 掌握度筛选  ④ CardItem  ⑤ FAB  ⑥ TabBar
          ↓
  Acceptance Agent（三维度验收）
    · Playwright 截图 pixel diff（视觉）
    · grep 合规扫描（设计系统）
    · Verifier AI（业务完整性）
          ↓
  gap report（视觉 diff + 合规评分 + AC 矩阵）
          ↓
  User 决策：接受 or 打回修复
          ↓（打回时）
  Repair Loop（按 M / V / B 分类修复）
    M 类 → AI 自主修复 → re-run 合规 → commit
    V 类 → User 确认方向 → fe-builder 重写 → re-run pixel diff
    B 类 → 归因 → 前端 bug: fe-builder 修 / 后端 bug: 开 issue
          ↓
  全页回归（check-compliance.sh 全四门禁）
          ↓
  通过 → 合并 · 不通过且超 3 次 → 升级 User
```

---

## Skill 一览

六个 skill 全部可用，按阶段分布：

| Skill | 阶段 | 输入 | 输出 | 状态 |
|---|---|---|---|---|
| `/fe-preflight <page>` | 阶段一 | mockup HTML + tokens.css + ui-kit 组件列表 | `build-spec.json` + `token-mapping-review.md` | ✅ 可用 |
| `/fe-builder <page> [block-id]` | 阶段二 | `build-spec.json` 对应区块 | TSX + CSS + grep 验证通过 + commit | ✅ 可用 |
| `/fe-accept-diff <page>` | 阶段三 C 轨 | Vite dev server（无 MSW） | `<page>-diff-report.md`（视觉偏差 %） | ✅ 可用 |
| `/fe-accept-mock <page>` | 阶段三 B 轨 | MSW handlers + Vite dev server | `<page>-gap-report.md`（视觉 + 合规 + AC） | ✅ 可用 |
| `/fe-accept-e2e <page>` | 阶段三 A 轨 | 真实后端（gateway:8080） | `<page>-e2e-report.md`（完整 AC 矩阵） | ✅ 可用 |
| `/fe-repair <page> [m\|v\|b] [block-id]` | 修复回路 | gap report（Markdown） | 定向修复 + 全页回归 + commit | ✅ 可用 |

---

## 如何使用这些 Skill

### 场景一：开发一个全新页面（完整三段式）

```
Step 1 · Pre-flight
  /fe-preflight <page>
  → 生成 build-spec.json + token-mapping-review.md
  → 审阅 token-mapping-review.md 中的"近似处理"条目，确认后继续

Step 2 · Builder（区块循环）
  /fe-builder <page>
  → 按 build-spec.json 的 blocks 顺序逐块实现
  → 每块完成后自动运行 check-compliance.sh，通过后 commit

Step 3 · 快速视觉验证（可选，开发中随时跑）
  /fe-accept-diff <page>
  → 无需 MSW，30 秒出结果
  → 查看 diff-report.md，偏差超阈值则回 fe-builder 修

Step 4 · PR 前完整验收（B 轨）
  /fe-accept-mock <page>
  → MSW 驱动数据渲染，pixel diff + testid + 交互路径
  → 审阅 gap-report.md，决策：接受 or 打回

Step 5 · 打回修复
  /fe-repair <page>
  → 自动读取 gap-report.md
  → M 类（合规失败）→ AI 全自主修复
  → V 类（视觉偏差）→ 等你看 diff 截图后指令修复
  → B 类（AC 失败） → 归因后修前端 or 开后端 issue

Step 6 · Sprint 联调验收（A 轨，需后端已启动）
  /fe-accept-e2e <page>
  → 真实 OCR / SSE / 持久化 / 分页全验
  → 审阅 e2e-report.md，不通过的后端问题开 issue
```

---

### 场景二：修改已有页面（验证改动没破坏现有功能）

```
改完代码后：

  /fe-accept-diff <page>          ← 最快，30 秒，先排查明显视觉问题
  （如有问题）→ /fe-repair <page> m   ← 优先修合规失败（M 类）

  提 PR 前：
  /fe-accept-mock <page>          ← 完整 B 轨，生成 gap report
  （如有问题）→ /fe-repair <page>     ← 按 M/V/B 分类处理
```

---

### 场景三：只想快速检查视觉有没有明显偏差

```
/fe-accept-diff <page>
→ 查看 design/tasks/acceptance/<page>/diff.png
→ 看 diff-report.md 里的偏差 %
→ 在阈值内（ListPage ≤8%，DetailPage ≤15%，CapturePage ≤20%）→ OK
→ 超阈值 → /fe-repair <page> v
```

---

### 场景四：Sprint 结束，完整业务验收

```
确认后端已启动（gateway:8080 + wrongbook-service:8081）：

  /fe-accept-e2e <page>
  → 自动检查 gateway 健康，未就绪则 HALT 并提示启动步骤
  → 跑 OCR / SSE / 持久化 / 分页全套
  → 审阅 e2e-report.md：
      前端问题 → /fe-repair <page> b
      后端问题 → e2e report 中自动创建 issue 文件
```

---

### 各 Skill 的触发语（自然语言）

不用记命令，这些描述都能触发对应 skill：

| 说这些 | 触发 skill |
|---|---|
| "开始开发 ListPage" / "做 S7 列表页" | `fe-preflight` → `fe-builder` |
| "快速截图对比" / "跑 C 轨" / "只验视觉" | `fe-accept-diff` |
| "跑 B 轨验收" / "生成 gap report" / "PR 前验收" | `fe-accept-mock` |
| "跑 A 轨" / "联调验收" / "跑 e2e" | `fe-accept-e2e` |
| "修复 gap report 问题" / "修合规失败" / "修视觉偏差" | `fe-repair` |

---

### Skill 之间的数据流

```
fe-preflight
  └─ 输出 → build-spec.json
               └─ 消费 → fe-builder
                           └─ 输出 → 实现代码（TSX + CSS）
                                       ├─ 消费 → fe-accept-diff   → diff-report.md
                                       ├─ 消费 → fe-accept-mock   → gap-report.md
                                       └─ 消费 → fe-accept-e2e    → e2e-report.md
                                                     ↓（有问题）
                                                 fe-repair（读 Markdown report）
                                                     └─ 输出 → 修复 commit + 更新 report
```
