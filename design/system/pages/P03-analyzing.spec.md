---
page_id: P03
name: AI 分析中
name_en: Analyzing
route_h5: /analyzing/:taskId
route_miniprogram: pages/camera/analyzing
deeplink: wb://analyzing/:taskId
auth_state: authenticated
persona:
  - P1-K12
scenarios:
  - SC-01
  - SC-07
mockup_canonical: design/mockups/wrongbook/03_analyzing.html
mockup_version: v1
last_reviewed: 2026-05-02
status: spec-draft
sprint: S1
---

# P03 · AI 分析中

> 与 DESIGN.md §5.1 第 2 张页面对齐：沿用 P02 暗底（视觉无缝衔接）· 顶部题目缩略图 + 模型 Badge · 4 步流水线 wait→now→done · 等宽 JSON 流式区 · 取消按钮灰色 pill 最下方。

---

## §1 页面目的（why · 1 句话）

让学生在 4–8s AI 推理等待中保持"被看见"，心态平稳，并能随时取消。

---

## §2 ASCII 布局图

```
┌─────────────────────────────────────┐
│  [statusbar]                         │
│  [p03-thumb-card · 题目缩略图卡]    │
│   model badge: "qwen-vl-max"        │
│                                     │
│  [analyzing-pipeline · M11]         │
│   ① 图像预处理   ✓ 已完成 (240ms) │
│   ② OCR 题干    ◐ 进行中 sse-pulse│
│   ③ 错因诊断    ○ 等待           │
│   ④ 生成解法    ○ 等待           │
│                                     │
│  [analyzing-pipeline-json-stream]   │
│   等宽字体 SF Mono · 流式 JSON     │
│   {"stem":"已知函数 f(x)=x²-4x..." │
│                                     │
│                                     │
│  [analyzing-pipeline-cancel-btn]    │
│   灰色 pill "取消分析"              │
└─────────────────────────────────────┘
                       · 整页 mood=cool
```

---

## §3 Block 清单（每块独立可实现单元）

| Block ID | 名称 | Section | Mood | Component | testid root | Tokens 用到 |
|---|---|---|---|---|---|---|
| `B1` | 状态栏（透明叠加） | hero | cool | L0.NavBar(transparent) | `p03-statusbar` | `--tkn-color-text-on-dark` |
| `B2` | 题目缩略图卡 + 模型 Badge | hero | cool | L0.Card(dark) + L0.Badge | `p03-thumb-card` | `--tkn-color-surface-dark-3` · `--tkn-radius-md` |
| `B3` | AnalyzingPipeline 4 步流水线 | hero | cool | M11.AnalyzingPipeline | `analyzing-pipeline` | `--tkn-color-primary-dark` · `--tkn-color-mastery-mastered` · `--tkn-motion-duration-base` |
| `B4` | JSON 流式区（等宽字体） | hero | cool | L0.ScrollView(monospace) | `analyzing-pipeline-json-stream` | `--tkn-type-caption` · `--tkn-color-text-on-dark` |
| `B5` | 取消按钮（灰 pill） | hero | cool | L0.Button(ghost) | `analyzing-pipeline-cancel-btn` | `--tkn-color-sep` · `--tkn-radius-pill` |

> **fe-preflight 用法**：把 mockup HTML 切成这些块；fe-builder 按块逐个实现并跑 lint。
> **铁律 2 校验**：本页所有 section `data-mood="cool"`，沿用 P02 `--tkn-color-bg-camera`。

---

## §4 数据契约（page-level interface）

```typescript
interface AnalyzingPageProps {
  taskId: string;
  questionId: string;
  thumbnailUrl: string;
  model: 'qwen-vl-max' | 'gpt-4o-mini';
  startedAt: number;       // unix ms
}

interface AnalyzeStreamEvent {
  type: 'STEP_START' | 'STEP_DONE' | 'PARTIAL_JSON' | 'DONE' | 'FAIL' | 'CANCELLED';
  step?: 1 | 2 | 3 | 4;     // STEP_START / STEP_DONE
  durationMs?: number;       // STEP_DONE
  partialJson?: string;      // PARTIAL_JSON 片段
  errorCode?: string;        // FAIL
}
```

> 类型不一致直接 fail。

---

## §5 API 触点

| Method | Path | 用途 | P95 预算 | 失败降级 |
|---|---|---|---|---|
| GET (SSE) | `/api/ai/stream/{taskId}` (H5) | 流式订阅 4 步 | 首字节 ≤ 3s · 总 ≤ 8s | 10s 无首字节切备用模型 |
| WS | `/ws/analyze/{taskId}` (小程序) | 同上（WebSocket 替代） | 同上 | 同上 |
| POST | `/api/ai/cancel/{taskId}` | 用户取消 | 200ms | 失败保留 PENDING task，不阻塞返回 |
| POST | `/api/ai/fallback/{taskId}` | 连续 2 次失败降级手填 | 500ms | 直接跳手填页 |

