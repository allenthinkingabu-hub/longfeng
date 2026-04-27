---
name: fe-accept-diff
description: >
  Stage 3 验收 · C 轨（随时）。无需 MSW 或后端，直接对 Vite dev server 渲染截图，
  与 mockup HTML 做 pixel diff，输出轻量视觉结构 gap report。
  触发场景：用户说 "/fe-accept-diff 页面名"、"跑 C 轨"、"快速截图对比"、
  "只验视觉结构"。
---

# fe-accept-diff · 纯截图验收轨（Stage 3 C 轨）

## 目标

在不依赖 MSW 或任何后端的情况下，直接对 Vite dev server 的渲染结果截图，
与 mockup HTML 做 pixel diff，快速判断是否存在明显布局 / 颜色问题。

C 轨定位：**视觉结构快速检查**，不验业务逻辑。随时可跑，无前置依赖。

产出：`design/tasks/acceptance/<PAGE>-diff-report.md`

---

## 输入

| 参数 | 说明 | 示例 |
|---|---|---|
| `<PAGE>` | 页面名称 | ListPage / CapturePage / DetailPage |
| `--port` | Vite dev server 端口（可选，默认 5174）· h5 app 实际跑在 5174，5173 是 prototype | 5174 |

页面默认映射：

| 页面 | 路由 | Mockup HTML | CSS | TSX |
|---|---|---|---|---|
| ListPage | `/wrongbook` | `design/mockups/wrongbook/05_wrongbook_list.html` | `frontend/apps/h5/src/pages/List/List.module.css` | `frontend/apps/h5/src/pages/List/index.tsx` |
| CapturePage | `/wrongbook/capture` | `design/mockups/wrongbook/02_capture.html` | `frontend/apps/h5/src/pages/Capture/Capture.module.css` | `frontend/apps/h5/src/pages/Capture/index.tsx` |
| DetailPage | `/wrongbook/:id` | `design/mockups/wrongbook/06_wrongbook_detail.html` | `frontend/apps/h5/src/pages/Detail/Detail.module.css` | `frontend/apps/h5/src/pages/Detail/index.tsx` |

---

## 执行步骤（严格按序）

### Step 1 · 确认页面映射

根据 `<PAGE>` 参数查上表，得到：
- `MOCKUP_HTML_PATH` — mockup HTML 文件路径
- `PAGE_ROUTE` — 页面路由
- `CSS_FILE` — CSS Module 文件路径
- `TSX_FILE` — TSX 文件路径

若页面名**不在映射表中**，打印提示并 **HALT**：

```
⛔ 未知页面：<PAGE>
   支持的页面：ListPage / CapturePage / DetailPage
   用法：/fe-accept-diff ListPage
```

---

### Step 2 · 运行合规检查（不因失败 HALT）

```bash
bash .claude/skills/fe-builder/scripts/check-compliance.sh \
  <CSS_FILE> \
  <TSX_FILE>
```

记录各 Gate 的计数，写入 diff report 的"设计系统合规"区块。
即使不通过也**继续执行**后续步骤（不 HALT），在报告里标记为 ❌。

---

### Step 3 · 检查前置依赖

检查 `frontend/apps/h5/package.json`（`devDependencies` + `dependencies`）中是否包含：
- `@playwright/test` 或 `playwright` — Playwright（用于截图）
- `pixelmatch` — pixel diff 算法
- `pngjs` — PNG 处理

若缺失任一依赖，打印安装命令并 **HALT**：

```
⚠️  SETUP REQUIRED · 缺少前置依赖

请在 frontend/apps/h5 目录下运行：

  pnpm add -D @playwright/test pixelmatch pngjs
  npx playwright install chromium

完成后重新运行 /fe-accept-diff <PAGE>。
```

---

### Step 4 · 检查 / 启动 Vite Dev Server

检查端口（默认 5174）是否已在监听：

```bash
lsof -i :<PORT> | grep LISTEN
```

若未运行，在后台启动：

