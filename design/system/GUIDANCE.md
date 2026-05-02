# GUIDANCE.md · FE / QA AI Agent 实施 Runbook

> **文档定位**：本文是 **AI Agent 的执行手册**（runbook），告诉前端开发 Agent / QA 测试 Agent **一步一步**怎么从 spec 走到代码 + 测试通过。
>
> **与其他文档的关系**：
> - `STYLE-TRUTH.md` = **真相文档**（设计长什么样的权威依据 · archive 反推）
> - `DESIGN.md` = **设计宪法**（铁律 · token 体系 · mood 5 类 · 页面映射）
> - **`GUIDANCE.md`（本文）= 执行手册**（拿到 spec 后下一步打哪个命令）
> - `pages/{ID}.spec.md` = 单页规格卡（数据 / 状态 / API / AC / testid）
>
> **读者**：fe-builder / fe-preflight / fe-accept-mock / fe-accept-diff / fe-accept-e2e 等 AI Agent · 接手单页开发的人类工程师 / QA。
>
> **铁律**：
> - 本文与 STYLE-TRUTH/DESIGN 冲突时，**以 STYLE-TRUTH 为准**（archive 真相 > 任何抽象描述）。
> - 任一 verify 步不过，**绝不进入下一步**（前端铁律）。
> - 没有命令的"步骤描述"在本文不算合规，每步必须可执行可校验。

---

## §0 工作链全景图

```
┌────────────────────────────────────────────────────────────────────┐
│                        PRE-FLIGHT（人工 / fe-preflight）            │
│  设计师锁 spec.md → fe-preflight 抽 build-spec.json + token mapping │
└──────────────────────────────────┬─────────────────────────────────┘
                                   ↓
┌────────────────────────────────────────────────────────────────────┐
│                      BUILD（fe-builder Agent）                      │
│  本文 §2 是这一段的 runbook                                         │
│  spec.md + STYLE-TRUTH + archive mockup → 生成 React 代码            │
└──────────────────────────────────┬─────────────────────────────────┘
                                   ↓
┌────────────────────────────────────────────────────────────────────┐
│                    ACCEPT（fe-accept Agent · 3 轨）                 │
│  本文 §3 是这一段的 runbook                                         │
│  B 轨 mock 单测（必过） + C 轨像素 diff（参考） + A 轨真实后端 E2E    │
└────────────────────────────────────────────────────────────────────┘
```

---

## §1 前提：必读 3 份文档（Agent 启动前先读）

### 1.1 必读清单（按顺序）

| # | 文档 | 读什么 | 校验方式 |
|---|---|---|---|
| 1 | `design/system/STYLE-TRUTH.md` | §1 哲学 / §2 token 全集 / §3 mood 5 类 / §4 组件库 | `wc -l design/system/STYLE-TRUTH.md` ≈ 700 行；至少读到 §4 |
| 2 | `design/system/DESIGN.md` | §1 八条铁律 / §2 token 三层 / §5 页面映射 | 找到当前页 ID 在 §5.0 矩阵里的 mood 列 |
| 3 | `design/system/pages/{当前页ID}.spec.md` | §3 Block 清单 / §8 AC 表 / §14 token 清单 | 必须能列出本页所有 testid + AC ID |
| 4 | `design/mockups/wrongbook/_archive/{对应文件}.html` | 实际视觉真相 | 找到 spec 的 §13 mockup_canonical 字段 |

### 1.2 启动前 grep 自检（5 秒）

```bash
# 1. 确认 STYLE-TRUTH 存在
test -f design/system/STYLE-TRUTH.md || { echo "❌ STYLE-TRUTH 缺失"; exit 1; }

# 2. 确认目标 spec 存在
test -f "design/system/pages/${PAGE_ID}.spec.md" || { echo "❌ spec 缺失"; exit 1; }

# 3. 确认 token 文件 v2.0
grep -q "v2.0 (archive-aligned)" design/system/tokens/color.json || { echo "❌ token 未对齐 v2.0"; exit 1; }

# 4. 确认 archive mockup 存在（找 spec 里 mockup_canonical 字段）
spec_mockup=$(grep "^mockup_canonical:" design/system/pages/${PAGE_ID}.spec.md | awk '{print $2}')
test -f "$spec_mockup" || test -f "design/mockups/wrongbook/_archive/$(basename $spec_mockup)" \
  || { echo "❌ archive mockup 缺失 · 该页是 archive 缺失页 · 见 STYLE-TRUTH §6"; }
```

