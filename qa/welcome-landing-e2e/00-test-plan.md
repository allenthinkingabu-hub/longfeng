# QA Orchestrator · 欢迎页全漏斗 E2E 自动化调度计划

> **状态**：草案 v2 · 待 user 确认 "用例通过，准许执行"
> **角色**：QA Orchestrator (本会话) + 多个 Dev Sub-Agent (按需 fan-out)
> **目标范围**（user 已确认 §6）：P-LANDING (`/welcome`) → P-GUEST-CAPTURE (`/guest/capture`) → P00 (`/auth`) **全漏斗**
> **基线分支**：`feature/s7-frontend-core` (当前分支 · 经 user 修正确认)
> **Author 配置**：`zhe.wang` (与当前 git user 一致)
> **资产沉淀目录**：`qa/welcome-landing-e2e/` (repo root · 无 export/ 父级 · per user)
> **Bug 编号前缀**：`BUG-LF-NN-{slug}.md` (per user)
> **浏览器运行模式**：Headless + trace.zip (per user · 快 + 失败可时光回放)

---

## §0 Context · 为什么这次测试

P-LANDING 已被 sprint S3 实现并经多轮 design-audit (d5/d6 router-fix · chrome-jsx-batch · 详见 `git log`)。但：

1. **现有 E2E 仅 3 个 case** (`e2e/specs/sc-11.spec.ts`) — happy / samples 500 降级 / IP 限流 sanity，**远未穷举 spec §8 的 8 条 AC + §9 的 6 类异常 + §10 的 6 个埋点 + §11 的 5 项性能预算 + §12 的 4 项 a11y**
2. **最新 d6 multi-round 报告** (`e2e/reports/design-audit/d6-multi-round-final.md`) 显示 12 页 stuck — P-LANDING 不在 stuck 列表（已通过 design-reviewer），但 **mockup-diff 与 vrt-multi 的多 viewport 是否仍 PASS 需重跑**
3. user 要求"严格模拟真实用户从 Web UI 一步步操作"——意味着不只是 selector 断言，需要覆盖完整漏斗 (view → cta → guest-capture / login)、跨设备 (mobile/iPad/desktop)、降级链路、a11y 键盘流、reduced-motion 兜底

**预期产出**：穷举测试用例集 + 对抗式修复闭环 + 可复用资产沉淀，让 P-LANDING 之后任何回归都能一键复跑。

---

## §0.5 测试最终能达到的效果（user 评估"是否准许执行"的依据）

### A. 可证明的事实（PR 级证据 · 跑完都有物理产物）

| 维度 | 证据形式 | 数字目标 |
|---|---|---|
| 3 页全漏斗功能 | ~60 TC 全绿 + Playwright trace.zip + screenshot per TC | anonymous → guest → login → home 任一节点回归 ≤ 5 分钟 catch |
| 视觉对齐（B 机制） | `e2e/reports/mockup-diff/{P-LANDING,P-GUEST-CAPTURE}-*.png` 三联 | 像素差 ≤ 5%（P00 archive 缺失·按 STYLE-TRUTH §6 走 vrt 替代） |
| 跨设备（A 机制） | `vrt-multi-viewport-snapshots/` 共 12 张 baseline | iPhone+iPad11+iPad12+Desktop each ≤ 1% diff（治本 d5/d6 chrome 误植回归） |
| AI 视觉评审（C 机制） | `e2e/reports/design-review/{P-LANDING,P-GUEST-CAPTURE,P00}.json` | verdict=PASS · issues 全 ≤ medium |
| 性能 | PerformanceObserver 量化 + bundle size check | TTI/LCP/CLS/bundle 4 项 each page 达 spec §11 预算 |
| A11y | axe-core scan + keyboard trace | 0 serious · landmark/tab order/reduced-motion 全合规 |
| 埋点 | network HAR + payload assert | 12 个 event（landing 6 + guest 6）都被 capture 且字段完整 |

### B. 可交付的物理资产（commit 后永久留下）

- `qa/welcome-landing-e2e/` 完整目录（test-plan + 01-trajectory + 60+ TC driver + bug 报告 + final report）
- `e2e/specs/sc-11-extended.spec.ts` + `sc-12-extended.spec.ts` + `sc-p00-extended.spec.ts`（与 sprint 主 spec 解耦）
- 扩展后的 3 个 POM (LandingPage / GuestCapturePage / AuthPage) 共加 ~50 个断言方法
- 对抗式修复链路下的 N 个 `BUG-LF-NN-*.md` + 对应修复 commit (author=zhe.wang)

