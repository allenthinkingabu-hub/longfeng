---
page_id: P02
name: 拍题相机
name_en: Capture
route_h5: /capture
route_miniprogram: pages/camera/capture
deeplink: wb://capture
auth_state: authenticated
persona:
  - P1-K12
scenarios:
  - SC-01
mockup_canonical: design/mockups/wrongbook/02_capture.html
mockup_version: v1
last_reviewed: 2026-05-02
status: spec-locked
sprint: S1
---

# P02 · 拍题相机

> **混合 mood**（与 DESIGN.md §5.3 mood 矩阵 v2 对齐）：外层 page = warm 暖米白底（顶部 topbar / chip-strip / 提示 / shutter-bar / mode-tabs），**仅 viewfinder = cool**（功能必需 · 黑取景器最大化纸面白字对比度 + 边缘检测可见性）。情感性 dark 全屏严格禁止——学生从 P-HOME warm 暖底进入，不应跳进黑屏。viewfinder 在 warm 页面中作为"嵌入 widget"呈现（11px 圆角 + 投影），心智模型 = "错题本里嵌入相机"，不是"启动相机 app"。

---

## §1 页面目的（why · 1 句话）

让学生用最少动作（≤3 次 tap）把"一道纸面错题"变成上传成功并交给 AI 分析。

---

## §2 ASCII 布局图

```
┌─────────────────────────────────────┐  ← 外层 mood=warm · color-warm-bg
│  [statusbar · warm 深色字]          │
│  [p02-topbar · 白底圆按钮 + 深 SVG] │
│  [subject-chip-strip · 学科色 chips]│
├─────────────────────────────────────┤
│  ┌───────────────────────────────┐  │
│  │ [p02-viewfinder · cool 嵌入]  │  │← 内层 mood=cool（功能必需）
│  │  --tkn-color-bg-camera         │  │  11px 圆角 + 投影
│  │  边缘检测框（白虚线 1px）     │  │  在 warm 底上像 widget
│  │  九宫格（白半透 0.18）        │  │
│  │  vf-question-preview 白字     │  │
│  └───────────────────────────────┘  │
├─────────────────────────────────────┤
│  [p02-tip · 深字 0.64 "对准题目..."]│
│  [p02-shutter-bar · warm]           │
│   gallery 白底 · 78px 白圆深描边 · flash 白底
│  [p02-mode-tabs · 深字 + 选中蓝 #0066cc]
└─────────────────────────────────────┘
                                · 外 warm + 内 cool 嵌套
```

---

## §3 Block 清单（每块独立可实现单元）

| Block ID | 名称 | Section | Mood | Component | testid root | Tokens 用到 |
|---|---|---|---|---|---|---|
| `B1` | 顶部 Topbar 关闭/帮助 | hero | **warm** | L0.NavBar | `p02-topbar` | `--tkn-color-card` · `--tkn-color-text-primary` · `--tkn-shadow-card-deep` · `--tkn-spacing-md` |
| `B2` | 学科 Chip 横滚条（4 学科） | hero | **warm** | M5.SubjectChip × 4 | `subject-chip-strip` | `--tkn-subject-math/physics/chemistry/english`（铁律1例外 `subject-palette`）· `--tkn-radius-pill` · `--tkn-color-text-primary`（选中描边） |
| `B3` | 取景器（相机预览 + 边缘检测 + 网格） | hero | **cool**（功能必需 · 嵌入 widget） | L0.Custom Viewfinder | `p02-viewfinder` | `--tkn-color-bg-camera` · `--tkn-color-text-on-dark` · `--tkn-radius-md` · `--tkn-shadow-card-deep` |
| `B4` | 取景提示文案（"对准题目..."） | hero | **warm** | L0.Caption | `p02-tip` | `--tkn-type-caption` · `--tkn-color-text-secondary` |
| `B5` | 快门栏（图库 / 78px 快门 / 闪光） | hero | **warm** | L0.Custom ShutterBar | `p02-shutter-bar` | `--tkn-radius-circle` · `--tkn-color-white` · `--tkn-color-card` · `--tkn-color-text-primary` · `--tkn-shadow-card-deep` |
| `B6` | 模式 Tab（单题 / 多题 / 文件） | hero | **warm** | L0.TabBar | `p02-mode-tabs` | `--tkn-type-caption-bold` · `--tkn-color-text-secondary`（默认）· `--tkn-color-primary-link`（选中） |

> **fe-preflight 用法**：把 mockup HTML 切成这些块；fe-builder 按块逐个实现并跑 lint。
> **铁律 2 校验（混合 mood）**：外层 `<section data-mood="warm">`，B3 viewfinder 嵌套 `<div data-mood="cool">`。lint 按"最近祖先 mood"判定 token 合法性 ——
>  · B1 / B2 / B4 / B5 / B6 内禁止 `--tkn-color-bg-camera` / `--tkn-color-text-on-dark` / 任何 cool dark token
>  · B3 viewfinder 内禁止 `--tkn-color-warm-*` / `--tkn-color-mastery-*` / `--tkn-color-bg-light`
>  · 唯二的例外注册：B2 `subject-palette`（铁律 1 例外）。

