---
name: fe-builder
description: >
  AI 前端高保真开发的 Stage 2 Builder 工作流。读取 fe-preflight 生成的 build-spec.json，
  按区块（block）逐一实现 CSS Module + TSX，确保所有颜色/间距使用 --tkn-* design tokens，
  每个区块完成后运行 grep 合规检查。
  触发场景：用户说 "/fe-builder 页面名"、"执行 stage 2"、"开始 builder"、
  "按 build-spec 实现"、"开发 <PAGE> 页面"，或在 pre-flight 完成后准备实现页面代码时。
---

# fe-builder · 开发中 · 分区块执行

## 目标

读取 `design/tasks/preflight/<PAGE>-build-spec.json`，按区块顺序实现页面，
保证设计系统合规（零硬编码色值 + token 覆盖）。

## 输入

| 参数 | 说明 | 示例 |
|---|---|---|
| `<PAGE>` | 页面名称 | ListPage / CapturePage / DetailPage |
| `[<block-id>]` | 可选，只做指定区块 | nav-bar / card-item |

页面 → 文件路径默认映射：

| 页面 | CSS Module | TSX |
|---|---|---|
| ListPage | `frontend/apps/h5/src/pages/List/List.module.css` | `frontend/apps/h5/src/pages/List/index.tsx` |
| CapturePage | `frontend/apps/h5/src/pages/Capture/Capture.module.css` | `frontend/apps/h5/src/pages/Capture/index.tsx` |
| DetailPage | `frontend/apps/h5/src/pages/Detail/Detail.module.css` | `frontend/apps/h5/src/pages/Detail/index.tsx` |

## 执行步骤

### Step 1 · 读取施工图

```bash
cat design/tasks/preflight/<PAGE>-build-spec.json
cat design/tasks/preflight/<PAGE>-token-mapping-review.md
```

重点读取：
- `_color_decisions`：A1/B1/C1/D1 等用户决策，全程执行时必须遵守
- `blocks[]`：每个区块的 `tokens`、`ui_kit_component`、`testids`、`mockup_notes`
- token-mapping-review.md 中的已批准例外（相机色、E3 裸色值等）

### Step 2 · 读取现有代码

读取目标 CSS Module 和 TSX 文件的当前状态，理解已有结构，避免破坏无关逻辑。

### Step 3 · 实现（按区块顺序）

**对每个 block（或指定的单个 block）：**

#### 3a. CSS Module

写入 `<PAGE>.module.css`，遵守以下规则（详见 references/css-patterns.md）：

1. **零硬编码色值**：所有颜色必须是 `var(--tkn-*)` 或已批准的本地 CSS 变量
2. **本地变量集中声明**：非 token 的例外色值在 `.root {}` 顶部用 `--` 前缀声明，并加注释说明原因
3. **RGB 分量变量**：需要 `rgba(color, opacity)` 时，声明 `--primary-rgb: 0, 113, 227` → 用 `rgba(var(--primary-rgb), 0.25)`
4. **间距/圆角 token 优先**：`--tkn-spacing-*`、`--tkn-radius-*`，差值 ≤ 2px 直接取最近 token
5. **语义化 class 名**：学科色用 `.subMath/.subPhysics` 而非 `.subBlue/.subOrange`

#### 3b. TSX

编辑 `index.tsx`，对应 CSS 改动：

1. **清除内联颜色**：`style={{ color: '#FF3B30' }}` → `className={s.explainError}`
2. **SVG 图标**：品牌色 `stroke="#007AFF"` → `stroke="currentColor"`（白色 icon 除外）
3. **class 名同步**：CSS 改名时同步更新 TSX 中的引用（如 `s.subBlue` → `s.subMath`）
4. **testid 确保存在**：spec 中每个 testid 都必须出现在对应 DOM 元素上

### Step 4 · 合规检查（每个区块完成后必跑）

```bash
bash .claude/skills/fe-builder/scripts/check-compliance.sh \
  frontend/apps/h5/src/pages/<DIR>/<PAGE>.module.css \
  frontend/apps/h5/src/pages/<DIR>/index.tsx
```

三条门禁：
1. **零硬编码色** — CSS module 中无 `#xxxxxx` / `rgb(` / `hsl(`（本地变量定义行除外）
2. **testid 覆盖** — TSX 中 `data-testid` 出现次数 ≥ spec 中该 block 的 testids 数量
3. **无 iOS 系统色变量** — 无 `var(--blue)` / `var(--red)` / `var(--green)` 等已废弃变量

### Step 5 · 合规检查通过后提交

```bash
git add frontend/apps/h5/src/pages/<DIR>/
git commit -m "feat(<page>): [<AC>] <block-label> · token 合规"
```

### Step 6 · 循环下一个区块

重复 Step 3-5，直到所有区块完成（或指定区块完成）。

### Step 7 · 汇总

打印完成摘要：
```
✅ Builder 完成 · <PAGE>
   完成区块：N 个
   合规检查：全部通过
   iOS 系统色残留：0
   建议：运行 /fe-accept 进行验收
```

## 硬约束

1. **不允许** 在 CSS 中直接写 `#007AFF` / `#FF3B30` / `#34C759` / `#FF9500` 等 iOS 系统色
2. **不允许** 跳过合规检查直接进入下一区块
3. **CSS local var 例外必须有注释** — 说明为什么没有对应 token
4. **学科色类名必须语义化** — 用 `.subMath` 不用 `.subBlue`
5. **已批准例外（来自 token-mapping-review.md）可保留** — 如相机页 `--cam-yellow: #FFCC00`

## 参考文档

- `references/css-patterns.md` — CSS token 使用模式（本地变量/RGB 分量/相机例外）
- `references/compliance-rules.md` — 合规规则详细说明 + 已知例外清单
- `scripts/check-compliance.sh` — 合规检查脚本
