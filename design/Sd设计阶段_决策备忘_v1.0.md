# Sd 设计阶段 · 决策备忘

> 版本：**v1.0 · 2026-04-21**
> 作用：把"是否需要独立 UI/UX 设计阶段 · 设计工具选型 · Sd 阶段产出清单"这些关键决策以 Git-diff 可追溯的形式固化，供后续 AI Agent 读取与执行。
> 上游：`业务与技术解决方案_AI错题本_基于日历系统.md`（v1.2）
> 下游：`落地实施计划_v1.0_AI自动执行.md`（接下来会同步加入 Phase Sd）

---

## 0. TL;DR（三句话结论）

1. **必须增加一个独立设计阶段 Sd**，作为与关键路径并行的第二条执行链（和 S11 匿名服务并列）。
2. **不引入 Figma**，全程采用 **Code-as-Design** —— 设计 Token / 组件 / 状态 / 可点击原型 全部以 Git-diffable 的代码与 JSON/MD 形式存在。
3. Sd 产出 **9 个交付物 + 1 个 Review Gate**，是 S7（前端）DoR 的硬依赖，也是 S9（E2E）selector 稳定性的基础。

---

## 1. 问题诊断：19 张 mockup 不等于"可交付的设计"

当前 `design/mockups/wrongbook/` 里 19 张 HTML 只是**视觉稿**。对一个"AI 全程制造、人类只在 Gate 决策"的流水线而言，离"前后端能开发、测试能测试"还差 8 类关键信息：

| # | 缺失项 | 影响面 |
|---|---|---|
| 1 | **设计 Token 单一源**（颜色 / 字号 / 间距 / 圆角 / 阴影 / 动效时长） | H5 与小程序样式必然漂移；Builder Agent 会自编色值 |
| 2 | **组件清册** | 没有"Button 组件长什么样 / 有几个 variant / 几种状态"的权威清单 |
| 3 | **页面状态谱**（loading / empty / error / success / disabled / selected） | 每张 mockup 只画了"成功态"，AI 会自行脑补 empty state |
| 4 | **交互规约**（转场 / 手势 / 焦点 / 动画曲线 / 反馈时长） | 不同页面动效风格必然割裂 |
| 5 | **A11y 基线**（对比度 / ARIA / 触达区 / 朗读顺序） | 上线即可能不合规 |
| 6 | **响应与多端**（断点 / rpx / 暗色模式预留） | 后期迁移成本指数级增长 |
| 7 | **data-testid 约定** | **最致命**：15 个 Playwright spec 没有稳定 selector 无法落地 |
| 8 | **埋点事件目录** | 方案 §0 定义了增长漏斗 35%/25%/15%，但事件名 / Payload / 触发时机空白 |

---

## 2. Sd 阶段定位与依赖

### 2.1 在 DAG 中的位置

```
                   ┌─────────┐
 关键路径           │   S0    │
                   └────┬────┘
                        │
          ┌─────────────┼─────────────┐
          ▼             ▼             ▼
     ┌────────┐    ┌────────┐    ┌────────┐
     │   S1   │    │   Sd   │    │  S11   │   ← 三条并行链
     │  DDL   │    │ Design │    │ Anon   │
     └────┬───┘    │ System │    └────┬───┘
          │        └────┬───┘          │
          ▼             │              │
     ┌────────┐         │              │
     │S2..S6  │         │              │
     └────┬───┘         │              │
          ▼             ▼              │
     ┌──────────────────────┐          │
     │         S7           │ ← Sd DoD  │
     │   (需 Sd 全部 done)  │   是 S7   │
     └──────────┬───────────┘   DoR     │
                ▼                        │
            S8 → S9 ←─────────────────────┘
                     ↑
               Sd.6 testid 规约
               是 S9 selector 稳定性基础
```

### 2.2 依赖约束

- **前置**：S0（需要 monorepo 骨架）
- **并行**：与 S1..S6 全程并行；与 S11 相互独立
- **阻塞**：S7 DoR 必须含"Sd 全部 done"；S9 DoR 必须含"Sd.6 testid 已应用"
- **不在关键路径**：关键路径最长链深度保持 10 级不变；Sd 做得快或慢不影响 Release 总体节奏（除非 Sd 未完成）

---

## 3. Sd 九大产出（不合并 · 不缩减）