---

## §2 FE Agent 实施 Runbook（10 步 · 每步 INPUT/DO/OUTPUT/VERIFY）

### Step 1 · 锁定页面 mood（决定 token 池）

**INPUT**：`PAGE_ID`（如 `P02-capture`）

**DO**：
```bash
# 在 DESIGN.md §5.0 19 页矩阵里查 Mood 列
grep -A 1 "P02 |" design/system/DESIGN.md | head -5
```

**OUTPUT**：`MOOD ∈ {A, B, C, D, E}`，对应（来自 STYLE-TRUTH §3）：
- A `hero+overlap` —— 深蓝 hero + 米白 scroll overlap
- B `pure-warm` —— 米白 + 白卡 + iOS nav
- C `dark-camera` —— 全屏 #0B0F1A + 黄检测
- D `celebrate-green` —— 175deg 绿渐变
- E `teal-observer` —— 青绿主题

**VERIFY**：必须能用一句话回答"本页 mood 是哪个 + 为什么"。回答不出 = 重读 STYLE-TRUTH §3。

---

### Step 2 · 找 archive 参考 mockup

**INPUT**：`PAGE_ID` + 步骤 1 的 `MOOD`

**DO**：
```bash
# 优先找同名 archive
ls design/mockups/wrongbook/_archive/ | grep -i "${PAGE_ID}"

# 找不到（如 P00 缺失）→ 找同 mood 最近邻参考
# Mood A 参考: _archive/01_home.html / 14_landing.html
# Mood B 参考: _archive/04_result.html / 13_settings.html
# Mood C 参考: _archive/02_capture.html
# Mood D 参考: _archive/09_review_done.html
# Mood E 参考: _archive/18_observer.html / 16_shared.html
```

**OUTPUT**：1 个 archive HTML 文件路径（参考样板）

**VERIFY**：
```bash
# 能从 archive 文件中提取 :root 块（确认是项目 token 体系）
grep -c -- "--tkn\|--blue\|--text:" $REFERENCE_ARCHIVE
# > 0 = 有效样板
```

---

### Step 3 · 抽取本页 token 池

**INPUT**：spec.md §14 + STYLE-TRUTH §2 + step 2 的 archive 参考

**DO**：
```bash
# 从 spec 抽 token 清单
awk '/^## §14/,/^---/' design/system/pages/${PAGE_ID}.spec.md > /tmp/page-tokens.txt

# 与 STYLE-TRUTH §2 交叉验证（每个 token 必须能在 color.json/shadow.json/radius.json 找到）
for t in $(grep -oE -- '--tkn-[a-z-]+' /tmp/page-tokens.txt | sort -u); do
  found=$(grep -l "$t" design/system/tokens/*.json | head -1)
  [ -z "$found" ] && echo "❌ $t 在 token 文件中找不到"
done
```

**OUTPUT**：本页能用的 token 全集（grep 友好的清单）

**VERIFY**：每个 token 都能映射回某个 JSON 文件 + 行号。映射不到 = 该 token 待新增到 token 文件。

---

### Step 4 · 抽取本页 Block + testid 清单

**INPUT**：spec.md §3 Block 表

**DO**：
```bash
# 列所有 block 和它们的 testid root
awk '/^## §3/,/^## §4/' design/system/pages/${PAGE_ID}.spec.md | grep -oE '`[bB][0-9]+`|p[0-9-]+-[a-z-]+|[a-z]+-[a-z-]+' | sort -u
```

**OUTPUT**：Block ID + testid root 列表（如 `B1 = p02-topbar / B2 = subject-chip-strip / B3 = p02-viewfinder ...`）

**VERIFY**：
```bash
# spec.§8 AC 表中所有 testid 必须出现在 §3 block 表中
ac_testids=$(awk '/^## §8/,/^## §9/' design/system/pages/${PAGE_ID}.spec.md | grep -oE '`[a-z][a-z0-9-]+-[a-z0-9-]+`' | sort -u)
block_testids=$(awk '/^## §3/,/^## §4/' design/system/pages/${PAGE_ID}.spec.md | grep -oE '`[a-z][a-z0-9-]+-[a-z0-9-]+`' | sort -u)
diff <(echo "$ac_testids") <(echo "$block_testids") || echo "❌ AC 引用了 §3 不存在的 testid"
```

---

