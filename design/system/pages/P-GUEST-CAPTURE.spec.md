---
page_id: P-GUEST-CAPTURE
name: 游客拍题
name_en: Guest Capture
route_h5: /guest/capture
route_miniprogram: pages/guest/capture
deeplink: wb://guest/capture
auth_state: anonymous
persona:
  - 犹豫期潜在用户
scenarios:
  - SC-12
mockup_canonical: design/mockups/wrongbook/15_guest_capture.html
mockup_version: v1
last_reviewed: 2026-05-02
status: spec-draft
sprint: S3
---

# P-GUEST-CAPTURE · 游客拍题

> **使用说明**：本 spec 视觉 95% 复用 P02-capture.spec.md，差异点：顶部多一条 GuestQuotaBanner（encouragement-soft 半透明），数据上不写 wrongbook / review-plan。
> **Iron rule 6**：单段沉浸 hero（cool 全屏），banner 不算独立 section。

---

## §1 页面目的（why · 1 句话）

让犹豫期访客在 0 注册成本下完成 1 次完整拍题 + AI 分析体验，同时清晰提示"游客模式 · 注册后不限次"以驱动注册转化。

---

## §2 ASCII 布局图

```
┌─────────────────────────────────────┐
│  [B1 statusbar · cool]               │
│  [B2 guest-quota-banner · cool]      │  ← encouragement-soft 半透明 banner
│   "今天还可试用 1 次 · [注册后不限次 →]"
├─────────────────────────────────────┤
│                                      │
│  [B3 camera-preview · cool]          │  ← --tkn-color-bg-camera
│   70% 高度取景器                      │
│   边缘检测框（虚线动画）               │
│                                      │
├─────────────────────────────────────┤
│  [B4 subject-chip-row · cool]        │
│   横滚 4 学科 chip (math/physics/    │
│   chemistry/english)                 │
│                                      │
│  [B5 capture-controls · cool]        │
│   [图库] [78px 圆形快门] [闪光灯]     │
└─────────────────────────────────────┘
```

---

## §3 Block 清单（每块独立可实现单元）

| Block ID | 名称 | Section | Mood | Component | testid root | Tokens 用到 |
|---|---|---|---|---|---|---|
| B1 | 状态栏 | shell | cool | (atom) | `p-guest-capture-statusbar` | `--tkn-color-text-on-dark` |
| B2 | 游客额度横幅 | hero | cool | GuestQuotaBanner (Banner + Link) | `guest-quota-banner` | `--tkn-color-encouragement-soft` / `--tkn-color-encouragement-DEFAULT` / `--tkn-color-text-on-dark` / `--tkn-type-caption` |
| B3 | 取景器 | hero | cool | CameraPreview (70% h) | `camera-preview` | `--tkn-color-bg-camera` / `--tkn-color-overlay-media` / `--tkn-color-text-on-dark` |
| B4 | 学科 Chip 横滚 | hero | cool | SubjectChip[] (M5 · variant=filter) | `subject-chip-row` | `--tkn-subject-math` / `--tkn-subject-physics` / `--tkn-subject-chemistry` / `--tkn-subject-english` / `--tkn-radius-pill` |
| B5 | 快门 + 辅助控件 | hero | cool | (Button × 3 + 78px 圆形快门) | `capture-controls` | `--tkn-color-white` / `--tkn-color-primary-DEFAULT` / `--tkn-radius-circle` / `--tkn-shadow-focus` |

---

## §4 数据契约（page-level interface）

```typescript
interface GuestCaptureContext {
  deviceFp: string;               // IndexedDB + Canvas + UA 组合指纹
  guestSessionId: string | null;  // 首次进入为 null，POST 后获取
  quotaRemaining: 0 | 1;          // 1/天 设备额度
  quotaResetAt: string;           // ISO，Asia/Shanghai 00:00
  selectedSubject: 'math' | 'physics' | 'chemistry' | 'english';
}

interface GuestAnalyzeReq {
  device_fp: string;
  subject: string;
  image_url: string;              // 由 client OSS 预签名上传后获取
}

interface GuestAnalyzeResp {
  guest_session_id: string;
  task_id: string;                // 跳 P03 用
  status: 'ANALYZING';
}

interface GuestQuotaError {
  error: 'QUOTA_EXHAUSTED';
  reset_at: string;
}
```

