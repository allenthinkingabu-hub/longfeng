# Exit Gate · S7 FE-02-capture-flow
> Date: 2026-05-02 · Agent: FE-02-capture-flow Sub-agent

---

## 完成状态 (Done)

### 文件产出

| 文件 | 状态 | 说明 |
|---|---|---|
| `hooks/useEventSource.ts` | ✅ 新建 | SSE hook + D-AI-Cancel + 重连 + SLOW 回调 |
| `pages/Capture/index.tsx` | ✅ 重写 | Mood C 1:1 对齐 archive 02_capture.html |
| `pages/Capture/Capture.module.css` | ✅ 重写 | 全 token 校正，无 hex 硬编码 |
| `pages/Analyzing/index.tsx` | ✅ 新建 | P03 4-step SSE pipeline + cancel |
| `pages/Analyzing/Analyzing.module.css` | ✅ 新建 | Mood C dark token |
| `pages/Result/index.tsx` | ✅ 新建 | P04 纯暖风格 + 骨架屏 + save CTA |
| `pages/Result/Result.module.css` | ✅ 新建 | Mood B pure-warm token |
| `App.tsx` | ✅ 更新 | /analyzing/:taskId + /question/:qid/result 路由 |
| `__mocks__/handlers/analyzing.ts` | ✅ 新建 | SSE mock + P04 GET/POST endpoints |
| `__mocks__/browser.ts` | ✅ 更新 | analyzingHandlers 注册 |
| `packages/testids/src/index.ts` | ✅ 更新 | p02 / p03 / p04 全量 testid 注册 |
| `ui-plan.md` | ✅ 新建 | 本文件 |
| `exit-gate.md` | ✅ 新建 | 本文件 |

---

## AC 覆盖表

### P02 (Capture)

| AC ID | 验收点 | 状态 | 说明 |
|---|---|---|---|
| AC-P02-001 | `data-mood="C"` 属性 | ✅ | `<div data-mood="C">` + `<div data-testid="p02-viewfinder" data-mood="C">` |
| AC-P02-002 | 4 学科 chip，选中有 `aria-pressed="true"` | ✅ | `aria-pressed={subject === sj.value}` |
| AC-P02-003 | 78px 快门按钮 | ✅ | `.shutter { width:78px; height:78px }` |
| AC-P02-004 | 上传成功 → nav P03 携带 taskId | ✅ | `nav('/analyzing/${taskId}')` setTimeout 300ms |
| AC-P02-005 | Mood token 隔离 | ✅ | 无 warm-* token；cam-* token 仅 root 范围 |
| AC-P02-006 | 模式 tab 默认"拍题"，aria-selected | ✅ | default mode='photo', `aria-selected={mode===val}` |
| AC-P02-007 | 闪光灯 aria-pressed toggle | ✅ | `aria-pressed={flashOn}` |

### P03 (Analyzing)

| AC ID | 验收点 | 状态 | 说明 |
|---|---|---|---|
| AC-P03-001 | SSE 首字节后 step-1 切到 now | ✅ | useEventSource STEP_START 事件处理 |
| AC-P03-002 | 4 步 testid 各有 data-status | ✅ | `data-status={stepStatuses[step]}` |
| AC-P03-003 | JSON 流式区等宽字体 | ✅ | `font-family: 'SF Mono', monospace` |
| AC-P03-004 | 模型 badge 文本 | ✅ | `data-testid="analyzing-pipeline-model-badge"` |
| AC-P03-005 | 取消 → POST /cancel → 返回 P02 | ✅ | `cancel()` → fetch POST → `nav('/capture')` |
| AC-P03-006 | DONE → ≤300ms 跳 P04 | ✅ | `setTimeout(() => nav(…), 200)` |
| AC-P03-007 | SLOW → `p03-slow-banner` | ✅ | `onSlow` 回调 → setSlowBanner |
| AC-P03-008 | 整页无 warm- token | ✅ | CSS 只用 cam-bg / surface-dark / blue / green |