### Step 5 · 抽取本页 AC 验收点

**INPUT**：spec.md §8

**DO**：
```bash
# 列所有 AC 行（含 [AI 推测] 标记的也要列）
awk '/^## §8/,/^## §9/' design/system/pages/${PAGE_ID}.spec.md | grep -E '^\| `AC-' | head -30
```

**OUTPUT**：每条 AC 含 4 列（AC ID / 验收点 / 涉及 Block / testid 验证点）

**VERIFY**：每条 AC 至少绑定 1 个 testid（铁律 8）。没有 testid 的 AC = 验收 fail。

---

### Step 6 · 写 React 组件骨架（按 Block 切分）

**INPUT**：Step 2 archive 模板 + Step 3 token 池 + Step 4 Block 清单

**DO**：每个 Block 写一个 `.tsx` 组件文件 + 一个 `.module.css`：
```tsx
// frontend/apps/h5/src/pages/{PAGE_ID}/B1-Topbar.tsx
import styles from './B1-Topbar.module.css';

export function B1Topbar() {
  return (
    <header
      className={styles.topbar}
      data-testid="p02-topbar"  // 从 spec.§3 testid root 字段
      data-mood="C"              // 从 STYLE-TRUTH §3 mood 5 类
      role="banner"              // 从 spec.§12 a11y
    >
      {/* ... */}
    </header>
  );
}
```

```css
/* B1-Topbar.module.css */
.topbar {
  /* ⚠️ 所有视觉值必须 var(--tkn-*) 引用 · 硬编码 = lint fail */
  background: var(--tkn-color-bg-camera);
  color: var(--tkn-color-text-on-dark);
  padding: var(--tkn-spacing-md);
}
```

**OUTPUT**：每个 Block 一对 `.tsx + .module.css` 文件

**VERIFY** （硬编码 lint）：
```bash
# CSS 中不允许出现硬编码 hex/rgb（除白名单）
grep -nE '#[0-9a-fA-F]{3,8}|rgb\(|rgba\(' \
  frontend/apps/h5/src/pages/${PAGE_ID}/*.module.css \
  | grep -v "var(--tkn-" \
  && echo "❌ 硬编码命中 · 改用 var(--tkn-*)"
```

---

### Step 7 · 1:1 像素对齐 archive

**INPUT**：archive HTML（Step 2）+ 已写代码（Step 6）

**DO**：
1. 用 Playwright headless 截图当前实现 → `screenshots/current/${PAGE_ID}.png`
2. 用 Playwright headless 截图 archive HTML → `screenshots/archive/${PAGE_ID}.png`
3. ImageMagick `compare -metric AE` 对比

```bash
npx playwright screenshot --viewport-size=393,852 \
  "file://$(pwd)/design/mockups/wrongbook/_archive/${PAGE_ID}.html" \
  /tmp/archive.png

npx playwright screenshot --viewport-size=393,852 \
  "http://localhost:3000/${PAGE_ROUTE}" \
  /tmp/current.png

compare -metric AE -fuzz 5% /tmp/archive.png /tmp/current.png /tmp/diff.png
```

**OUTPUT**：差异像素数 + diff 图

**VERIFY**：差异 ≤ 200 像素（5% fuzz）。差异过大 = 回 Step 6 调对应组件。

---

### Step 8 · token / hardcode lint（铁律 8）

**INPUT**：Step 6 输出代码

**DO**：
```bash
# 1. 硬编码 hex 检查
grep -rnE '#[0-9a-fA-F]{3,8}' frontend/apps/h5/src/pages/${PAGE_ID}/*.module.css \
  | grep -v "var(--tkn-" \
  | grep -v "/\*" \
  || echo "✓ 无硬编码 hex"

# 2. 硬编码 px 检查（仅允许特定白名单：1px border / 2px focus / 0px / 100% / auto）
grep -rnE '[0-9]+px' frontend/apps/h5/src/pages/${PAGE_ID}/*.module.css \
  | grep -v "var(--tkn-" \
  | grep -vE "1px|2px|0px|/\*" \
  || echo "✓ 无硬编码 px"

# 3. testid 完整性
for t in $(awk '/^## §8/,/^## §9/' design/system/pages/${PAGE_ID}.spec.md | grep -oE '`[a-z][a-z0-9-]+-[a-z0-9-]+`' | sort -u); do
  cleaned=$(echo "$t" | tr -d '`')
  grep -rq "data-testid=\"$cleaned\"" frontend/apps/h5/src/pages/${PAGE_ID}/ \
    || echo "❌ AC 引用的 testid '$cleaned' 在代码中缺失"
done

# 4. 已废 v1.0 token 检查
grep -rnE -- "--tkn-color-warm-(bg|elevated|sunken|text|divider)|--tkn-gradient-aurora|--tkn-gradient-focus-night|--tkn-gradient-result-warm" \
  frontend/apps/h5/src/pages/${PAGE_ID}/ \
  && echo "❌ 使用了已废 v1.0 token · 详见 warmth.json 的 _meta.migration_map"
```

**OUTPUT**：lint 报告

**VERIFY**：4 项全过。任一不过 = 回 Step 6。

---

### Step 9 · a11y 自检

**INPUT**：spec.md §12 a11y 段

**DO**：
```bash
# Playwright + axe-core
npx playwright test --grep "a11y" frontend/apps/h5/tests/pages/${PAGE_ID}.spec.ts
```

**Test 文件模板**：
```ts
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

test('a11y scan ${PAGE_ID}', async ({ page }) => {
  await page.goto('/${PAGE_ROUTE}');
  const results = await new AxeBuilder({ page }).analyze();
  expect(results.violations).toEqual([]);
});
```

**OUTPUT**：axe-core violations 数

**VERIFY**：violations = 0。出现违规 → 按 violation.id 修对应组件（缺 aria-label / 缺 role / 焦点顺序错）。

---

### Step 10 · 自报：交付清单

**INPUT**：Step 6-9 全部通过

**DO**：在 PR / 任务 issue 里粘贴：
```markdown
## ${PAGE_ID} 实施完成报告

### 文件清单
- React 组件: frontend/apps/h5/src/pages/${PAGE_ID}/
- 测试文件: frontend/apps/h5/tests/pages/${PAGE_ID}.spec.ts
- 截图: design/system/screenshots/current/${PAGE_ID}.png

### 验收数据
- ✅ 1:1 像素对齐 archive · 差异像素 = ${N}（≤200）
- ✅ token lint 全过（无硬编码 / 无 v1.0 已废 token）
- ✅ testid 覆盖 · ${M} 个 AC × ${K} 个 testid 全部映射
- ✅ axe-core a11y · violations = 0
- ✅ Playwright B 轨 · ${P}/${P} 通过

### 留意事项
（如果有 [AI 推测] AC 待业务确认 · 列在这里）
```

**OUTPUT**：PR 描述文档

**VERIFY**：交付清单**所有勾必须真的过**，不允许空打勾。

---

## §3 QA Agent 验收 Runbook（fe-accept · 3 轨）

### 3.1 B 轨 · Mock 单测（必过 · CI 阻断）

**目的**：在不连后端的情况下，验证 UI 与 spec.§8 AC 1:1 对齐。

**INPUT**：spec.md §8 AC 表 + spec.md §3 testid + 已实现代码

**DO**：
```bash
# 1. 启动 mock server（拦截所有 /api/* 返回 spec.§4 数据契约示例）
npm run mock:start -- --port 3001

# 2. 跑 Playwright 单测
PAGE_ID=P02-capture npm run test:accept-mock

# 3. 跑 token-coverage（grep 校验所有 spec.§14 token 都被代码引用了）
node scripts/token-coverage.js --page=$PAGE_ID
```

**Test 模板**（每条 AC 一个 it）：
```ts
import { test, expect } from '@playwright/test';

test.describe('${PAGE_ID} · accept-mock B 轨', () => {

  test.beforeEach(async ({ page }) => {
    await page.goto('/${PAGE_ROUTE}');
  });

  // AC-${PAGE_ID}-001 ...
  test('AC-${PAGE_ID}-001 · 验收点描述（来自 spec.§8）', async ({ page }) => {
    const el = page.getByTestId('xxx');           // 从 spec.§8 testid 验证点
    await expect(el).toBeVisible();
    await expect(el).toHaveAttribute('data-mood', 'C');  // 验 mood
  });

  // AC-${PAGE_ID}-002 ...
  test('AC-${PAGE_ID}-002 · 验收点描述', async ({ page }) => {
    /* ... */
  });

  // ... 每条 AC 必须一一对应
});
```

**OUTPUT**：测试结果（pass/fail per AC）

**VERIFY**：
- ✅ AC 数 = 测试 it 数（一对一）
- ✅ 全部通过（fail = 阻断 PR）
- ✅ token-coverage ≥ 100%（spec.§14 所有 token 在代码中至少被引用一次）

**FAIL 怎么办**：
1. 看 fail 是哪个 AC + 哪个 testid
2. 在浏览器手动跑：`/${PAGE_ROUTE}` → DevTools 找该 testid
3. 对比 archive HTML 同样位置看应该长什么样
4. 把差异交给 fe-builder 修

---

### 3.2 C 轨 · 像素 diff（参考 · 不阻断）

**目的**：自动捕捉视觉漂移（颜色 / 间距 / 字号差异）。

**INPUT**：archive HTML + 已实现代码

**DO**：
```bash
# 1. 截图 archive 作 baseline
npx playwright screenshot --viewport-size=393,852 \
  "file://$(pwd)/design/mockups/wrongbook/_archive/${PAGE_ID}.html" \
  design/system/screenshots/baseline/${PAGE_ID}.png

