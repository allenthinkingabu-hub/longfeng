# scratch_summary_sd_t03

## 本任务状态
status: **done**
finished_at: 2026-04-22T22:00:00Z
attempt: 1

## 产出清单

| file | sha256 | note |
|---|---|---|
| `frontend/packages/ui-kit/package.json` | (not hashed; contains `build:tokens` script + `style-dictionary@^4` devDep) | workspace-root-free; plain npm install inside package |
| `frontend/packages/ui-kit/build-tokens.mjs` | `73c3519e5a4cebe35d44fd8ab3d1a7da06995391c3f8bd0dd28a7b15d6d5e62e` | 180 lines, SD 4.x config + parser/transforms/format/fileHeader |
| `frontend/packages/ui-kit/src/tokens.css`   | `ec9f10d631d1a643f443428478144a27a00f48865579bb5be71777ecb8bdcb4b` | 150 vars, `:root`, `outputReferences: true`, header begins "AUTO-GENERATED …" |
| `frontend/packages/ui-kit/src/tokens.wxss`  | `37e29f71fcb652e79e70b97139983557f5dd733a22de48f0cd5f84fede6d4b0d` | 150 vars, `page`, saturation+shadow overrides applied |
| `frontend/packages/ui-kit/src/tokens.ts`    | `9da3b4498d3f0f5466b37ac30aa2777444ba4aad10b3562eed6977c5ff19a88f` | exports `tkn` (nested `as const`) + `Tkn` type |

State updated: `state/phase-sd.yml` (sd-t03 = done), `state/interfaces.yml` (created fresh with sd-t02→sd-t03 and sd-t03→sd-t04 edges).

## 上游 sha256 校验（全部匹配 phase-sd.yml.outputs_hash）

| file | expected | actual | match |
|---|---|---|---|
| color.json      | `eed7b6…40cf` | `eed7b6…40cf` | ✓ |
| typography.json | `1c31a7…ae7e` | `1c31a7…ae7e` | ✓ |
| spacing.json    | `6280bc…1a69` | `6280bc…1a69` | ✓ |
| radius.json     | `296ef3…ef7e` | `296ef3…ef7e` | ✓ |
| shadow.json     | `bfe088…d74d` | `bfe088…d74d` | ✓ |
| motion.json     | `95d4a4…a178a` | `95d4a4…a178a` | ✓ |

`SAT_DELTA_PCT = 12`（从 `color.json._meta.platform_overrides.wechat.saturation_delta` 读取）。

## 平台差异校验（hex 样本）

### 全变 (6 例，证明 transform 生效)

| token                        | tokens.css  | tokens.wxss | HSL-sat 变化 |
|------------------------------|-------------|-------------|--------------|
| `--tkn-color-success-default`| `#1a7d34`   | `#118630`   | +12 pts      |
| `--tkn-color-warning-default`| `#b45309`   | `#bd5200`   | +12 pts      |
| `--tkn-color-danger-default` | `#c0392b`   | `#ce2e1d`   | +12 pts      |
| `--tkn-subject-math`         | `#c41e3a`   | `#d21031`   | +12 pts      |
| `--tkn-subject-chemistry`    | `#1a6b3a`   | `#127338`   | +12 pts      |
| `--tkn-color-bg-light`       | `#f5f5f7`   | `#f4f4f8`   | +12 pts (tiny on low-chroma) |

### 不变 (已在 100% 饱和或无饱和的色，非 bug)

| token                        | tokens.css = tokens.wxss | 原因 |
|------------------------------|--------------------------|------|
| `--tkn-color-black`          | `#000000` | 无饱和可 boost |
| `--tkn-color-white`          | `#ffffff` | 无饱和可 boost |
| `--tkn-color-primary-default`| `#0071e3` | 原 HSL.s = 100% → clamp |
| `--tkn-color-info-default`   | `#005c99` | 原 HSL.s = 100% → clamp |
| `--tkn-subject-physics`      | `#0057b7` | 原 HSL.s = 100% → clamp |
| `--tkn-subject-english`      | `#9c4f00` | 原 HSL.s = 100% → clamp |

> DoD 措辞 "color token 饱和度全部比 tokens.css 高" 需要这样理解：饱和度 < 100 的 全部 +12 并 clamp；饱和度 = 100 的不可能再高。transform 是否真正生效，由上表"全变"6 例证明。