---

## §6 状态机

| State | 触发 | UI 表现 |
|---|---|---|
| `QUEUED` | 进入页面，等首字节 | 4 步全 wait（灰圆）+ 缩略图卡浮起 |
| `STEP_1`...`STEP_4` | 收到 STEP_START | 当前步 now（蓝脉冲）+ 已完成步 done（绿√） |
| `SUCCEEDED` | 收到 DONE | 全部 done · 200ms 淡出 → P04 |
| `FAILED` | 收到 FAIL | 当前步切红 ✗ + 顶部红条 "AI 暂时帮不上忙" |
| `CANCELLED` | 用户点 B5 取消 | 200ms 淡出 → P02 |
| `SLOW` | 单步 > 10s | 顶部黄条 "切换备用模型中…" + 切 gpt-4o-mini |

---

## §7 跳转图

```
[入口]
  P02 上传成功 ──→ P03 (taskId)
  深链 wb://analyzing/:taskId ──→ P03（恢复任务）
        │
        ├──[STEP_1..4 全部 done]──→ P04 (qid)
        ├──[B5 取消]──────────→ P02
        ├──[FAIL × 2]──────────→ 手填页 P03_MANUAL
        └──[10s 无首字节]──────→ 切备用模型（不离页）
```

---

## §8 AC 覆盖表（与 business-analysis.yml 双向锚定）

| AC ID | 验收点 | 涉及 Block | testid 验证点 |
|---|---|---|---|
| `AC-P03-001` | SSE 首字节 ≤ 3s 内 STEP_1 切到 now 状态 | B3 | `analyzing-pipeline-step-1` `data-status="now"` |
| `AC-P03-002` | 4 步流水线状态正确流转 wait→now→done，每步独立 | B3 | `analyzing-pipeline-step-{1..4}` 4 个 testid 各自有 status 属性 |
| `AC-P03-003` | JSON 流式区使用等宽字体，partial json 持续滚动到底 | B4 | `analyzing-pipeline-json-stream` `font-family` 含 monospace |
| `AC-P03-004` | 模型 Badge 显示当前模型名（qwen-vl-max 或 gpt-4o-mini） | B2 | `analyzing-pipeline-model-badge` 文本 ∈ ['qwen-vl-max','gpt-4o-mini'] |
| `AC-P03-005` | 点击取消按钮调用 `POST /api/ai/cancel`，成功后回 P02 | B5 | `analyzing-pipeline-cancel-btn` `aria-label="取消分析"` |
| `AC-P03-006` | DONE 后 ≤ 300ms 跳转 P04 | — (state) | `wb_ai_stream_done` 埋点 + URL 跳到 `/question/:qid/result` |
| `[AI 推测] AC-P03-007` | 单步超时 10s 自动切备用模型，顶部出现黄条 | — | `p03-fallback-banner` 渲染（仅 SLOW 状态） |
| `[AI 推测] AC-P03-008` | 整页禁止暖色 / 庆祝 token | 全部 | grep `--tkn-color-warm-` / `--tkn-color-mastery-` = 0（除复用 mastery-mastered 作为完成态色） |

> **铁律 1 注意**：B3 step done 状态使用 `--tkn-color-mastery-mastered`，是与 molecules.md 一致的 token 复用，但语义仍是"步骤完成"。本页未触发 self-grading 例外，无需 `data-iron-rule-1-exception`。

---

## §9 异常路径

| 触发 | UI | 系统行为 |
|---|---|---|
| AI 单步 >10s | 顶部黄条 "切换备用模型中…" + 切 gpt-4o-mini | 不离页，重置当前步 |
| AI 连续 2 次失败 | 顶部红条 + 跳手填页（保留已 OCR 题干预填） | 调 `/api/ai/fallback` |
| 用户左滑返回 / 系统 Home | 拦截 + 弹 Sheet "确认取消分析？" | 不允许静默丢任务 |
| 网络断开 | SSE 重连 3 次后顶部红条 "网络异常，已暂停" | 用户手动取消或重试 |

---

## §10 埋点事件

| 事件名 | 触发 Block | 必带属性 |
|---|---|---|
| `wb_ai_stream_start` | 进入 P03 | `taskId`, `model`, `qid` |
| `wb_ai_stream_step` | B3 (STEP_DONE) | `step`, `durMs` |
| `wb_ai_stream_done` | DONE | `totalMs`, `tokens`, `model` |
| `wb_ai_stream_fail` | FAIL | `code`, `step`, `model` |
| `wb_ai_stream_slow` | SLOW | `ms`, `step` |
| `wb_ai_stream_cancel` | B5 | `step`, `elapsedMs` |

---

## §11 性能预算

- TTI ≤ 500ms（从 P02 跳过来）
- 首字节 ≤ 3s（SSE 首个 STEP_START）
- 总分析 ≤ 8s（4 步串行 P95）
- CLS < 0.05（流水线高度固定，JSON 区独立滚动）
- 取消 API ≤ 200ms