# 2. 截图当前实现
npx playwright screenshot --viewport-size=393,852 \
  "http://localhost:3000/${PAGE_ROUTE}" \
  design/system/screenshots/current/${PAGE_ID}.png

# 3. 像素 diff
compare -metric AE -fuzz 5% \
  design/system/screenshots/baseline/${PAGE_ID}.png \
  design/system/screenshots/current/${PAGE_ID}.png \
  design/system/screenshots/diff/${PAGE_ID}.png \
  2> /tmp/diff-count.txt

cat /tmp/diff-count.txt
```

**OUTPUT**：差异像素数 + diff 图（红色高亮差异区）

**VERIFY**（参考阈值）：
- ≤ 200 px (5% fuzz) → 视觉高度对齐 ✅
- 200-1000 px → 接受但记录在 PR
- \> 1000 px → 视觉漂移过大 · 看 diff 图定位 → 回 fe-builder

---

### 3.3 A 轨 · 真实后端 E2E（每 sprint 末跑一次）

**目的**：验证前端 + 后端联调端到端可用，含错误路径 / 状态机 / 异步加载。

**INPUT**：spec.§5 API + spec.§6 状态机 + spec.§9 异常路径

**DO**：
```bash
# 1. 启动真实后端（review-plan-service / wrongbook-service / file-service）
cd backend/review-plan-service && ./gradlew bootRun &
cd backend/wrongbook-service && ./gradlew bootRun &
cd backend/file-service && ./gradlew bootRun &