| 编号 | 产出 | 载体 | 核心价值 |
|---|---|---|---|
| **Sd.1** | 设计 Token | `design/system/tokens/*.json` + Style Dictionary 转译到 CSS/SCSS/JS/WXSS | 颜色/字号/间距/圆角/阴影/动效的单一源 |
| **Sd.2** | 组件清册 | `design/system/components.md` + Storybook 8 | ~20 个组件 · variants / states / props / a11y / 双端等价物 |
| **Sd.3** | 高保真页面规约 | `design/specs/P*.md`（19 份） + 现有 mockup 加 testid | 状态枚举 / 数据字段↔API 字段 / 事件↔埋点 / 断点 |
| **Sd.4** | 用户旅程图 | `design/flows/SC*.mmd`（Mermaid · 15 份） | 覆盖 15 SC · 含分支 / 错误路径 / 回退态 |
| **Sd.5** | Handoff 资产 | `design/assets/icons/*.svg`（SVGO 优化）+ 字体 + logo + favicon + App icon + OG 图 | 全部 Git 入库 |
| **Sd.6** | testid 规约 | `design/system/testid-convention.md` + ESLint rule + mockup 注入 | `<screen>.<region>.<element>` · Playwright selector 稳定 |
| **Sd.7** | A11y 基线 | `docs/a11y-report.json`（axe-core 产出） | WCAG AA · 0 violations |
| **Sd.8** | 文案 · i18n · 种子内容 | `packages/i18n/zh-CN.json` + `design/seed/sample-questions.json` + 法务占位文案 | UI 文案 0 硬编码 · 样题 JSON 供 S1 fixture 读取 · 法务文案占位（正式版由 User 提供） |
| **Sd.9** | 可点击原型 | `apps/prototype/`（Vite + React Router · 串 19 张 mockup） | 部署到 `prototype.longfeng.com` · Verifier Agent 踩点 / User 试用 |

### 3.1 Review Gate（Sd 独有的硬门禁）

- **触发**：Sd.1..Sd.9 九项全部 Builder Agent 自检通过后，推入 Sd Review Gate
- **执行者**：Design Reviewer Agent（独立上下文）+ User 终审
- **校验项**：
  1. tokens JSON 与 ui-kit CSS 变量一致（自动脚本）
  2. Storybook 每个组件每个状态可见，axe-core 0 violation
  3. 19 张 mockup 关键可交互元素 100% 带 testid
  4. Mermaid flow 中每个 SC 的关键步骤可在 Playwright spec 中找到对应 step
  5. 可点击原型可以走通 15 SC 路径
  6. User 确认"审美 / 信息架构 / 交互"三项无反对意见
- **失败处置**：Reviewer Agent 产出具体 change-request 评论 → Builder Agent 修复 → 重跑 Gate
- **通过后**：`git tag sd-done` → S7 DoR 解锁

---

## 4. 设计工具决策：为什么不用 Figma

### 4.1 Figma 在 AI-first 流水线中的三个痛点

1. **AI 绕 Figma 工作低效**：Figma 是 GUI-first 工具，AI 即使走 MCP/API 也只能有限读写；round-trip "方案改动 → 同步 Figma → 导出 token → 回写代码"每次都有信息损耗。
2. **Git diff 不可见**：`.fig` 是二进制，PR review 时 Reviewer Agent 看不到变更——破坏"一切皆 Git tag、一切皆可审计"基线。
3. **Token 双源漂移**：Figma 的 Variables / Tokens Studio 与代码 `tokens.json` 必然不同步，维护同步脚本本身是小项目。

### 4.2 Code-as-Design 的优势

- 所有产出可 Git diff
- Reviewer Agent 直接读 JSON / MD / 组件源码
- Verifier Agent 对 Storybook 跑 axe-core / Chromatic 视觉回归（自动化）
- 零 SaaS 订阅费
- Design Agent 用同一套 LLM 工具（读写 token JSON、写组件、更新 mockup）无割裂

### 4.3 未来接入人类设计师的两条退路

1. **Penpot**（开源 Figma 平替 · 可自部署）作为**只读镜像**：token 源仍在 Git，脚本单向推到 Penpot。设计师改就改 Git 或提 issue。
2. **Figma 仅作审美参考**：非技术 stakeholder 允许在 Figma 里画草稿，但进入 Sd 阶段前**必须转译为 HTML/token**；`.fig` 文件不进 Git 主线，只放 `design/references/`。

---

## 5. "高保真"的重新定义

在 AI-first 流水线里，"高保真"不是一张像素完美的截图，而是以下**五件互相咬合的可机读产物**的集合：

| 产物 | 服务谁 | 自动化机制 |
|---|---|---|
| 可运行的 Storybook | 前端 Builder Agent · QA Verifier Agent | 每个组件 × 每个状态一键可见 |
| 可点击原型（Sd.9） | User · Verifier Agent | 浏览器走一遍就能验证 SC 路径 |
| 可扫描的 a11y 报告 | Reviewer Agent · 合规审计 | axe-core 自动跑 |
| 可回归的视觉快照 | Chromatic / Percy | 视觉漂移自动标记 |
| 可机器读取的 token / testid / i18n-key | Builder / Verifier | JSON + 代码约定 · 无歧义 |

凑齐这五样，开发和测试 Agent 的输入比任何 Figma 稿都完整——**Figma 给"它长什么样"，Code-as-Design 给"它长什么样 + 怎么实现 + 怎么验证 + 怎么翻译"**。

---

## 6. 配套改进：4 个小坑

| # | 小坑 | 归处 |
|---|---|---|
| 1 | 埋点事件目录 | 折入 **Sd.3** 页面规约（每页必列触发的 event） |
| 2 | 特性开关（Feature Flag） | 走 Nacos 动态配置；在落地计划 **§19 安全与合规** 新增子章 |
| 3 | 法务文案（隐私政策 / ToS / consent gate 文案） | 折入 **Sd.8**；正式版由 User 从法务处拿后替换 Git 占位 |
| 4 | 样题 & onboarding 种子内容 | 折入 **Sd.8** `design/seed/sample-questions.json`；S1 的 fixture 直接读 |

