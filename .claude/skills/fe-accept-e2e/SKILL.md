---
name: fe-accept-e2e
description: >
  Stage 3 验收 · A 轨（每 Sprint）。启动真实后端，通过 Playwright e2e 验证
  OCR、SSE 流、数据持久化、游标分页等需要真实服务的业务 AC。
  触发场景：用户说 "/fe-accept-e2e 页面名"、"跑 A 轨"、"跑 e2e"、"联调验收"、
  "完整 e2e"。需要先确认后端服务已启动。
---

# fe-accept-e2e · 真实后端 E2E 验收轨（Stage 3 A 轨）

## 目标

连接真实后端服务，通过 Playwright e2e 验证无法用 MSW 覆盖的核心业务能力：
- OCR 识别（file-service + ai-analysis-service）
- SSE 流式讲解（LLM 真实调用）
- 数据持久化（DB 写入 + 刷新验证）
- 游标分页（真实 API 游标）
- 跨服务数据流（删除 → 列表同步）

产出：`design/tasks/acceptance/<PAGE>-e2e-report.md`

---

## 基础设施概览

| 服务 | 地址 | 说明 |
|---|---|---|
| PostgreSQL | localhost:15432 | 主数据库 |
| Redis | localhost:16379 | 缓存 / Session |
| RocketMQ | localhost:19876 | 消息队列 |
| MinIO | localhost:19000 | 对象存储（图片） |
| gateway | localhost:8080 | API 入口（A 轨关键节点） |
| wrongbook-service | localhost:8081 | 错题核心服务 |
| ai-analysis-service | localhost:8082 | AI 讲解 / 分析 |
| file-service | localhost:8083 | 文件上传 / OCR |
| review-plan-service | localhost:8084 | 复习计划 |
| Vite dev server | localhost:5174 | 前端（proxy → gateway:8080） |

---

## 输入

| 参数 | 说明 | 示例 |
|---|---|---|
| `<PAGE>` | 页面名称 | ListPage / CapturePage / DetailPage |

页面默认映射：

| 页面 | 路由 | build-spec 路径 |
|---|---|---|
| ListPage | `/wrongbook` | `design/tasks/preflight/ListPage-build-spec.json` |
| CapturePage | `/wrongbook/capture` | `design/tasks/preflight/CapturePage-build-spec.json` |
| DetailPage | `/wrongbook/:id` | `design/tasks/preflight/DetailPage-build-spec.json` |

---

## A 轨核心验证 AC

| AC | 功能 | 需要真实后端的原因 |
|---|---|---|
| SC-01.AC-1 | 相机拍题 → OCR 识别 → stem_text | 需真实 OCR 服务（file-service + ai-analysis） |
| SC-03.AC-1 | AI 讲解 SSE 流 → 文字渐现 | 需真实 LLM 调用 |
| SC-02.AC-3 | 标签保存 → 刷新后持久 | 需真实 DB 写入 |
| SC-08.AC-3 | 游标分页加载更多 | 需真实 API 游标 |
| SC-04.AC-1 | 删除题目 → 从列表消失 | 需真实软删除 |

---

## 执行步骤（严格按序）

### Step 1 · 读取 build-spec.json

```bash
cat design/tasks/preflight/<PAGE>-build-spec.json
```

提取：
- `blocks[].testids` — 本次要检查的所有 testid 列表
- `blocks[].ac` — 对应的 AC ID

若 build-spec.json 不存在，打印提示并 **HALT**：

```
⛔ build-spec.json 未找到。
   请先运行 /fe-preflight <PAGE> 生成施工图后再验收。
```

---

### Step 2 · 后端健康检查

检查所有必要服务是否运行：

```bash
# 检查基础设施容器
docker ps --format "{{.Names}}\t{{.Status}}" | grep -E "postgres|redis|rocketmq|nacos|minio"

# 检查 gateway（最关键）
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health || \
curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/health

# 检查 wrongbook-service
curl -s -o /dev/null -w "%{http_code}" http://localhost:8081/actuator/health
```

若 gateway **未响应**（非 2xx），打印诊断信息并 **HALT**（此为硬约束，不可跳过）：