# 2. 等 actuator/health 全 UP
until curl -s localhost:8080/actuator/health | grep '"status":"UP"'; do sleep 2; done

# 3. 跑 E2E
PAGE_ID=P02-capture BASE_URL=http://localhost:3000 \
  API_BASE_URL=http://localhost:8080 \
  npm run test:accept-e2e
```

**Test 文件**（覆盖每个 spec.§6 state）：
```ts
test.describe('${PAGE_ID} · accept-e2e A 轨', () => {

  test('IDLE → UPLOADING → UPLOADED · 黄金路径', async ({ page }) => {
    /* ... */
  });

  test('权限 denied · 异常路径（spec.§9 第 1 行）', async ({ page }) => {
    /* ... */
  });

  test('上传失败 · 异常路径（spec.§9 第 N 行）', async ({ page }) => {
    /* ... */
  });

  // ... spec.§9 每行一个测试
});
```

**OUTPUT**：E2E 报告 + 性能数据（spec.§11 P95 budget 比对）

**VERIFY**：
- ✅ spec.§6 所有 state 跑过（不只是 happy path）
- ✅ spec.§9 所有异常路径有覆盖
- ✅ spec.§11 性能 P95 全部满足

---

## §4 不通过怎么办（FAIL Recovery 手册）

### 场景 A：fe-accept-mock B 轨 fail · 某条 AC 没过

```
1. 看 Playwright 报错的 testid
2. grep 该 testid 在 spec.§3 哪个 Block
3. 打开对应 archive HTML，找到那个 Block 的 HTML 结构
4. 对比当前实现，找差异（缺 attr / 缺 child / className 错）
5. 在 fe-builder 输出代码中改对应组件
6. 重跑 B 轨
```

### 场景 B：硬编码 hex 命中（Step 8 lint fail）

```
1. grep -n "#[0-9a-fA-F]" 找具体行
2. 在 STYLE-TRUTH.md §2 找最近的 token（按用途/数值）
3. 替换成 var(--tkn-*)
4. 如果 STYLE-TRUTH 没有这个色 → 该色应该加到 color.json (走治理流程)
```

### 场景 C：使用了已废 v1.0 token

```
v1.0 token              →  v2.0 替代
--tkn-color-warm-bg     →  --tkn-color-bg-light
--tkn-color-warm-elevated →  --tkn-color-card
--tkn-color-warm-text-primary →  --tkn-color-text-primary
--tkn-color-warm-text-secondary → --tkn-color-text-secondary
--tkn-color-warm-divider →  --tkn-color-sep
--tkn-shadow-warm-card  →  --tkn-shadow-card-deep
--tkn-shadow-warm-hero  →  --tkn-shadow-hero-card
--tkn-color-aurora-particle → --tkn-color-glass-white-18
--tkn-color-aurora-blur →  --tkn-color-glass-white-08
--tkn-gradient-aurora   →  --tkn-gradient-hero-home
--tkn-gradient-focus-night → --tkn-color-bg-camera (实色非渐变)
--tkn-gradient-result-warm → --tkn-color-bg-light  (实色非渐变)
--tkn-shadow-warm-card-pressed → 删除 · 改用 transform:scale(.97/.98)
```

完整对照见 `design/system/tokens/warmth.json#_meta.migration_map`。

