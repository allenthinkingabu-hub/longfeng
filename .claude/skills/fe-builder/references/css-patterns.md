# CSS Token 使用模式 · fe-builder 参考

> 来源：S7 实战（ListPage / CapturePage / DetailPage）

---

## 1. 基本 Token 引用

```css
/* ✅ 正确 */
.nav { background: var(--tkn-color-bg-light); }
.title { color: var(--tkn-color-text-primary); }
.card { border-radius: var(--tkn-radius-lg); }
.content { padding: var(--tkn-spacing-7) var(--tkn-spacing-8); }

/* ❌ 错误 */
.nav { background: #F2F2F7; }
.title { color: #1C1C1E; }
```

---

## 2. 本地 CSS 变量例外模式

**当 mockup 色值没有对应 token 时**，在 `.root {}` 顶部集中声明本地变量，并加注释：

```css
.root {
  /* ── Non-token local vars · approved exceptions ──────────────── */
  --grad-start: #5FA8FF;          /* 渐变起点，无 token，pre-flight E1 approved */
  --separator: rgba(60, 60, 67, 0.14); /* iOS separator，无 --tkn-color-border-subtle token */
  --nav-glass: rgba(245, 245, 247, 0.78); /* nav 玻璃效果，基于 --tkn-color-bg-light */
  --search-bg: rgba(118, 118, 128, 0.12); /* segment/ghost-btn，E3 approved */
  /* ──────────────────────────────────────────────────────────────── */
}
```

**规则：**
- 例外变量名用 `--` 前缀但不用 `--tkn-` 前缀（明确区分非 token）
- 每个例外必须有注释说明原因（pre-flight 批准号 / 无对应 token 等）
- 必须在文件顶部（`.root {}` 内）集中声明，不能散落在各处

---

## 3. RGB 分量变量（用于透明衍生色）

CSS 无法从 hex token 提取 RGB，所以用 RGB 分量变量实现 `rgba()` 透明色：

```css
.root {
  --primary-rgb: 0, 113, 227;      /* derived from --tkn-color-primary-default #0071e3 */
  --danger-rgb: 192, 57, 43;       /* derived from --tkn-color-danger-default #c0392b */
  --warning-rgb: 180, 83, 9;       /* derived from --tkn-color-warning-default #b45309 */
  --success-rgb: 26, 125, 52;      /* derived from --tkn-color-success-default #1a7d34 */
}

/* 使用 */
.fab {
  box-shadow: 0 16px 30px rgba(var(--primary-rgb), 0.35);
}
.chipPrimary {
  background: rgba(var(--primary-rgb), 0.08);
  border-color: rgba(var(--primary-rgb), 0.18);
}
.btnPrimary:disabled {
  background: rgba(118, 118, 128, 0.20); /* gray，无语义，可写裸值 */
}
```

---

## 4. 渐变按钮模式

```css
/* 主色渐变（CTA 主按钮 / FAB / Shutter） */
.btnPrimary {
  background: linear-gradient(180deg, var(--grad-start), var(--tkn-color-primary-default));
  box-shadow: 0 10px 24px rgba(var(--primary-rgb), 0.28);
}

/* 危险渐变（删除确认按钮） */
.btnDanger {
  background: linear-gradient(180deg, #FF6B5E, var(--tkn-color-danger-default));
  box-shadow: 0 10px 24px rgba(var(--danger-rgb), 0.28);
}
```

> `#FF6B5E` 是危险渐变起点亮色，无对应 token，可作为裸值保留。

---

## 5. 相机页专属例外模式（CapturePage）

相机页是深色 UI，有一套与普通页面完全不同的色彩体系，全部允许裸色值：

```css
.root {
  /* Camera-specific exceptions · approved in CapturePage-token-mapping-review.md */
  --cam-bg: #0B0F1A;               /* 相机深色背景 */
  --cam-bg-grad: #1d2433;          /* viewfinder 渐变背景 */
  --cam-yellow: #FFCC00;           /* 扫描线/角框/模式 indicator */
  --grad-start: #5FA8FF;           /* shutter 渐变起点 */
  --primary-rgb: 0, 113, 227;
  --danger-rgb: 192, 57, 43;
}

/* rgba 磨砂效果 · 相机页允许裸 rgba 值 */
.iconBtn { background: rgba(0, 0, 0, 0.45); }
.subj { background: rgba(255, 255, 255, 0.10); }
```

---

## 6. Font Family 模式

```css
/* ✅ 正确：token + 中文字体 fallback */
.root {
  font-family: var(--tkn-font-text), 'PingFang SC', sans-serif;
}

/* ❌ 错误：直接写系统字体栈 */
.root {
  font-family: -apple-system, BlinkMacSystemFont, "SF Pro Text", sans-serif;
}
```

---

## 7. 学科色语义化命名

```css
/* ✅ 正确：语义化（C1 决策） */
.subMath      { color: var(--tkn-subject-math); }
.subPhysics   { color: var(--tkn-subject-physics); }
.subChemistry { color: var(--tkn-subject-chemistry); }
.subEnglish   { color: var(--tkn-subject-english); }

/* ❌ 错误：颜色化命名（与实际颜色绑定，违反 C1） */
.subBlue   { color: #007AFF; }
.subOrange { color: #FF9500; }
```

TSX 同步：

```tsx
// ✅ 正确
const SUBJECT_COLOR = { math: s.subMath, physics: s.subPhysics, ... };

// ❌ 错误
const SUBJECT_COLOR = { math: s.subBlue, physics: s.subOrange, ... };
```

---

## 8. SVG 图标颜色

```tsx
{/* ✅ 正确：用 currentColor 继承父级 CSS color */}
<svg><path stroke="currentColor" ... /></svg>

{/* ✅ 正确：白色图标（在深色/彩色背景上，#fff 是允许的） */}
<svg><path stroke="#fff" ... /></svg>

{/* ❌ 错误：品牌色写死在 SVG 属性里 */}
<svg><path stroke="#007AFF" ... /></svg>
```

---

## 9. 间距 Token 速查

| px 值 | Token |
|---|---|
| 4px | `--tkn-spacing-2` |
| 6px | `--tkn-spacing-3` |
| 8px | `--tkn-spacing-4` |
| 10px | `--tkn-spacing-5` |
| 12px | `--tkn-spacing-6` |
| 14px | `--tkn-spacing-7` |
| 16px | `--tkn-spacing-8` |
| 20px | `--tkn-spacing-9` (≈) |
| 24px | `--tkn-spacing-10` |

| px 值 | Token |
|---|---|
| 4px | `--tkn-radius-xs` |
| 6px | `--tkn-radius-sm` |
| 11px | `--tkn-radius-md` |
| 12px | `--tkn-radius-lg` |
| 999px | `--tkn-radius-pill` |
| 50% | `--tkn-radius-circle` |

> **规则**：差值 ≤ 2px 取最近 token（D1 决策）；差值 > 2px 写裸值并加注释。
