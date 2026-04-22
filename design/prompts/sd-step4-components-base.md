# Sd · Step 4 — 基础组件库（H5 + 小程序双端）Prompt

> 基于 Step 3 产出的 `tokens.ts / tokens.css / tokens.wxss`，在 `frontend/packages/ui-kit/` 实现 **渲染首页 v3 所需的最小必要组件集**（10 个组件 + 1 份 Vant 变量映射）。

**Task ID**: `sd-t04-components-base`
**上游依赖**: `sd-t03-style-dictionary-transpile` (must be `status: done`)
**下游依赖**: `sd-t05-page-spec-home`（消费本步组件）
**预计 tool calls**: ≤ 55 | **预计 tokens**: ≤ 55% window
**Reset 触发**: tool_call > 55 且 DoD 未全勾 | tsc 连续 3 次失败 | H5/Mini 任一端被迫硬编码颜色

---

## ===== PROMPT START =====

你是 **只做组件原子化** 的 Builder。你的任务边界严格限定在 10 个组件 + 1 份 Vant 映射；不画页面、不写路由、不写数据层、不写测试脚手架。

### 冷启动 5 步

1. **读项目不变量**: `设计系统总则.md`（如存在）+ `落地实施计划_v1.0_AI自动执行.md` §1.6 工具白名单、§1.8 Context 契约、§9.2 Sd 阶段 DoD
2. **读架构**: `AI上下文与连贯性设计_v1.0.md`（如存在）+ 本项目 `frontend/` 目录结构；若 `frontend/packages/ui-kit/` 不存在 → 停
3. **读 state**: `state/phase-sd.yml` → 确认 `sd-t03.status == "done"`
4. **读接口契约**: `state/interfaces.yml` → 找 `sd-t03 → sd-t04`；验证 `tokens.ts / tokens.css / tokens.wxss / build-tokens.mjs` 的 `path + sha256 + symbols`
5. **读前任 scratch**: `design-system/scratch_summary_sd_t03.md`（如存在）+ `design-system/components.md`（Step 2 产出的组件清单）

---

### 硬性输入（Inputs，只读）

```
frontend/packages/ui-kit/
  src/tokens.ts                 ← 消费为类型与常量
  src/tokens.css                ← H5 组件 via var(--tkn-*)
  src/tokens.wxss               ← 小程序组件 via var(--tkn-*)
design-system/
  components.md                 ← 组件命名与 props 契约清单
  apple-base.decision.md        ← 44pt tap target、边角节律
  01_home_v3.html               ← 视觉参考（此版 = v2 结构 + v1 Apple 系统色）
state/
  phase-sd.yml
  interfaces.yml
```

若 `01_home_v3.html` 缺失或 `components.md` 缺少下面 10 个组件任一条目 → 停，写 scratch 标 `blocked`。

---

### 产物（Outputs）

```
frontend/packages/ui-kit/
  src/
    components/
      h5/                          ← React 18 + TS，只消费 tokens.css 的 var(--tkn-*)
        Button.tsx
        IconButton.tsx
        Card.tsx
        Chip.tsx                   ← 含 SubjectChip / StreakChip 两个预设 variant
        Badge.tsx
        Avatar.tsx
        ProgressRing.tsx
        Sparkline.tsx
        TabBar.tsx
        WeekStrip.tsx
        index.ts                   ← 统一 re-export
    mini/
      vant-overrides.wxss          ← 把 Vant Weapp CSS 变量映射到 --tkn-*
      README-mini.md               ← 一张表：Vant 组件 → 本项目 tokens 映射关系
  package.json                     ← 补 exports 字段 + peerDependencies
  tsconfig.json                    ← 如不存在则新建（strict、JSX react-jsx）
```

**严格不得** 产出:
- Storybook / 文档站 / demo 页
- 测试文件（vitest/jest/testing-library）
- 小程序端 WXML 组件文件（小程序走 Vant Weapp + WXSS 变量映射，不自造组件）
- 任何 Svelte/Vue/SolidJS 实验

---

### 技术契约

