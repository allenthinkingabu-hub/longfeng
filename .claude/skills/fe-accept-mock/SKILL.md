---
name: fe-accept-mock
description: >
  Stage 3 验收 · B 轨（每 PR）。用 MSW 拦截 API 请求，驱动前端渲染真实数据，
  通过 Playwright 截图 pixel diff 量化视觉偏差，检查 testid 可见性，模拟关键交互路径，
  最终输出标准 gap report 供 User 决策。
  触发场景：用户说 "/fe-accept-mock 页面名"、"跑 B 轨验收"、"做 MSW 验收"、
  "生成 gap report"，或在 fe-builder 完成后准备提 PR 前。
---

# fe-accept-mock · MSW 验收轨（Stage 3 B 轨）

## 目标

在不启动后端的情况下，用 MSW fixture 驱动页面渲染，对三个维度进行量化验证：
1. **视觉保真** — Playwright pixel diff（mockup HTML vs 实现页面）
2. **设计系统合规** — 4 gate grep 检查
3. **业务完整性（MSW 范围）** — testid 可见 + 关键交互路径可达

产出：`design/tasks/acceptance/<PAGE>-gap-report.md`

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

### Step 1 · 读取 build-spec.json

```bash
cat design/tasks/preflight/<PAGE>-build-spec.json
```

提取：
- `blocks[].testids` — 本次要检查的所有 testid 列表（汇总成数组）
- `blocks[].ac` — 对应的 AC ID
- `mockup_source` — mockup HTML 路径

如果 build-spec.json 不存在，打印提示并 HALT：
```
⛔ build-spec.json 未找到。
   请先运行 /fe-preflight <PAGE> 生成施工图后再验收。
```

---

### Step 2 · 运行合规检查（Gate 1–4）

```bash
bash .claude/skills/fe-builder/scripts/check-compliance.sh \
  <CSS_FILE> \
  <TSX_FILE>
```

记录输出，合规评分写入 gap report 的"设计系统合规"区块。

即使不通过也**继续执行**后续步骤（不 HALT），在 gap report 里标记为 ❌ 并提示修复。

---

### Step 3 · 检查前置依赖

检查 `frontend/apps/h5/package.json`（`devDependencies` + `dependencies`）中是否包含：
- `msw` — MSW 本体
- `@playwright/test` 或 `playwright` — Playwright
- `pixelmatch` — pixel diff 算法
- `pngjs` — PNG 处理

```bash
cd frontend/apps/h5 && node -e "const p=require('./package.json'); const deps={...p.dependencies,...p.devDependencies}; const need=['msw','playwright','pixelmatch','pngjs']; const missing=need.filter(x=>!deps[x]&&!deps['@playwright/test']||!deps[x]); console.log(JSON.stringify({deps: Object.fromkeys ? {} : {msw:!!deps['msw'],playwright:!!(deps['playwright']||deps['@playwright/test']),pixelmatch:!!deps['pixelmatch'],pngjs:!!deps['pngjs']}}))"
```

**更简单的方法**：直接 `cat` package.json 后逐项 grep。

若缺失任一依赖，打印 SETUP REQUIRED 并 **HALT**：

```
⚠️  SETUP REQUIRED · 缺少前置依赖

请在 frontend/apps/h5 目录下运行：

  pnpm add -D msw @playwright/test pixelmatch pngjs
  npx playwright install chromium

完成后重新运行 /fe-accept-mock <PAGE>。
```

---

### Step 4 · 检查 / 创建 MSW Handler

检查 MSW handler 是否存在：

```bash
ls frontend/apps/h5/src/__mocks__/handlers/
```

**若存在** 对应页面的 handler 文件（如 `wrongbook.ts`），直接使用，跳到 Step 5。

**若不存在**，根据 `design/analysis/s7-business-analysis.yml` 中的 API 契约，
创建 fixture + handler。具体规则：

#### 4a · 确认 API 端点

从 `s7-business-analysis.yml` 中找该页面用到的 HTTP API（`api_contract` 字段）。

#### 4b · 创建 fixture JSON

路径：`frontend/apps/h5/src/__mocks__/fixtures/<page-slug>.json`

fixture 要包含**足够渲染所有 UI 区块**的数据：
- 列表至少 3 条，覆盖不同学科色
- 详情要包含 `tags`、`explanation` 等可选字段
- 状态变量（`mastery`）要有变化

#### 4c · 创建 Handler 文件

路径：`frontend/apps/h5/src/__mocks__/handlers/<page-slug>.ts`

```typescript
import { http, HttpResponse } from 'msw';
import fixture from '../fixtures/<page-slug>.json';

export const <pageslug>Handlers = [
  http.get('/api/v1/wrongbook/items', () => {
    return HttpResponse.json(fixture.listResponse);
  }),
  // ... 其余端点
];
```

#### 4d · 注册到 browser.ts

检查 `frontend/apps/h5/src/__mocks__/browser.ts` 是否存在，若不存在则创建：

```typescript
import { setupWorker } from 'msw/browser';
import { <pageslug>Handlers } from './handlers/<page-slug>';

export const worker = setupWorker(...<pageslug>Handlers);
```

若已存在，将新 handler 追加到导入和参数列表。

#### 4e · 确认 MSW 在 Vite 中激活

检查 `frontend/apps/h5/src/main.tsx` 是否包含 MSW 启动逻辑。若不包含，添加：

```typescript
if (import.meta.env.DEV) {
  const { worker } = await import('./__mocks__/browser');
  await worker.start({ onUnhandledRequest: 'bypass' });
}
```

---

### Step 5 · 启动 Vite Dev Server

检查端口（默认 5173）是否已在监听：

```bash
lsof -i :<PORT> | grep LISTEN
```