### C. 业务级效果（最终价值）

1. **防止历史回归**：d5/d6 那种"iPhone chrome 在 iPad/Desktop 误植"类 bug 被 12 张 baseline 锁死
2. **漏斗即测试**：欢迎页 → 拍题 → 登录任一改版回归即时定位
3. **可复用 pattern**：QA 资产组织方式可复制到其他页面（P-HOME / P02 / P15…）
4. **设计实施铁律 §2.11 自检自动化**：design-reviewer 派发已串进流程

### D. ⚠️ 不能保证的事（坦白红线 · 必须告诉 user）

| 测不出的事 | 为何 E2E 测不出 | 兜底方 |
|---|---|---|
| `AC-LANDING-007` 转化率 ≥35% | 需 prod 真实流量 | 数据团队 RUM/埋点漏斗 |
| 真 30/min IP 限流 | E2E reload 触不到生产网关 | BE-05/06 集成测试 |
| 真微信 OAuth | `wx.login()` 不能在 chromium 自动化 | MSW mock + dev-only 账密替代 |
| 性能 = prod 数字 | 本地 chromium + mock 网络偏乐观 | prod RUM (Web Vitals) |
| DEGRADED 时 BE 监控告警链路 | E2E 不触 SRE 系统 | 运维侧 alarm rule |

---

## §1 关键事实 (Phase 1 探索结论 · 已锁定)

| 维度 | 结论 | 锚点 |
|---|---|---|
| 路由 | `/welcome` → `LandingPage` | `frontend/apps/h5/src/App.tsx:145` |
| 实现 | 单文件 442 行内联 + CSS module | `frontend/apps/h5/src/pages/Landing/index.tsx` + `Landing.module.css` |
| Spec | 15 段完整 spec · 8 AC · 5 状态机 · 6 异常 · 6 埋点 | `design/system/pages/P-LANDING.spec.md` |
| Mockup 权威 | v0 archive (`_archive/14_landing.html`) — current 版有 PM 后期偏离 | spec frontmatter `mockup_canonical_note` |
| 既有 POM | `LandingPage.ts` (3 公共方法 + 1 失效断言) | `e2e/pages/LandingPage.ts:1-48` |
| 既有 spec | `sc-11.spec.ts` (3 test) | `e2e/specs/sc-11.spec.ts:1-48` |
| Mockup-diff 注册 | P-LANDING tolerance 5% / P-GUEST-CAPTURE 5% / P00 自动 skip(archive 缺) | `e2e/specs/mockup-vs-impl.spec.ts:58,14,65` |
| VRT-multi 注册 | 3 页 × 4 viewport · tolerance 1% | `vrt-multi-viewport.spec.ts:27,10,19` |
| Dev server | `cd frontend/apps/h5 && pnpm dev` → `http://localhost:5173` | 无自动 webServer |
| Track 三轨 | mock-b (MSW · 必过) / e2e-a (真 API · sprint 末) / vrt + design (审计) | `e2e/package.json` |
| P-LANDING testid | landing-page · -hero · -hero-headline · -hero-cta-{try,login} · -samples · -samples-card-{1,2,3} · -three-step · -cta-bottom{,-btn} · -kpi{-total,-retention} | spec §8 + 实现 lines 207-419 |
| P-GUEST-CAPTURE testid | p-guest-capture · camera-preview · guest-quota-banner{,-text,-cta} · subject-chip-row · subject-chip-{math/physics/chemistry/english} · capture-controls{,-shutter} · quota-exhausted-screen · quota-exhausted-cta-register | `frontend/apps/h5/src/pages/GuestCapture/index.tsx` lines 220-385 |
| P00 testid | p00-root · p00-statusbar · p00-logo-zone{,-logo} · p00-wechat-cta-btn (含 `data-iron-rule-1-exception="wechat-brand"`) · p00-other-methods-link · p00-consent-bar{,-checkbox,-link-tos,-link-privacy} · auth-dev-form · auth-form-{account,password,submit} | `frontend/apps/h5/src/pages/Auth/index.tsx` lines 108-261 |
| Chrome 边界 | P-LANDING / P-GUEST-CAPTURE 含 4 处 `data-mockup-chrome` · P00 mockup 缺该 attr (B 机制 skip) | spec §15 各页 |
| P00 特殊 | archive 缺失 · current `00_login.html` 是按 STYLE-TRUTH §6 反推 · 微信按钮带 iron-rule-1-exception | STYLE-TRUTH.md:628-639 |

