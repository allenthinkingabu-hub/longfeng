# UI Plan · S7 FE-02-capture-flow
> Generated: 2026-05-02 · Agent: FE-02-capture-flow Sub-agent

---

## P02 · Capture (拍题相机) · Mood C dark-camera

### Block 拆分

| Block | 文件位置 | 描述 | Testid |
|---|---|---|---|
| B0 Status bar | `Capture/index.tsx` | 白字时间 + 图标 | — |
| B1 Nav | `Capture/index.tsx` | 返回按钮 + 标题 + 闪光 | `p02-topbar` |
| B2 Detect badge | `Capture/index.tsx` | 玻璃态 pill + 黄点 pulse | `p02-detect-badge` |
| B3 Tip card | `Capture/index.tsx` | 玻璃态 14px 提示 + 盾牌图标 | `p02-tip-card` |
| B4 Viewfinder | `Capture/index.tsx` | 暗底 radial-gradient + 纸面 + 黄色检测框 + 扫描线 | `p02-viewfinder` |
| B5 Subjects | `Capture/index.tsx` | 5 个玻璃态学科 chip | `p02-subjects` |
| B6 Mode tabs | `Capture/index.tsx` | 3 个 tab（相册/拍题/文件）+ 黄色选中条 | `p02-mode-tabs` |
| B7 Controls | `Capture/index.tsx` | 相册按钮 + 78px 快门 + 文件按钮 | `p02-shutter-btn` |
| B8 Tab bar | `Capture/index.tsx` | 暗玻璃 5-tab 底部栏 | — |

### 交互流程

```
进入 IDLE
  ↓ 点击快门 → triggerCamera() → <input capture="environment">
  ↓ 点击相册 → triggerGallery() → <input> click
  ↓ onFileChange → handleFile(file) → setState(UPLOADING)
    → filesClient.presign() → directUpload() → complete()
    → setState(UPLOADED) → nav('/analyzing/:taskId')
  ↓ (upload fail) → setState(ERROR) → show errorBanner
```

### 状态切换

```
IDLE ──[快门/相册]──→ UPLOADING ──[success]──→ UPLOADED ──→ nav P03
                                └──[fail]──────→ ERROR
```

### 关键 CSS token 对照

| 元素 | CSS 值 | Token 来源 |
|---|---|---|
| 全屏背景 | `#0B0F1A` | `--camera-screen` STYLE-TRUTH §2.1 |
| Viewfinder 径向 | `radial-gradient(800px 400px at 50% 30%, #1d2433 0%, #0B0F1A 65%)` | `--grad-camera-vf` §2.6 |
| 黄色检测框 | `3px solid #FFCC00` | `--yellow` §2.1 |
| 快门 core | `linear-gradient(180deg, #5FA8FF, #007AFF)` | `--grad-icon-blue-deep` §2.6 |
| 快门 ring | `0 0 0 4px rgba(255,255,255,.18), 0 0 0 8px rgba(255,255,255,.10)` | `--shadow-shutter` §2.5 |
| 扫描线 | `linear-gradient(90deg, transparent, rgba(255,204,0,.95), transparent)` | `--grad-scan-yellow` §2.6 |
| Dock 渐变 | `linear-gradient(180deg, rgba(11,15,26,0) 0%, rgba(11,15,26,.85) 35%, #0B0F1A 100%)` | `--grad-camera-dock` §2.6 |
| 学科 chip on | `rgba(255,255,255,.95) + color #007AFF` | §4.21 |
| Tab bar | `rgba(11,15,26,.78) + blur 22px saturate 180%` | §4.14 |

---

## P03 · Analyzing (AI 分析中) · Mood C dark

### Block 拆分