### P04 (Result)

| AC ID | 验收点 | 状态 | 说明 |
|---|---|---|---|
| AC-P04-001 | `data-mood="B"` 存在 | ✅ | `<div data-mood="B">` + `<main data-mood="B">` |
| AC-P04-002 | 错因卡 `border-left: 4px solid #FF3B30` | ✅ | `.reasonCard { border-left: 4px solid var(--red) }` |
| AC-P04-003 | 错解/正解卡 1px inset | ✅ | `.ansWrong/.ansRight { box-shadow: inset 0 0 0 1px … }` |
| AC-P04-004 | 6 节点全 `data-status="future"` | ✅ | `data-status="future"` on each node div |
| AC-P04-005 | 错因文本 + 正解 + 3 步 非空 | ✅ | 填入 mock data；real API 亦可 |
| AC-P04-006 | 保存 CTA → nav P05 携带 highlight | ✅ | `nav('/wrongbook?highlight=:qid')` |
| AC-P04-007 | 学科 chip ≤80px | ✅ | `.chip { max-width: 80px }` |
| AC-P04-008 | confidence < 0.6 → `p04-low-conf-banner` | ✅ | `pageState === 'LOW_CONF'` 时渲染 |

---

## 已知限制 / Caveat

| 编号 | 描述 | 影响 | 处置 |
|---|---|---|---|
| C-14 | ai-analysis 模块编译 fail，SSE 端点跑不起来 | P03 SSE 无真实后端 | MSW mock 完整模拟 4-step，QA 可用 mock 通过 B 轨验收 |
| C-P02-CAM | H5 无法在桌面浏览器启动真实 camera | viewfinder 显示 faux paper | 符合设计要求（纸面模拟）；真机测试须真相机权限 |
| C-P04-API | GET /api/wb/questions/:qid 返回非 2xx | ResultPage 降级为 mock data | MSW handler 已覆盖；真 API 就绪后自动切换 |
| C-PIXEL | 像素对比 / Playwright E2E | 留 QA 轨（A 轨 Sprint 末） | `prefers-reduced-motion` a11y 已在 CSS 兜底 |

---

## 铁律校验 (self-check)

```bash
# 禁止 hex 硬编码（非 token 的蓝色）
grep -r "#0071e3" frontend/apps/h5/src/pages/ # expected: 0 matches

# 禁止旧 v1.0 token
grep -r "\-\-tkn-color-warm-\|gradient-aurora\|gradient-focus-night" \
  frontend/apps/h5/src/pages/ # expected: 0 matches

# P02 viewfinder 应有 data-mood="C"
grep "data-mood=\"C\"" frontend/apps/h5/src/pages/Capture/index.tsx

# 所有 memory-curve node 应有 data-status="future"
grep "data-status=\"future\"" frontend/apps/h5/src/pages/Result/index.tsx

# KP chip max-width ≤ 80px
grep "max-width: 80px" frontend/apps/h5/src/pages/Result/Result.module.css
```

---

## QA 留点 (Pixel diff / Playwright E2E 留 QA 轨)

1. **B 轨 Mock 验收**（可立即运行）
   - MSW mock 完整注册：SSE 4-step + P04 GET/POST
   - 所有 testid 已注册，可直接 `data-testid` 断言

2. **C 轨 像素对比**（fe-accept-diff）
   - P02 viewfinder 对比 `_archive/02_capture.html` 截图
   - P03 pipeline 对比 `03_analyzing.html` 截图
   - P04 answers + reason card 对比 `_archive/04_result.html` 截图

3. **A 轨 E2E**（Sprint 末）
   - P02 → P03 → P04 完整流程（需真实后端 C-14 修复后）
   - cancel 流程：P03 取消 → 返回 P02
   - save 流程：P04 保存 → P05 highlight

---

## 不 commit

Orchestrator 代做 commit。所有文件在 `agent/fe-02-capture-flow` 分支已写入。