---

## §5 API 触点

| Method | Path | 用途 | P95 预算 | 失败降级 |
|---|---|---|---|---|
| POST | `/api/file/presign` | 拿到上传 URL | 200ms | 重试 1 次 → Toast |
| POST | `/api/guest/analyze` | 启动游客分析 | 800ms（不含 AI） | `429 QUOTA_EXHAUSTED` → 跳 P00 |
| GET | `/api/guest/session/:sid` | 查询会话状态（claim 用） | 150ms | silently retry |

---

## §6 状态机

| State | 触发 | UI 表现 |
|---|---|---|
| `IDLE` | 进入页面 | 快门可点 · banner 显示"还可试用 1 次" |
| `FOCUSING` | 取景器对焦中 | 边缘检测框虚线动画 |
| `CAPTURED` | 快门按下 | 预览图 + "重拍 / 使用" 浮层 |
| `UPLOADING` | "使用" 后 | 进度条 0-100% |
| `ANALYZING` | upload 完成 | 跳 P03（顶部保留游客 banner） |
| `QUOTA_EXHAUSTED` | API 返回 429 | 整页挡板"今日额度已用完 · 注册后不限次" + CTA → P00 |
| `ERROR` | 网络 / 权限失败 | Toast "请检查相机权限" + 重试 |

---

## §7 跳转图

```
[入口]
  P-LANDING "试一试" ──┐
  wb://guest/capture ─┤──→ P-GUEST-CAPTURE
  P00 "看看再说" ──────┘
        │
        ├──[B2 banner "注册后不限次"]──→ P00
        ├──[B5 快门 → 上传成功]────────→ P03 (游客态，顶部保留 banner)
        ├──[B5 图库入口]──────────────→ 系统相册
        └──[QUOTA_EXHAUSTED 挡板 CTA]──→ P00
```

---

## §8 AC 覆盖表

| AC ID | 验收点 | 涉及 Block | testid 验证点 |
|---|---|---|---|
| `AC-GUEST-001` | 顶部 GuestQuotaBanner 显示剩余额度 + "注册后不限次"链接 | B2 | `guest-quota-banner-text` 文本含 "1 次" · `guest-quota-banner-cta` href = `/auth` |
| `AC-GUEST-002` | banner 背景为 encouragement-soft 半透明 · 不遮挡取景器 | B2 | `guest-quota-banner` background 命中 `--tkn-color-encouragement-soft` |
| `AC-GUEST-003` | 取景器占视口 70% 高度 · 暗底 --tkn-color-bg-camera | B3 | `camera-preview` height ≈ 70vh · 父容器 background 命中 `--tkn-color-bg-camera` |
| `AC-GUEST-004` | 4 学科 chip 横滚 · 可单选 · 选中态 1px 内描边 | B4 | `subject-chip-math`, `subject-chip-physics`, `subject-chip-chemistry`, `subject-chip-english` 可见 · 选中 chip aria-pressed=true |
| `AC-GUEST-005` | 快门 78px 圆形 · 触摸目标 ≥44×44px | B5 | `capture-controls-shutter` 宽高 = 78px · 满足 a11y |
| `AC-GUEST-006` | 429 QUOTA_EXHAUSTED 触发整页挡板 + CTA → P00 | B2/B5 | `quota-exhausted-screen` 可见 · `quota-exhausted-cta-register` click 后路由 = `/auth` |
| `AC-GUEST-007` | [AI 推测] 游客 → 注册 转化率 ≥25%（增长漏斗 KPI） | B2 | `anon_guest_claim_success` / `anon_guest_capture_view` 比值 |
| `AC-GUEST-008` | 整页 0 暖色 0 庆祝色（P02 视觉一致性） | B1-B5 | grep mockup `--tkn-color-mastery-*` count = 0 |

---

## §9 异常路径