### 场景 D：a11y violation

| violation.id | 含义 | 修法 |
|---|---|---|
| `landmark-one-main` | 缺 `<main>` | 加 `<main role="main">` 包裹核心区 |
| `image-alt` | 图缺 alt | `<img alt="..." />` 或 `aria-label` |
| `color-contrast` | 文字对比度 < 4.5:1 | 用 STYLE-TRUTH 内带 contrast_on_light 字段的 token |
| `aria-roles` | role 用错 | 见 spec.§12 a11y 字段 |
| `button-name` | 按钮无可读名 | `<button aria-label="...">` 或加文本 |

### 场景 E：archive 缺失页（如 P00 login）

```
1. 不找 archive · 直接读 STYLE-TRUTH §6（缺失页指引）
2. 用 STYLE-TRUTH §4 组件清单逐个组装
3. 视觉对照：跑 Mood A / B / C / D / E 任一同 mood 的 archive 页验证风格一致性
4. 跳过 §3.2 C 轨像素 diff（无 baseline）· 但 §3.1 B 轨 + §3.3 A 轨必须跑
```

---

## §5 命令速查

### 5.1 grep 速查（常用 lint）

```bash
# 已废 v1.0 token 全检查
grep -rnE -- "--tkn-color-warm-(bg|elevated|sunken|text|divider)|--tkn-gradient-aurora|--tkn-gradient-focus-night|--tkn-gradient-result-warm|--tkn-shadow-warm-" frontend/apps/h5/src/

# 硬编码 hex
grep -rnE '#[0-9a-fA-F]{3,8}' frontend/apps/h5/src/ | grep -v "var(--tkn-"

# v1.0 错值 hex
grep -rnE '#0071e3|#1d1d1f|#2C2A26' frontend/apps/h5/src/ design/

# testid 命名规范（必须 kebab-case）
grep -rnE 'data-testid="[A-Z_]' frontend/apps/h5/src/ && echo "❌ testid 不是 kebab-case"

# Mood 5 类合规（v2.0）
grep -rnE 'data-mood="(cool|warm|celebrate)"' frontend/apps/h5/src/ \
  && echo "⚠️ 仍用 v1.0 cool/warm/celebrate · 改 A/B/C/D/E 5 类"
```

### 5.2 token-coverage 校验

```bash
# 检查代码引用了 spec.§14 列出的所有 token
node scripts/token-coverage.js --page=${PAGE_ID}

# 期望输出：
# spec.§14 token 数: 24
# 代码引用 token 数: 24
# coverage: 100% ✓
```

### 5.3 Playwright 截图 + diff

```bash
# 截图当前实现
npx playwright screenshot \
  --viewport-size=393,852 \
  --full-page \
  "http://localhost:3000/${PAGE_ROUTE}" \
  /tmp/current.png

# 截图 archive 参考
npx playwright screenshot \
  --viewport-size=393,852 \
  --full-page \
  "file://$(pwd)/design/mockups/wrongbook/_archive/${PAGE_ID}.html" \
  /tmp/archive.png

# Diff
compare -metric AE -fuzz 5% /tmp/archive.png /tmp/current.png /tmp/diff.png
```

