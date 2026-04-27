# 合规规则 · fe-builder

> S7 实战确立的规则体系。每次 Builder 区块完成后必须全部通过。

---

## 三条 Grep 门禁

### Gate 1 · 零硬编码色值（CSS Module）

```bash
# 检查 CSS module 中是否有硬编码颜色
# 预期：0 行（本地变量定义行除外）
grep -n '#[0-9a-fA-F]\{3,6\}\|rgb(\|hsl(' <CSS_FILE> \
  | grep -v '^\s*/\*'   # 排除注释行
  | grep -v '--[^:]*:' # 排除 CSS 变量定义行（.root 内的例外声明）
```

**允许出现的例外（不算违规）：**

| 值 | 用途 | 原因 |
|---|---|---|
| `#5FA8FF` | 渐变起点（`--grad-start`） | 无 token，pre-flight 批准 |
| `#FF6B5E` | 危险渐变起点 | 渐变起点亮色，无 token |
| `#FFCC00` | 相机黄（`--cam-yellow`） | 相机 UI 专属，pre-flight 批准 |
| `#0B0F1A` | 相机深色背景（`--cam-bg`） | 相机 UI 专属，pre-flight 批准 |
| `#fbf8f0`、`#ece5d3` 等 | 纸张/缩略图装饰色 | 纯装饰，无对应 token |
| `#fde9e9`、`#7a3b3b` 等 | 题号 badge 装饰色 | 纯装饰，无对应 token |
| `rgba(255, 80, 80, 0.55)` | 错题划线装饰 | 装饰性，不影响功能色 |

> **判断原则**：例外色值必须在 `.root {}` 内作为本地变量声明，而非直接写在 class 属性里。

### Gate 2 · 无 iOS 系统色残留

```bash
# 检查是否有已废弃的 iOS 色变量
grep -n 'var(--blue)\|var(--red)\|var(--green)\|var(--orange)\|var(--indigo)' <CSS_FILE>
# 预期：0 行
```

这些是 S7 改造前的旧变量名，不应在新代码中出现。

### Gate 3 · Testid 覆盖（TSX）

```bash
# 统计 TSX 中 data-testid 的数量
grep -c 'data-testid' <TSX_FILE>
# 结果应 ≥ build-spec.json 中该页面所有 testids 总数
```

---

## iOS 系统色 → Design Token 映射（A1/B1 决策）

| iOS 色值 | 用途 | → Design Token |
|---|---|---|
| `#007AFF` | 主色（蓝） | `--tkn-color-primary-default (#0071e3)` |
| `#FF3B30` | 危险色（红） | `--tkn-color-danger-default (#c0392b)` |
| `#FF9500` | 警告色（橙） | `--tkn-color-warning-default (#b45309)` |
| `#34C759` | 成功色（绿） | `--tkn-color-success-default (#1a7d34)` |
| `#5856D6` | 靛色 | `--tkn-color-primary-default`（无 indigo token，用 primary 替代） |
| `#007AFF @ opacity` | 主色透明 | `rgba(var(--primary-rgb), opacity)` |
| `#FF3B30 @ opacity` | 危险色透明 | `rgba(var(--danger-rgb), opacity)` |
| `#1C1C1E` / `#111` | 深色文字 | `--tkn-color-text-primary (#1d1d1f)` |
| `#3C3C43` | 次级文字 | `--tkn-color-text-secondary` |
| `#8E8E93` | 三级文字 | `--tkn-color-text-tertiary` |
| `#F2F2F7` | 页面背景 | `--tkn-color-bg-light (#f5f5f7)` |
| `rgba(60,60,67,.14)` | 分割线 | `--separator`（本地变量，无 border-subtle token） |

---

## 学科色映射（C1 决策）

| 学科 | iOS mockup 色 | → Design Token |
|---|---|---|
| 数学 | `#007AFF`（蓝） | `--tkn-subject-math (#c41e3a)` |
| 物理 | `#FF9500`（橙） | `--tkn-subject-physics (#1560bd)` |
| 化学 | `#5856D6`（靛） | `--tkn-subject-chemistry (#2d6a2d)` |
| 英语 | `#34C759`（绿） | `--tkn-subject-english (#c45e00)` |

> **注意**：C1 导致 CSS 类名必须从 `.subBlue` 改为 `.subMath`（语义化）。

---

## 已知 Token 缺口（无需修复，用本地变量替代）

| 缺失 Token | 替代方案 |
|---|---|
| `--tkn-color-border-subtle` | `--separator: rgba(60,60,67,.14)` 本地变量 |
| `--tkn-color-segment-track` | `--search-bg: rgba(118,118,128,.12)` 本地变量（E3） |
| `--tkn-color-input-bg` | 同上 |
| `--tkn-color-separator` | 同 `--separator` |

---

## 快速自检 Checklist

Builder 每完成一个区块，过一遍：

- [ ] CSS module 中无 `#007AFF` / `#FF3B30` / `#34C759` / `#FF9500` / `#5856D6`
- [ ] CSS module 中无 `var(--blue)` / `var(--red)` / `var(--green)` 等旧变量
- [ ] 所有颜色要么是 `var(--tkn-*)` 要么是在 `.root {}` 中声明的本地例外变量
- [ ] SVG 中品牌色 `stroke="#007AFF"` 已改为 `stroke="currentColor"`
- [ ] TSX 中内联 `style={{ color: '#FF3B30' }}` 已改为 `className={s.explainError}` 等
- [ ] build-spec.json 中的 testid 全部存在于 TSX
- [ ] 学科色 class 名语义化（`.subMath` 不是 `.subBlue`）