| 触发 | UI | 系统行为 |
|---|---|---|
| 设备配额耗尽 (`429`) | 整页挡板 "今日额度已用完" + 注册 CTA | 路由跳 P00；不再显示 banner |
| AI 失败 ≥2 次 | P03 游客态顶部红条 + "建议注册后重试" | **不扣减额度**；session.status=FAILED |
| 相机权限拒绝 | 挡板 "请允许相机权限" + 跳系统设置 | 不扣减额度 |
| 弱网 / 上传失败 | Toast "上传失败 · 重试" + 自动重试 1 次 | 仍失败则降级到挡板 |
| 用户同设备同 IP 24h 内重复访问 | 进入即检查 quota，0 时直接挡板 | 由后端 Redis bucket 拦截 |
| 海外地区 GDPR | 上传前弹未成年人保护声明，必须勾选才能拍 | 本地记录 consent_at |

---

## §10 埋点事件

| 事件名 | 触发 Block | 必带属性 |
|---|---|---|
| `anon_guest_capture_view` | 进入页面 | `device_fp`, `entry_source`, `quota_remaining` |
| `anon_guest_capture_shoot` | B5 快门按下 | `device_fp`, `subject` |
| `anon_guest_analyze_start` | B5 上传完成调用 analyze | `device_fp`, `subject` |
| `anon_guest_analyze_done` | analyze 接口返回 | `device_fp`, `latency`, `subject`, `success` |
| `anon_guest_quota_exhausted` | 429 触发挡板 | `device_fp`, `ip_hash` |
| `anon_guest_register_cta` | banner / 挡板 CTA 点击 | `device_fp`, `cta_position` |

> 必须携带 `device_fp`；禁带原始 PII。

---

## §11 性能预算

- TTI ≤ 800ms（取景器流可见）
- 相机权限弹窗 ≤ 200ms
- API `/api/guest/analyze` P95 ≤ 800ms（不含 AI）
- AI SSE 首字节 ≤ 3s（在 P03 游客态承接）
- CLS < 0.05

---

## §12 A11y

- Landmarks: `<main role="main">` (B3), `<nav role="navigation" aria-label="学科选择">` (B4), `<footer role="contentinfo">` (B5)
- 焦点顺序: `guest-quota-banner-cta` → `subject-chip-math` → ... → `capture-controls-shutter`
- 屏幕阅读器朗读优先级: B2 banner 优先 aria-live="polite" "你今天还可以试用 1 次"
- `prefers-reduced-motion`: 关闭边缘检测框虚线动画 · 关闭快门按下 scale 反馈
- 触摸目标全部 ≥ 44×44px

---

## §13 Mockup 锚定

- **权威**: `design/mockups/wrongbook/15_guest_capture.html` (v1)
- **历史变体**: 旧版 v0 已归档至 `_archive/`
- **视觉参照**: `02_capture.html` (P02 主拍题页) 95% 视觉一致
- **反向锚定**: mockup HTML `<head>` 中含 `<meta name="design-spec" content="P-GUEST-CAPTURE.spec.md@v1">`

---

## §14 Tokens 清单（grep 校验用）

```
L1 (Apple base):
  --tkn-color-black
  --tkn-color-white
  --tkn-color-text-on-dark
  --tkn-color-primary-DEFAULT
  --tkn-color-overlay-media
  --tkn-font-display
  --tkn-font-text
  --tkn-type-body
  --tkn-type-caption
  --tkn-type-micro
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
  --tkn-motion-ease-standard
  --tkn-motion-dur-base
  --tkn-motion-dur-fast

L2 (warmth · banner 局部使用):
  --tkn-color-bg-camera
  --tkn-color-encouragement-DEFAULT
  --tkn-color-encouragement-soft

EXCEPTION (subject · 仅 B4 chip):
  --tkn-subject-math
  --tkn-subject-physics
  --tkn-subject-chemistry
  --tkn-subject-english
```

---

## 模板使用清单

- [x] frontmatter 完整
- [x] §1-§3 完成
- [x] §4 GuestAnalyzeReq/Resp 与业务文档 §10 一致
- [x] §6 状态机含 QUOTA_EXHAUSTED
- [x] §8 AC 表 8 条；`AC-GUEST-007` 标 [AI 推测]
- [x] §10 埋点事件 anon_ 前缀
- [x] §14 Tokens 清单完整