---

## §2 E2E Test Cases (穷举 · ~64 条 · 11 个 Phase · 覆盖 3 页全漏斗)

> 命名规范：`TC-LF-{Phase}{seq}` (LF = LongFeng)。每条 TC 对应 `runs/TC-LF-XX/` 截图+trace 目录沉淀。
>
> Phase A-H = P-LANDING (35 条) · Phase I = P-GUEST-CAPTURE (12 条) · Phase J = P00 (12 条) · Phase K = 跨页漏斗 (5 条)

### Phase A · 功能链路 (6 条 · mock-b 轨 · @sc-11 @smoke)

| TC | Targets | Method | Pass criterion |
|---|---|---|---|
| TC-LF-A1 | spec §6 LOADING→READY · §11 TTI | UI walk: `goto /welcome` → 量 hero headline 可见时间 | `landing-hero-headline` 在 1000ms 内可见 + 文本 = "AI 帮你拍下错题，不再做无用功" (AC-LANDING-001) |
| TC-LF-A2 | AC-LANDING-002 双 CTA 视觉层级 | UI: 进入页面 → 截图 + getByTestId | `landing-hero-cta-try` 背景色解析为 white · `landing-hero-cta-login` 解析为 `--tkn-color-primary-DEFAULT` (#007AFF) |
| TC-LF-A3 | AC-LANDING-003 试一试漏斗 | UI: click `landing-hero-cta-try` → 等路由 | URL = `/guest/capture` · 埋点 `anon_landing_cta_try` 上报 (`device_fp` + `cta_position=hero`) |
| TC-LF-A4 | 登录漏斗 | UI: click `landing-hero-cta-login` → 等路由 | URL = `/auth` (P00) · 埋点 `anon_landing_cta_login` |
| TC-LF-A5 | 二次 CTA · 底部 dock | UI: 滚到底部 → click `landing-cta-bottom-btn` | 跳转同 try (P-GUEST-CAPTURE) · 埋点 `cta_position=bottom` |
| TC-LF-A6 | 家长入口 | UI: click "家长/老师入口" 小字链接 | 跳 `/observer` · 埋点 `anon_landing_parent_entry` |

### Phase B · 数据展示 (5 条 · mock-b 轨)

| TC | Targets | Method | Pass criterion |
|---|---|---|---|
| TC-LF-B1 | AC-LANDING-004 样例卡 3 个 + 学科色条 | UI: 滚到 samples 区 → 截每张卡 | `landing-samples-card-1` 左条 = `--tkn-subject-math` · -2 = physics · -3 = english (computed background) |
| TC-LF-B2 | AC-LANDING-005 KPI banner | UI: 滚到 KPI → 读文本 | `landing-kpi-total` 含 "100w" · `landing-kpi-retention` 含 "47%" |
| TC-LF-B3 | 三步漫画完整可见 | UI: 滚到 three-step | `landing-three-step` 可见 · 内有 3 张 feature row · 4 色梯度 icon block 渲染正常 |
| TC-LF-B4 | 样例卡点击 → 内嵌弹层 | UI: click `landing-samples-card-1` | 不离开 `/welcome` · 弹层 / 展开态出现 · 埋点 `anon_landing_sample_open` |
| TC-LF-B5 | hero demo media 自动播放 | UI: 等 hero 动图自动播完 | 埋点 `anon_landing_demo_play` 上报 (`sec` 字段) |

### Phase C · 异常路径 (6 条 · spec §6 + §9)

| TC | Targets | Method | Pass criterion |
|---|---|---|---|
| TC-LF-C1 | AC-LANDING-006 samples 500 降级 | MSW 拦截 `/api/landing/samples` → 500 | `landing-samples` 可见 + 含降级文案 (现有 sc-11 已覆盖 · 保留) |
| TC-LF-C2 | KPI 接口超时 | MSW delay > 3s | `landing-kpi` 隐藏 · 不阻塞 hero CTA |
| TC-LF-C3 | 双接口同时失败 (DEGRADED 严重态) | MSW 两个都 500 | 只露 hero+CTA · 不白屏 · console 无 unhandled error |
| TC-LF-C4 | 网络完全失败 (ERROR 态) | `context.setOffline(true)` | 显示静态海报兜底 · "无网络也能登录"链接可见 |
| TC-LF-C5 | EMPTY 态 (samples 数组空) | MSW 返回 `{samples: []}` | 显示"敬请期待样例"静态文案 |
| TC-LF-C6 | 微信内置浏览器 fallback | spoof UA `MicroMessenger` + 屏蔽 Lottie | hero 退化为静态 PNG · 不卡 |

### Phase D · 视觉对齐 · A+B+C 机制 (4 条 · 仅 P-LANDING · @vrt-multi @mockup-diff)

| TC | Targets | Method | Pass criterion |
|---|---|---|---|
| TC-LF-D1 | Mockup-diff (B 机制) | `pnpm e2e:mockup-diff -- --grep P-LANDING` | diff 像素率 ≤ 5% · 三联 PNG 落 `e2e/reports/mockup-diff/P-LANDING-*.png` |
| TC-LF-D2 | VRT iPhone (A 机制 · 单 viewport) | `pnpm e2e:vrt-check -- --grep P-LANDING` | maxDiffPixelRatio ≤ 1% |
| TC-LF-D3 | VRT 多 viewport (A 机制 · 4 设备) | `pnpm e2e:vrt-multi-check -- --grep P-LANDING` | iPhone+iPad11+iPad12+Desktop 各自 ≤ 1% (重点抓 iPad chrome 误植回归) |
| TC-LF-D4 | Vision design-review (C 机制) | 派 `design-reviewer` agent · input page_id=`P-LANDING` | verdict = `PASS` · 报告落 `e2e/reports/design-review/P-LANDING.json` (issues 全 ≤ medium) |

> 注：P-GUEST-CAPTURE 视觉对齐在 Phase I (TC-LF-I9~I12)；P00 视觉对齐在 Phase J (TC-LF-J9~J12)。

### Phase E · A11y (4 条 · spec §12)

| TC | Targets | Method | Pass criterion |
|---|---|---|---|
| TC-LF-E1 | axe 扫无 serious | 现有 `assertAxeNoSerious()` | violations.serious.length === 0 |
| TC-LF-E2 | Landmarks | UI: query `header[role=banner]` / `main[role=main]` / `footer[role=contentinfo]` | 三 landmark 都存在 |
| TC-LF-E3 | Tab 焦点顺序 | UI: 反复 Tab → 记录 `document.activeElement` | 顺序 = logo → cta-try → cta-login → samples-card-1 → ... → cta-bottom-btn |
| TC-LF-E4 | `prefers-reduced-motion` | `emulateMedia({ reducedMotion: 'reduce' })` | hero 动图不自播 · 样例卡入场无 stagger 动画 |

### Phase F · 性能 (4 条 · spec §11)

| TC | Targets | Method | Pass criterion |
|---|---|---|---|
| TC-LF-F1 | TTI ≤ 1000ms | Playwright `page.evaluate` 读 PerformanceObserver | TTI < 1000ms (mock 网络 · 本地 chromium) |
| TC-LF-F2 | LCP ≤ 1500ms | LargestContentfulPaint observer | < 1500ms |
| TC-LF-F3 | CLS < 0.05 | LayoutShift entries sum | < 0.05 |
| TC-LF-F4 | 总 JS bundle ≤ 180KB | `pnpm build` 后量 dist/assets/Landing-*.js + critical CSS | landing chunk ≤ 180KB (gzipped) |

### Phase G · 跨设备 + 弱网 (3 条)

| TC | Targets | Method | Pass criterion |
|---|---|---|---|
| TC-LF-G1 | iPad Pro 11" 横竖屏切换 | `setViewportSize` 834×1194 → 1194×834 | 无 iPhone chrome 残影 (回归 d5/p-landing-13cb862 同类 bug) · cta dock 仍 sticky |
| TC-LF-G2 | Desktop 1440×900 | viewport 1440×900 | hero 自适应 max-width · 不出现"手机壳"样式 (这正是历史 P-LANDING bug) |
| TC-LF-G3 | 弱网 (slow 3G) | `context.route` 加 throttle | hero 退静态海报 · skeleton 显示 · TTI 仍 ≤ 3s |

### Phase H · 边界与防御 (3 条 · spec §9)

| TC | Targets | Method | Pass criterion |
|---|---|---|---|
| TC-LF-H1 | 双 CTA 同时点击防误触 | UI: Promise.all 两个 click | 只跳第一目标 · 第二个 click 被 disabled |
| TC-LF-H2 | IP 限流 30/min (覆盖 sc-11 既有) | 第 31 次 reload | 拿到 429 · UI 友好提示 · 不白屏 |
| TC-LF-H3 | GDPR 海外地区 ConsentBar | spoof header `cf-ipcountry: DE` | 顶部 ConsentBar 出现 · 强制同意才显示动图 |

### Phase I · P-GUEST-CAPTURE (12 条 · 匿名拍题相机页)

| TC | Targets | Method | Pass criterion |
|---|---|---|---|
| TC-LF-I1 | 进入 quota banner 显示 (AC-GUEST-001) | UI: open `/guest/capture` → 读 `guest-quota-banner-text` | 文本包含 "还剩 N 次" 数字 · banner 可见 · `aria-live=polite` |
| TC-LF-I2 | Mood C 全屏 #0B0F1A (spec §15 视觉) | computed style `background-color` of `p-guest-capture` | 解析为 `rgb(11, 15, 26)` (#0B0F1A) · `data-mood="C"` 存在 |
| TC-LF-I3 | 取景器 70vh (AC-GUEST-003) | UI: 读 `camera-preview` 高度 | height ≈ window.innerHeight × 0.7 (±5%) · role=main |
| TC-LF-I4 | 学科 chip 单选 (AC-GUEST-004) | UI: 顺序点 math → physics | `aria-pressed=true` 只在最后选的 chip · 其他全 false |
| TC-LF-I5 | 快门 78px (AC-GUEST-005) | computed style of `capture-controls-shutter` | width=78px · height=78px · 圆形 |
| TC-LF-I6 | 拍照流程 happy path | UI: 选学科 → 点快门 → 等 ANALYZING → 等 RESULT | 状态机 IDLE→FOCUSING→CAPTURED→UPLOADING→ANALYZING 全跑通 · 埋点 `anon_guest_capture_shoot` + `anon_guest_analyze_start` + `anon_guest_analyze_done` 三连发 |
| TC-LF-I7 | QUOTA_EXHAUSTED 挡板 (AC-GUEST-006) | header `x-e2e-quota-out=1` → reload | `quota-exhausted-screen` role=alertdialog 全屏可见 · `quota-exhausted-cta-register` 跳 `/auth` |
| TC-LF-I8 | 注册 CTA 漏斗 | UI: click `guest-quota-banner-cta` | URL = `/auth?redirect=/guest/capture` · 埋点 `anon_guest_register_cta` |
| TC-LF-I9 | Mockup-diff (B 机制) | `pnpm e2e:mockup-diff -- --grep P-GUEST-CAPTURE` | diff ≤ 5% · 三联 PNG 落 reports |
| TC-LF-I10 | VRT 多 viewport (A 机制) | `pnpm e2e:vrt-multi-check -- --grep P-GUEST-CAPTURE` | 4 viewport 各 ≤ 1% |
| TC-LF-I11 | Vision review (C 机制) | 派 design-reviewer · page_id=P-GUEST-CAPTURE | verdict=PASS · 0 blocker |
| TC-LF-I12 | A11y + reduced-motion + 触摸目标 ≥ 44px (AC-GUEST-008 + spec §12) | axe scan + emulateMedia reduce + box size check | axe 0 serious · 快门/chip click 区域 ≥ 44×44 · 取景动画无入场 stagger |

### Phase J · P00 登录页 (12 条 · 注意 archive 缺失 · B 机制 skip)

| TC | Targets | Method | Pass criterion |
|---|---|---|---|
| TC-LF-J1 | 进入 hero 显示 (AC-P00-001) | UI: open `/auth` → 等 `p00-logo-zone` | logo 64px · Mood A 深蓝渐变 hero 可见 |
| TC-LF-J2 | 微信按钮 iron-rule-1-exception (AC-P00-002) | UI: read attr + computed bg color | `data-iron-rule-1-exception="wechat-brand"` 存在 · `background-color` = rgb(7, 193, 96) (#07C160) |
| TC-LF-J3 | 协议未勾选时按钮 disabled (AC-P00-003) | UI: 直接点 `p00-wechat-cta-btn` 不勾协议 | 按钮无响应 · 或弹 toast "请先同意协议" · 不发 wechat-login 请求 |
| TC-LF-J4 | 勾协议后按钮启用 | UI: click `p00-consent-bar-checkbox` → 点微信按钮 | 发出 `/api/auth/wechat-login` POST · 埋点 `auth_wechat_start` |
| TC-LF-J5 | 微信登录成功 + claim 跳转 (AC-P00-005) | MSW mock wechat-login 成功 · session 存 guest_session_id | 跳 `/` (P-HOME) · `auth_wechat_success` + `anon_guest_claim_success` 两埋点 |
| TC-LF-J6 | 微信授权失败 toast (spec §9) | MSW mock 401 | toast 显示 · 留在 `/auth` · `auth_wechat_fail` 埋点 |
| TC-LF-J7 | redirect 参数生效 (AC-P00-006) | UI: open `/auth?redirect=/guest/capture` → 完成登录 | 登录后跳 `/guest/capture` 而非 `/` |
| TC-LF-J8 | 协议链接外跳 (spec §7) | UI: click `p00-consent-bar-link-tos` + `-link-privacy` | target=_blank 或路由到 `/legal/tos` · `/legal/privacy` |
| TC-LF-J9 | dev-only 账密表 (fixture 复用) | UI: click `p00-other-methods-link` → 填表 → submit | `auth-dev-form` 展开 · `/api/auth/dev-login` 发出 · 离开 `/auth` |
| TC-LF-J10 | VRT 多 viewport (A 机制 · P00 唯一视觉守门) | `pnpm e2e:vrt-multi-check -- --grep P00` | 4 viewport 各 ≤ 1% (B 机制 skip · 仅靠 VRT) |
| TC-LF-J11 | Vision review (C 机制) | 派 design-reviewer · page_id=P00 | verdict=PASS · spec/STYLE-TRUTH §6 一致性 |
| TC-LF-J12 | A11y · 协议勾选键盘可达 (AC-P00-008) | Tab → space toggle | checkbox 可 focus · space 切换 state · screen reader 朗读"已同意/未同意" |

### Phase K · 跨页漏斗串联 (5 条 · 真实用户漏斗)

| TC | Targets | Method | Pass criterion |
|---|---|---|---|
| TC-LF-K1 | 完整漏斗 anonymous → guest → result | UI: `/welcome` → click try → `/guest/capture` → 选学科+拍 → 等结果 | 全程不 reload · 单 session 完成 · 4 个埋点串成漏斗 (`anon_landing_view` → `_cta_try` → `anon_guest_capture_view` → `_capture_shoot`) |
| TC-LF-K2 | 完整漏斗 anonymous → login → home | UI: `/welcome` → click 登录 → `/auth` → 走 dev 账密 → `/` | 跳到 P-HOME · session 升级 anonymous→authenticated · `auth_wechat_start` 不触发 (走 dev) |
| TC-LF-K3 | guest claim → 登录后续期 | UI: `/welcome` → guest 拍一题 → 注册 CTA → `/auth` → 完成登录 | guest_session_id 被 claim · prev guest 题目挂到新 user · `anon_guest_claim_success` 埋点带 `guest_session_id` |
| TC-LF-K4 | quota 耗尽逼迫注册 | UI: header `x-e2e-quota-out=1` → 进 `/guest/capture` → 点挡板 CTA | 跳 `/auth?redirect=/guest/capture` · 完成登录后回到 `/guest/capture` · quota 重置 |
| TC-LF-K5 | 浏览器返回 + session 保持 | UI: K1 走完后 `page.goBack()` 两次 → 再前进 | 不丢 session · 不重复触发埋点 view (去重) · URL 状态正确 |

**统计**：64 条 TC,  覆盖：
- P-LANDING spec §8 (8/8 AC) · §9 (6/6 异常) · §10 (6/6 埋点) · §11 (4/5 性能) · §12 (4/4 a11y) · §15 chrome
- P-GUEST-CAPTURE spec §8 (8/8 AC) · §9 (6/6 异常) · §10 (6/6 埋点) · §12 a11y · §15 chrome
- P00 spec §8 (8/8 AC) · §9 (5/5 异常) · §10 (4/4 埋点) · §12 a11y · STYLE-TRUTH §6 设计建议
- 跨页 5 条覆盖完整漏斗
- 漏：`AC-LANDING-007` 转化率 (需 prod RUM · 标 N/A)

---

## §3 对抗式修复协议 (Adversarial Workflow)

```
QA Orchestrator (本会话)
    │
    ├─ Round 1: 并行跑 Phase A-H 全部 35 TC
    │     → 收 fail 列表 (按 testid + assertion 归类)
    │
    ├─ 对每个 fail · 写 BUG 报告 (export 目录 · 见 §4)
    │     → 派 Dev Sub-Agent 修复 (并行 worktree · 见 §5)
    │     → Sub-Agent return commit hash + verify result
    │
    ├─ Trust-but-Verify: QA 自跑 fail TC + 关联 TC 回归
    │     → PASS · 关 BUG · 进下轮
    │     → FAIL · BUG status=NEEDS_REVERIFY + 第 N+1 轮 dispatch
    │
    └─ 终止条件:
          ✅ 全 35 TC 绿 · 任意一轮全绿 → 出 final report
          ⛔ 5 轮内 stuck (相同 fail 计数无下降) → escalate to user
          ⛔ 任一 sub-agent 报 spec/mockup 冲突 → ASK USER 决策
```

**Sub-Agent 类型选择**：
- 单 testid / 单 assertion 修复 → 派 `general-purpose` (一次性 fix)
- 涉及视觉 / 多 viewport / chrome 边界 → 派 `page-fixer` (现成 sub-agent · 自带 mockup-diff + vrt-multi 自检)
- 整轮调度（多页 fan-out）→ 派 `design-audit-orchestrator` (现成 · 内嵌 page-fixer 并行 worktree)

**禁止**：QA 自己改实现代码（只 review · 只验收 · 跨身份污染会让审计失效）。

---

## §4 测试资产沉淀目录 (移植自 safar-server qa pattern)

> **位置**：`{repo_root}/export/qa/welcome-landing-e2e/` (待 §6 user 确认前缀 + 位置)

```
export/qa/welcome-landing-e2e/
├── 00-test-plan.md              # 本计划文件的执行版（commit 时同步）
├── 01-trajectory.md             # append-only 轨迹 (Time | Bug | Actor | Event)
├── 02-e2e-report.md             # 每轮执行汇总
├── manual-test-guide.md         # 手工测试指南（中文步骤）
│
├── BUG-LF-NN-{slug}.md          # bug 报告（待确认前缀 LF/WB/LW）
│   └─ 章节: Status / Severity / Spec ref / Reproduction / Expected /
│            Actual / Root cause / Handoff to Dev Agent /
│            QA Verification Log (轮次表)
│
├── runs/                        # gitignore · 每次执行的产物
│   ├── TC-LF-A1/                # 每个 TC 一个目录: screenshot.png + trace.zip + network.har
│   ├── ...
│   ├── seed-fixtures.json       # MSW handler 快照
│   └── SAMPLES/                 # 异常态降级文案样例
│
└── scripts/                     # 测试资产 · 永久保留 · gitignore 不管
    ├── package.json
    ├── lib.mjs                  # 共享工具 (login / open landing / capture)
    ├── seed.mjs                 # MSW + clock 数据准备 (幂等)
    ├── tc-lf-a1.mjs ~ tc-lf-h3.mjs  # 一 TC 一 driver
    ├── probe-perf.mjs           # Phase F 性能 probe (PerformanceObserver)
    ├── probe-a11y.mjs           # Phase E axe scan
    └── _verify-bug-NN.mjs       # 单 bug 修复验证脚本（Dev sub-agent 跑过后 QA 复跑）
```

**Bug 编号约定**（待 §6 user 确认前缀）：
- 格式：`BUG-{prefix}-{NN}-{slug}.md`
- Severity 三级：P0 (FR/NFR/安全 · 阻塞) / P1 (边界/可见 · 循环内修) / P2 (美观/非阻塞 · 后续)

**TC 与既有 e2e/specs/ 关系**：
- `e2e/specs/sc-11.spec.ts` 现有 3 test 全部保留并扩到 35 条新增的 TC（合并到 `sc-11.spec.ts` 还是新建 `sc-11-extended.spec.ts` 待执行时定 · 倾向后者保持 sprint 主 spec 不动）
- 新增 spec 文件 `e2e/specs/sc-11-extended.spec.ts` + 扩展 `e2e/pages/LandingPage.ts` 加 25+ 新方法
- 性能 / a11y / 埋点 driver 单独沉淀到 `export/qa/welcome-landing-e2e/scripts/`，不污染 `e2e/` 主 sprint 目录

---

## §5 Worktree 隔离协议

| 角色 | Worktree | 分支命名 | 完成后操作 |
|---|---|---|---|
| QA Orchestrator (本会话) | 不开 worktree · 在主 repo 内只读跑测试 + 写 export 资产 | — | 修复完所有 bug 后，把 `export/qa/welcome-landing-e2e/` 的资产 commit 进 `feature/s7-frontend-core` |
| Dev Sub-Agent (per bug) | `git worktree add .claude/worktrees/qa-welcome-bug-NN feature/s7-frontend-core` | `qa/welcome-bug-NN` (从 `feature/s7-frontend-core` 切) | sub-agent commit 后，QA 验收 PASS → merge 回 `feature/s7-frontend-core` (fast-forward 或 squash) → 删 worktree |

**严禁**：
- ❌ 触碰任何其他分支 (main / S7 之外的 feature/*)
- ❌ 用 `--no-verify` 跳过 hook
- ❌ force push 到 `feature/s7-frontend-core`
- ❌ 在主 repo 直接 commit 修复 (必须走 worktree)

**Author**：所有 commit `Author: zhe.wang <...>` (与当前 git config 一致 · 无需改动)。

---

## §6 待 user 决策的 3 个 placeholder

将在 `AskUserQuestion` 中确认：
1. **Bug 编号前缀**：`BUG-LF-NN`(LongFeng) / `BUG-WB-NN`(WrongBook) / 其他？
2. **资产沉淀目录**：`export/qa/welcome-landing-e2e/`(模仿 safar pattern · 新建顶层 `export/`) / `e2e/qa-export/welcome-landing-e2e/`(挂在 e2e workspace 下) / 其他？
3. **欢迎页范围**：仅 P-LANDING (`/welcome`) / P-LANDING + P00 登录 (`/auth`) (中文"欢迎页面"语义模糊) / 仅 P-LANDING 但漏斗终点验到 P-GUEST-CAPTURE 落地为止？

---

## §7 启动前置协议（per user role prompt）

✅ Phase 1 (Initial Understanding) — 已完成 (3 Explore agent 平行)
✅ Phase 2 (Test Cases 起草) — 本文 §2
⏳ Phase 3 (User Review) — **待 user 明确回复"用例通过，准许执行"**
⏳ Phase 4 (Worktree + 实施) — 仅在 §3 完成后启动
⏳ Phase 5 (多轮对抗修复) — 见 §3
⏳ Phase 6 (Final report + commit 资产) — 收尾

---

## §8 Verification (Sprint 末验收 · 与 ExitPlanMode 无关)

任意时刻 user 可独立复跑：

```bash
# 1) 启动 dev (终端 1 · 一次性)
cd frontend/apps/h5 && pnpm dev

# 2) 跑全部 P-LANDING E2E (终端 2)
cd e2e
pnpm e2e:mock-b -- --grep "@sc-11"          # Phase A-C+E+H · 功能+a11y+边界
pnpm e2e:mockup-diff -- --grep P-LANDING    # Phase D1
pnpm e2e:vrt-multi-check -- --grep P-LANDING # Phase D3
node export/qa/welcome-landing-e2e/scripts/probe-perf.mjs  # Phase F
# Phase D4 vision review: 派 design-reviewer agent

# 3) 看报告
pnpm report  # 打开 reports/html/index.html
```

预期：35 TC 全绿 · mockup-diff ≤ 5% · vrt-multi 4 viewport 都 ≤ 1% · design-reviewer verdict=PASS · perf 4 项达标 · axe 无 serious。

---

## §9 关键文件清单（实施时改这些）

**新增**：
- `e2e/specs/sc-11-extended.spec.ts` — 32 条新 TC (Phase A2-H3 · 不含已有 sc-11 三条)
- `export/qa/welcome-landing-e2e/00-test-plan.md` — 本计划执行版
- `export/qa/welcome-landing-e2e/01-trajectory.md` — append-only 轨迹
- `export/qa/welcome-landing-e2e/scripts/*.mjs` — 性能 / a11y / 埋点 probe + 各 TC driver

**扩展**：
- `e2e/pages/LandingPage.ts` — 加 25+ 新断言方法 (assertCtaTryStyle / assertSampleSubjectColor / assertTtiUnder / assertTabOrder / assertReducedMotionRespected / 等)
- `e2e/fixtures/guest.ts` — 加 GDPR header spoof + offline mode helper

**只读**（参考 · 不改）：
- `design/system/pages/P-LANDING.spec.md` (15 段 spec)
- `design/mockups/wrongbook/_archive/14_landing.html` (mockup 权威)
- `design/system/STYLE-TRUTH.md` (token 真相)
- `frontend/apps/h5/src/pages/Landing/index.tsx` (实现 · 仅 sub-agent 在 worktree 改)
- `.claude/agents/{design-reviewer,page-fixer,design-audit-orchestrator}.md` (sub-agent 定义 · 直接调用)

---

> 以上为草案 v1。待 user (1) 回答 §6 三个 placeholder · (2) 在 ExitPlanMode 时确认"用例通过，准许执行" · 才进入 Phase 4 实施。