### shadow wechat_override 应用证据

| token                | tokens.css                                   | tokens.wxss                                  |
|----------------------|----------------------------------------------|----------------------------------------------|
| `--tkn-shadow-card`  | `rgba(0, 0, 0, 0.22) 3px 5px 30px 0px`       | `rgba(0,0,0,0.26) 3px 5px 30px 0px`          |

## 运行校验

- `npm install` 成功：169 packages
- `npm run build:tokens` 退出码 0
- `node --experimental-strip-types -e "import('./src/tokens.ts')"` → `imported OK`；`tkn.color.primary.DEFAULT = "#0071e3"`；`tkn.subject.math = "#c41e3a"`
- `shasum -a 256 design/system/tokens/*.json` 全部未变（仍与 phase-sd.yml.outputs_hash 一致）

## 对 sd-t04 的交接要点

1. **消费方式**：
   - H5 / React 18（Konsta UI）：`@import "@lf/ui-kit/src/tokens.css"` 到 app 根样式；组件里读 `var(--tkn-color-primary-default)` 或 `import { tkn } from "@lf/ui-kit/src/tokens"`。
   - 小程序（Vant Weapp）：`@import "@lf/ui-kit/src/tokens.wxss"` 到 `app.wxss`；组件用 `var(--tkn-*)` 取值即可（已叠加 +12% 饱和度与 shadow override）。
2. **禁止** 在组件层硬编码 hex；任何新色必须回到 Step 2 扩展 `design/system/tokens/color.json`（含 subject 调色板已占 4 个槽位）。
3. **TS 类型**：`Tkn = typeof tkn`；下游可写 `(k: keyof Tkn["color"]) => tkn.color[k]` 做受限 props。
4. **命名**：kebab-case 路径拼接；源 JSON key `DEFAULT`（Tailwind 风格）会被降为 `-default` 后缀（如 `--tkn-color-primary-default`）。TS 侧保留 `tkn.color.primary.DEFAULT` 的原 key。

## 对本任务设计的说明（供 Step 4 理解约束）

- `tokens.ts` 用 `css` transformGroup 而非 `js`：SD `js` group 自带 `color/hex`，会把 `rgba(0,0,0,0.80)` 压成 `#000000cc` 8 位 hex。下游 Konsta 组件、某些 webview 对 8-hex 支持不稳，统一改 `css` group 让 rgba 保持字面量。
- 外层 `tkn` 包裹在 JSON 中是供 CSS var 命名用的（`--tkn-*`），在 TS 里会造成 `tkn.tkn.color.*` 冗余；format 侧显式 `root = dictionary.tokens.tkn` 剥掉一层。
- 没有引入 chroma-js / tinycolor 等色彩库；HSL 往返手写 ~35 行（`parseColor`、`rgbToHsl`、`hslToRgb`、`adjustSaturationHex`）。

## 偏离/未解决的坑

1. **路径偏差**：Prompt 模板里 Inputs 写 `design-system/tokens/`，而 sd-t02 实际输出在 `design/system/tokens/`。已按实际状态修正 source glob；**建议**后续版本的 prompt 同步更正。
2. **`state/interfaces.yml` 在 Sd 开始时不存在**：按 prompt 是"读 → 校验 sha256 不一致则 reset"。但因为源文件缺失等价于"没有已知契约"，我采用 `phase-sd.yml.outputs_hash` 作为权威来源，并在本任务中**创建** interfaces.yml；如果团队约定 interfaces.yml 必须由 sd-t02 产出，需在 sd-t02 补一轮小修。
3. **`scratch_summary_sd_t02.md` 不存在**：无法读取 Step 2 的遗留决策。关键决策（如 `platform_overrides` 的 saturation_delta=12）已在 `color.json._meta` 和 `MASTER.md` 中显式表达，不构成阻塞。
4. **workspace 设置**：prompt 示例命令用 `--workspace=frontend/packages/ui-kit`，但仓库没有 monorepo 根 package.json。本任务在 `frontend/packages/ui-kit/` 直接 `npm install`；未来加 monorepo root 时需把 style-dictionary 提到 root devDep 或保持 per-package。
5. **node_modules 未 git ignore**：`frontend/packages/ui-kit/node_modules/` 体积不小；仓库若要入 git，需要在 repo 根加 `.gitignore`。这属于 sd-t04 / 运维范畴，本任务未处理。