```bash
cd frontend/apps/h5 && pnpm dev --port <PORT> &
```

轮询等待就绪（最多 30 秒，每 2 秒检查一次）：

```bash
for i in $(seq 1 15); do
  curl -s http://localhost:<PORT> > /dev/null && echo "ready" && break
  sleep 2
done
```

若 30 秒内未就绪，打印提示并 **HALT**：

```
⛔ Vite Dev Server 未在 <PORT> 端口就绪（超时 30s）。
   请手动运行：cd frontend/apps/h5 && pnpm dev --port <PORT>
   确认启动后重新运行 /fe-accept-diff <PAGE>。
```

---

### Step 5 · 运行 Pixel Diff

复用 B 轨脚本：

```bash
node .claude/skills/fe-accept-mock/scripts/pixel-diff.js \
  "<MOCKUP_HTML_PATH>" \
  "http://localhost:<PORT><PAGE_ROUTE>" \
  "design/tasks/acceptance/<PAGE>"
```

解析 JSON 输出，提取 `diff_pct`。

截图自动保存到 `design/tasks/acceptance/<PAGE>/`：
- `mockup.png` — mockup HTML 截图
- `impl.png` — 实现页面截图
- `diff.png` — 差异图

**判定阈值**：

| 页面 | 接受阈值 |
|---|---|
| ListPage | ≤ 8% |
| DetailPage | ≤ 15% |
| CapturePage | ≤ 20% |

---

### Step 6 · 生成 Diff Report

输出路径：`design/tasks/acceptance/<PAGE>-diff-report.md`

```markdown
# Diff Report · <PAGE> · <DATE>

> 轨道：C 轨（纯截图）· 生成于 <DATETIME>
> 实现路由：http://localhost:<PORT><PAGE_ROUTE>
> 参照 Mockup：<MOCKUP_HTML_PATH>

---

## 视觉保真

| 项目 | 值 | 阈值 | 结论 |
|---|---|---|---|
| 整页 pixel diff | <DIFF_PCT>% | ≤ <THRESHOLD>% | ✅/❌ |

截图：
- Mockup：`design/tasks/acceptance/<PAGE>/mockup.png`
- 实现：`design/tasks/acceptance/<PAGE>/impl.png`
- Diff：`design/tasks/acceptance/<PAGE>/diff.png`

## 设计系统合规（快速）

- Gate 1 硬编码色值：<N> 处 ✅/❌
- Gate 2 旧 iOS 变量：<N> 处 ✅/❌
- Gate 3 内联品牌色：<N> 处 ✅/❌
- Gate 4 testid 覆盖：<N> 处 ✅/❌

## 注意

> C 轨**不检查**：MSW 数据渲染、testid 可见性、交互路径、业务 AC。
> 如需完整验收，运行 `/fe-accept-mock <PAGE>`（B 轨）或 `/fe-accept-e2e <PAGE>`（A 轨）。

## User 决策

- [ ] 接受当前视觉偏差（<DIFF_PCT>%，阈值 <THRESHOLD>%）
- [ ] 打回 Builder 修复：____
```

---

### Step 7 · 汇总打印

```
✅ fe-accept-diff 完成 · <PAGE>

   视觉偏差：<DIFF_PCT>%（阈值 <THRESHOLD>%）<PASS/FAIL>
   合规评分：<N>/4

   Diff Report：design/tasks/acceptance/<PAGE>-diff-report.md
   截图目录：  design/tasks/acceptance/<PAGE>/

⏸  C 轨仅验视觉结构。如需业务验收，运行 /fe-accept-mock 或 /fe-accept-e2e。
```

---

## 硬约束

1. **不修改任何源码** — check-compliance.sh 结果仅记录到报告，不触发修复
2. **pixel diff 只信数字** — 不凭视觉主观判断，只读 `diff_pct` 字段
3. **不宣布"验收通过"** — 决策权在 User，结论仅标 ✅/❌
4. **diff_pct 超阈值只标 ❌** — 不自动触发修复流程，等待 User 决策