| Block | 文件位置 | 描述 | Testid |
|---|---|---|---|
| B1 Statusbar | `Analyzing/index.tsx` | 白字状态栏 | `p03-statusbar` |
| B2 Thumb card | `Analyzing/index.tsx` | 缩略图 + 题目标题 + 模型 badge | `p03-thumb-card` |
| B3 Pipeline | `Analyzing/index.tsx` | 4 步流水线（wait/now/done/fail）+ aria-live | `analyzing-pipeline` |
| B4 JSON stream | `Analyzing/index.tsx` | 等宽字体流式区 + 光标动画 | `analyzing-pipeline-json-stream` |
| B5 Cancel | `Analyzing/index.tsx` | 灰 pill 取消按钮 → POST /cancel | `analyzing-pipeline-cancel-btn` |

### 交互流程

```
进入 QUEUED
  ↓ useEventSource(taskId) → connect EventSource
  ↓ STEP_START(1) → stepStatuses[1]='now', status='STEP_1'
  ↓ STEP_DONE(1) → stepStatuses[1]='done', record duration
  ↓ STEP_START(2) → ... → repeat 4 steps
  ↓ PARTIAL_JSON → append to partialJson → display in B4
  ↓ DONE → status='SUCCEEDED' → nav('/question/:qid/result', 200ms)
  ↓ FAIL → status='FAILED' → errorBanner
  ↓ SLOW (>10s) → setSlowBanner(true) → model='gpt-4o-mini'
  ↓ [Cancel btn] → cancel() → POST /api/ai/cancel/:taskId → nav('/capture')
```

### 状态切换

```
QUEUED → STEP_1 → STEP_2 → STEP_3 → STEP_4 → SUCCEEDED → nav P04
       → SLOW (onSlow)
       → FAILED
       → CANCELLED (cancel btn) → nav P02
```

### SSE Event 格式

```typescript
interface AnalyzeStreamEvent {
  type: 'STEP_START' | 'STEP_DONE' | 'PARTIAL_JSON' | 'DONE' | 'FAIL' | 'CANCELLED';
  step?: 1 | 2 | 3 | 4;
  durationMs?: number;
  partialJson?: string;
  errorCode?: string;
}
```

### 关键 CSS token 对照

| 元素 | CSS 值 | Token 来源 |
|---|---|---|
| 页面背景 | `linear-gradient(180deg, #0A0E1A 0%, #1A1F2E 100%)` | `--grad-camera-vf` 近似 §2.6 |
| surface-dark-3 | `#28282a` | §spec P03 §2 |
| stepCircle now | `#2997ff` + sse-pulse animation | blue-light |
| stepCircle done | `#34C759` | `--green` §2.1 |
| cancel btn | `rgba(44,42,38,.08)` + border `rgba(255,255,255,.18)` | §spec |

---

## P04 · Result (AI 分析结果) · Mood B pure-warm

### Block 拆分

| Block | 文件位置 | 描述 | Testid |
|---|---|---|---|
| B1 Nav | `Result/index.tsx` | 玻璃态白底 nav + 返回 + "分析完成" + 用时 tag | `p04-navbar` |
| B2 Question hero | `Result/index.tsx` | 白卡 + 纸面缩略图 + 题干 + 公式 | `p04-question-hero` |
| B3 Answers | `Result/index.tsx` | 错解(红渐变+1px inset) + 正解(绿渐变+1px inset) | `p04-answers-row` |
| B4 Reason card | `Result/index.tsx` | 4px 红左条 + 橙图标 + 错因文本 | `p04-reason-card` |
| B5 Steps | `Result/index.tsx` | 3 步解法（蓝数字圆 + 公式块） | `p04-solution-stepper` |
| B6 KP + diff | `Result/index.tsx` | 知识点 chips(≤80px) + 难度星 | `p04-meta-chips` |
| B7 Ebbing | `Result/index.tsx` | 6 节点艾宾浩斯 preview（全灰 data-status="future"） | `memory-curve` |
| B8 CTA | `Result/index.tsx` | 手动修正(ghost) + 保存并开启复习(primary blue) | `p04-save-cta` |

### 交互流程

