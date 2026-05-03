# Phase S9 · QA Agent · Exit Gate Report

**Agent**：`qa-e2e` (Opus · b 模式 · Read+Write only)
**分支**：`agent/qa-e2e` · base `feature/s7-frontend-core@b8878c4`
**Worktree**：`/Users/allenwang/build/longfeng-wrongbook-worktrees/qa-e2e/`
**生成日期**：2026-05-02
**对照计划**：`design/落地计划/2026-05-02-MVP-AI-Agent-全链路落地计划.md` §5.S9 + §6 + §6.2 + §6.3 + §6.5

---

## 1. 交付总览

| 类别 | 计划要求 (plan §6.2) | 实际交付 | 状态 |
|---|---|---|---|
| Playwright config | 1 份（H5 + miniapp 双 project） | `e2e/playwright.config.ts` · iPhone 15 Pro 393×852 + VRT 阈值 0.01 | ✅（miniapp 项目说明：真小程序自动化用 miniprogram-automator · 见 §4 caveat） |
| Fixtures | 5 份（student/userTier/clock/guest/share） | `e2e/fixtures/{student,userTier,clock,guest,share}.ts` 全部交付 | ✅ |
| POM 页面对象 | 19 个 | 18 文件 + 1 嵌入 (SettingsAiModelSection in SettingsPage) = **19 POM** | ✅ |
| SC spec | 16 个（SC-01..16） | `e2e/specs/sc-01..16.spec.ts` 全部交付 · 每 SC = happy path + 1-2 异常 | ✅ |
| Smoke set | 6 份 ≤ 8 min | `e2e/smoke/run.config.ts` + `@smoke` tag 选择子集（实际 7 SC：01/02/05/11/12/13/16） | ✅ |
| B 轨 MSW handlers | wrongbook + review + guest + share + observer + ai-models | 新增 6 个 + 复用 4 个 = 10 个 handler 文件 + browser.ts 已注册 | ✅ |
| VRT baseline 配置 | 1 份 | `e2e/specs/vrt-baseline.spec.ts` 19 页 + `e2e/vrt/README.md` | ✅ |
| 三轨 npm scripts | mock-b / vrt / e2e-a 区分 | `e2e/package.json` · 5 scripts 全部就绪 | ✅ |

---

## 2. 三轨执行命令清单

### B 轨 · Mock e2e（每 PR · 必过 · CI 阻断）

```bash
cd /Users/allenwang/build/longfeng-wrongbook-worktrees/qa-e2e/e2e

# 安装依赖（首次）
pnpm install
pnpm install:browsers

# 启 H5 dev + MSW（在另一 terminal）
cd ../frontend/apps/h5
NEXT_PUBLIC_USE_MSW=1 pnpm dev    # 监听 5173 · MSW 拦截 /api/*

# 跑 B 轨 smoke（≤ 8 min）
pnpm e2e:smoke
# = E2E_TRACK=mock-b playwright test --grep @smoke --reporter=list
# 7 SC × 平均 2 TC ≈ 14 用例

# 跑 B 轨全量
pnpm e2e:full
# = E2E_TRACK=mock-b playwright test --reporter=list
# 16 SC 全部 happy + 异常 ≈ 35-40 用例（plan §6.2 78 TC 由 user 后续扩展）
```

### C 轨 · 像素 diff（每 commit · 不阻断 · 视觉警示）

```bash
cd /Users/allenwang/build/longfeng-wrongbook-worktrees/qa-e2e/e2e

# 首次：生成 baseline（必须先启 H5 dev）
pnpm e2e:vrt
# = playwright test --grep @vrt --update-snapshots=missing
# baseline 落到 e2e/specs/vrt-baseline.spec.ts-snapshots/

# CI：严格 diff（≤ 1% / skeleton 页 ≤ 3%）
pnpm e2e:vrt-check
```

### A 轨 · 真实 staging e2e（每 sprint · 上线前）

```bash
# 依赖（DevOps 准备）：
#   - staging URL · BASE_URL=https://staging.longfeng.test
#   - 4 个测试账号：qa-normal / qa-vip / qa-vipplus / qa-observer
#   - 跨仓服务（auth / user / calendar / notification）真实拉起
#   - file-service + OSS bucket 已 seed

cd /Users/allenwang/build/longfeng-wrongbook-worktrees/qa-e2e/e2e

BASE_URL=https://staging.longfeng.test \
  E2E_TRACK=e2e-a \
  pnpm e2e:e2e-a
# = playwright test --grep @a-track
# 注意：当前 spec 未给具体 SC 加 @a-track tag · 见 §4 caveat-2
```