```
⛔ 后端服务未就绪，A 轨无法继续。

检查项：
  1. Docker 基础设施是否启动？
     → cd infrastructure && docker compose up -d
  2. wrongbook-service 是否启动？
     → 检查 Java 服务进程：lsof -i :8081
  3. gateway 是否可达？
     → curl http://localhost:8080/actuator/health

后端就绪后重新运行 /fe-accept-e2e <PAGE>。
如需无后端验收，请使用 /fe-accept-mock（B 轨）。
```

---

### Step 3 · 运行合规检查

```bash
bash .claude/skills/fe-builder/scripts/check-compliance.sh <CSS_FILE> <TSX_FILE>
```

CSS/TSX 文件路径按页面映射：

| 页面 | CSS | TSX |
|---|---|---|
| ListPage | `frontend/apps/h5/src/pages/List/List.module.css` | `frontend/apps/h5/src/pages/List/index.tsx` |
| CapturePage | `frontend/apps/h5/src/pages/Capture/Capture.module.css` | `frontend/apps/h5/src/pages/Capture/index.tsx` |
| DetailPage | `frontend/apps/h5/src/pages/Detail/Detail.module.css` | `frontend/apps/h5/src/pages/Detail/index.tsx` |

不因失败 HALT，记录合规评分到 E2E Report。

---

### Step 4 · 检查前置依赖

检查 `frontend/apps/h5/package.json`（`devDependencies` + `dependencies`）中是否包含 `@playwright/test` 或 `playwright`。

若缺失，打印提示并 **HALT**：

```
⚠️  SETUP REQUIRED · 缺少 Playwright 依赖

请在 frontend/apps/h5 目录下运行：

  pnpm add -D @playwright/test
  npx playwright install chromium

完成后重新运行 /fe-accept-e2e <PAGE>。
```

---

### Step 5 · 确认 Vite Dev Server

```bash
lsof -i :5174 | grep LISTEN
```

若未运行，在后台启动并等待就绪（最多 30s）：

```bash
cd frontend/apps/h5 && pnpm dev --port 5174 &

for i in $(seq 1 15); do
  curl -s http://localhost:5174 > /dev/null && echo "Vite ready" && break
  sleep 2
done
```

注意：前端必须配置 proxy → gateway:8080，否则真实 API 调用无法到达后端。
检查 `vite.config.ts` 中是否存在 proxy 配置。

---

### Step 6 · Playwright E2E 测试

按 AC 分组执行，每个 AC 用 inline Node.js + Playwright 脚本测试。

每个测试用 ✅ / ⚠️ / ❌ 标注：
- ✅ 通过
- ⚠️ 部分通过（如 SSE 流有数据但速度异常、超时但有内容）
- ❌ 失败（附失败原因）

---

#### AC SC-08 · 列表渲染 + 游标分页（ListPage 必测）

```javascript
// inline Node.js + Playwright
const { chromium } = require('@playwright/test');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();

  // 1. 访问列表页，验证卡片渲染
  await page.goto('http://localhost:5174/wrongbook');
  await page.waitForSelector('[data-testid="wrongbook.list.item-card"]', { timeout: 10000 });
  // ✅ 列表卡片可见

  // 2. 点击卡片，验证路由跳转
  await page.click('[data-testid="wrongbook.list.item-card"]:first-child');
  await page.waitForURL(/\/wrongbook\/\w+/, { timeout: 5000 });
  // ✅ 路由变为 /wrongbook/:id

  // 3. 返回列表，测试游标分页
  await page.goto('http://localhost:5174/wrongbook');
  const initialCount = await page.locator('[data-testid="wrongbook.list.item-card"]').count();
  await page.click('[data-testid="wrongbook.list.load-more"]');
  await page.waitForTimeout(2000);
  const newCount = await page.locator('[data-testid="wrongbook.list.item-card"]').count();
  // ✅ newCount > initialCount → 游标分页追加新卡片

  // 4. 切换掌握度筛选
  await page.click('[data-testid="wrongbook.list.filter-mastery"]');
  await page.waitForTimeout(1000);
  // ✅ 列表按筛选条件更新

  await browser.close();
})();
```