若未启动，在后台启动：

```bash
cd frontend/apps/h5 && pnpm dev --port <PORT> &
```

等待端口就绪（最多 30 秒，每 2 秒检查一次）：

```bash
for i in $(seq 1 15); do
  curl -s http://localhost:<PORT> > /dev/null && echo "ready" && break
  sleep 2
done
```

---

### Step 6 · Pixel Diff（mockup vs 实现）

```bash
node .claude/skills/fe-accept-mock/scripts/pixel-diff.js \
  "<MOCKUP_HTML_PATH>" \
  "http://localhost:<PORT><PAGE_ROUTE>" \
  "design/tasks/acceptance/<PAGE>"
```

解析 JSON 输出，提取 `diff_pct`。

**判定阈值**（来自方法论）：

| 页面 | 接受阈值 |
|---|---|
| ListPage | ≤ 8% |
| DetailPage | ≤ 15% |
| CapturePage | ≤ 20% |

---

### Step 7 · Testid 可见性检查

从 Step 1 汇总的 testid 列表中，运行：

```bash
node .claude/skills/fe-accept-mock/scripts/check-testids.js \
  "http://localhost:<PORT><PAGE_ROUTE>" \
  '<TESTIDS_JSON_ARRAY>'
```

解析 JSON 输出，记录每个 testid 的 `visible: true/false`。

---

### Step 8 · 关键交互路径验证

根据页面类型，用 Playwright 模拟以下路径（用 inline Node.js 脚本执行）：

**ListPage：**
- 点击列表卡片 → 验证路由变为 `/wrongbook/:id`
- 点击筛选图标 → 验证筛选 Sheet 显示（`wrongbook.list.filter-sheet` 可见）

**DetailPage：**
- 点击标签编辑 → 验证标签 Sheet 显示
- 点击掌握度按钮 → 验证按钮状态变化

**CapturePage：**
- 验证相机区块可见（`capture.camera.preview` 可见）

每条路径记录 ✅ / ❌。

---

### Step 9 · 生成 Gap Report

输出路径：`design/tasks/acceptance/<PAGE>-gap-report.md`

```markdown
# Gap Report · <PAGE> · <DATE>

> 轨道：B 轨（MSW）· 生成于 <DATETIME>
> 实现路由：http://localhost:<PORT><PAGE_ROUTE>
> 参照 Mockup：<MOCKUP_HTML_PATH>

---

## 一、视觉保真

| 区块 | 偏差 % | 阈值 | 结论 | 截图 |
|---|---|---|---|---|
| 整页（首屏） | <DIFF_PCT>% | ≤ <THRESHOLD>% | ✅/❌ | [diff](../../design/tasks/acceptance/<PAGE>/diff.png) |

> 截图路径：
> - Mockup：`design/tasks/acceptance/<PAGE>/mockup.png`
> - 实现：`design/tasks/acceptance/<PAGE>/impl.png`
> - Diff：`design/tasks/acceptance/<PAGE>/diff.png`

---

## 二、设计系统合规

- Gate 1 硬编码色值：<N> 处 ✅/❌
- Gate 2 旧 iOS 变量：<N> 处 ✅/❌
- Gate 3 内联品牌色：<N> 处 ✅/❌
- Gate 4 testid 覆盖：<N> 处 ✅/❌
- **整体合规评分：<N>/4** ✅/❌

---

## 三、业务完整性（MSW 范围）

### Testid 可见性

| testid | 可见 | 对应 AC |
|---|---|---|
| <TESTID> | ✅/❌ | <AC_ID> |

### 交互路径

| 路径描述 | 结论 | 备注 |
|---|---|---|
| 列表卡片 → 详情路由 | ✅/❌ | |
| 筛选 Sheet 开关 | ✅/❌ | |

---

## 四、超出 MSW 范围（待真实后端）

| 功能 | AC | 原因 |
|---|---|---|
| OCR 识别流程 | SC-01.AC-1 | 需真实 OCR 服务 |
| SSE 讲解流 | SC-03.AC-1 | 需真实 LLM 流式接口 |
| 数据持久化 | SC-02.AC-3 | 刷新后 fixture 不持久 |

---

## 五、User 决策

- [ ] 接受当前视觉偏差（<DIFF_PCT>%，阈值 <THRESHOLD>%）
- [ ] 接受合规评分（<N>/4）
- [ ] 接受 OCR/SSE 延迟验证（A 轨 Sprint 联调时验）
- [ ] 打回 Builder 修复指定区块：____
```

---

### Step 10 · 汇总打印并等待 User 决策

```
✅ fe-accept-mock 完成 · <PAGE>

   视觉偏差：    <DIFF_PCT>%（阈值 <THRESHOLD>%）<PASS/FAIL>
   合规评分：    <N>/4 <PASS/FAIL>
   Testid 覆盖：<PASS_COUNT>/<TOTAL_COUNT>
   交互路径：    <PASS_COUNT>/<TOTAL_COUNT>

   Gap Report：design/tasks/acceptance/<PAGE>-gap-report.md
   截图目录：   design/tasks/acceptance/<PAGE>/

⏸  请 User 审阅 gap report 后决策：接受偏差 or 打回 Builder。
```

---

## 硬约束

1. **不修改任何源码** — 只读取和验证，不动 `frontend/apps/` 任何文件（MSW handler 创建除外）
2. **不跳过 HALT 条件** — build-spec 缺失和依赖缺失时必须停止
3. **pixel diff 脚本输出必须是 JSON** — 不凭视觉判断，只信数字
4. **gap report 必须基于实际测量值填写** — 不填 "大约"、"估计" 等主观描述
5. **等待 User 决策** — 不自行宣布 "验收通过"，决策权在 User
