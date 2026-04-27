---
name: fe-repair
description: >
  验收后修复回路。读取 gap report，按 M/V/B 三类路径执行修复，
  每次修复后全页回归验证，超过 3 次重试则升级 User。
  触发场景：用户说 "/fe-repair 页面名"、"修复 gap report 问题"、
  "修合规失败"、"修视觉偏差"、"修 AC 失败"，
  或 accept skill 完成后 User 决定打回修复。
---

# fe-repair · 验收后修复回路

## 输入参数

| 参数 | 说明 | 示例 |
|---|---|---|
| `<PAGE>` | 页面名称 | ListPage / CapturePage / DetailPage |
| `[type]` | 问题类型（可选）：m / v / b / all（默认 all） | m |
| `[block-id]` | 限定修复哪个区块（可选，不指定则修所有该类问题） | card-item |

---

## 页面文件映射

| 页面 | CSS | TSX | Mockup | 路由 |
|---|---|---|---|---|
| ListPage | `frontend/apps/h5/src/pages/List/List.module.css` | `frontend/apps/h5/src/pages/List/index.tsx` | `design/mockups/wrongbook/05_wrongbook_list.html` | `/wrongbook` |
| CapturePage | `frontend/apps/h5/src/pages/Capture/Capture.module.css` | `frontend/apps/h5/src/pages/Capture/index.tsx` | `design/mockups/wrongbook/02_capture.html` | `/wrongbook/capture` |
| DetailPage | `frontend/apps/h5/src/pages/Detail/Detail.module.css` | `frontend/apps/h5/src/pages/Detail/index.tsx` | `design/mockups/wrongbook/06_wrongbook_detail.html` | `/wrongbook/:id` |

---

## 工具参考

**compliance check**：
```bash
bash .claude/skills/fe-builder/scripts/check-compliance.sh <CSS_FILE> <TSX_FILE>
```

**pixel diff**（V 类修复后验证）：
```bash
node .claude/skills/fe-accept-mock/scripts/pixel-diff.js \
  "<MOCKUP_HTML_PATH>" \
  "http://localhost:<PORT><PAGE_ROUTE>" \
  "design/tasks/acceptance/<PAGE>"
```

---

## 合规替换规则表

| 旧写法 | 应替换为 |
|---|---|
| `#007AFF` | `var(--tkn-color-primary-default)` |
| `#FF3B30` | `var(--tkn-color-danger-default)` |
| `#34C759` | `var(--tkn-color-success-default)` |
| `#FF9500` | `var(--tkn-color-warning-default)` |
| `var(--blue)` | `var(--tkn-color-primary-default)` |
| `var(--red)` | `var(--tkn-color-danger-default)` |

> 完整规则详见 `.claude/skills/fe-builder/references/compliance-rules.md`

**特殊情况**：若色值为装饰色（如 `#fff`、`#000`、`rgba(0,0,0,0.4)` 遮罩）且无对应 token，
按 fe-builder 规范添加为 `.root` 局部变量，并注释说明 `/* no token for X */`。

---

## 执行步骤

### Step 1 · 读取 Gap Report

按优先级查找（B 轨优先）：

```bash
cat design/tasks/acceptance/<PAGE>-gap-report.md 2>/dev/null || \
cat design/tasks/acceptance/<PAGE>-e2e-report.md 2>/dev/null || \
cat design/tasks/acceptance/<PAGE>-diff-report.md 2>/dev/null
```

**Gap report 文件路径说明**：
- B 轨产出：`design/tasks/acceptance/<PAGE>-gap-report.md`
- C 轨产出：`design/tasks/acceptance/<PAGE>-diff-report.md`
- A 轨产出：`design/tasks/acceptance/<PAGE>-e2e-report.md`

若都不存在，HALT：
```
⛔ 未找到 <PAGE> 的 gap report。
   请先运行 /fe-accept-mock <PAGE>（B 轨）生成 gap report。
```

**从 Markdown 中提取失败项**：
- 合规 Gate 失败项：从"设计系统合规"区块的 ❌ 行提取
- 视觉偏差超阈值项：从"视觉保真"区块找 ❌ 行，提取 diff_pct 和截图路径
- AC 失败项：从"业务完整性"或"AC 覆盖矩阵"区块找 ❌/⚠️ 行

若无任何失败项，打印并退出：
```
✅ <PAGE> gap report 无需修复，所有项通过。
```

---

### Step 2 · 问题分类

将提取的失败项归类为 M / V / B：

| 类型 | 触发条件 | AI 自主度 |
|---|---|---|
| M 类（Mechanical） | Gate 1–4 任意失败（硬编码色值 / 缺 testid / 旧 iOS 变量） | 全自主，无需人判断 |
| V 类（Visual） | pixel diff ❌（超阈值） | 人决定接受或指定修复方向，AI 执行 |
| B 类（Business） | AC ❌ / ⚠️ | 先归因（前端/后端），前端 bug 自修，后端 bug 开 issue |