---

#### AC SC-02.AC-3 · 标签持久化（DetailPage 必测）

```javascript
const { chromium } = require('@playwright/test');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();

  // 获取一个有效的题目 ID
  await page.goto('http://localhost:5174/wrongbook');
  await page.waitForSelector('[data-testid="wrongbook.list.item-card"]');
  await page.click('[data-testid="wrongbook.list.item-card"]:first-child');
  const url = page.url();
  const itemId = url.split('/').pop();

  // 点击标签编辑按钮
  await page.click('[data-testid="wrongbook.detail.tag-edit"]');
  await page.waitForSelector('[data-testid="wrongbook.detail.tag-sheet"]', { timeout: 5000 });

  // 修改标签（点击一个标签 toggle）
  await page.click('[data-testid="wrongbook.detail.tag-option"]:first-child');
  await page.click('[data-testid="wrongbook.detail.tag-save"]');
  await page.waitForTimeout(1000);

  // 刷新页面，验证标签持久化
  await page.goto(`http://localhost:5174/wrongbook/${itemId}`);
  await page.waitForSelector('[data-testid="wrongbook.detail.tag-list"]', { timeout: 5000 });
  // ✅ 刷新后标签仍然存在 → 持久化验证通过

  await browser.close();
})();
```

---

#### AC SC-03.AC-1 · SSE 讲解流（DetailPage 必测）

等待上限 **30s**，超时记 ⚠️。

```javascript
const { chromium } = require('@playwright/test');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();

  // 访问详情页
  await page.goto('http://localhost:5174/wrongbook');
  await page.waitForSelector('[data-testid="wrongbook.list.item-card"]');
  await page.click('[data-testid="wrongbook.list.item-card"]:first-child');

  // 等待讲解区块出现
  await page.waitForSelector('[data-testid="wrongbook.detail.explain-stream"]', { timeout: 10000 });

  // 轮询文本长度增长（验证流式渐进显示）
  let prevLen = 0;
  let growing = false;
  for (let i = 0; i < 15; i++) {
    await page.waitForTimeout(2000);
    const text = await page.locator('[data-testid="wrongbook.detail.explain-stream"]').innerText();
    if (text.length > prevLen) {
      growing = true;
      prevLen = text.length;
    }
  }
  // ✅ growing === true → SSE 流正在渐进输出
  // ⚠️ 若 30s 后 growing 为 false 但有内容 → 流已完成但测试进入时已结束
  // ❌ 若无内容 → SSE 未触发

  await browser.close();
})();
```

---

#### AC SC-01.AC-1 · OCR 拍题识别（CapturePage 专属）

等待上限 **60s**，超时记 ⚠️。

```javascript
const { chromium } = require('@playwright/test');
const path = require('path');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();

  await page.goto('http://localhost:5174/wrongbook/capture');
  await page.waitForSelector('[data-testid="capture.upload-btn"]', { timeout: 5000 });

  // 上传测试图片
  const fileInput = page.locator('input[type="file"]');
  await fileInput.setInputFiles(path.resolve('design/test-assets/sample-question.jpg'));

  // 等待解析完成（最多 60s）
  await page.waitForSelector('[data-testid="capture.upload-progress"]', { timeout: 5000 });
  await page.waitForFunction(
    () => {
      const el = document.querySelector('[data-testid="capture.upload-progress"]');
      return el && !el.textContent.includes('analyzing');
    },
    { timeout: 60000 }
  );

  // 验证 stem_text 非空
  const stemText = await page.locator('[data-testid="capture.stem-text"]').innerText();
  // ✅ stemText.trim().length > 0 → OCR 识别成功
  // ❌ 若超时或 stemText 为空 → OCR 服务未正常响应

  await browser.close();
})();
```

---

#### AC SC-04.AC-1 · 删除题目（DetailPage 必测）

```javascript
const { chromium } = require('@playwright/test');
(async () => {
  const browser = await chromium.launch();
  const page = await browser.newPage();

  // 进入详情页，记录题目 ID
  await page.goto('http://localhost:5174/wrongbook');
  await page.waitForSelector('[data-testid="wrongbook.list.item-card"]');
  await page.click('[data-testid="wrongbook.list.item-card"]:first-child');
  const url = page.url();
  const itemId = url.split('/').pop();

  // 点击删除按钮
  await page.click('[data-testid="wrongbook.detail.delete.btn"]');
  await page.waitForSelector('[data-testid="wrongbook.detail.delete.confirm"]', { timeout: 5000 });
  await page.click('[data-testid="wrongbook.detail.delete.confirm"]');

  // 验证路由跳回列表
  await page.waitForURL('**/wrongbook', { timeout: 5000 });
  // ✅ 路由已跳回 /wrongbook

  // 验证该题在列表中消失
  await page.waitForTimeout(1000);
  const deletedCard = page.locator(`[data-testid="wrongbook.list.item-card"][data-id="${itemId}"]`);
  const isVisible = await deletedCard.isVisible().catch(() => false);
  // ✅ isVisible === false → 软删除生效，列表已同步

  await browser.close();
})();
```

---

### Step 7 · 生成 E2E Report

输出路径：`design/tasks/acceptance/<PAGE>-e2e-report.md`

```markdown
# E2E Report · <PAGE> · <DATE>

