# AI 验证 AI 编写的前端代码 · 方法论

> 版本：v1.0 · 2026-04-27
> 背景：在 AI 高保真前端开发工作流（三段式方案）中，Stage 3 Acceptance 阶段的完整验证策略。
> 核心命题：**AI 写的代码，能被另一个 AI + 工具链客观验证到什么程度？**

---

## 一、问题定性

### 为什么需要"AI 验 AI"？

AI 在前端开发中的能力边界决定了它的输出天然有偏差：

| 能力边界 | 影响 |
|---|---|
| AI 无法渲染页面，没有视觉反馈回路 | 写完代码不知道"看起来对不对" |
| Mockup HTML → React 转换天生有损 | 空间布局/层叠关系只能靠推断，不能精确还原 |
| AI 读代码而非渲染结果 | 颜色值用对了，但整体视觉感受可能仍然偏离 |

**结论**：不能依赖 AI 的"自我感觉良好"，需要一套外部可量化的验证机制。

---

## 二、验证环境选型：A / B / C

验证前端代码有三种环境方案，各有适用场景：

### 方案 A · 真实后端

启动完整后端栈（基础设施 + 业务微服务），前端连接真实 API。

```
基础设施层：PostgreSQL + Redis + RocketMQ + Nacos + MinIO（Docker Compose）
业务微服务：gateway · wrongbook-service · ai-analysis-service · file-service · review-plan-service
```

**能验的范围：**
- ✅ OCR 识别流程（文件上传 → 后端处理 → stem_text 返回）
- ✅ SSE AI 讲解流（EventSource 真实流式响应）
- ✅ 数据持久化（标签保存、软删除、刷新后数据仍在）
- ✅ 分页游标（cursor 分页多页加载）
- ✅ 跨服务数据流（拍题 → 解析 → 复习计划联动）

**局限：**
- ❌ 启动成本高（Java 编译 + DB migration + 多服务协调）
- ❌ 不稳定因素多（服务启动顺序、数据库状态、网络）
- ❌ 外部依赖（真实 LLM API、真实 OCR 服务）

---

### 方案 B · MSW（Mock Service Worker）

在前端 Vite dev 层拦截所有 API 请求，返回预设 fixture JSON，无需后端。

```
拦截层：msw + vite-plugin-msw
Fixture：src/__mocks__/handlers/ 目录，按 API 路径组织
```

**能验的范围：**
- ✅ 所有 UI 区块正常渲染（数据由 fixture 驱动）
- ✅ UI 状态覆盖（loading / error / empty / success 全部可触发）
- ✅ 前端路由跳转（list → detail → back）
- ✅ 纯前端交互（筛选、标签 Sheet 开关、掌握度切换）
- ✅ testid 在 DOM 中存在
- ✅ Playwright 截图有内容可比对

**局限：**
- ❌ 数据不持久（保存标签刷新后消失）
- ❌ SSE 流支持有限（EventSource mock 需额外处理）
- ❌ 无法验证真实业务数据流

---

### 方案 C · Playwright 截图对比（仅结构验证）

不启动后端也不加 MSW，直接对实现截图，与 mockup HTML 渲染截图做 pixel diff。

```
对比源 A：file:///design/mockups/wrongbook/05_wrongbook_list.html
对比源 B：http://localhost:5174/wrongbook（Vite dev server）
→ 截图 → pixel diff → 偏差百分比
```

**能验的范围：**
- ✅ 空/加载态的视觉结构（nav、tabbar、空状态卡片等）
- ✅ CSS token 是否影响视觉（颜色偏差可被截图捕捉）

**局限：**
- ❌ 没有数据时大部分区块无法渲染，pixel diff 意义有限
- ❌ 无法验证业务功能

---

## 三、三种方案的覆盖矩阵

| 验证维度 | 方案 A（真实后端） | 方案 B（MSW） | 方案 C（仅截图） |
|---|:---:|:---:|:---:|
| **视觉保真** | ✅ 完整 | ✅ 完整 | ⚠️ 仅静态结构 |
| **设计系统合规**（token / testid grep） | ✅ | ✅ | ✅ |
| **路由跳转** | ✅ | ✅ | ❌ |
| **UI 状态覆盖**（loading/error/empty） | ✅ | ✅ | ❌ |
| **数据持久化**（保存/删除/刷新验） | ✅ | ❌ | ❌ |
| **SSE 讲解流** | ✅ | ⚠️ 有限 | ❌ |
| **OCR / 文件上传** | ✅ | ❌ | ❌ |
| **分页游标** | ✅ | ⚠️ 需精心 fixture | ❌ |
| **跨服务流程** | ✅ | ❌ | ❌ |
| **启动成本** | 高 | 低 | 极低 |
| **CI 可复现性** | ⚠️ 依赖外部服务 | ✅ | ✅ |

