---
name: fe-preflight
description: >
  AI 前端高保真开发的开发前信息对齐（Pre-flight）工作流。在 AI 开始写页面代码之前，
  解析 mockup HTML 提取色值/间距，将其映射到 @longfeng/ui-kit design tokens，
  识别每个视觉区块对应的 ui-kit 组件，读取 business-analysis.yml 关联 AC 和 testid，
  最终输出 build-spec.json（施工图）和 token-mapping-review.md（近似项供用户确认）。
  触发场景：用户说 "/pre-flight 页面名"、"开始 pre-flight"、"先做信息对齐"、
  "分析 mockup"、"生成 build-spec"，或在准备开发某个前端页面之前。
---

# fe-preflight · 开发前信息对齐

## 目标

把 mockup HTML + design system + business-analysis.yml 转成 **build-spec.json**（AI Builder 的施工图），
填补"AI 不知道用哪个 ui-kit 组件、不知道哪个色值对应哪个 token"的信息缺口。

## 输入

| 参数 | 说明 | 示例 |
|---|---|---|
| `<PAGE>` | 页面名称，如 ListPage / CapturePage / DetailPage | ListPage |
| `--mockup` | mockup HTML 路径（可选，自动推断） | design/mockups/wrongbook/05_wrongbook_list.html |
| `--analysis` | business-analysis.yml 路径（可选，自动推断） | design/analysis/s7-business-analysis.yml |

页面 → mockup 默认映射：
- `ListPage` → `design/mockups/wrongbook/05_wrongbook_list.html`
- `CapturePage` → `design/mockups/wrongbook/02_capture.html`
- `DetailPage` → `design/mockups/wrongbook/06_wrongbook_detail.html`

## 执行步骤（严格按序）

### Step 1 · 运行 parse_mockup.py

```bash
mkdir -p design/tasks/preflight
python3 .claude/skills/fe-preflight/scripts/parse_mockup.py \
  <MOCKUP_PATH> \
  frontend/packages/ui-kit/src/tokens.css \
  --output design/tasks/preflight/<PAGE>-token-report.json
```

读取输出 JSON，重点关注：
- `color_map`：所有颜色的 token 映射（exact / approx / none）
- `summary.needs_review`：需要用户确认的近似映射数量
- `sections_found`：启发式识别出的区块关键词

### Step 2 · 读取 ui-kit 组件参考

读取 `.claude/skills/fe-preflight/references/ui-kit-components.md`，建立"场景 → 组件"的判断能力。
重点记住"无对应组件 → 自定义 CSS 的场景"列表。

### Step 3 · 读取 mockup HTML 识别视觉区块

逐段阅读 mockup HTML，识别页面的视觉区块层次。每个区块需确定：
1. **区块 ID**（slug，如 `nav-bar`、`subject-chips`、`card-item`）
2. **区块中文名**
3. **对应的 ui-kit 组件**（参考 references/ui-kit-components.md）或标 `"custom"`
4. **该区块使用的颜色**（从 Step 1 的 `color_map` 查找对应 token）
5. **该区块使用的间距/圆角**（从 Step 1 的 `spacing_map` 查找对应 token）

### Step 4 · 读取 business-analysis.yml 关联 AC 和 testid

```bash
cat design/analysis/s7-business-analysis.yml
```

对每个区块，从 `ac_coverage[].ux_anchor` 字段中提取对应的 `ac_id` 和 testid 列表。

### Step 5 · 生成 build-spec.json

输出路径：`design/tasks/preflight/<PAGE>-build-spec.json`

结构模板：
```json
{
  "page": "<PAGE>",
  "mockup_source": "<MOCKUP_PATH>",
  "business_analysis": "design/analysis/s7-business-analysis.yml",
  "generated_at": "<ISO8601>",
  "blocks": [
    {
      "id": "nav-bar",
      "label": "顶部导航栏",
      "ui_kit_component": "NavBar",
      "ui_kit_props": { "title": "错题本", "testIdPrefix": "wrongbook.list" },
      "tokens": {
        "background": "--tkn-color-white",
        "border_bottom": "--tkn-color-border-subtle"
      },
      "custom_css": false,
      "testids": ["wrongbook.list.filter-toggle"],
      "ac": "SC-08.AC-1",
      "mockup_notes": "顶部固定 · 含搜索栏 · 右侧筛选图标"
    }
  ]
}
```

**关键规则**：
- `ui_kit_component` 为 `"custom"` 时，`tokens` 字段必须覆盖该区块内**所有**色值和间距
- `ui_kit_component` 非 `"custom"` 时，只列出组件自身不处理的额外 token
- `mockup_notes` 简述该区块与通用组件的视觉差异（Builder 的上下文提示）

### Step 6 · 生成 token-mapping-review.md

输出路径：`design/tasks/preflight/<PAGE>-token-mapping-review.md`

**只包含需要用户判断的条目**（exact match 不需要 review）：

```markdown
# Token 映射 Review — <PAGE>

> 以下是无法精确匹配的颜色/间距，需要 User 确认处理方式。

## 近似匹配（approx）· 请确认可接受

| mockup 原始值 | 最近 token | 色差 | 出现次数 | 用于区块 |
|---|---|---|---|---|
| `#f2f2f7` | `--tkn-color-bg-light (#f5f5f7)` | Δ3 | 8 | subject-chips 背景 |

## 无法匹配（none）· 请指定 token 或允许自定义

| mockup 原始值 | 出现次数 | 用于区块 | 建议 |
|---|---|---|---|
| `#ab1234` | 2 | card-item 左侧竖条 | 建议用 --tkn-color-danger-default |

## User 确认

- [ ] 所有 approx 条目已确认可接受
- [ ] 所有 none 条目已指定处理方式
- [ ] build-spec.json 已 review，Builder 可开工
```

### Step 7 · 汇总并等待 User 确认

打印：
```
✅ Pre-flight 完成 · <PAGE>
   区块数：N 个
   需 review 的 token 映射：M 项
   输出：
     design/tasks/preflight/<PAGE>-build-spec.json
     design/tasks/preflight/<PAGE>-token-mapping-review.md

⏸  请 User review token-mapping-review.md 后确认，Builder 方可开工。
```

## 硬约束

1. **不写任何页面代码** — 只分析产出 spec，不动 `frontend/apps/` 任何文件
2. **token 映射必须来自 parse_mockup.py 输出** — 不凭记忆写 token 名
3. **ui-kit 组件判断必须参考 references/ui-kit-components.md** — 不凭直觉选组件
4. **所有 custom 区块必须列全部 token 引用** — 不允许出现裸色值
5. **等待 User 确认后才声明完成** — 不自行跳过 review 步骤