```
进入 LOADING → skeleton
  ↓ fetch('/api/wb/questions/:qid') → (fallback mock if fail)
  ↓ 设置 question + nodes
  ↓ confidence < 0.6 → LOW_CONF state → show lowConfBanner
  ↓ confidence ≥ 0.6 → DRAFT state
  ↓ [Save CTA] → SAVING → POST /save → (fallback ok)
    → SAVED → nav('/wrongbook?highlight=:qid', 200ms)
```

### 状态切换

```
LOADING → DRAFT (confidence ≥ 0.6)
        → LOW_CONF (confidence < 0.6)
                → EDITING (user taps field)
                → SAVING (CTA click)
                          → SAVED → nav P05
ERROR (fetch fail → mock fallback · 不阻塞)
```

### 关键 CSS token 对照

| 元素 | CSS 值 | Token 来源 |
|---|---|---|
| 页面背景 | `#F2F2F7` | `--bg` iOS Light §2.1 |
| Nav backdrop | `rgba(242,242,247,.78) + blur 22px saturate 180%` | §4.14 Mood B |
| 错解卡 bg | `linear-gradient(160deg, #FFE8E6 0%, #FFF 70%)` | `--grad-ans-wrong` §2.6 |
| 正解卡 bg | `linear-gradient(160deg, #E4F7EA 0%, #FFF 70%)` | `--grad-ans-right` §2.6 |
| 错因左条 | `border-left: 4px solid #FF3B30` | iron-rule: 4px left bar only |
| 步骤数字圆 | `linear-gradient(180deg, #5FA8FF, #007AFF) + shadow-step-num` | §4.25 |
| 保存 CTA | `linear-gradient(180deg, #5FA8FF, #007AFF) + shadow-cta-blue` | `--grad-cta-primary` §2.6 |
| Ebbing card | `linear-gradient(160deg, #EEF4FF 0%, #F7ECFF 100%)` | `--grad-ebbing` §2.6 |
| KP chip | `rgba(88,86,214,.10)` + `max-width: 80px` | §4.23 铁律5 |
| 节点 first pill | `linear-gradient(90deg, #5FA8FF, #007AFF) + glow 4px` | §4.26 |

---

## hooks/useEventSource.ts

### 功能

- SSE 订阅 `/api/ai/stream/{taskId}`
- 自动重连（最多 3 次，指数退避 500ms * 2^n）
- 10s 无首字节 → onSlow 回调
- 取消：`cancel()` → POST `/api/ai/cancel/{taskId}` → EventSource.close()
- `prefers-reduced-motion`：动画由 CSS 层处理，hook 不直接控制

### 返回值

```typescript
{
  status: StreamStatus,           // QUEUED / STEP_1..4 / SUCCEEDED / FAILED / CANCELLED / SLOW
  stepStatuses: Record<1..4, 'wait'|'now'|'done'|'fail'>,
  stepDurations: Partial<Record<1..4, number>>,  // ms
  partialJson: string,            // 累积 JSON 字符串
  cancel: () => Promise<void>,    // D-AI-Cancel
}
```

---

## 文件清单

```
frontend/apps/h5/src/
├── App.tsx                           [updated] P03 + P04 路由注册
├── hooks/
│   └── useEventSource.ts             [new] SSE hook + D-AI-Cancel
├── pages/
│   ├── Capture/
│   │   ├── index.tsx                 [rewrite] Mood C 1:1 archive
│   │   └── Capture.module.css        [rewrite] all tokens corrected
│   ├── Analyzing/
│   │   ├── index.tsx                 [new] P03 4-step pipeline
│   │   └── Analyzing.module.css      [new]
│   └── Result/
│       ├── index.tsx                 [new] P04 warm result
│       └── Result.module.css         [new]
└── __mocks__/
    ├── browser.ts                    [updated] analyzingHandlers 注册
    └── handlers/
        └── analyzing.ts              [new] SSE mock + P04 endpoints

packages/testids/src/index.ts         [updated] p02/p03/p04 testids 注册
```