---

## §12 A11y

- Landmarks: `<main role="main" aria-live="polite">` 包裹 B3 + B4
- 焦点顺序: B5 取消按钮（不允许焦点进流水线，仅 polite 朗读）
- 屏幕阅读器优先级: B3 通过 `aria-live="polite"` 朗读步骤变化，例 "OCR 题干进行中"
- `prefers-reduced-motion`: 关闭 sse-pulse 脉冲动画（铁律 3 兜底），仅文字"进行中"标识

---

## §13 Mockup 锚定

- **权威**: `design/mockups/wrongbook/03_analyzing.html` (v1)
- **历史变体**: 无
- **截图**: `design/system/screenshots/P03-v1-light.png`（fe-preflight 自动生成）
- **反向锚定**: mockup HTML `<head>` 中含 `<meta name="design-spec" content="P03-analyzing.spec.md@v1">`

---

## §14 Tokens 清单（grep 校验用）

```
L1 (Apple base):
  --tkn-color-black
  --tkn-color-white
  --tkn-color-text-on-dark
  --tkn-color-primary-DEFAULT
  --tkn-color-primary-dark
  --tkn-color-surface-dark-1
  --tkn-color-surface-dark-3
  --tkn-color-system-danger-DEFAULT
  --tkn-font-display
  --tkn-font-text
  --tkn-type-body
  --tkn-type-caption
  --tkn-type-caption-bold
  --tkn-spacing-xs
  --tkn-spacing-sm
  --tkn-spacing-12
  --tkn-spacing-md
  --tkn-spacing-lg
  --tkn-spacing-xl
  --tkn-radius-md
  --tkn-radius-lg
  --tkn-radius-pill
  --tkn-radius-circle
  --tkn-shadow-focus
  --tkn-motion-dur-base

L2 (warmth · 仅 mood=warm/celebrate 区段使用):
  --tkn-color-bg-camera     (cool hero 暗底渐变 · 沿用 P02)
  --tkn-color-sep       (cancel 按钮底色 · 透明叠加在暗底上)

L3 (celebration · 仅庆祝白名单时刻):
  --tkn-color-mastery-mastered   (步骤 done 完成态色 · molecule 内复用)

EXCEPTION (subject):
  (本页不出现)
```

> 本页 mood=cool · 仅复用 `--tkn-color-mastery-mastered` 作为"步骤完成"语义颜色（molecules.md M11 已注册），未触发 iron rule 1 self-grading 例外。

---

## §15 实现边界（Implementation Boundaries · F 机制 · MUST）

> **背景**：mockup HTML 含 iPhone chrome 装饰（边框 / 状态栏 / home indicator / notch）· 这些是 mockup 展示用 · **不是**真实页面应实现的元素。
>
> **完整方案**：`docs/DESIGN-AUDIT-SYSTEM-PLAN.md` · F 机制 + H 机制 + design-reviewer agent

### 实施前检查（per CLAUDE.md §2.0 · 强制）

```bash
grep -nE 'data-mockup-chrome' design/mockups/wrongbook/_archive/<file>.html
```

### Chrome 边界表（通用 · 各页 mockup 实际含哪些以 `data-mockup-chrome` attr 为准）

| 元素 | 实现否 | 备注 |
|---|---|---|
| `[data-mockup-chrome="iphone-frame"]` (phone wrapper · 黑色边框 + radius:54px + box-shadow inset) | ❌ 不实现 | mockup 装饰 · 改 `width:100% min-height:100vh` |
| `[data-mockup-chrome="iphone-statusbar"]` (9:41 + 信号 + wifi + 电池) | ❌ 不实现 | 浏览器/系统原生提供 |
| `[data-mockup-chrome="iphone-homebar"]` (底部 home indicator 横条) | ❌ 不实现 | iOS chrome |
| `[data-mockup-chrome="iphone-notch"]` (顶部凹槽 / Dynamic Island) | ❌ 不实现 | iOS chrome |
| 其他（hero / nav / cta / list / cards / 等所有未标 chrome 的元素） | ✅ 实现 | 真实页面内容 · 100% viewport 自适应 |

### 自检（per CLAUDE.md §2.11 · 实施完 commit 前必做）

```bash
pnpm e2e:mockup-diff -- --grep <page_id>      # B 机制 · 容差 5%
pnpm e2e:vrt-multi   -- --grep <page_id>      # A 机制 · 4 viewport baseline
# 派 design-reviewer agent (subagent_type: design-reviewer · 输入 page_id)
```

verdict 处理：
- ✅ `PASS` → 可 commit
- ❌ `FAIL` → 修复循环 · 不交付（按 issues[].suggested_fix 改 · 重跑直到 PASS）
- ⚠️ `AMBIGUOUS` → ask user 决策 · 不假设