---

## §4 数据契约（page-level interface）

```typescript
interface CapturePageProps {
  defaultSubject: 'math' | 'physics' | 'chemistry' | 'english';
  lastSubject?: 'math' | 'physics' | 'chemistry' | 'english';
  config: { maxFileMB: number };
  device: { permissions: { camera: 'granted' | 'denied' | 'prompt'; gallery: 'granted' | 'denied' | 'prompt' } };
}

interface CaptureUploadResp {
  questionId: string;
  uploadUrl: string;
  taskId: string;        // 创建后立刻发起 ai/analyze, 跳 P03 时携带
  status: 'PENDING';
}
```

> 类型不一致直接 fail。

---

## §5 API 触点

| Method | Path | 用途 | P95 预算 | 失败降级 |
|---|---|---|---|---|
| POST | `/api/file/presign` | 拿到上传 URL | 200ms | 重试 3 次后切原生表单上传 |
| POST | `/api/wb/questions` | 创建 PENDING question + 拿 questionId | 300ms | 弱网断点续传 chunk 2MB |
| POST | `/api/ai/analyze` | 触发 AI 分析任务（导航 P03 前发起） | 400ms | 失败回 P02 顶部红 toast |

---

## §6 状态机

| State | 触发 | UI 表现 |
|---|---|---|
| `IDLE` | 进入页面、相机权限 granted | 取景器渲染、快门可用 |
| `FOCUSING` | 学生轻点取景器 | 短暂白边脉冲 ≤300ms |
| `CAPTURED` | 按下快门 | 取景器锁定 + 预览态（缩略图浮起） |
| `UPLOADING` | 预签名 + 上传进行中 | 快门变进度环（0-100%）+ 取消按钮 |
| `UPLOADED` | 上传 200 + questionId 落库 | 跳 P03（200ms 淡出） |
| `ERROR` | 权限拒绝 / 超时 / 上传失败 | 顶部红条 + 重试按钮 + 引导设置 |

---

## §7 跳转图

```
[入口]
  Tab 3「拍题」 ─┐
  P05 右下 FAB ─┤──→ P02
  P-HOME 快捷  ─┤
  深链 wb://capture ─┘
                │
                ├──[B5 快门 → 上传成功]──→ P03 (taskId)
                ├──[B1 关闭 ×]────────→ 返回上一页 (Tab/P05/P-HOME)
                └──[权限拒绝]───────→ 引导卡 + 跳系统设置
```

---

## §8 AC 覆盖表（与 business-analysis.yml 双向锚定）

| AC ID | 验收点 | 涉及 Block | testid 验证点 |
|---|---|---|---|
| `AC-P02-001` | 相机权限 granted 时取景器 70% 高度作为内嵌 cool widget 渲染（外层 warm） | B3 | `p02-viewfinder` 存在且 `data-mood="cool"`；其最近祖先 `<section data-mood="warm">` |
| `AC-P02-002` | 顶部 4 个学科 chip 横滚展示，default 学科有 1px 内描边选中态 | B2 | `subject-chip-math` 等 4 个 testid 至少有 1 个 `aria-pressed="true"` |
| `AC-P02-003` | 78px 圆形快门按钮触摸面积 ≥ 44×44px，按下后进入 UPLOADING | B5 | `p02-shutter-bar-shutter-btn` `min-height ≥ 78` |
| `AC-P02-004` | 上传完成后 ≤ 500ms 跳 P03 携带 taskId 参数 | — (state) | `wb_capture_upload_success` 埋点 + URL 包含 `taskId` |
| `AC-P02-005` | 混合 mood token 隔离（铁律 2 校验） | 全部 | (a) B1/B2/B4/B5/B6 内 grep `--tkn-color-bg-camera` / `--tkn-color-text-on-dark` 命中 = 0；(b) B3 内 grep `--tkn-color-warm-` / `--tkn-color-mastery-` / `--tkn-color-bg-light` 命中 = 0；(c) 整页 `--tkn-color-celebrate-` 命中 = 0 |
| `[AI 推测] AC-P02-006` | 模式 Tab 默认选中"单题"，切换到"多题/文件"时快门改文案为"开始拍" | B6 | `p02-mode-tabs-tab-1` `aria-selected="true"` |
| `[AI 推测] AC-P02-007` | 闪光灯按钮点击后 `aria-pressed` toggle | B5 | `p02-shutter-bar-flash-btn` |

> **fe-accept-mock 用法**：用这张表逐条断言；缺 testid = 验收 fail。

---

## §9 异常路径

| 触发 | UI | 系统行为 |
|---|---|---|
| 相机权限 denied | 全屏暗底中央卡："需要相机权限才能拍题" + "去设置" 蓝 pill + "选图库代替" 灰链接 | 跳系统设置（小程序：调用 wx.openSetting） |
| 弱网（>3s 无进度） | 快门进度环停留 + 上方 toast "网络较慢，正在重试…" | chunk 2MB 分片，重试 3 次 |
| 文件 >10MB | 上传前自动压缩到 4MB，显示"压缩中"hint 200ms | 端上 canvas 压缩 |
| AI 服务降级 503 | 取消上传 + 顶部红条 "服务暂不可用，稍后重试" | 用户手动重试 |