若 `[type]` 参数指定了 m/v/b，只处理对应类型，跳过其他类型并打印说明。

---

### Step 3 · M 类修复（机械修复，全自主）

对每个 M 类问题按以下流程处理：

```
a. 定位问题：从合规检查输出提取具体文件 + 行号
b. 分析原因：硬编码色值 / 缺 testid / 旧 iOS 变量
c. 执行修复：
   - 硬编码色值 → 替换为对应 token（参考上方合规替换规则表）
   - 旧 iOS 变量 → 替换为新 token
   - 缺 testid → 添加 data-testid 属性
d. 全页回归：
   bash .claude/skills/fe-builder/scripts/check-compliance.sh <CSS> <TSX>
e. 通过 → git add + commit（message 格式：fix(M): <页面>·<问题描述>）
f. 仍失败 → 重试（记录重试次数）
g. 超 3 次 → 升级 User（停止，说明卡在哪里）
```

---

### Step 4 · V 类修复（视觉修复，人决定方向）

```
a. 展示 diff 截图路径给 User：
   "V 类问题：<PAGE> pixel diff = <PCT>%（阈值 <THRESHOLD>%）
    Diff 截图：design/tasks/acceptance/<PAGE>/diff.png

    请查看截图后告诉我：
    1. 接受偏差（标注为已知偏差，此问题不再修复）
    2. 不接受，描述你看到的问题（颜色偏差/布局偏移/其他）"

b. 等待 User 回复（⏸ 暂停，不自动执行）

c. User 选择"接受" → 在 gap report 末尾追加记录：
   "V 类偏差（<PCT>%）已由 User 标注为已知偏差，接受。日期：<DATE>"
   → 结束 V 类处理

d. User 描述问题 → 执行 fe-builder 定向修复指定区块
   → 重跑 pixel diff
   → 在阈值内 → commit
   → 超阈值 → 重试（记录次数）
   → 超 3 次 → 升级 User

e. 每次修复后全页回归 check-compliance.sh（防改 CSS 破坏合规）
```

---

### Step 5 · B 类修复（业务归因）

对每个 AC 失败项执行三连归因：

**归因 ①：testid 在 DOM 中是否存在？**

用 Playwright inline 脚本检查：
```js
const elem = await page.$('[data-testid="<TESTID>"]');
```
- 存在 → 继续归因 ②
- 不存在 → 前端 bug（testid 缺失） → 进入前端修复流程

**归因 ②：对应 API 请求是否发送？**

检查方式：在 gap report / e2e report 中查看 AC 失败的 Network 日志描述。
若 report 中未记录网络状态，说明需要 A 轨（e2e）才能归因，提示 User。

**归因 ③：后端响应是否正常？**

若 A 轨 report 记录了 HTTP 状态码：
- 5xx → 后端 bug → 创建 issue
- 4xx（非 401/403）→ 可能是请求格式错误（前端 bug）
- 2xx 但数据不对 → 后端业务逻辑 bug → 创建 issue

**前端 bug 处理**：
```
→ 分析 TSX 代码，找到问题（testid 位置错误 / 路由逻辑 / 状态更新）
→ 修复 TSX
→ 用 Playwright 重验 AC
→ 通过 → commit（message 格式：fix(B): <页面>·<AC_ID>·<问题描述>）
→ 失败 → 重试（最多 3 次）
→ 超限 → 升级 User
```

**后端 bug 处理**：

创建 issue 记录文件：
- 路径：`design/tasks/issues/<PAGE>-<AC_ID>-<DATE>.md`
- 内容格式：

```markdown
## 后端 Issue · <AC_ID>

**现象**：<AC 期望行为> 未达成
**归因**：<HTTP 状态码 / 字段缺失 / 业务逻辑错误>
**影响**：前端已实现，但无法端到端验证
**处理**：移交后端团队
```

打印提示并退出前端修复流程（**不在前端加 fallback**）。

---

### Step 6 · 修复完成汇总

每种类型处理完后，输出总结：

```
✅ fe-repair 完成 · <PAGE>

   M 类修复：<N> 项 ✅ / <N> 项升级 User ⚠️
   V 类修复：<N> 项 ✅ / <N> 项 User 接受偏差 / <N> 项升级 User ⚠️
   B 类修复：<N> 项前端 ✅ / <N> 项后端 issue 已创建 / <N> 项升级 User ⚠️

   修复历史已追加到：design/tasks/acceptance/<PAGE>-gap-report.md

   建议：运行 /fe-accept-mock <PAGE> 重新验收确认修复效果。
```

---

## 全局守则（硬约束）

1. **V 类修复必须等 User 决策**，禁止自行决定"接受偏差"
2. **B 类后端 bug 禁止在前端加 fallback**，必须创建 issue 并退出
3. **每次修复后必须全页回归 check-compliance.sh**（防修 A 破 B）
4. **同一区块最多重试 3 次**，超限必须升级 User，停止重试
5. **修复历史必须追加到 gap report**（不覆盖原内容）