#### 1) H5 组件实现规则

- **语言**: TypeScript strict，React 18 函数组件，默认导出 + 命名导出并存
- **样式策略**: 只允许 `className` + `style` 两种方式；禁止 styled-components / emotion / tailwind-apply；所有颜色 / 间距 / 圆角 / 字号 / 阴影 **只能** 以 `var(--tkn-*)` 形式出现
- **Props**: 必须导出 `XxxProps` 接口；必选 `testId?: string`（默认为组件名 kebab-case）；禁止使用 `any`
- **Tap target**: 交互元素最小高度 44px（写入 CSS 或 inline style），不达标直接退 in_progress
- **A11y 最小集**:
  - 可点击元素加 `role="button"` 或用原生 `<button>`
  - 图标按钮必须 `aria-label`
  - 颜色相关状态必须同时有图标或文字（不能只靠颜色传达语义）
  - `:focus-visible` 可见焦点轮廓（外发光，取 `var(--tkn-color-blue-500)` 外 2px）
- **Motion**: `@media (prefers-reduced-motion: reduce)` 下禁用动画

#### 2) 10 个 H5 组件逐个契约

| 组件 | 必要 props | 视觉参考（v3 HTML） | Token 用法要点 |
|---|---|---|---|
| `Button` | `variant: 'primary'\|'secondary'\|'ghost'`, `size: 'sm'\|'md'\|'lg'`, `leftIcon?`, `loading?`, `disabled?`, `testId?` | `.hero-go`、`.ibtn` | primary bg=`--tkn-color-blue-500`，高 44/48；radius=`--tkn-radius-btn`(14px) |
| `IconButton` | `icon`, `aria-label`, `variant: 'solid'\|'ghost'`, `size: 44\|48` | `.hero-add` | 必 44px，方形，radius 同上 |
| `Card` | `tone: 'default'\|'dark'\|'editorial'`, `padding`, `children` | `.tile`、`.hero`、`.insight` | default bg=`--tkn-color-card`，dark bg=`--tkn-color-ink`；radius=`--tkn-radius-card`(20-26px) |
| `Chip` | `variant: 'subject'\|'streak'\|'neutral'`, `color?: 'red'\|'yellow'\|'green'\|'teal'`, `count?`, `label` | `.subchip`、`.streakchip` | pill 999px；subject 走 subject palette；streak 走 orange |
| `Badge` | `count`, `variant: 'dot'\|'num'`, `tone: 'red'\|'orange'` | `.badge`、`.num` | 数字 badge 固定 16-17px，border 2px=`var(--tkn-color-card)` |
| `Avatar` | `name`, `size: 32\|36\|44`, `rainbow?: boolean` | `.avatar` | rainbow 时用 conic-gradient 取 6 色系统色 |
| `ProgressRing` | `value: 0-100`, `size: 72\|78`, `gradient?: [string,string]`, `label?` | `.rh-circle`、`.ring` | stroke 走 `--tkn-color-orange-500` / yellow |
| `Sparkline` | `data: number[]`, `color?`, `area?: boolean`, `height?` | `.weekly .spark`、`.mastery .spark` | 线色 `--tkn-color-green-500`；面走 linear-gradient + stop-opacity |
| `TabBar` | `items: {key, label, icon, badge?}[]`, `activeKey`, `onChange` | `.tabbar` | 背景 `rgba(242,242,247,0.86) + backdrop-filter:blur(22px) saturate(180%)`；active 色 `--tkn-color-blue-500` |
| `WeekStrip` | `days: {key, short, date, dots: SubjectDot[], count?, isToday?}[]`, `onSelect?` | `.weekcard + .wcrow + .wd` | today 背景 `--tkn-color-ink`；dots 颜色从 `tkn.color.subject.*` 映射 |

#### 3) 小程序端策略：Vant Weapp 变量映射