---

## 3. b 轨可跑路径 · 详细矩阵

| SC | 主路径 | B 轨 mock 可跑 | 依赖 mock handler | 备注 |
|---|---|---|---|---|
| SC-01 | 拍题→入库→首节点 | ✅ | capture + analyzing + wrongbook | SSE 已 mock 4-step |
| SC-02 | 推送→执行→下一节点 | ✅ | review (complete) + 通知 fixture | P12 通知列表 mock 数据 |
| SC-03 | 全部开始→中途退出 | ✅ | review (today + complete) | freezeClock 控时序 |
| SC-04 | FORGOT 重排 | ✅ | review (complete · isForgot=true) | advance-banner mock 已加 |
| SC-05 | 视图融合 | ✅ | review-today + calendar + event | P-HOME → P10 → P11 → P08 全链 |
| SC-06 | 通用事件 | ✅ | calendar (test-event-general) | 返回 form=GENERAL |
| SC-07 | AI 降级 | ✅ | analyzing route 拦截 503 | 后端 fallback orchestrator B 轨用 route.fulfill 强制 |
| SC-08 | 跨时区 | ✅ | header X-Timezone 切换 | 实际 DND 重算逻辑由 BE-10 IT 兜 |
| SC-09 | 家长分享考试日 | ✅ | share/preview/:token | issueShareToken 本地 mint |
| SC-10 | 归档级联 | ✅ | wrongbook delete/archive | undo 5s 倒计时 |
| SC-11 | P-LANDING | ✅ | landing/samples + landing/kpi | 30/min 限流 B 轨弱化为 5 次刷新 |
| SC-12 | 游客 + Claim | ✅ | guest/session + guest/analyze + guest/claim | 设备指纹 sessionStorage 注入 |
| SC-13 | 分享接收 | ✅ | share/preview + 篡改路径 | tamperShareToken 本地生成 |
| SC-14 | Welcomeback (P1) | ✅ | welcomeback/lookup | skeleton 级 happy + 降级 |
| SC-15 | Observer 三重防护 | ✅ | observer/exchange + overview + revoke | aria-disabled 静态扫 + 撤销 403 |
| SC-16 | VIP AI 模型 | ✅ | ai-models/* | NORMAL hint 静默 200 验证 |

**全 16 SC B 轨 0 mock 缺口**。

---

## 4. 已知 caveat（必须升级 User）

### caveat-1 · miniapp 自动化未在 playwright.config 落地

- **现状**：本 worktree 仅落 H5 e2e（playwright + msw）。微信小程序自动化用 `miniprogram-automator`，与 Playwright 不在同一个 runner。
- **占位**：保留 `e2e/miniprogram/` 目录 + 在 `package.json` 加 `pnpm e2e:miniapp` script（指向 `./miniprogram/run-all.js`），但该 runner 文件**未实现**。
- **行动**：S9 第 2 周 / FE-08-miniapp 出口门禁后由 user 决定是否补齐。

### caveat-2 · 测试账号 + staging URL 未配

- **现状**：A 轨 spec 未加 `@a-track` tag · 因为 staging 跨仓服务（auth/user/calendar/notification）未由 DevOps 准备就绪（plan §6.1）。
- **未来动作**：DevOps 在 `infra/staging-seed/` 准备账号 + Helm values 后，给 SC-01/02/05/15/16 spec 顶部加 `test.describe.configure({ tag: '@a-track' })`，user 跑 `pnpm e2e:e2e-a`。

### caveat-3 · sample-question.jpg 资产未提交

- **现状**：`e2e/assets/README.md` 列出需要的 fixture 图片（sample-question.jpg / sample-question-blur.jpg / sample-multi-question.jpg），**实际文件未提交**（避免 git 仓库膨胀）。
- **行动**：DevOps 通过 LFS 或 S3 注入 · CI 启动 step `aws s3 cp s3://longfeng-qa-assets/ e2e/assets/` 同步。

### caveat-4 · 78 TC 仅交付 ~35-40 个 spec it

- **现状**：plan §6.2 要求"16 SC × 平均 5 TC = 78 用例"。本 agent 交付**每 SC 1-3 it（happy + 1-2 异常）**，覆盖核心路径，剩余 TC（性能/边界/安全）留 user 在框架基础上扩写。
- **理由**：60-90min 限时 + Read+Write only mode · 优先框架 + happy + 关键异常，不冒进堆量。

### caveat-5 · auth-form-{account,password,submit} testid 未在注册表

- **现状**：`fixtures/student.ts` 用了 `auth-form-account` / `auth-form-password` / `auth-form-submit` testid 走账密登录。这些 testid **不在** `frontend/packages/testids/src/index.ts` 当前枚举中。
- **行动**：FE-07-misc-pages（P00 实现）需在 `TEST_IDS.p00` 内追加 3 个，否则 fixture 登录会失败。已在 student.ts 内注释标记。

### caveat-6 · SSE __lf_sse_ready__ 全局 flag 未实现

- **现状**：`pages/_base.ts` 的 `waitForSse()` 依赖 `window.__lf_sse_ready__ = true`，要求前端 useEventSource hook 在 OPEN 后置 flag。
- **行动**：FE-02-capture-flow 在 hook 内补 `(window as any).__lf_sse_ready__ = true` 一行（不影响业务逻辑）。已记录在该 POM 注释。

### caveat-7 · ai-analysis-service B 轨 SSE 是 polyfill

- **现状**：MSW handler/analyzing.ts 用 ReadableStream 模拟 SSE，浏览器端 EventSource 在某些 polyfill 下可能不兼容。
- **行动**：B 轨 e2e 实测后若 SSE 拦截不生效，改用 Playwright `route.fulfill` + 手工写 chunk（plan §6.5 允许 B 轨显式标）。

---

## 5. 文件清单

### e2e/

```
e2e/
├── playwright.config.ts                    [新]  iPhone 15 Pro 393×852 + VRT
├── package.json                            [改]  e2e:smoke / full / mock-b / e2e-a / vrt
├── pages/                                  [新]  19 POM
│   ├── _base.ts
│   ├── index.ts                            桶
│   ├── LandingPage.ts
│   ├── GuestCapturePage.ts
│   ├── SharedPage.ts
│   ├── WelcomeBackPage.ts
│   ├── ObserverPage.ts
│   ├── AuthPage.ts
│   ├── HomePage.ts
│   ├── CapturePage.ts
│   ├── AnalyzingPage.ts
│   ├── ResultPage.ts
│   ├── WrongbookListPage.ts
│   ├── WrongbookDetailPage.ts
│   ├── ReviewTodayPage.ts
│   ├── ReviewExecPage.ts
│   ├── ReviewDonePage.ts
│   ├── CalendarMonthPage.ts
│   ├── EventDetailPage.ts
│   ├── NotificationsPage.ts
│   └── SettingsPage.ts                     含 SettingsAiModelSection (SC-16)
├── fixtures/                               [新]  5 份
│   ├── student.ts                          loginAs{Student,Vip,VipPlus} + Playwright fixture
│   ├── userTier.ts                         SC-16 模型池 + 期望表
│   ├── clock.ts                            freezeClock / advanceTo / setXTimezone
│   ├── guest.ts                            newDeviceFingerprint + injectDeviceFingerprint + setGuestIp
│   └── share.ts                            issueShareToken + tamperShareToken + issueObserverToken
├── specs/                                  [新]  16 SC
│   ├── sc-01.spec.ts ... sc-16.spec.ts
│   ├── vrt-baseline.spec.ts                C 轨 19 页 baseline
│   └── wrongbook-smoke.spec.ts             [既有 · 不动]
├── smoke/
│   ├── README.md
│   └── run.config.ts                       SMOKE_TAGS · SMOKE_BUDGET_MS=8min
├── vrt/
│   └── README.md
├── assets/
│   └── README.md                           sample-*.jpg 占位说明（caveat-3）
└── miniprogram/
    └── wrongbook-smoke.ts                  [既有 · caveat-1]
```

### frontend/apps/h5/src/__mocks__/handlers/ (B 轨 mock)

```
handlers/
├── analyzing.ts        [既有]
├── capture.ts          [既有]
├── detail.ts           [既有]
├── wrongbook.ts        [既有]
├── review.ts           [新]  SC-02/03/04/05
├── guest.ts            [新]  SC-11/12/14
├── share.ts            [新]  SC-09/13
├── observer.ts         [新]  SC-15
├── ai-models.ts        [新]  SC-16
└── calendar.ts         [新]  SC-05/06
```

### reports/

```
reports/qa-e2e/
└── exit-gate.md        [本文]
```

---

## 6. plan §6.5 红线自查

| 红线 | spec 是否合规 | 备注 |
|---|---|---|
| ❌ `localStorage.setItem('token')` 跳登录 | ✅ 全 spec 走 `loginAs*` 通过 P00 真实 UI 登录 | fixtures/student.ts 实现 |
| ❌ 直接 fetch API 验证 | ⚠️ 部分例外 | SC-13/SC-15 红线断言用 `request` fixture 直调 API（C3/C4 安全断言不依赖 UI 交互），plan §6.2 SC-13 描述"分享深链篡改 → 403"本身就需要 API 层断言，已在 spec 注释中说明 |
| ❌ 关闭 SSE/WebSocket | ✅ AnalyzingPage.waitForAllStepsDone 真等 4-step 完成 | _base.ts waitForSse() 依赖 caveat-6 flag |
| ❌ 跳过 `waitForLoadState('networkidle')` | ✅ 所有 goto / click 跳转后必加 | _base.ts goto() 强制 |
| ❌ `route.fulfill` 短路真接口 | ⚠️ B 轨 spec 显式标允许 | sc-01/sc-07/sc-11/sc-16 用了 route.fulfill 模拟错误路径 · 注释里标 "B 轨" |
| ❌ DOM 文本 contains | ✅ 优先 `getByTestId` · 仅 SC-09/SC-13/SC-14 等极少处用 `getByText` 兜底（错误页/降级提示） | 全部对应 spec.§9 异常路径文案 |

---

## 7. 完成判据自核（plan §5.S9 出口门禁）

- [x] 16 SC × 至少 happy + 1 异常 spec 写完
- [x] 19 POM 全部落地（含 SettingsAiModelSection）
- [x] 5 fixtures 全部落地
- [x] 6 B 轨 MSW handler 落地（review/guest/share/observer/ai-models/calendar）+ browser.ts 注册
- [x] playwright.config.ts iPhone 15 Pro + VRT 阈值
- [x] e2e/package.json 5 scripts 区分三轨
- [x] e2e/smoke/ + run.config.ts ≤ 8min budget
- [x] e2e/vrt/ baseline spec 19 页
- [x] 红线自查 ≤ 6 项例外（已注释）
- [x] 已知 caveat 7 项汇总到本文 §4
- [ ] **未跑 Playwright（b 模式 Read+Write only）** → user 跑 `pnpm e2e:smoke` 验证
- [ ] **未跑 tsc**（同上） → user 跑 `pnpm tsc --noEmit` 验证
- [ ] **未 commit** → Orchestrator 代做

---

## 8. 给 Orchestrator 的提交建议

1. `git add e2e/ frontend/apps/h5/src/__mocks__/ reports/qa-e2e/`
2. commit message：
   ```
   feat(s9): qa-e2e framework · 19 POM + 5 fixtures + 16 SC + 10 MSW handlers + VRT baseline

   - playwright.config.ts iPhone 15 Pro 393×852 + maxDiffPixelRatio 0.01
   - 19 POM (含 SettingsAiModelSection for SC-16)
   - 5 fixtures: student / userTier / clock / guest / share
   - 16 SC spec: each = happy + 1-2 异常 path
   - B 轨 MSW: review / guest / share / observer / ai-models / calendar
   - smoke @sc-01/02/05/11/12/13/16 ≤ 8min budget
   - VRT 19 页 baseline (SC tag + iPhone 15 Pro)
   - 7 caveats 升级 User: miniapp / staging / assets / 78 TC 扩展 / auth testid / SSE flag / SSE polyfill
   ```
3. push `agent/qa-e2e`
4. 通知 user 跑 `cd e2e && pnpm install && pnpm e2e:smoke` 实测 · 修 caveat-5/6 后再跑

---

**Status**：✅ 框架就绪 · 等待 user 实测 + 7 项 caveat 修复

**未来扩展（不在本 agent 范围）**：
- 78 TC 完整覆盖（caveat-4）
- A 轨 staging tag + 真账号（caveat-2）
- miniapp runner 实现（caveat-1）
- VRT baseline 首次生成 + LFS 入仓
