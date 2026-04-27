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
```

---

## Skill 结构（规划）

三段式 skill，每段独立可调用：

| Skill 命令 | 阶段 | 输入 | 输出 |
|---|---|---|---|
| `/pre-flight <page>` | 阶段一 | mockup 路径 + tokens.css + ui-kit 组件列表 | `build-spec.json` + `token-mapping-review.md` |
| `/build-block <page> <block-id>` | 阶段二 | `build-spec.json` 对应区块 | TSX 区块代码 + grep 验证 pass + commit |
| `/accept <page>` | 阶段三 | 实现代码 + mockup + business-analysis.yml | gap report（视觉 + 合规 + AC） |

---

## 优先级建议

1. **最高**：Pre-flight Agent（解决"AI 不知道用哪个组件/token"的根本问题）
2. **次高**：Acceptance Agent 的设计系统合规扫描（grep 可立即执行，成本最低）
3. **次高**：Acceptance Agent 的 Playwright 截图 pixel diff（需要 mockup 可在浏览器渲染）
4. **中**：Builder 区块循环（需要 build-spec.json 先存在）
5. **低**：Verifier AI 业务完整性（business-analysis.yml 已有，可作为最后一步）