- **不自造** WXML 组件；所有交互组件用 Vant Weapp（`@vant/weapp`）
- 产出一份 `vant-overrides.wxss`：把 Vant 的 CSS 变量（如 `--button-primary-background-color`）重新指向 `--tkn-*`；应用方 `@import` 后 Vant 组件自动拾取本项目 tokens
- 映射至少覆盖：button、cell、tabbar、badge、dialog、notify、toast
- 对于 H5 专有的组件（ProgressRing / Sparkline / WeekStrip），在 `README-mini.md` 里明确标注 "小程序使用 Canvas 实现，留 sd-t06 实现"，本步不出 mini 实现

#### 4) package.json 更新

```json
{
  "name": "@lf/ui-kit",
  "version": "0.1.0",
  "type": "module",
  "exports": {
    ".": "./src/components/h5/index.ts",
    "./tokens": "./src/tokens.ts",
    "./tokens.css": "./src/tokens.css",
    "./tokens.wxss": "./src/tokens.wxss",
    "./mini/vant-overrides.wxss": "./src/mini/vant-overrides.wxss"
  },
  "peerDependencies": {
    "react": "^18.0.0",
    "react-dom": "^18.0.0"
  }
}
```

---

### 工具白名单

允许:
- `Read`（tokens.* / components.md / 01_home_v3.html / state/*.yml）
- `Write` / `Edit`（仅限上面产物路径）
- `Bash`:
  - `npx tsc --noEmit -p frontend/packages/ui-kit`
  - `npm install react@^18 react-dom@^18 -D --workspace=frontend/packages/ui-kit`
  - `ls frontend/packages/ui-kit/src/components/h5/`

禁止:
- 安装 Konsta UI / shadcn / tailwind / styled-components / emotion
- 写任何 `.test.*` / `.stories.*` 文件
- 在组件里 import `tokens.css`（由 app 层负责引入，组件只消费变量）
- 硬编码任何非黑白色 hex（`#fff` / `#000` 除外，且只能做 overlay 层透明度兜底）
- 画页面（任何多组件拼装的示例 > 80 行即视为"在做页面"→ 退到 sd-t05）

---

### 执行序列（15 步）

1. 冷启动 5 步
2. 读 `01_home_v3.html` 完整 CSS 段，建立"视觉 → token"反查表（存内存）
3. 校验 `tokens.ts` 导出的 `tkn.color.subject.*` 含 math/physics/chemistry/english **四色**；缺化学 → 写 scratch 标 `blocked`，退回 sd-t02/sd-t03 补齐
4. 创建 `frontend/packages/ui-kit/src/components/h5/` 目录
5. 按照"2) 10 个组件"的顺序逐个实现；每个组件单文件 ≤ 120 行
6. 每完成 3 个组件跑一次 `tsc --noEmit`；有错先修完再继续
7. 写 `src/components/h5/index.ts` 统一 re-export
8. 写 `src/mini/vant-overrides.wxss`，至少 7 个 Vant 组件变量映射
9. 写 `src/mini/README-mini.md`，包含映射表 + 未实现组件清单
10. 更新 `package.json` 的 `exports` 与 `peerDependencies`
11. 如缺 `tsconfig.json` → 新建：`"strict": true, "jsx": "react-jsx", "module": "ESNext", "moduleResolution": "Bundler", "target": "ES2022"`
12. 跑 `tsc --noEmit`，退出码必须 0
13. 抽查：grep 组件文件中是否出现非 `--tkn-*` 的硬编码颜色（`#[0-9A-Fa-f]{3,6}` 且不在注释里且不是 `#fff`/`#000`/`#0000`）→ 若命中，重构到使用 token
14. 更新 `state/phase-sd.yml` + `state/interfaces.yml`（新增 sd-t04 → sd-t05 条目，列出 10 个组件导出符号与文件 sha256）
15. 写 `design-system/scratch_summary_sd_t04.md`

---

### DoD 清单

- [ ] 10 个 H5 组件文件全部存在，每个 ≤ 120 行
- [ ] `index.ts` re-export 完整
- [ ] `tsc --noEmit` 退出码 0
- [ ] 10 个组件文件中 grep 不到硬编码彩色 hex（白黑透明除外）
- [ ] 每个交互组件最小高度 ≥ 44px（grep `height:.{0,6}(44|48|56)`）
- [ ] `vant-overrides.wxss` 覆盖 ≥ 7 个 Vant 变量族
- [ ] `README-mini.md` 含 ProgressRing/Sparkline/WeekStrip 小程序后补承诺
- [ ] `package.json` exports 完整指向 4 个入口
- [ ] `state/phase-sd.yml` sd-t04 = done；`state/interfaces.yml` 新增条目
- [ ] `scratch_summary_sd_t04.md` 已写

---

### 失败回滚

- **Case A**: tokens.ts 缺化学色 → 标 `blocked`，scratch 指明回 sd-t02 补
- **Case B**: tsc 连续 3 次不过 → 保留代码，标 `in_progress`，scratch 列出错误栈和已过的组件清单
- **Case C**: 发现某组件视觉无法仅用 token 达成（如 v3 里的渐变需要新 token）→ 不自造 token；scratch 标 `blocked`，请求 sd-t02 扩展 JSON（禁止在组件内写死 gradient）

---

### `scratch_summary_sd_t04.md` 格式

```markdown
# scratch_summary_sd_t04

## 本任务状态
status: done | blocked | in_progress

## 产出清单（11 条）
- src/components/h5/Button.tsx         sha256=…  LOC=…
- … × 10
- src/mini/vant-overrides.wxss         sha256=…  映射 Vant 变量数=…

## Token 消费核验
- grep 硬编码 hex 结果: 0 命中（或列出漏网）
- 44px 最小高度核验: 全过（或列出例外）

## 对 sd-t05 的交接要点
- import 样例: `import { Button, Card, TabBar } from '@lf/ui-kit'`
- 小程序: `@import "@lf/ui-kit/mini/vant-overrides.wxss"` 入 app.wxss
- 未实现（待 sd-t06）: ProgressRing-mini / Sparkline-mini / WeekStrip-mini

## 未解决的坑
- [如有 — 例如 v3 里的 aurora 渐变在 token 层尚无对应 token，已升级到 sd-t02 补]
```

---

### Reset Protocol

- tool_call > 55 且 DoD 未全勾 → 停，写 scratch 标 `in_progress`，按组件粒度列出 done/undone
- `npm install` 超 2 分钟未返回 → 视作受限，标 `blocked`
- 永不 rm -rf / git reset

---

### 禁止事项

- 禁止写页面 / 路由 / 状态管理 / 数据 hooks
- 禁止引入除 react/react-dom 外的运行期依赖
- 禁止把 `tokens.css` import 到组件文件（由 app 侧统一 import）
- 禁止在组件里 `require('./xxx.css')` 或 CSS Modules
- 禁止为了省事把两个组件合到一个文件
- 禁止跳过 tsc 校验直接更新 state

## ===== PROMPT END =====

---

## 父 Agent 元信息

- **为什么只做 10 个组件**: 首页 v3 所有视觉单位均可由这 10 个组件 + 一个 `InsightCard`（用 `Card tone='dark'` 即可）拼成，再多就是过度工程；其余组件（Input/Dialog/Toast 等）留待首个表单页/详情页触发需求时加。
- **为什么小程序端不自造组件**: Vant Weapp 已覆盖 90% 场景，自造等同于重复造轮子。通过 WXSS 变量映射让 Vant 自动吃本项目 tokens，维护成本最低。
- **为什么硬卡 44px**: Apple HIG 最低 tap target 44pt，v2 HTML 里 `.ibtn=38px` 和 v1 里 `.kpbtn=34px` 均违反；组件层卡死才能防止页面层偷懒。
- **为什么禁止组件 import tokens.css**: CSS 重复 import 会产生变量作用域混乱，app 层 root import 一次最稳；组件只消费 `var(--tkn-*)` 即可。
- **为什么 Chip 合并 SubjectChip/StreakChip 而不拆三份**: variant 枚举在 TS 类型里已足够清晰，拆三个组件反而让消费端记更多名字；视觉差异由 variant 控制。