---

## 四、推荐组合策略

### 按验收目标选组合

```
目标：视觉验收（设计还原度）
  → 方案 B + Playwright pixel diff
  → 不需要后端，5 分钟可跑

目标：业务流程完整验证
  → 方案 A + Playwright e2e
  → 需要启动完整后端栈

目标：CI 自动化门禁
  → 方案 B（稳定可复现）+ grep 合规检查
  → 不依赖外部服务，PR 级别可跑

目标：上线前完整验收
  → 方案 A + B + Playwright（三维度全跑）
  → 产出完整 gap report 供人工决策
```

### 本项目（Longfeng · S7）推荐

```
Stage 3 Acceptance 分两轨并行：

轨道 1（快速 · 每 PR）：
  方案 B（MSW）+ grep 合规 + Playwright 截图对比
  → 视觉保真 + 设计系统合规 + 基础业务结构
  → 产出：gap report（视觉偏差 % + 合规评分）

轨道 2（完整 · 每 Sprint）：
  方案 A（真实后端）+ Playwright e2e
  → 业务流程完整性（OCR / SSE / 持久化）
  → 产出：AC 覆盖矩阵（✅/⚠️/❌）

最终决策：人工审阅 gap report → 接受偏差 or 打回 Builder
```

---

## 五、验证的三个维度

### 维度 1 · 视觉保真（Visual Fidelity）

**工具**：Playwright 截图 + pixelmatch

**流程**：
```
1. 渲染 mockup HTML → 截图（对比源 A）
2. 渲染实现页面（含 MSW mock 数据）→ 截图（对比源 B）
3. pixelmatch 对比，输出 diff 图 + 偏差百分比
```

**判定阈值**：
| 页面类型 | 接受阈值 |
|---|---|
| 列表页（低动态内容） | ≤ 8% |
| 富交互页（动态区块多） | ≤ 15% |
| 相机页（深色 UI，字体差异大） | ≤ 20% |

**阈值原理**：字体渲染差异（mockup 用 SF Pro，浏览器用系统字体）+ 动态内容差异 + 设计 token 近似映射误差，基准偏差约 5-8%。

---

### 维度 2 · 设计系统合规（Design System Compliance）

**工具**：`fe-builder/scripts/check-compliance.sh`（grep 门禁）

**四条门禁**：
```bash
# Gate 1：CSS Module 中零硬编码品牌色
grep '#[0-9a-fA-F]{3,6}\|rgb(\|hsl(' *.module.css | 排除注释和本地变量定义

# Gate 2：无旧 iOS 色变量残留
grep 'var(--blue)\|var(--red)\|var(--green)\|var(--orange)' *.module.css

# Gate 3：TSX 中无内联品牌色
grep "style={{.*color: '#[0-9a-fA-F]" index.tsx | 排除 #fff

# Gate 4：testid 数量满足最低要求
grep -c 'data-testid' index.tsx
```

**评分**：4/4 通过为合规，任意一项失败需修复后方可 commit。

---

### 维度 3 · 业务完整性（Business Integrity）

**工具**：对照 `design/analysis/s7-business-analysis.yml` 逐 AC 检查

**检查项**：
```
对每条 AC：
  1. testid 是否存在于 DOM？（Playwright $('[data-testid="..."]').isVisible()）
  2. 用户操作路径是否可达？（Playwright 模拟点击/输入/导航）
  3. 预期状态变化是否发生？（API 调用 / 路由变化 / UI 状态）
```

**AC 覆盖矩阵格式**：
```markdown
| AC | testid | 交互路径 | 状态 |
|---|---|---|---|
| SC-08.AC-1 | wrongbook.list.item-card ✅ | 点击 → /wrongbook/:id ✅ | ✅ |
| SC-01.AC-1 | capture.camera.btn ✅ | 拍照 → 上传 → 解析中 | ⚠️ OCR 需真实后端 |
| SC-03.AC-1 | wrongbook.detail.explain-stream ✅ | SSE 流 → 文字渐现 | ⚠️ 需真实 LLM |
```

---

## 六、gap report 结构

Stage 3 完成后输出标准 gap report：

