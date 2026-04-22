# AI 可执行落地实施计划 — 通用日历系统 UI/UX 设计与前端开发（多端版 v2）

> **平台范围**：MVP 覆盖 **微信小程序（原生）** + **H5（React）**；P2 阶段扩展 **iOS/Android App（React Native）**。
> **设计风格**：**Apple Human Interface Guidelines（HIG）**——大圆角、毛玻璃、柔和阴影、SF Pro 字体家族、系统色板。
> 本计划与 `AI落地实施计划_通用日历系统.md`（后端 PostgreSQL + Spring Data JPA 方案，简称"后端计划"）严格对齐。
> 执行原则：**每阶段 = 明确工具/Skill + 可执行命令 + 可验证断言 + 回滚策略**。未通过"完成判定"不得进入下一阶段。
> 根目录：`$FE_ROOT = /Users/allenwang/build/longfeng/calendar-platform/frontend`
> 后端根目录：`$BE_ROOT = /Users/allenwang/build/longfeng/calendar-platform`
> 最终验收：`scripts/e2e-smoke.sh` 全绿 —— 涵盖 H5 Playwright 全链路 + 小程序 miniprogram-automator 全链路 + 与后端 smoke.sh 串联联调。

---

## 0. 执行总览（Roadmap）

分 **4 个 Stage、共 18 个阶段（F0–F17）**。Stage 0 为公共地基，Stage 1/2 为双端独立开发（可并行），Stage 3 为联调与交付。

### Stage 0：公共地基
| 阶段 | 名称 | 预计耗时 | 关键工具 / Skill | 依赖 |
|------|------|---------|------------------|------|
| F0 | 环境体检与工具链安装 | 20 min | Node 20、pnpm 9、微信开发者工具（Stable）、miniprogram-ci、Playwright CLI、`mcp__workspace__bash` | 后端 P0 |
| F1 | Monorepo 骨架（小程序/H5/共享/e2e 四根） | 30 min | pnpm workspace、Turborepo 2、TypeScript 5、ESLint 9、Prettier 3、Husky 9、commitlint | F0 |
| F2 | 设计系统：Apple HIG（含自建 `apple-hig-style` skill） | 90 min | `skill-creator`（Anthropic）、`canvas-design`、Figma（HIG Kit 参考）、Style Dictionary 4 | F0 |
| F3 | 共享 Design Tokens（HIG 色板/字体/圆角/阴影/毛玻璃/动效） | 40 min | Style Dictionary 4、W3C Design Tokens 规范、`packages/design-tokens` | F2 |
| F4 | API 契约与共享 SDK | 50 min | SpringDoc OpenAPI 3（后端）、OpenAPI Generator 7（小程序侧 TS）、Orval 7（H5 侧 TS + React Query）、Zod | 后端 P9、F1 |
| F5 | i18n / 时区共享模块（zh_CN / en_US / ja_JP） | 30 min | i18next 资源 JSON、Day.js + tz 插件、ICU MessageFormat、`packages/i18n` | F3 |

### Stage 1：H5 端（React Web Mobile）
| 阶段 | 名称 | 预计耗时 | 关键工具 / Skill | 依赖 |
|------|------|---------|------------------|------|
| F6 | H5 脚手架 + Apple HIG UI 底座 | 40 min | Vite 5、React 18、TypeScript 5、**Konsta UI**（企业级 iOS/HIG 主题 React 组件库）、Tailwind 3、Radix UI | F1、F3 |
| F7 | H5 基础设施（路由/状态/HTTP/鉴权/错误边界） | 40 min | React Router 6.23、TanStack Query v5、Zustand 4、Axios 1.7、react-error-boundary 4、jose 5 | F4、F6 |
| F8 | H5 页面开发：日历三视图 + 事项 CRUD + 设置 + 通知中心 | 180 min | FullCalendar 6、@dnd-kit/core、React Hook Form 7、Zod 3、Framer Motion 11、EventSource (SSE)、sonner | F7 |
| F9 | H5 单测 + 组件测试 + 可访问性 | 60 min | Vitest 1.6、@testing-library/react 15、@testing-library/user-event、MSW 2、jest-axe 9、@axe-core/react | F8 |
| F10 | H5 E2E 联调 + 性能门禁 | 70 min | Playwright 1.45、@axe-core/playwright、Lighthouse CI（@lhci/cli）、web-vitals 4 | F9、后端 P13 |

### Stage 2：微信小程序（原生 TypeScript）
| 阶段 | 名称 | 预计耗时 | 关键工具 / Skill | 依赖 |
|------|------|---------|------------------|------|
| F11 | 小程序脚手架 + Apple HIG 适配（Vant Weapp 主题定制） | 50 min | 微信小程序原生框架、TypeScript 5、**Vant Weapp**（有赞企业组件库）、`@vant/weapp` 主题变量覆盖、miniprogram-build、mobx-miniprogram | F1、F3 |
| F12 | 小程序基础设施（分包/路由/全局 store/wx.request 封装/JWT） | 50 min | mobx-miniprogram-bindings、`wx.request` 封装（拦截器 + 重试 + 幂等）、订阅消息能力申请、TransmittableGlobalData | F11、F4 |
| F13 | 小程序页面开发：日历 + CRUD + 设置 + 通知（订阅消息） | 180 min | 自定义 calendar 组件（scroll-view + grid 模拟）、picker、form、wx.requestSubscribeMessage、WebSocket（可选） | F12 |
| F14 | 小程序自动化测试（官方 automator） | 70 min | **miniprogram-automator**（官方 Puppeteer 风格 E2E 工具）、Jest 29、allure-jest、Testcontainers 驱动后端 | F13、后端 P13 |
| F15 | 小程序 CI/CD + 体验版上传 | 40 min | **miniprogram-ci**（官方 CI 库）、私钥密钥对、体验版二维码 | F14 |

### Stage 3：联调、观测与交付
| 阶段 | 名称 | 预计耗时 | 关键工具 / Skill | 依赖 |
|------|------|---------|------------------|------|
| F16 | 可观测性（H5 Sentry + 小程序 错误日志平台） | 40 min | @sentry/react 8、@sentry/vite-plugin、微信小程序 Sentry SDK（`@sentry/minapp` 社区方案）或 Fundebug | F10、F14 |
| F17 | 端到端联调冒烟（`e2e-smoke.sh` 串联后端 + H5 + 小程序） | 40 min | Playwright + miniprogram-automator + 后端 `smoke.sh`，allure-combine 合并报告 | 全部 |

**总里程碑**：F17 退出码 0 且两套自动化报告全绿 = MVP 落地完成。

---

## Stage 0：公共地基

### F0. 环境体检与工具链安装

#### 目标
前端所有后续工作的工具链前置条件齐备。

#### 使用的 Skill / 工具
- Bash：`mcp__workspace__bash`
- 版本管理：`fnm` / `nvm`
- 小程序工具链：
  - **微信开发者工具 Stable（CLI 版）** —— 官方唯一合法 IDE，支持命令行 build/preview/upload
  - **miniprogram-ci** —— 微信官方 CI 库，用于自动化上传体验版、正式版
  - **miniprogram-automator** —— 微信官方自动化测试工具（Puppeteer 风格）

#### AI 执行步骤
1. 创建目录：
   ```bash
   mkdir -p /Users/allenwang/build/longfeng/calendar-platform/frontend && cd "$_"
   ```
2. 写入 `scripts/fe-env-check.sh`：
   ```bash
   #!/usr/bin/env bash
   set -e
   WX_IDE_CLI="/Applications/wechatwebdevtools.app/Contents/MacOS/cli"
   {
     echo "=== Node ===";                    node -v
     echo "=== pnpm ===";                    pnpm -v || (corepack enable && corepack prepare pnpm@9 --activate && pnpm -v)
     echo "=== Git ===";                     git --version
     echo "=== Docker ===";                  docker --version
     echo "=== 微信开发者工具 CLI ==="
     if [[ -x "$WX_IDE_CLI" ]]; then "$WX_IDE_CLI" --help | head -5; else echo "MISSING_WX_CLI"; fi
     echo "=== miniprogram-ci 版本探测 ===";  npx --yes miniprogram-ci --version || true
     echo "=== Playwright ==="              ; npx --yes playwright --version || true
   } | tee fe-env-check.log
   ```