> 轨道：A 轨（真实后端）· 生成于 <DATETIME>
> 后端：gateway:8080 · wrongbook-service:8081
> 前端：http://localhost:5174

---

## 一、设计系统合规

- Gate 1 硬编码色值：<N> 处 ✅/❌
- Gate 2 旧 iOS 变量：<N> 处 ✅/❌
- Gate 3 内联品牌色：<N> 处 ✅/❌
- Gate 4 testid 覆盖：<N> 处 ✅/❌
- **整体合规评分：<N>/4** ✅/❌

---

## 二、AC 覆盖矩阵

| AC | 功能 | testid | 测试结果 | 备注 |
|---|---|---|---|---|
| SC-08.AC-1 | 列表渲染 | wrongbook.list.item-card | ✅/❌ | |
| SC-08.AC-3 | 游标分页 | wrongbook.list.load-more | ✅/❌ | |
| SC-02.AC-3 | 标签持久化 | wrongbook.detail.tag-save | ✅/❌ | |
| SC-03.AC-1 | SSE 讲解流 | wrongbook.detail.explain-stream | ✅/⚠️/❌ | |
| SC-01.AC-1 | OCR 识别 | capture.upload-progress | ✅/❌ | |
| SC-04.AC-1 | 删除题目 | wrongbook.detail.delete.btn | ✅/❌ | |

---

## 三、超出前端范围（后端 / 外部服务）

| 现象 | 归因 | 处理 |
|---|---|---|
| SSE 流延迟 >5s | LLM 服务负载 | 后端 issue |

---

## 四、User 决策

- [ ] 接受当前 AC 覆盖率（✅ <N>/<TOTAL>）
- [ ] 接受 ⚠️ 项（标注已知问题）
- [ ] 打回 Builder 修复前端问题：____
- [ ] 开后端 issue：____
```

---

### Step 8 · 汇总打印

```
✅ fe-accept-e2e 完成 · <PAGE>

   合规评分：  <N>/4
   AC 通过：  <PASS>/<TOTAL>（含 ⚠️ <WARN> 项）
   AC 失败：  <FAIL> 项

   E2E Report：design/tasks/acceptance/<PAGE>-e2e-report.md

⏸  请 User 审阅 e2e report 后决策。
```

---

## 硬约束

1. **不修改任何业务源码** — 只读取和验证，不动 `frontend/apps/` 任何文件
2. **gateway 未响应必须 HALT** — 不跳过后端健康检查，不以前端 fallback 代替真实后端验证
3. **SSE 测试等待上限 30s** — 超时记 ⚠️，不无限等待
4. **OCR 测试等待上限 60s** — 超时记 ⚠️，不无限等待
5. **后端 bug 只记录到报告** — 不在前端添加任何 fallback 掩盖后端问题
6. **不自行宣布"验收通过"** — 决策权在 User，等待 User 审阅 e2e report 后决策