```markdown
# Gap Report · <PAGE> · <DATE>

## 视觉保真
| 区块 | 偏差 % | 是否在阈值内 | 疑似原因 |
|---|---|---|---|
| nav-bar | 3.2% | ✅ | 字体渲染差异 |
| card-item | 9.8% | ✅ | 学科色 token 近似（Δ≈29） |
| tabbar | 2.1% | ✅ | — |

## 设计系统合规
- Gate 1 硬编码色值：0 处 ✅
- Gate 2 旧 iOS 变量：0 处 ✅
- Gate 3 内联品牌色：0 处 ✅
- Gate 4 testid 覆盖：23/23 ✅
- **整体合规评分：100%** ✅

## 业务完整性（MSW 轨道）
| AC | testid | 路由/状态 | 结论 |
|---|---|---|---|
| SC-08.AC-1 | ✅ | 列表→详情 ✅ | ✅ |
| SC-02.AC-1 | ✅ | 标签 Sheet 开关 ✅ | ✅ |

## 业务完整性（真实后端轨道）
| AC | 验证项 | 结论 |
|---|---|---|
| SC-01.AC-1 | OCR 识别 → stem_text 填充 | ⚠️ 待真实后端验 |
| SC-03.AC-1 | SSE 讲解流 → 文字渐现 | ⚠️ 待真实后端验 |

## User 决策
- [ ] 接受当前视觉偏差（最大 9.8%，在阈值内）
- [ ] 接受 OCR/SSE 延迟验证（下一 Sprint 联调时验）
- [ ] 打回 Builder 修复指定区块：____
```

---

## 七、验证后修复回路（Repair Loop）

Acceptance 阶段发现的问题按可自动化程度分三类，对应三条不同修复路径。

### M 类 · 机械修复（Mechanical）

**触发**：Gate 1–4 合规失败（硬编码色值、缺 testid、旧 iOS 变量残留）。

| 步骤 | 执行者 | 动作 |
|---|---|---|
| 1 | fe-accept-mock | 报告具体失败位置（文件 + 行号） |
| 2 | fe-builder | 定向修复失败区块（只改问题行） |
| 3 | check-compliance.sh | 全页面重跑四门禁 |
| 4 | 全通过 | commit · 结束 |
| 重试上限 | — | 同一区块 ≤ 3 次，超限升级 User |

**特点**：无需人工判断。合规失败有明确的"正确答案"（换 token / 加 testid），AI 可自主完成 fix → verify → commit 全循环。

---

### V 类 · 视觉修复（Visual）

**触发**：pixel diff 超阈值（列表页 >8%，富交互页 >15%，相机页 >20%）。

```
fe-accept-mock 报告偏差超阈值 + 输出 diff 截图路径
       ↓
  User 查看 diff 截图
       ↓
  ┌────────────────┬────────────────────────────────┐
  │ 接受偏差        │ 不接受                          │
  │（字体渲染等      │（颜色/布局问题）                  │
  │  不可避免因素）   │                                │
  │ → 标注"已知偏差" │ → 描述期望修复方向 → 打回 fe-builder │
  │ → 通过          │ → 最多 3 次重试                  │
  └────────────────┴────────────────────────────────┘
       ↓（不接受路径）
  fe-builder 定向重写区块
       ↓
  重跑 pixel diff
       ↓
  在阈值内 → commit · 结束
  仍超阈值且已 3 次 → 升级 User（接受 or 人工干预）
```

**关键约束**：**V 类问题 AI 不能自主决定"接受"**。偏差是否可接受是视觉审美判断，决策权在人。AI 只提供 diff 证据，执行 User 指定的修复方向。

---

### B 类 · 业务修复（Business）

**触发**：AC 验收失败（testid 不在 DOM、交互路径不可达、状态变化未发生）。

```
fe-accept-mock 报告 AC 失败
       ↓
  自动归因三连判：
    ① testid 是否在 DOM 中？（Playwright isVisible）
    ② 对应 API 请求是否发送？（Network log）
    ③ 后端响应状态码是否正常？（2xx / 4xx / 5xx）
       ↓
  ┌────────────────────┬──────────────────────────┐
  │ 前端 bug            │ 后端 bug                  │
  │（testid 缺失 /       │（API 500 / 字段缺失 /      │
  │  路由错误 /          │  业务逻辑错误）             │
  │  状态未更新）         │                           │
  │ → fe-builder 修 TSX │ → 创建后端 issue           │
  │ → 重跑 AC 验证       │ → 退出前端修复流程          │
  │ → 最多 3 次重试      │（不在前端兜底后端问题）       │
  └────────────────────┴──────────────────────────┘
```

**关键约束**：归因正确是 B 类修复的前提。**禁止在前端加 fallback 掩盖后端 bug**——如果后端字段为空就在前端补默认值，掩盖了真实问题且制造了数据不一致风险。

---

### 修复回路全局守则

| 守则 | 说明 |
|---|---|
| **最多 3 次重试** | 同一区块同一类问题，累计 3 次不过 → 强制升级 User，停止自动重试 |
| **每次修复后全页回归** | 必须重跑全页面 check-compliance.sh，防止修 A 破 B |
| **修复历史追加到 gap report** | 每次重试记录在 gap report，形成可追溯修复日志 |
| **V/B 超限不自动合并** | 未经 User 明确接受的视觉/业务问题，不进入主分支 |