---

## §10 埋点事件

| 事件名 | 触发 Block | 必带属性 |
|---|---|---|
| `wb_capture_open` | 页面进入 | `entry: 'tab' \| 'fab' \| 'deeplink'`, `defaultSubject` |
| `wb_capture_subject_change` | B2 | `from`, `to` |
| `wb_capture_shutter` | B5 | `subject`, `mode` |
| `wb_capture_upload_start` | B5 | `bytes`, `subject` |
| `wb_capture_upload_success` | B5 | `ms`, `bytes`, `subject` |
| `wb_capture_permission_denied` | 异常 | `reason: 'camera' \| 'gallery'` |

> 事件经 `packages/analytics`；本页全程登录态，不需 `device_fp`。

---

## §11 性能预算

- TTI ≤ 800ms（相机权限已授时）
- LCP ≤ 1000ms（取景器首帧）
- CLS < 0.05
- API P95 / `presign` ≤ 200ms · `questions` ≤ 300ms
- 快门按下到进 P03 ≤ 1500ms（含上传 800ms + AI 启动 400ms + 路由切换 200ms）

---

## §12 A11y

- Landmarks: `<header role="banner">` (B1), `<main role="main">` (B3 viewfinder + B5 shutter)
- 焦点顺序: B1 close → B2 first chip → B5 shutter btn → B5 gallery → B5 flash → B6 mode tabs
- 屏幕阅读器优先级: B5 快门 `aria-label="拍摄按钮 · 当前学科 数学"`，按下后朗读 "正在上传 X%"
- `prefers-reduced-motion`: 关闭快门进度环旋转，仅用线性数字 % 替代

---

## §13 Mockup 锚定

- **权威**: `design/mockups/wrongbook/02_capture.html` (v1)
- **历史变体**: v0 全屏 cool 暗底（已废弃，违反 mood 哲学 v2）
- **截图**: `design/system/screenshots/P02-v1-mixed.png`（fe-preflight 自动生成）
- **反向锚定**: mockup HTML `<head>` 中含 `<meta name="design-spec" content="P02-capture.spec.md@v1">`

---

## §14 Tokens 清单（grep 校验用）

```
L1 (Apple base · 整页可用):
  --tkn-color-black           (设备 chrome 边框，不算页面 mood)
  --tkn-color-white
  --tkn-color-primary-link    (B6 mode-tab 选中态 #0066cc · 亮底专用)
  --tkn-font-display
  --tkn-font-text
  --tkn-type-body
  --tkn-type-caption
  --tkn-type-caption-bold
  --tkn-spacing-2
  --tkn-spacing-xs
  --tkn-spacing-sm
  --tkn-spacing-12
  --tkn-spacing-md
  --tkn-spacing-lg
  --tkn-spacing-xl
  --tkn-radius-sm
  --tkn-radius-md
  --tkn-radius-pill
  --tkn-radius-circle
  --tkn-shadow-focus
  --tkn-motion-dur-fast
  --tkn-motion-dur-base
  --tkn-motion-ease-standard

L2 (warmth · 外层 warm 区段使用 · B1/B2/B4/B5/B6):
  --tkn-color-bg-light              (page 背景米白)
  --tkn-color-card        (B1 icon-btn / B5 侧按钮 / B3 widget 卡片背)
  --tkn-color-text-primary    (statusbar / topbar SVG / B2 chip 选中描边)
  --tkn-color-text-secondary  (B4 提示 / B6 默认 tab)
  --tkn-shadow-card-deep           (B1 / B3 / B5 微投影)

L2 (cool · 仅 B3 viewfinder 内层使用 · 功能必需例外):
  --tkn-color-bg-camera       (B3 vf-camera-bg · 不可被 B1/B2/B4/B5/B6 引用)
  --tkn-color-text-on-dark         (B3 内白字 / 网格 / 边缘检测)

L3 (celebration · 仅庆祝白名单时刻):
  (本页禁用)

EXCEPTION (subject · 铁律 1 例外 ID `subject-palette`):
  --tkn-subject-math
  --tkn-subject-physics
  --tkn-subject-chemistry
  --tkn-subject-english

FORBIDDEN (整页禁止):
  --tkn-color-bg-light-celebrate / --tkn-color-encouragement-* / --tkn-color-mastery-* / --tkn-color-celebrate-*
  --tkn-color-primary-dark         (亮底页面用 primary-link，dark 仅留给 B3 内层 cool 区，但 B3 当前不需要主色)
```

> **混合 mood lint 规则**：每个 token 引用都要追溯到"最近祖先 `data-mood`"——warm 祖先内禁 cool dark token，cool 祖先（仅 B3）内禁 warm token。例外白名单：`subject-palette`（B2 chips）。

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