3. 执行：`bash scripts/fe-env-check.sh`
4. 如"微信开发者工具 CLI"缺失：引导用户从 [https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html](https://developers.weixin.qq.com/miniprogram/dev/devtools/download.html) 安装最新 Stable 版并勾选"设置 → 安全 → 开启服务端口"。

#### 验证步骤
```bash
grep -E "v(20|21|22)\." fe-env-check.log     # Node ≥ 20
grep -E "^9\." fe-env-check.log              # pnpm ≥ 9
! grep -q "MISSING_WX_CLI" fe-env-check.log  # 微信开发者工具必须存在
grep -qE "Playwright" fe-env-check.log
```
任一断言失败即阶段失败，必须修复后重跑。

#### 回滚
- Node 缺失：`curl -fsSL https://fnm.vercel.app/install | bash && fnm install 20 && fnm use 20`
- 微信开发者工具不能用 `sudo` 或脚本强装，必须提示用户人工安装。

#### 完成判定
`fe-env-check.log` 全部断言通过；任何缺失工具均已补齐。

---

### F1. Monorepo 骨架（双端独立 + 共享包）

#### 目标
建立支持"小程序原生 / H5 React / 共享 / E2E"四根的 pnpm workspace：
```
frontend/
├── package.json
├── pnpm-workspace.yaml
├── turbo.json
├── tsconfig.base.json
├── apps/
│   ├── h5/                    # Vite + React 18（F6 填充）
│   └── miniprogram/           # 微信小程序原生 TS（F11 填充）
├── packages/
│   ├── design-tokens/         # F3 输出 Apple HIG 双端 Token
│   ├── api-contracts/         # F4 OpenAPI JSON + 校验
│   ├── api-client-h5/         # Orval 生成（H5 专用）
│   ├── api-client-mp/         # OpenAPI Generator 生成（小程序专用，wx.request 适配）
│   ├── i18n/                  # 三语资源 + 时区工具
│   └── utils/                 # 纯函数公共工具（不含 DOM / wx）
├── e2e/
│   ├── h5/                    # Playwright
│   └── miniprogram/           # miniprogram-automator + Jest
└── scripts/
```

#### 使用的 Skill / 工具
- `pnpm@9`、`turbo@2`、`typescript@5.4`
- 代码风格：`eslint@9`（flat config）、`prettier@3`、`@typescript-eslint`、`eslint-plugin-miniprogram`（小程序专用规则）
- Git Hook：`husky@9`、`lint-staged@15`、`@commitlint/cli`、`@commitlint/config-conventional`

#### AI 执行步骤
1. `pnpm init -y`，根目录安装开发依赖：
   ```bash
   pnpm add -Dw turbo typescript@5.4 eslint@9 prettier@3 husky@9 lint-staged \
     @commitlint/cli @commitlint/config-conventional \
     eslint-plugin-miniprogram
   ```
2. `pnpm-workspace.yaml`：
   ```yaml
   packages:
     - "apps/*"
     - "packages/*"
     - "e2e/*"
   ```
3. `turbo.json` 定义 `build | lint | test | e2e` pipeline，注意小程序和 H5 的 `outputs` 不同：
   - H5：`apps/h5/dist/**`
   - 小程序：`apps/miniprogram/dist/**`
4. 初始化 Husky：
   ```bash
   pnpm exec husky init
   echo 'pnpm lint-staged' > .husky/pre-commit
   echo 'pnpm commitlint --edit $1' > .husky/commit-msg
   ```
5. `tsconfig.base.json`：`"strict": true, "noUncheckedIndexedAccess": true, "target": "ES2022"`；另外为小程序单独留 `tsconfig.miniprogram.json`（`"lib": ["ES2020"]`，去掉 `DOM`）。
6. 根 `.gitignore` 忽略 `node_modules/`、`dist/`、`.turbo/`、`playwright-report/`、`test-results/`、`allure-results/`、`apps/miniprogram/project.private.config.json`（含 appId）。

#### 验证步骤
```bash
cd $FE_ROOT
pnpm install
pnpm exec turbo run build --dry=json | jq -e '.tasks | length > 0'
test -f apps/h5/package.json || echo "OK H5 placeholder pending"     # F6 才真正填充
test -f apps/miniprogram/package.json || echo "OK MP placeholder pending"
test -f packages/design-tokens/package.json
pnpm exec eslint --print-config apps/h5/package.json > /dev/null 2>&1 || true
git init && git add -A && git commit -m "feat: scaffold multi-platform frontend monorepo" -n
git tag fe-v0.F1
```

#### 完成判定
`pnpm install` 无 ERR；目录结构齐备；Husky 钩子可执行；tag `fe-v0.F1` 打成功。

---

### F2. 设计系统：Apple HIG（含自建 `apple-hig-style` skill）

#### 目标
- 基于 Apple **官方公开的 Human Interface Guidelines** 设计语言，**提炼出可合规使用的设计规范**（非复刻图标 / 非抄袭素材）。
- 使用 `skill-creator` 自建一个 **`apple-hig-style`** skill，沉淀 HIG 的 token 与生成规则。后续 H5、小程序、未来 RN App 所有设计稿都通过该 skill 调用 `canvas-design` 生成。
- 产出 6 张关键页面视觉稿 PNG（登录、月视图、周视图、日视图、事项抽屉、用户设置），分辨率 ≥ 1125×2436（iPhone 规格）。
- 产出 `design/UI_UX_规范.md`：组件使用、三态（loading/empty/error）、毛玻璃场景、动效原则、暗黑模式。

#### 使用的 Skill / 工具
- **`skill-creator`**（Anthropic 官方）：自建 skill。
- **`canvas-design`**（Anthropic 官方）：生成视觉稿。
- 参考资料（全部为公开文档，**仅提炼设计语言、不直接搬素材**）：
  - Apple Human Interface Guidelines 官方文档
  - Apple Design Resources（仅用作 token 校准参考，不得复制图标 / Bitmap）
  - 公开色板规范：iOS System Colors、Dynamic Colors（Light/Dark）
- **版权合规约束**：不使用 Apple 商标（Apple logo）、不使用 SF Symbols 图标文件、不声称产品"为 Apple 官方出品"。图标统一使用 `lucide-react`（MIT 协议）且按 HIG 圆角/线宽规范重绘。

#### AI 执行步骤
1. **先读 skill-creator 的 SKILL.md**：
   ```bash
   # 在 Cowork 环境中
   Read /var/folders/v9/z0czr6yx07l8yncxyc690p000000gn/T/claude-hostloop-plugins/913d2c22a7b64a48/skills/skill-creator/SKILL.md
   ```
2. **自建 `apple-hig-style` skill**。骨架放在 `$FE_ROOT/skills/apple-hig-style/`：
   - `SKILL.md`（触发条件、使用说明）
   - `reference/hig-tokens.json`：色板、字体栈、圆角阶梯、阴影、毛玻璃强度、动效时长与曲线
   - `reference/hig-principles.md`：层级、留白、清晰（Clarity）、顺从内容（Deference）、深度（Depth）三大原则
   - `templates/`：6 个页面的 prompt 模板，供 `canvas-design` 引用
   - `scripts/validate-tokens.mjs`：JSON schema 校验
3. **HIG Token 关键值（写入 `hig-tokens.json`）**：
   ```jsonc
   {
     "color": {
       "system": {
         "blue":   { "light": "#007AFF", "dark": "#0A84FF" },
         "green":  { "light": "#34C759", "dark": "#30D158" },
         "indigo": { "light": "#5856D6", "dark": "#5E5CE6" },
         "orange": { "light": "#FF9500", "dark": "#FF9F0A" },
         "red":    { "light": "#FF3B30", "dark": "#FF453A" },
         "yellow": { "light": "#FFCC00", "dark": "#FFD60A" }
       },
       "gray": {
         "1": { "light": "#8E8E93", "dark": "#8E8E93" },
         "2": { "light": "#AEAEB2", "dark": "#636366" },
         "3": { "light": "#C7C7CC", "dark": "#48484A" },
         "4": { "light": "#D1D1D6", "dark": "#3A3A3C" },
         "5": { "light": "#E5E5EA", "dark": "#2C2C2E" },
         "6": { "light": "#F2F2F7", "dark": "#1C1C1E" }
       },
       "label": {
         "primary":   { "light": "rgba(0,0,0,0.85)", "dark": "rgba(255,255,255,0.85)" },
         "secondary": { "light": "rgba(60,60,67,0.60)", "dark": "rgba(235,235,245,0.60)" },
         "tertiary":  { "light": "rgba(60,60,67,0.30)", "dark": "rgba(235,235,245,0.30)" }
       },
       "background": {
         "primary":   { "light": "#FFFFFF", "dark": "#000000" },
         "secondary": { "light": "#F2F2F7", "dark": "#1C1C1E" },
         "tertiary":  { "light": "#FFFFFF", "dark": "#2C2C2E" }
       }
     },
     "typography": {
       "family": "-apple-system, BlinkMacSystemFont, \"SF Pro Text\", \"SF Pro Display\", \"PingFang SC\", \"Helvetica Neue\", sans-serif",
       "scale": {
         "largeTitle": { "size": 34, "weight": 700, "lineHeight": 41 },
         "title1":     { "size": 28, "weight": 700, "lineHeight": 34 },
         "title2":     { "size": 22, "weight": 700, "lineHeight": 28 },
         "title3":     { "size": 20, "weight": 600, "lineHeight": 25 },
         "headline":   { "size": 17, "weight": 600, "lineHeight": 22 },
         "body":       { "size": 17, "weight": 400, "lineHeight": 22 },
         "callout":    { "size": 16, "weight": 400, "lineHeight": 21 },
         "subhead":    { "size": 15, "weight": 400, "lineHeight": 20 },
         "footnote":   { "size": 13, "weight": 400, "lineHeight": 18 },
         "caption1":   { "size": 12, "weight": 400, "lineHeight": 16 },
         "caption2":   { "size": 11, "weight": 400, "lineHeight": 13 }
       }
     },
     "radius": { "xs": 6, "sm": 10, "md": 14, "lg": 18, "xl": 22, "continuous": true },
     "shadow": {
       "card":  "0 1px 3px rgba(0,0,0,0.04), 0 4px 16px rgba(0,0,0,0.06)",
       "sheet": "0 -4px 24px rgba(0,0,0,0.08)",
       "popover": "0 8px 32px rgba(0,0,0,0.12)"
     },
     "blur": {
       "thin":  { "backdropFilter": "blur(10px) saturate(180%)" },
       "regular": { "backdropFilter": "blur(20px) saturate(180%)" },
       "thick": { "backdropFilter": "blur(40px) saturate(180%)" }
     },
     "motion": {
       "easing": {
         "standard":    "cubic-bezier(0.4, 0, 0.2, 1)",
         "decelerate":  "cubic-bezier(0.0, 0, 0.2, 1)",
         "accelerate":  "cubic-bezier(0.4, 0, 1, 1)",
         "spring":      "cubic-bezier(0.34, 1.56, 0.64, 1)"
       },
       "duration": { "fast": 200, "standard": 300, "slow": 500 }
     }
   }
   ```
4. 用 `canvas-design` skill 按 `templates/` 里 6 个 prompt 生成 PNG 到 `design/mockups/ios/`：
   - `login.png` / `month-view.png` / `week-view.png` / `day-view.png` / `event-drawer.png` / `user-settings.png`
5. 撰写 `design/UI_UX_规范.md`：分 **色板 / 字体 / 间距 / 圆角 / 阴影 / 毛玻璃 / 动效 / 暗黑模式 / 可访问性（WCAG AA 对比度 ≥ 4.5）/ 三态 / 图标重绘规则** 共 11 节。

#### 验证步骤
```bash
cd $FE_ROOT
# 1) skill 结构合法
test -f skills/apple-hig-style/SKILL.md
test -f skills/apple-hig-style/reference/hig-tokens.json
node skills/apple-hig-style/scripts/validate-tokens.mjs     # JSON schema 通过，否则 exit 1

# 2) 6 张视觉稿齐全
for f in login month-view week-view day-view event-drawer user-settings; do
  test -f ../design/mockups/ios/$f.png || { echo "MISSING: $f"; exit 1; }
done

# 3) 分辨率断言（iPhone 14 Pro 规格 1179x2556，允许 ±10%）
python3 -c "
from PIL import Image, ImageStat
import os, sys
paths = ['../design/mockups/ios/%s.png' % f for f in ['login','month-view','week-view','day-view','event-drawer','user-settings']]
for p in paths:
    img = Image.open(os.path.abspath(p))
    w,h = img.size
    assert w >= 1000 and h >= 2000, f'{p} resolution too low: {w}x{h}'
print('OK')
"

# 4) 规范文档关键章节存在
for s in 色板 字体 间距 圆角 阴影 毛玻璃 动效 暗黑 可访问性 三态 图标; do
  grep -q "$s" ../design/UI_UX_规范.md || { echo "MISSING section: $s"; exit 1; }
done

# 5) 版权合规硬约束：不得出现 Apple logo 素材或 SF Symbols 文件
! find ../design/mockups -iname "*apple*" -o -iname "*sf-symbols*"
```

#### 回滚
删除 `skills/apple-hig-style/`、`design/mockups/ios/`、`UI_UX_规范.md` 重跑。

#### 完成判定
- 自建 skill 结构合法、JSON schema 通过；
- 6 张视觉稿分辨率与文件齐备；
- 规范文档 11 节完整；
- 无版权违规素材。

---

### F3. 共享 Design Tokens（Style Dictionary 双端输出）

#### 目标
把 F2 的 `hig-tokens.json` 用 Style Dictionary 转译成：
- `packages/design-tokens/dist/css/tokens.css`（供 H5）
- `packages/design-tokens/dist/ts/tokens.ts`（供 H5 类型引用）
- `packages/design-tokens/dist/wxss/tokens.wxss`（供小程序，CSS 变量形式）
- `packages/design-tokens/dist/js/tokens.js`（供小程序 JS 侧读取，用于 Vant Weapp 主题覆盖）

#### 使用的 Skill / 工具
- `style-dictionary@4`（业界标准）
- 自定义 format：wxss 不支持 `:root`，用 `page { ... }` 包裹

#### AI 执行步骤
1. `packages/design-tokens/package.json`、`style-dictionary.config.mjs`：
   ```js
   export default {
     source: ['../../skills/apple-hig-style/reference/hig-tokens.json'],
     platforms: {
       css:  { transformGroup: 'css', buildPath: 'dist/css/',  files: [{ destination: 'tokens.css', format: 'css/variables' }]},
       ts:   { transformGroup: 'js',  buildPath: 'dist/ts/',   files: [{ destination: 'tokens.ts',  format: 'javascript/es6' }]},
       wxss: { transformGroup: 'css', buildPath: 'dist/wxss/', files: [{ destination: 'tokens.wxss', format: 'css/wxss-page' }]},
       js:   { transformGroup: 'js',  buildPath: 'dist/js/',   files: [{ destination: 'tokens.js',  format: 'javascript/module-flat' }]},
     }
   };
   ```
2. 注册自定义 format `css/wxss-page`：把 `:root {}` 替换为 `page {}`。
3. `pnpm --filter @calendar/design-tokens build`。

#### 验证步骤
```bash
cd $FE_ROOT
pnpm --filter @calendar/design-tokens build
test -f packages/design-tokens/dist/css/tokens.css
test -f packages/design-tokens/dist/wxss/tokens.wxss
# wxss 文件必须用 page { 包裹而非 :root
grep -q "^page {" packages/design-tokens/dist/wxss/tokens.wxss
! grep -q ":root" packages/design-tokens/dist/wxss/tokens.wxss
# 关键 token 存在
grep -q "color-system-blue-light: #007AFF" packages/design-tokens/dist/css/tokens.css
grep -q "radius-md" packages/design-tokens/dist/css/tokens.css
```

#### 完成判定
4 份产物全部生成；wxss 语法正确；关键 token 值正确。

---

### F4. API 契约与共享 SDK（双端独立客户端）

#### 目标
- 后端暴露 OpenAPI 3 JSON → 写入 `packages/api-contracts/openapi.json`。
- **H5 侧**：Orval 生成 `useXxxQuery / useXxxMutation` hooks + Zod schema。
- **小程序侧**：OpenAPI Generator 的 `typescript-fetch` 模板 + 自研 fetcher 适配 `wx.request`（含拦截器、重试、`Request-Id` 幂等 header）。
- 单一 OpenAPI JSON = 双端契约一致性的唯一源。

#### 使用的 Skill / 工具
- `@openapitools/openapi-generator-cli@2`
- `orval@7`
- `zod@3`、`msw@2`
- 后端协同：`springdoc-openapi-starter-webmvc-ui`

#### AI 执行步骤
1. 抓取 OpenAPI：
   ```bash
   curl -fsS http://127.0.0.1:8080/v3/api-docs > packages/api-contracts/openapi.json
   ```
   若后端未起：使用 `design/openapi-draft.json`（AI 依据后端计划接口草拟）。
2. **H5 客户端**：`packages/api-client-h5/orval.config.ts`（参考 v1 方案），产物在 `src/generated/`。
3. **小程序客户端**：
   - `pnpm dlx @openapitools/openapi-generator-cli generate -i packages/api-contracts/openapi.json -g typescript-fetch -o packages/api-client-mp/src/generated`
   - 在 `packages/api-client-mp/src/runtime/wx-fetch.ts` 实现 `fetch-polyfill`，把 `Request/Response` 转成 `wx.request`：
     ```ts
     export const wxFetch: typeof fetch = (input, init) => new Promise((resolve, reject) => {
       wx.request({
         url: typeof input === 'string' ? input : input.url,
         method: (init?.method || 'GET') as any,
         header: Object.fromEntries(new Headers(init?.headers).entries()),
         data: init?.body,
         success: (res) => resolve(new Response(JSON.stringify(res.data), {
           status: res.statusCode,
           headers: new Headers(res.header as any),
         })),
         fail: reject,
       });
     });
     ```
   - 把 `wxFetch` 注入 `Configuration` 的 `fetchApi` 字段。
4. 两端都包装一层 `createClient(opts)`，自动注入 `Authorization`、`X-Tenant-Id`、`X-Timezone`、`X-Locale`、`Request-Id`。

#### 验证步骤
```bash
cd $FE_ROOT
pnpm --filter @calendar/api-client-h5 generate
pnpm --filter @calendar/api-client-mp generate
pnpm --filter @calendar/api-client-h5 typecheck
pnpm --filter @calendar/api-client-mp typecheck
# 关键 API 类定义存在
grep -rq "createCalendarEvent\|CalendarEventApi" packages/api-client-mp/src/generated
grep -rq "useCreateCalendarEvent" packages/api-client-h5/src/generated
# wxFetch 正确注入
grep -q "fetchApi: wxFetch" packages/api-client-mp/src/client.ts
```

#### 完成判定
双端客户端 TS 编译 0 error；关键 API 均生成；`wxFetch` 正确挂载。

---

### F5. i18n / 时区共享模块

#### 目标
- 三语资源 JSON 一次编写，两端复用（H5 `i18next-http-backend` 加载，小程序按 `getStorageSync('locale')` 动态 `require`）。
- 时区工具：Day.js + tz 插件，双端同一套 API（`formatInTz(iso, tz?)`）。
- 与后端 i18n key 严格一致（`calendar.title`、`calendar.status.0..4`、`error.CAL-*`）。

#### 使用的 Skill / 工具
- `i18next@23`、`dayjs@1` + `utc`/`timezone`/`localizedFormat`/`relativeTime`

#### AI 执行步骤
1. `packages/i18n/locales/{zh-CN,en-US,ja-JP}/common.json`，字段与后端完全一致。
2. `packages/i18n/src/time.ts`：
   ```ts
   import dayjs from 'dayjs'; import utc from 'dayjs/plugin/utc'; import tz from 'dayjs/plugin/timezone';
   dayjs.extend(utc); dayjs.extend(tz);
   export const formatInTz = (iso: string, zone = 'Asia/Shanghai', fmt='YYYY-MM-DD HH:mm') =>
     dayjs.utc(iso).tz(zone).format(fmt);
   ```
3. `packages/i18n/src/index-h5.ts` 与 `packages/i18n/src/index-mp.ts` 分别导出"浏览器加载"和"小程序同步加载"两种初始化方式。

#### 验证步骤
```bash
cd $FE_ROOT
pnpm --filter @calendar/i18n test
# 三语资源齐全且 key 对齐
for lng in zh-CN en-US ja-JP; do
  jq -e '.calendar.title and .calendar.status["0"] and .error["CAL-2001"]' packages/i18n/locales/$lng/common.json > /dev/null
done
# formatInTz 单测通过
node -e "
const { formatInTz } = require('./packages/i18n/dist/time.js');
const r = formatInTz('2026-04-20T06:30:00Z', 'Asia/Shanghai');
if (r !== '2026-04-20 14:30') { console.error('got', r); process.exit(1); }
console.log('OK', r);
"
```

#### 完成判定
三语键名无缺失、`formatInTz` UTC→Asia/Shanghai 正确返回 `2026-04-20 14:30`。

---

## Stage 1：H5 端（React Mobile）

### F6. H5 脚手架 + Apple HIG UI 底座（Konsta UI）

#### 目标
- `apps/h5` 使用 Vite 5 + React 18 + TS 5 初始化。
- UI 底座：**Konsta UI**（Tailwind + React 的 iOS/Material 双主题组件库，企业级 MIT 协议，作者即 Framework7 团队）—— 启用 `theme="ios"` 即可获得 HIG 风格。
- 配合 F3 的 `tokens.css` 覆盖 Konsta 默认变量，使色板/圆角/字体与 HIG token 完全对齐。
- 配备 Storybook 8 用于组件目录与视觉回归。

#### 使用的 Skill / 工具
- `vite@5`、`@vitejs/plugin-react-swc`
- **`konsta@3`**（企业级 iOS/Material React 组件库）
- `tailwindcss@3`、`postcss@8`、`autoprefixer`
- `storybook@8` + `@storybook/addon-a11y`

#### AI 执行步骤
1. `pnpm create vite@latest apps/h5 -- --template react-ts`（覆盖原占位）。
2. 安装：
   ```bash
   pnpm --filter h5 add konsta react-dom@18 react@18
   pnpm --filter h5 add -D tailwindcss postcss autoprefixer storybook @storybook/react-vite @storybook/addon-a11y @storybook/addon-interactions
   pnpm --filter h5 exec tailwindcss init -p
   ```
3. `tailwind.config.ts`：按 Konsta 文档引入 `require('konsta/config')`。
4. `apps/h5/src/main.tsx`：
   ```tsx
   import { App } from 'konsta/react';
   import '@calendar/design-tokens/dist/css/tokens.css';
   import './index.css';
   createRoot(document.getElementById('root')!).render(
     <App theme="ios" safeAreas>...</App>
   );
   ```
5. `index.css` 顶部 `@import "@calendar/design-tokens/dist/css/tokens.css";`，覆盖 Konsta 的 iOS 色板到 HIG token：
   ```css
   :root {
     --k-color-brand-primary: var(--color-system-blue-light);
     --k-color-brand-red:     var(--color-system-red-light);
     /* ... */
   }
   ```
6. Storybook 初始化：每个业务组件配 `*.stories.tsx`，覆盖 light/dark、loading/empty/error。

#### 验证步骤
```bash
cd $FE_ROOT
pnpm --filter h5 typecheck
pnpm --filter h5 build
pnpm --filter h5 storybook build 2>&1 | tee /tmp/sb.log
! grep -iE "error\b" /tmp/sb.log | grep -v "0 errors"
# iOS 主题启用
grep -q 'theme="ios"' apps/h5/src/main.tsx
# HIG token 覆盖到 Konsta
grep -q "k-color-brand-primary" apps/h5/src/index.css
```

#### 完成判定
H5 构建 & Storybook 构建 0 error；iOS 主题生效（按钮呈 iOS 蓝、大圆角）。

---

### F7. H5 基础设施（路由 / 状态 / HTTP / 鉴权 / 错误边界）

#### 目标
与 v1 方案一致：React Router 6 Data Router + TanStack Query v5 + Zustand + Axios 拦截器 + JWT + Error Boundary + Sentry 占位。

#### 使用的 Skill / 工具
- `react-router-dom@6.23`, `@tanstack/react-query@5`, `zustand@4`, `axios@1.7`, `react-error-boundary@4`, `jose@5`, `uuid@9`

#### AI 执行步骤
（同 v1 F4/F7 合并）
1. Router tree：`/login`, `/calendar/:view`, `/events/:id`, `/settings`, `/notifications`。
2. Axios client 注入 5 个 header；401 触发 refresh；`ApiResponse<T>` 统一解包。
3. Zustand `authStore` / `preferenceStore` 使用 `persist` 到 `localStorage`。
4. `<ErrorBoundary>` 顶层包裹 + `Sentry.captureException` 占位。

#### 验证步骤
```bash
pnpm --filter h5 typecheck
pnpm --filter h5 build
grep -qE "X-Timezone|X-Locale|Request-Id" apps/h5/src/shared/http/client.ts
grep -cE "lazy\(" apps/h5/src/app/router.tsx | awk '$1<5{exit 1}'
grep -q "persist(" apps/h5/src/shared/state/preferenceStore.ts
```

#### 完成判定
Build 0 error；懒加载路由 ≥ 5 条；Header 注入齐全。

---

### F8. H5 页面开发（日历三视图 / CRUD / 设置 / 通知中心）

#### 目标
一次性完成所有业务页面；每页严格覆盖三态（loading/empty/error）+ 动效 + 手势（日历滑动切换月份）。

#### 使用的 Skill / 工具
- `@fullcalendar/react@6` + `daygrid/timegrid/interaction`
- `@dnd-kit/core` 拖拽顺延
- `react-hook-form@7` + `@hookform/resolvers/zod` + `zod@3`
- `framer-motion@11`（HIG 风格 spring 动效）
- `sonner`（通知 Toast，iOS 风格）
- 原生 `EventSource`（SSE 通知）

#### AI 执行步骤
1. **日历视图** `features/calendar/`：
   - `CalendarPage.tsx` 按 `view` 参数渲染 FullCalendar；
   - 自定义 `eventContent` 使用 `<CalendarEventCard>`（Konsta `<Card>` + HIG 渐变）；
   - 顶部状态栏用 `backdrop-filter: blur(20px)` 毛玻璃效果；
   - 滑动切换月：`@use-gesture/react` + `framer-motion` spring。
2. **事项抽屉** `features/event/EventSheet.tsx`：
   - Konsta `<Sheet>`（底部弹出）；
   - `zod.discriminatedUnion('relationType', ...)` 按 1/2/3/4 动态字段；
   - 乐观更新 + 回滚。
3. **设置页** `features/settings/SettingsPage.tsx`：
   - Konsta `<List>` + `<ListItem>`，iOS 分组列表；
   - 时区 Picker 调 `Intl.supportedValuesOf('timeZone')`；
   - 切换后立刻 `dayjs.tz.setDefault()` + `i18n.changeLanguage()`。
4. **通知中心** `features/notification/`：
   - `useNotificationStream()`：`EventSource('/api/v1/notifications/stream')` + `queryClient.invalidateQueries`；
   - 使用 `sonner` 的 iOS 风格 toast。

#### 验证步骤
```bash
pnpm --filter h5 typecheck
pnpm --filter h5 build
# 三态覆盖（每个 feature 必有 Empty/Error/Loading 三态引用）
for d in calendar event settings notification; do
  grep -lR "EmptyState\|ErrorFallback\|SkeletonCard" apps/h5/src/features/$d | wc -l | awk '$1<1{exit 1}'
done
# Storybook 构建 0 error
pnpm --filter h5 storybook build
```

#### 完成判定
4 个 feature 目录下 page 组件齐全、三态完备、Storybook 构建通过。

---

### F9. H5 单测 + 组件测试 + 可访问性

#### 目标
- 覆盖率：语句 / 分支 / 函数 / 行 ≥ 80%；关键 feature（calendar/event/auth）≥ 90%。
- `jest-axe` 对每页 0 critical/serious 违规。

#### 使用的 Skill / 工具
- `vitest@1.6`、`@testing-library/react@15`、`@testing-library/user-event`
- `msw@2` 拦截 HTTP
- `jest-axe@9`、`@axe-core/react`

#### AI 执行步骤
1. `vitest.config.ts`：`environment='jsdom'`，coverage.thresholds 80。
2. 每个 feature 至少：happy path、error path、a11y 扫描。
3. MSW 覆盖 200/4xx/5xx/网络超时。
4. `@axe-core/react` 在 dev 启动时自动打印违规。

#### 验证步骤
```bash
pnpm --filter h5 test -- --coverage
find apps/h5 -name coverage-summary.json | head -1 | xargs -I{} \
  jq -e '.total.statements.pct >= 80 and .total.branches.pct >= 75' {}
# axe 测试存在
grep -rq "toHaveNoViolations" apps/h5/src/features
```

#### 完成判定
覆盖率阈值达标；jest-axe 0 critical/serious。

---

### F10. H5 E2E 联调 + 性能门禁

#### 目标
Playwright × 3 浏览器 × 9 场景，与后端（docker-compose）联调；Lighthouse CI a11y ≥ 95、performance ≥ 90。

#### 使用的 Skill / 工具
- `@playwright/test@1.45`、`@axe-core/playwright`
- `allure-playwright`
- `@lhci/cli`
- 后端 `infra/up.sh`

#### AI 执行步骤
1. `e2e/h5/playwright.config.ts`：`projects=[chromium,firefox,webkit]`，`baseURL=http://127.0.0.1:4173`。
2. 9 场景（E1–E9，同 v1），其中每条打 `@smoke` tag。
3. `e2e/h5/fixtures/tenant.ts` 登录态复用 `storageState`。
4. Lighthouse CI 对 `/calendar/month`、`/events/new`、`/settings` 三页断言。

#### 验证步骤
```bash
bash $BE_ROOT/infra/up.sh
bash $BE_ROOT/scripts/smoke.sh                               # 后端 smoke 先过
pnpm exec playwright install --with-deps
pnpm --filter e2e-h5 exec playwright test --reporter=list
jq -e '.stats.unexpected == 0 and .stats.flaky <= 1' e2e/h5/playwright-report/results.json
pnpm dlx @lhci/cli@latest autorun --config=./apps/h5/.lighthouserc.json
```

#### 完成判定
Playwright 全绿；Lighthouse 阈值达标；allure 报告生成。

---

## Stage 2：微信小程序端（原生 TypeScript）

### F11. 小程序脚手架 + Vant Weapp + HIG 主题覆盖

#### 目标
- `apps/miniprogram` 原生 TS 小程序工程（`project.config.json`、`sitemap.json`、`app.ts`）。
- 组件库：**Vant Weapp**（有赞出品，企业级 MIT 协议，生产广泛验证）。
- 通过覆盖 Vant CSS 变量让视觉与 Apple HIG 对齐（iOS 蓝、HIG 圆角、柔和阴影）。注意：**微信小程序平台 UI 规范要求 Tab Bar / 胶囊按钮等系统组件使用官方风格**，不能完全"伪装成 iOS"——我们只在业务卡片区域使用 HIG 风格，系统区保留微信规范，避免审核失败。

#### 使用的 Skill / 工具
- 微信小程序原生框架（Skyline 或 WebView，默认 WebView）
- TypeScript 5 + `miniprogram-api-typings`
- **`@vant/weapp`**（Vant Weapp）
- `miniprogram-build`（构建工具，或使用微信开发者工具 CLI `cli build-npm`）
- `mobx-miniprogram`、`mobx-miniprogram-bindings`

#### AI 执行步骤
1. 在 `apps/miniprogram/` 初始化骨架：
   ```
   apps/miniprogram/
   ├── project.config.json          # appid、compileType 等
   ├── project.private.config.json  # .gitignore（含本地 appId）
   ├── sitemap.json
   ├── tsconfig.json                # extends base，lib 去掉 DOM
   ├── miniprogram/
   │   ├── app.ts
   │   ├── app.json                 # pages 列表 + 分包
   │   ├── app.wxss                 # 引入 @calendar/design-tokens/dist/wxss/tokens.wxss
   │   ├── pages/
   │   ├── components/
   │   ├── stores/
   │   ├── services/
   │   └── utils/
   └── typings/
   ```
2. 安装：
   ```bash
   cd apps/miniprogram
   pnpm add @vant/weapp mobx-miniprogram mobx-miniprogram-bindings
   pnpm add -D miniprogram-api-typings miniprogram-automator miniprogram-ci
   ```
3. `app.wxss`：
   ```wxss
   @import "/miniprogram_npm/@calendar/design-tokens/dist/wxss/tokens.wxss";
   page {
     --van-primary-color: var(--color-system-blue-light);
     --van-border-radius-md: 14px;
     --van-border-radius-lg: 18px;
     --van-cell-background: var(--color-background-primary-light);
     /* ... 更多 HIG 对齐覆盖 */
   }
   ```
4. `project.config.json`：`packOptions.ignore` 排除测试文件；`setting.urlCheck=false`（开发阶段）；`miniprogramRoot=miniprogram/`。
5. `app.json` 规划分包：主包（登录/首页/日历）、`packageEvent`（事项 CRUD）、`packageSettings`（设置 + 通知）—— 满足小程序主包 ≤ 2MB 限制。

#### 验证步骤
```bash
cd $FE_ROOT/apps/miniprogram
# 1) 依赖安装成功
pnpm exec tsc --noEmit
# 2) 主包大小预检（构建产物）
pnpm exec miniprogram-build || true
du -sk miniprogram | awk '$1>2048{print "main bundle > 2MB:", $1, "KB"; exit 1}'
# 3) Vant 主题覆盖到位
grep -q "van-primary-color" miniprogram/app.wxss
# 4) 小程序 CLI 检查项目可加载
WX_CLI="/Applications/wechatwebdevtools.app/Contents/MacOS/cli"
"$WX_CLI" build-npm --project $(pwd)
test -d miniprogram/miniprogram_npm/@vant/weapp
```

#### 完成判定
TS 编译 0 error；主包 ≤ 2MB；Vant 主题成功覆盖为 HIG 色板；`build-npm` 成功。

---

### F12. 小程序基础设施

#### 目标
- 路由 / 页面跳转封装（`navigateTo`、`switchTab`、`redirectTo` 统一 `router.push`）。
- 全局 store：`userStore`、`preferenceStore`、`calendarStore`（mobx-miniprogram）。
- `wx.request` 封装：拦截器（JWT、租户、时区、Request-Id）+ 重试（幂等 GET 自动重试 2 次）+ 统一错误码 → i18n。
- 401 触发 refresh；refresh 失败 `wx.reLaunch('/pages/login/index')`。
- 订阅消息（Subscribe Message）能力预申请。

#### 使用的 Skill / 工具
- `mobx-miniprogram`、`mobx-miniprogram-bindings`
- `@calendar/api-client-mp`（F4 生成）
- `@calendar/i18n`（F5）

#### AI 执行步骤
1. `miniprogram/services/http.ts`：基于 `wx.request` 的 Promise 封装，注入 5 个 header，处理 `ApiResponse<T>` 解包与 401 刷新。
2. `miniprogram/services/router.ts`：封装 `router.push/replace/back/reLaunch`，支持页面传参序列化。
3. `miniprogram/stores/*.ts`：每个 store 使用 `observable` / `action`；组件通过 `createStoreBindings` 绑定。
4. `miniprogram/utils/auth.ts`：从 `getStorageSync('token')` 恢复 JWT，过期自动清除。
5. `miniprogram/utils/subscribe-message.ts`：封装 `wx.requestSubscribeMessage`。

#### 验证步骤
```bash
cd $FE_ROOT/apps/miniprogram
pnpm exec tsc --noEmit
# 拦截器注入齐全
grep -qE "X-Timezone|X-Locale|Request-Id|Authorization" miniprogram/services/http.ts
# 401 refresh 分支存在
grep -q "res.statusCode === 401" miniprogram/services/http.ts
# store 使用 mobx
grep -rq "from 'mobx-miniprogram'" miniprogram/stores
```

#### 完成判定
TS 0 error；http 封装覆盖拦截/重试/401；router 封装存在；mobx store ≥ 3 个。

---

### F13. 小程序页面开发（日历 + CRUD + 设置 + 通知）

#### 目标
与 H5 功能完全对齐；但页面结构采用微信小程序习惯（Tab Bar 首页=日历、事项、通知、我）。

#### 使用的 Skill / 工具
- Vant Weapp（`van-cell`、`van-picker`、`van-datetime-picker`、`van-field`、`van-action-sheet`、`van-notify`）
- 自定义 `<calendar-grid>` 组件（`scroll-view` + `grid-view` 模拟三视图）
- `wx.createWebSocketTask`（可选实时通知）

#### AI 执行步骤
1. **Tab Bar**（`app.json`）：日历 / 事项 / 通知 / 我 —— 图标按 HIG 线性图标重绘（`lucide` 导出为 PNG 后加入 `assets/tabbar/`）。
2. **日历页** `pages/calendar/index`：
   - 自定义 `<calendar-grid>` 组件：月/周/日三模式；
   - 顶部视图切换用 `<van-tabs>`；
   - 长按日期触发 `wx.showActionSheet` 快速创建；
   - 事件点击 `router.push('/pages/event-detail?id=')`。
3. **事项 CRUD** `subpackages/event/pages/event-form/index`：
   - 根据 `relationType` 切换字段；
   - 使用 `<van-field>` + 自定义 `<form-dynamic>`；
   - 保存时调 `useCreateEvent` → `wx.showToast`。
4. **设置页** `subpackages/settings/pages/settings/index`：
   - 时区下拉用 `<van-picker>`；
   - 语言切换后立即 `i18n.changeLanguage()` 并 `this.setData` 重渲染。
5. **通知中心** `subpackages/settings/pages/notifications/index`：
   - 分页加载（`onReachBottom`）；
   - 首次进入申请订阅消息模板（后端 P8 对应）。

#### 验证步骤
```bash
cd $FE_ROOT/apps/miniprogram
pnpm exec tsc --noEmit
# 关键页面与组件齐全
for p in pages/calendar/index pages/login/index subpackages/event/pages/event-form/index subpackages/settings/pages/settings/index subpackages/settings/pages/notifications/index; do
  test -f miniprogram/$p.wxml && test -f miniprogram/$p.ts || { echo "MISSING: $p"; exit 1; }
done
# 自定义日历组件存在
test -f miniprogram/components/calendar-grid/index.ts
# 订阅消息申请代码在
grep -rq "requestSubscribeMessage" miniprogram
# 分包大小 ≤ 2MB
du -sk miniprogram/subpackages/event miniprogram/subpackages/settings | awk '$1>2048{exit 1}'
```

#### 完成判定
所有页面 TS + WXML + WXSS 齐全；分包尺寸合规；订阅消息流程到位。

---

### F14. 小程序自动化测试（miniprogram-automator）

#### 目标
用微信官方 `miniprogram-automator` 跑通与 H5 E2E 对齐的 9 个业务场景（E1–E9），失败自动截图 + 保存。

#### 使用的 Skill / 工具
- **`miniprogram-automator`**（官方 E2E 工具，Puppeteer 风格 API）
- `jest@29` + `allure-jest` 报告
- 被测后端：后端计划 docker-compose

#### AI 执行步骤
1. `e2e/miniprogram/package.json`、`jest.config.ts`。
2. `e2e/miniprogram/setup.ts`：
   ```ts
   import automator from 'miniprogram-automator';
   const WX_CLI = '/Applications/wechatwebdevtools.app/Contents/MacOS/cli';
   export async function launch() {
     return automator.launch({
       cliPath: WX_CLI,
       projectPath: require('path').resolve(__dirname, '../../apps/miniprogram'),
       port: 9420,
     });
   }
   ```
3. 编写 9 条测试：`E1-login.spec.ts` … `E9-a11y.spec.ts`（a11y 检查在小程序里用官方"体验评分"，见 F14 验证步骤）。
4. `afterEach` 失败截图：`await miniProgram.screenshot({ fullPage: true, path: \`screenshots/${testName}.png\` })`。

#### 验证步骤
```bash
# 1) 后端 + 前端 UI 都跑 F10 的 smoke 先通过
bash $BE_ROOT/scripts/smoke.sh

# 2) 启动 miniprogram-automator
cd $FE_ROOT/e2e/miniprogram
pnpm exec jest --runInBand --reporters=default --reporters=allure-jest
# 所有用例必须 pass
jq -e '.numFailedTests == 0' jest-results.json

# 3) 官方体验评分 ≥ 85（通过 cli 获取）
"$WX_CLI" eval --project $FE_ROOT/apps/miniprogram 'wx.getPerformance()'
"$WX_CLI" auto --project $FE_ROOT/apps/miniprogram --auto-port 9420 &
sleep 3
node $FE_ROOT/e2e/miniprogram/scripts/score.js > score.json
jq -e '.score >= 85' score.json
```

#### 完成判定
9 条用例全 pass；官方体验评分 ≥ 85；allure 报告生成；失败截图与 trace 保留。

---

### F15. 小程序 CI/CD + 体验版上传

#### 目标
- 使用官方 `miniprogram-ci` 自动上传体验版到微信后台。
- 生成体验版二维码 PNG，供 QA 与 PM 扫码测试。
- 接入 GitHub Actions / GitLab CI（模板化 YAML 即可）。

#### 使用的 Skill / 工具
- **`miniprogram-ci`**（微信官方 Node 库）
- 私钥：需在 [微信公众平台 → 开发管理 → 开发设置](https://mp.weixin.qq.com/) 下载 `private.appid.key`（由用户人工准备，AI 不持有）
- `qrcode-terminal` 或直接保留官方返回的二维码 PNG

#### AI 执行步骤
1. `apps/miniprogram/scripts/upload.mjs`：
   ```js
   import ci from 'miniprogram-ci';
   const project = new ci.Project({
     appid: process.env.WX_APPID,
     type: 'miniProgram',
     projectPath: './miniprogram',
     privateKeyPath: process.env.WX_PRIVATE_KEY_PATH,
     ignores: ['node_modules/**/*'],
   });
   const ver = process.env.WX_VER || '0.1.0';
   await ci.upload({
     project,
     version: ver,
     desc: process.env.WX_DESC || 'auto upload',
     setting: { es6: true, es7: true, minify: true, autoPrefixWXSS: true },
     onProgressUpdate: console.log,
   });
   const { base64 } = await ci.preview({
     project, version: ver, desc: 'preview', qrcodeFormat: 'base64', qrcodeOutputDest: './preview-qr.jpg'
   });
   console.log('QR written preview-qr.jpg');
   ```
2. `.github/workflows/miniprogram-upload.yml` 模板：secrets `WX_APPID`、`WX_PRIVATE_KEY`（文件内容），在 runner 上写入临时文件再调脚本。
3. 为安全：**严禁**将 `private.appid.key` 入库；`.gitignore` 已覆盖。

#### 验证步骤
```bash
# 本地干跑（无真实 appid 则用 mock）
cd $FE_ROOT/apps/miniprogram
WX_APPID=wx_mock WX_PRIVATE_KEY_PATH=/tmp/mock.key WX_VER=0.0.0-ci-dryrun \
  node scripts/upload.mjs --dry-run 2>&1 | tee /tmp/upload.log || true
# 真实上传（若 CI 提供 secrets）：
# WX_APPID=<真实> WX_PRIVATE_KEY_PATH=<真实> WX_VER=0.1.0 node scripts/upload.mjs
# 断言：二维码文件生成
# test -f preview-qr.jpg
grep -q "privateKeyPath" scripts/upload.mjs
grep -q "ci.upload" scripts/upload.mjs
# 私钥文件不得提交
! git ls-files | grep -E "private\..+\.key$"
```

#### 完成判定
脚本结构正确；私钥零泄漏；CI YAML 可被 lint；（如配置了真实 secrets）体验版二维码生成成功。

---

## Stage 3：联调、观测与交付

### F16. 可观测性（双端）

#### 目标
- H5：Sentry 捕获异常 + Web Vitals 上报 + sourcemap 自动上传。
- 小程序：错误与性能上报（优先 Sentry 官方 JS Browser SDK 通过社区适配 `@sentry/miniapp`，或改用 Fundebug 小程序 SDK / Aegis）。
- 双端一致的上下文：`tenantId / userId / appVersion / platform`。

#### 使用的 Skill / 工具
- H5：`@sentry/react@8`、`@sentry/vite-plugin`
- 小程序：`@sentry/minapp` 社区包 或 `fundebug-weapp` / 腾讯 Aegis（企业内部常用）
- `web-vitals@4`

#### AI 执行步骤
1. H5 `apps/h5/src/shared/observability/sentry.ts`：
   ```ts
   Sentry.init({
     dsn: import.meta.env.VITE_SENTRY_DSN,
     environment: import.meta.env.MODE,
     tracesSampleRate: 0.1,
     integrations: [Sentry.browserTracingIntegration()],
   });
   ```
   在 `vite.config.ts` 加入 `sentryVitePlugin`。
2. 小程序 `apps/miniprogram/miniprogram/utils/observability.ts`：在 `app.ts` `onError` / `onPageNotFound` 回调中调用 SDK；`wx.reportPerformance` 发送关键指标。
3. 统一的 `setUser({ tenantId, userId })` 在登录后调用。

#### 验证步骤
```bash
# H5：构建时 sourcemap 上传日志
pnpm --filter h5 build 2>&1 | tee /tmp/build.log
grep -qi "source map" /tmp/build.log
# 小程序：onError 回调已挂载
grep -q "onError" apps/miniprogram/miniprogram/app.ts
grep -qE "Sentry|Fundebug|Aegis" apps/miniprogram/miniprogram/utils/observability.ts
```

#### 完成判定
双端初始化代码就位；H5 sourcemap 上传日志出现；小程序 `onError` 挂载到上报 SDK。

---

### F17. 端到端联调冒烟（MUST PASS，验收唯一标志）

#### 目标
一条脚本 `scripts/e2e-smoke.sh` 按顺序串联：
1. 启动后端 docker-compose 并跑 `$BE_ROOT/scripts/smoke.sh`；
2. 构建 H5 并跑 Playwright `@smoke` 9 场景；
3. 构建小程序 `build-npm` 并跑 miniprogram-automator 9 场景；
4. 合并 allure 报告为一份总报告。
**三步任一失败即整体失败。**

#### 使用的 Skill / 工具
- Bash、后端 `smoke.sh`、Playwright、miniprogram-automator
- `allure-commandline`（合并报告）

#### AI 执行步骤
`$FE_ROOT/scripts/e2e-smoke.sh`：
```bash
#!/usr/bin/env bash
set -euo pipefail
BE_ROOT=/Users/allenwang/build/longfeng/calendar-platform
FE_ROOT=/Users/allenwang/build/longfeng/calendar-platform/frontend
ALLURE_DIR=$FE_ROOT/allure-results

rm -rf "$ALLURE_DIR" && mkdir -p "$ALLURE_DIR"

echo "==> [1/4] 后端 smoke"
bash "$BE_ROOT/scripts/smoke.sh"

echo "==> [2/4] 启动 H5 + Playwright @smoke"
cd "$FE_ROOT"
pnpm --filter h5 build
pnpm --filter h5 preview --port 4173 --host 127.0.0.1 > /tmp/fe-preview.log 2>&1 &
H5_PID=$!
trap "kill $H5_PID 2>/dev/null || true" EXIT
for i in $(seq 1 30); do curl -fsS http://127.0.0.1:4173 >/dev/null && break; sleep 1; done
pnpm --filter e2e-h5 exec playwright test --grep @smoke --reporter=list
cp -r e2e/h5/allure-results/* "$ALLURE_DIR/" 2>/dev/null || true

echo "==> [3/4] 小程序 miniprogram-automator @smoke"
WX_CLI="/Applications/wechatwebdevtools.app/Contents/MacOS/cli"
"$WX_CLI" build-npm --project "$FE_ROOT/apps/miniprogram"
cd "$FE_ROOT/e2e/miniprogram"
pnpm exec jest --runInBand --testPathPattern='smoke'
cp -r allure-results/* "$ALLURE_DIR/" 2>/dev/null || true

echo "==> [4/4] 合并 allure 报告"
cd "$FE_ROOT"
pnpm dlx allure-commandline generate "$ALLURE_DIR" -o allure-report --clean
echo "E2E-SMOKE PASS. Report: $FE_ROOT/allure-report/index.html"
```

#### 验证步骤
```bash
bash $FE_ROOT/scripts/e2e-smoke.sh && echo "F17 OK"
test -f $FE_ROOT/allure-report/index.html
```
退出码 0 且 allure 报告生成 = **全部 MVP 落地完成**。

#### 回滚
任一步失败：回到对应 Stage（后端 / H5 / 小程序）对应阶段修复；累计失败 2 个阶段触发附录 B 的"人工介入"机制。

#### 完成判定
`e2e-smoke.sh` 退出码 0；allure 报告生成且"测试通过率 = 100%"。

---

## 附录 A：目录最终结构

```
frontend/
├── package.json
├── pnpm-workspace.yaml
├── turbo.json
├── tsconfig.base.json
├── apps/
│   ├── h5/                             # React + Vite + Konsta UI
│   │   ├── src/{app,features,shared}
│   │   ├── public/locales
│   │   ├── .lighthouserc.json
│   │   └── vite.config.ts
│   └── miniprogram/                    # 微信小程序原生 TS
│       ├── project.config.json
│       ├── miniprogram/
│       │   ├── app.ts/json/wxss
│       │   ├── pages/{login,calendar,...}
│       │   ├── subpackages/{event,settings}
│       │   ├── components/{calendar-grid,...}
│       │   ├── stores/
│       │   ├── services/{http,router}
│       │   └── utils/
│       └── scripts/upload.mjs          # miniprogram-ci 上传
├── packages/
│   ├── design-tokens/                  # Apple HIG → CSS/WXSS/TS/JS
│   ├── api-contracts/openapi.json
│   ├── api-client-h5/                  # Orval 生成
│   ├── api-client-mp/                  # openapi-generator + wxFetch
│   ├── i18n/
│   └── utils/
├── e2e/
│   ├── h5/                             # Playwright
│   └── miniprogram/                    # miniprogram-automator + Jest
├── skills/
│   └── apple-hig-style/                # 自建风格 skill
│       ├── SKILL.md
│       ├── reference/hig-tokens.json
│       ├── reference/hig-principles.md
│       └── templates/*.md
├── scripts/
│   ├── fe-env-check.sh
│   └── e2e-smoke.sh
└── design/
    ├── UI_UX_规范.md
    └── mockups/ios/*.png
```

---

## 附录 B：失败处理通用原则

1. **阶段级 git tag**：每阶段通过后 `git tag fe-v0.F{n}`，失败 `git reset --hard fe-v0.F{n-1}`。
2. **错误预算**：单阶段重试 ≤ 3 次；累计失败 2 个阶段触发"人工介入"并输出诊断报告（`docs/progress/F{n}.md` 含日志摘要）。
3. **禁止跳过验证**：每条验证命令必须真实执行；结果写入 `docs/progress/F{n}.md`。
4. **禁止擅改 API 契约**：任何差异回 F4 重新生成 SDK。
5. **禁止 `sudo`**；微信开发者工具安装、微信后台私钥等必须由用户人工完成。

---

## 附录 C：AI 执行契约与硬约束

- 全部命令非交互模式（`-y`、`--no-input`、`--reporter=list`）。
- 任何阻塞等待用 `for/sleep` + HTTP 探测，最多 5 分钟超时。
- 设计素材版权合规：**不得**使用 Apple logo、SF Symbols 原始文件、苹果商标；图标统一 `lucide` 开源集（MIT）。
- 小程序主包 ≤ 2MB、分包各自 ≤ 2MB、总包 ≤ 20MB（微信硬限）。
- 所有 `wx.request` 必须经 `services/http.ts` 封装，禁止业务代码直调。
- H5 所有 Axios 请求必须经 `shared/http/client.ts` 封装。
- 每阶段完成后写入 `docs/progress/F{n}.md`，含命令、stdout 摘要、验证结论、耗时。
- CI 全量校验：
  ```bash
  # 禁用 any（除非 eslint-disable 标记理由）
  ! grep -RnE ": any\b" apps/*/src packages/*/src | grep -v generated | grep -v "eslint-disable"
  # 小程序禁止直调 wx.request
  ! grep -RnE "wx\.request\(" apps/miniprogram/miniprogram/pages apps/miniprogram/miniprogram/subpackages
  # H5 禁止直接 axios.create
  ! grep -RnE "axios\.create\(|new Axios" apps/h5/src | grep -v shared/http/client.ts
  # 私钥零泄漏
  ! git ls-files | grep -E "private\..+\.key$"
  ```

---

## 附录 D：与后端计划 / 未来 RN 端的映射

| 维度 | H5 | 小程序 | 未来 RN App |
|------|----|--------|-------------|
| UI 底座 | Konsta UI（iOS theme） | Vant Weapp + HIG token 覆盖 | `nativewind` + `react-native-ios-kit`（候选） |
| 导航 | React Router 6 | 原生 `wx.navigate*` 封装 | React Navigation 7 |
| 状态 | Zustand + TanStack Query | mobx-miniprogram + 自定义缓存 | Zustand + TanStack Query（与 H5 复用） |
| HTTP | Axios + 拦截器 | `wx.request` 封装 | Axios（与 H5 复用） |
| 后端接口 | 同一 OpenAPI JSON 生成 | 同一 OpenAPI JSON 生成 | 同一 OpenAPI JSON 生成 |
| i18n / 时区 | `@calendar/i18n` | `@calendar/i18n` | `@calendar/i18n` |
| 设计 Token | `dist/css/tokens.css` | `dist/wxss/tokens.wxss` | `dist/ts/tokens.ts` |
| 鉴权 | JWT + refresh | JWT + refresh + `wx.login` → code2session | JWT + refresh + Keychain |
| 可观测 | Sentry | Aegis / Fundebug | Sentry React Native |
| E2E | Playwright | miniprogram-automator | Detox / Maestro |

关键复用点（这也是选择 HIG + 分端原生实现的核心价值）：**设计 Token、i18n 资源、OpenAPI 契约、业务规则三项始终单一源头**，UI 层按平台最优实现。

---

## 附录 E：与后端联调对接表

| 后端阶段 | 前端对接 | 断言 |
|----------|---------|------|
| 后端 P3（PostgreSQL DDL）| F4 OpenAPI 生成 | 字段类型严格匹配（bigint→string\|number、jsonb→string[]） |
| 后端 P5（Calendar CRUD）| F8 / F13 | 联调 9 场景（E1–E6）通过 |
| 后端 P6（UserSetting）| F8 / F13 设置页 | 时区/语言切换实时生效 |
| 后端 P7（Reminder + XXL-Job）| F8 / F13 通知 | E7 手动触发提醒后，H5 Toast + 小程序订阅消息接收 |
| 后端 P8（Notification）| F8 / F13 | Mock 渠道日志可被 E2E 断言 |
| 后端 P9（Gateway + JWT）| F7 / F12 | JWT 注入 + 401 刷新 |
| 后端 P10（i18n）| F5 / F6 | 错误码 `CAL-*` 文案一致 |
| 后端 P13（Testcontainers）| F10 / F14 | docker-compose 上线，双端 E2E 通过 |
| 后端 P15（Observability）| F16 | 后端 `/actuator/prometheus` + 前端 Sentry 均有指标 |
| 后端 P16（smoke）| F17 | `e2e-smoke.sh` 第 1 步直接调用后端 `smoke.sh` |

---

**本计划共 18 个阶段（F0–F17），分 Stage 0（公共地基）/ Stage 1（H5）/ Stage 2（小程序）/ Stage 3（联调与交付）。每阶段均指定专业企业级工具/Skill + 可执行命令 + 可验证断言 + 回滚策略。AI 必须按序执行并在每阶段通过"完成判定"后方可进入下一阶段。最终以 F17 的 `e2e-smoke.sh` 全绿为 MVP 前端 + 联调 + 自动化业务测试落地完成的唯一标志。未来 P2 阶段扩展 React Native App 时，Stage 0 全部资产（设计 Token、OpenAPI、i18n、业务规则）均可直接复用。**