---

### 修复类型对照表

| 验证维度 | 失败现象 | 修复类型 | AI 自主度 |
|---|---|---|---|
| 设计系统合规 | 硬编码色值 / 旧变量 | M 类 | 全自主 |
| 设计系统合规 | testid 缺失 | M 类 | 全自主 |
| 视觉保真 | pixel diff 超阈值（字体差异） | V 类 | 人决定是否接受 |
| 视觉保真 | pixel diff 超阈值（颜色/布局） | V 类 | 人指定方向，AI 执行 |
| 业务完整性 | testid 缺失 / 路由错误 | B 类-前端 | AI 修复（最多 3 次） |
| 业务完整性 | API 500 / 后端逻辑错误 | B 类-后端 | 创建 issue，AI 退出 |

---

## 八、诚实的能力边界

以下是这套验证方法**永远保证不了**的事情，需要在方法论层面保持诚实：

| 无法保证的 | 根本原因 |
|---|---|
| "视觉完全一致" | AI 没有视觉感知，pixel diff 只能量化偏差，不能让偏差归零 |
| "设计意图正确" | 颜色用对了 token ≠ 整体视觉感受符合设计师意图 |
| "空间比例精确还原" | AI 从 HTML 代码推断布局，天生有损 |
| "业务 feel 正确" | E2E 流程跑通 ≠ UX 体验符合产品预期 |
| "动态交互流畅" | Playwright 无法量化动画帧率、手势响应等主观体验 |

**正确的期望设定**：

> 三段式验证方案把 AI 的 B 类能力边界问题从
> **"不可见的隐患"** 变成 **"可量化的已知偏差"**。
>
> 最终决策权在人：审阅 gap report → 接受偏差 or 打回修复。
> 方案不声称消除偏差，只承诺让偏差可见、可量化、可追溯。

---

## 九、工具链汇总

| 工具 | 阶段 | 用途 |
|---|---|---|
| `fe-preflight` skill | Stage 1 | mockup 解析 → build-spec.json |
| `fe-builder` skill | Stage 2 | 分区块实现 + grep 合规检查 |
| `check-compliance.sh` | Stage 2/3/修复 | 4 gate 自动合规检查 |
| `fe-accept-mock` skill | Stage 3 **B 轨 · 每 PR** | MSW + Playwright pixel diff + testid → gap report |
| `fe-accept-e2e` skill（待建） | Stage 3 **A 轨 · 每 Sprint** | 真实后端 Playwright e2e → AC 覆盖矩阵 |
| `fe-accept-diff` skill（待建） | Stage 3 **C 轨 · 随时** | 仅截图 pixel diff → 静态结构快速验证 |
| `fe-repair` skill（待建） | 修复回路 | M/V/B 分类修复 + 回归验证 |

---

## 十、决策流程图

```
fe-builder 完成
       ↓
  ┌──────────────────────────────────────────────────┐
  │                Stage 3 Acceptance                │
  │                                                  │
  │  B 轨（每 PR · 快速）   → /fe-accept-mock         │
  │  MSW + pixel diff + testid 检查 → gap report     │
  │                                                  │
  │  C 轨（随时 · 极轻）    → /fe-accept-diff（待建）  │
  │  仅截图 pixel diff → 静态结构快速验证               │
  │                                                  │
  │  A 轨（每 Sprint · 完整）→ /fe-accept-e2e（待建）  │
  │  真实后端 Playwright e2e → AC 覆盖矩阵             │
  └──────────────────────────────────────────────────┘
       ↓
  User 审阅 gap report
       ↓
  ┌─────────────┬───────────────────────────────────┐
  │ 接受偏差     │ 打回修复 → /fe-repair               │
  │ → 合并分支   │                                    │
  └─────────────┤  M 类（合规失败）→ AI 全自主修复      │
                │  V 类（视觉偏差）→ User 确认→ AI 执行  │
                │  B 类（AC 失败） → 归因→前端修/后端 issue │
                └───────────────────────────────────┘
                       ↓
               全页回归（check-compliance.sh）
                       ↓
               通过 → commit · 合并
               超 3 次 → 升级 User
```

---

*文档维护：随三段式 skill 迭代同步更新。*
*关联文件：`design/AI前端高保真开发三段式方案.md` · `.claude/skills/fe-preflight/` · `.claude/skills/fe-builder/` · `.claude/skills/fe-accept-mock/` · `.claude/skills/fe-accept-e2e/`（待建）· `.claude/skills/fe-accept-diff/`（待建）· `.claude/skills/fe-repair/`（待建）*