---

## 7. 技术选型（Code-as-Design 技术栈）

| 用途 | 工具 | 版本 | 选型理由 |
|---|---|---|---|
| Token 转译 | **Style Dictionary** | 4.x | Amazon 出品 · 可同时输出 CSS/SCSS/JS/WXSS/iOS/Android |
| 组件目录 | **Storybook 8** | 8.x | React + 小程序 WXML story plugin · 支持 interaction test |
| 可点击原型 | **Vite + React Router** | Vite 5 · RR 6 | 串联 19 张 mockup · 部署即可分享 |
| A11y 扫描 | **@axe-core/cli** + Storybook addon | 4.x | 可 CI 集成 · 0 violation 硬门禁 |
| 视觉回归 | **Chromatic** 或 **Percy**（二选一） | 2024 | Storybook 原生集成 · PR 自动打 baseline |
| 流程图 | **Mermaid** | 11.x | Markdown 原生 · GitHub / GitLab 直出 |
| SVG 优化 | **SVGO** | 3.x | 所有图标入库前自动瘦身 |
| i18n | **i18next** + **react-i18next** | 23.x | 已被 H5/miniapp 双端支持 · JSON 键文件 |
| 硬编码文案检测 | **eslint-plugin-i18next** | 6.x | 静态扫描 UI 源码中的中文字面量 |

---

## 8. 将对《落地实施计划 v1.0》做的具体变更

以下变更将在本备忘签署后立即应用到 `落地实施计划_v1.0_AI自动执行.md`：

| # | 章节 | 变更 |
|---|---|---|
| 1 | §0 TL;DR | 阶段数 12→13；并行链数 2→3；新增 Sd 产出摘要 |
| 2 | §1.2 RACI | 新增 **Design Agent** 角色（执行 Sd + Review Gate 自审） |
| 3 | §1.3 工具链 | 追加 Storybook / Style Dictionary / axe-core / Mermaid / SVGO / i18next |
| 4 | §2.1 里程碑树 | 独立并行链增加 **Md · 设计系统**（S11 之外的第二条支链） |
| 5 | §2.3 依赖矩阵 | 新增 Sd 行；更新 S7 依赖补 **Sd**；更新 S9 依赖补 **Sd.6** |
| 6 | 新增 §16 | **Phase Sd · 设计系统与高保真**（DoR / 9 产出 / 命令 / Review Gate / DoD） |
| 7 | 原 §16..§24 | 顺延为 §17..§25（CI/CD / Helm / Security / Performance / Acceptance / Matrix / Risk / Deliverables / Playbook） |
| 8 | §11 S7 DoR | 追加：Sd 全部产出 `git tag sd-done` |
| 9 | §13 S9 DoR | 追加：Sd.6 testid 规约已应用到 mockup 和组件 |
| 10 | §19 安全与合规（新号） | 新增子章 **19.6 Feature Flag 管理**（Nacos 动态配置 · `FEATURE_*_ENABLED`） |
| 11 | §22 业务覆盖矩阵（新号） | 新增 Sd 行 · 引用 Sd-DoD |
| 12 | 附录 C DAG | 新增 Sd 节点 · 展示三条并行链 |

---

## 9. 决策记录（可追溯）

| 议题 | 选项 | 决策 | 决策人 | 日期 |
|---|---|---|---|---|
| 是否新增独立设计阶段 | 是 / 否 / 融入 S7 | **是 · 独立 Sd 阶段** | User (Allen) | 2026-04-21 |
| Sd 产出是否精简合并 | 精简到 5 项 / 保留 9 项 | **保留 9 项 · 不合并** | User (Allen) | 2026-04-21 |
| 是否追加 Review Gate | 追加 / 不追加 | **追加** | User (Allen) | 2026-04-21 |
| 可点击原型 Sd.9 | 保留 / 删除 | **保留** | User (Allen) | 2026-04-21 |
| 4 个小坑（埋点/开关/法务/样题） | 按原建议 / 调整 | **按原建议全部纳入** | User (Allen) | 2026-04-21 |
| 设计工具选型 | Figma / Penpot / Code-as-Design | **Code-as-Design** | User (Allen) | 2026-04-21 |

---

## 10. 下一步

1. 本备忘签入 Git：`git add design/Sd设计阶段_决策备忘_v1.0.md && git commit -m "docs(design): Sd phase decision memo v1.0"`
2. 立即开始同步修改 `落地实施计划_v1.0_AI自动执行.md`，按 §8 列表逐项更新
3. 修改完成后产出 `落地实施计划_v1.1_含Sd设计阶段.md` 或在原文件打 tag `plan-v1.1`

---

> **签署**：User · Allen Wang · 2026-04-21
> **执行授权**：下一位 AI Agent 可按本备忘 §8 直接对 `落地实施计划_v1.0_AI自动执行.md` 施加变更，无需再次询问。