### 5.4 mock server

```bash
# 启动 mock（拦截 /api/*）
npm run mock:start -- --port 3001 --fixture design/seed/sample-questions.json

# 用 mock 跑前端（前端代理到 mock 而非真后端）
NEXT_PUBLIC_API_BASE=http://localhost:3001 npm run dev
```

---

## §6 完整示例：P02 拍题相机端到端

### 6.1 启动信息
```
PAGE_ID = P02-capture
PAGE_ROUTE = /capture
MOOD = C dark-camera
ARCHIVE = design/mockups/wrongbook/_archive/02_capture.html
SPEC = design/system/pages/P02-capture.spec.md
```

### 6.2 Step 1 · Mood
DESIGN.md §5.0 P02 行 → Mood **C** dark-camera。
全屏 #0B0F1A 实色 + viewfinder 内模拟纸面 + 黄检测元素。

### 6.3 Step 2-5 · 抽信息
- archive 参考 = `_archive/02_capture.html`
- token 池（spec.§14）= `--tkn-color-bg-camera` / `--tkn-color-system-yellow` / `--tkn-color-glass-white-{10,12,18}` / `--tkn-color-glass-black-{40,45,55}` / `--tkn-shadow-shutter` / `--tkn-radius-circle` / 学科色 4 个
- Block = B1 topbar / B2 subject-chip-strip / B3 viewfinder / B4 tip / B5 shutter-bar / B6 mode-tabs
- AC = 7 条（AC-P02-001 ~ AC-P02-007）

### 6.4 Step 6 · 写代码
按 archive 02_capture.html 1:1 拷贝结构 → 转 React + 引用 token。

```tsx
// P02Capture.tsx
export default function P02Capture() {
  return (
    <div className={s.phone} data-mood="C">
      <header className={s.statusbar} data-testid="p02-statusbar">{/* ... */}</header>
      <nav className={s.nav}>{/* B1 */}</nav>
      <div className={s.detect} data-testid="p02-detect-badge"><span className={s.pulse}/>已识别页面边界</div>
      <main className={s.view} data-testid="p02-viewfinder">
        <div className={s.paper}>{/* 模拟纸面 */}</div>
        <div className={s.bracket + ' ' + s.tl}/>
        {/* 4 角 brackets · scan line */}
      </main>
      <div className={s.subjects} role="tablist" data-testid="subject-chip-strip">{/* B2 */}</div>
      <nav className={s.modes} data-testid="p02-mode-tabs">{/* B6 */}</nav>
      <div className={s.controls} data-testid="p02-shutter-bar">
        <button className={s.shutter} data-testid="p02-shutter-bar-shutter-btn"><div className={s.core}/></button>
      </div>
    </div>
  );
}
```

### 6.5 Step 7-9 · 验收
- 像素 diff: 142 px (5% fuzz · 通过)
- token lint: 无硬编码 · 无 v1.0 token · 通过
- a11y: violations = 0
- B 轨: 7/7 AC 通过

### 6.6 Step 10 · 交付报告
```markdown
## P02-capture 实施完成 · 2026-05-XX
- ✅ 1:1 archive 对齐 · 差异 142px
- ✅ Token lint 全过
- ✅ testid 覆盖 7 个 AC
- ✅ a11y violations = 0
- ✅ B 轨 7/7 通过
- ⏳ A 轨 sprint 末跑（依赖 file-service / wrongbook-service / ai-analysis-service）
```

---

## §7 当本文档跟不上变化时

| 触发 | 怎么做 |
|---|---|
| Archive 加了新页面 | 在 STYLE-TRUTH §8 巅峰参考表追加，本文 §1 无需改 |
| 新增 token | 加到 `color.json` 或对应 JSON · 自动通过 STYLE-TRUTH §2 引用 · 本文无需改 |
| 新增 mood | STYLE-TRUTH §3 + DESIGN.md 铁律 2 表 同步追加 · 本文 Step 1 自动反映 |
| Sprint 流程变了 | 改本文 §0 全景图 + §2 step 数 |
| 验收阈值变了 | 改本文 §3 「VERIFY」段的 fuzz / 像素阈值 |

**禁止**：在本文里硬编码 token 值或具体页面内容。本文应永远 reference 其他权威文档。

---

**文档版本**：v1.0
**生成日期**：2026-05-02
**维护者**：实施 Agent / 工程 lead
