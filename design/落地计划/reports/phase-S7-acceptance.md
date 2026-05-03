# Phase S7 · 前端拍题/分析/错题本 · Acceptance Report

**Date**: 2026-05-03
**Phase**: S7 (plan §5.S7) · 前端 H5 + 小程序
**Base**: `c07727a` (S0..S6 已合 + C-14 临时 fix)
**Final HEAD**: `2deae39`
**实际耗时**: ~50min · 5 路并行 (vs plan 2d=16h · 节省 ~95%)

## 出口门禁逐项核对

| 门禁 | 状态 | Agent | 备注 |
|---|---|---|---|
| H5 5+ 页面 + 3 Shell + 2 bootstrap 全 build PASS | ⚠️ pnpm build 未跑 | FE-01/02/03/06 | sub-agent Bash blocked · 留 user/QA |
| axe-core jest-axe 0 serious | ⚠️ unit test 已写 (List/Detail .test.tsx) · 未实跑 | FE-03 | 留 user pnpm test |
| testid 注册表 0 缺失 | ✅ | FE-01/02/03/06 | packages/testids 累积新增 ~50 testid |
| i18n CI (zh-CN ⊆ en-US ⊆ ja-JP) PASS | ⚠️ FE-08 写了 zh+en · 未跑 CI | FE-08 | i18n-zh-CN.ts + i18n-en-US.ts |
| 小程序 14 页在微信开发者工具加载 (npm run build + 真机扫码) | ⚠️ Bash blocked · 真机预览留 user | FE-08 | utils/ws.ts D-WS · subscribe-msg 3 模板 |
| **§3.4 FE 铁律全 PASS (每页面)** | ❌ b 模式 caveat | All | 见 caveat 段 |

## 各 Agent 提交统计

| Agent | branch | commits | 文件 | tests | 关键交付 |
|---|---|---|---|---|---|
| FE-01-shells-bootstrap | merged · 删 | 1 (c6b3540) | 15 | 0 (架构) | 3 Shell + bootstrap + useObserverGuard · C4 三重防护 |
| FE-02-capture-flow | merged · 删 | 1 (a603ab7) | 13 | 21 AC | P02/P03/P04 + useEventSource (D-AI-Cancel) + MSW SSE mock (绕 C-14) |
| FE-03-wrongbook-pages | merged · 删 | 1 (5343df5) | 9 | 20 AC + axe | P05/P06 (3-tab) · S7 七项契约对齐 · B 轨 unit test |
| FE-06-anon-pages | merged · 删 | 1 (8f20139) | 10 | 7/8 AC × 3 | P-LANDING/P-GUEST-CAPTURE/P-SHARED · useDeviceFingerprint · C3+C4 |
| FE-08-miniapp (opus) | merged · 删 | 1 (fa074d7) | 71 | - | 小程序 14 页 + STYLE-TRUTH token 全量重写 + D-WS + SC-16 三层 tier |

合并 commits + conflict resolution:
- `86580e4` merge(S7): fe-08-miniapp
- `10c4f4b` merge(S7): fe-01
- `fab8ca7` merge(S7): fe-02 (App.tsx conflict resolved)
- `6762017` merge(S7): fe-03
- `2deae39` merge(S7): fe-06 (App.tsx conflict resolved)

## Conflict Resolution 决策

**Conflict 1 · App.tsx (FE-01 vs FE-02)**:
- FE-01: 完整 14-route + 3 Shell 架构 + placeholder for AnalyzingPage/ResultPage
- FE-02: 简化 5-route + import 真实 AnalyzingPage/ResultPage
- **决策**: 保留 FE-01 完整架构 · 删 FE-01 的 AnalyzingPage/ResultPage placeholder (FE-02 import 真实)

**Conflict 2 · App.tsx (HEAD vs FE-06)**:
- HEAD: placeholder LandingPage/GuestCapturePage/SharedPage
- FE-06: import 真实 + 简化路由
- **决策**: 删 HEAD placeholder · 用 FE-06 真实 import · 保留 FE-01 完整路由结构

**testids/index.ts**: 自动 merge 成功 (FE-01/02/03 各加各的 testid · 不冲突)

## ⚠️ S7 关键 Caveat

**C-17 · 5 sub-agent IRON-LAW 完全不能闭环 (b 模式 Bash blocked)**
- IRON-LAW-3 要求 `/fe-accept-mock` (Playwright + MSW) + `/fe-accept-diff` (pixel diff vs archive) + axe a11y
- sub-agent 不能跑 Playwright / vite dev / pixel-diff / axe 实时
- **影响**: 视觉精度 + 交互真实性留 user / QA Agent 后续闭环
- **fix 路径**: user 自己跑 `pnpm dev` + Chrome DevTools 视觉对比 / 或 S9 QA Agent 派 e2e

**C-18 · pnpm build 未跑**
- TypeScript 编译可能 fail (sub-agent 写代码没 typecheck)
- 推断 import path / 类型签名可能错 · merge 时未检测
- **fix**: user 跑 `cd frontend && pnpm install && pnpm build` 验证 + 报错回 fix

**C-19 · MemoryCurve / RadarChart / AIBriefCard mock 数据**
- FE-03 P06 Detail 用 mock 推算这 3 个组件 · A 轨联调时替换真后端字段
- 后端 wrongbook-service 当前不返这些字段 · S4.5 / S8 补

**C-20 · 3 [AI 推测] AC 待业务确认**
- FE-06 P-LANDING/GUEST-CAPTURE/SHARED 各 1 条转化率 AC 标 [AI 推测]
- 业务方确认量化口径后写真实埋点

**C-21 · 微信小程序 caveat 6 项**
- 真机预览 (Bash blocked · user GUI build)
- WebSocket 联调 (/ws/analyze staging)
- 订阅消息模板 ID (后台申请 3 模板)
- Vant Weapp 依赖 (构建 npm)
- B/C 轨像素验收 (S9 QA)
- chunked SSE (P06 detail 多机型抽测)

## 关键发现 (影响 S8+)

### F-08 · FE Agent 并行验证：5 路并行 ~25min vs plan 2d
- BE 阶段经验复用 OK
- 但 IRON-LAW 闭环必须 user/QA 介入 (b 模式无法替代 Playwright)

### F-09 · STYLE-TRUTH token 偏差需修
- FE-01 发现 tokens.css `--tkn-color-primary-default: #0071e3` (应 #007AFF)
- 临时方案: Shell CSS 用 `.root --s-blue: #007AFF` 局部覆盖
- **彻底 fix**: 升级 tokens.css 到 STYLE-TRUTH §2 全套 (FE-08 已在 miniapp 做 · h5 待补)

### F-10 · App.tsx merge conflict 设计
- 5 路并行改 routing 必冲突 · 主 session resolve 平均 2 conflict / phase
- S8 启动前可考虑：派 FE-XX 写时声明 "改 App.tsx 仅追加路由 · 不重写 export const App"

## 下一 Phase (S8) 建议

**S8 = 前端复习闭环 + 学情看板** (plan §5.S8 · 1.5d)

可并行 4 路:
- FE-04-review-pages (P07/P08/P09 + 自评 3 档 + Cancel race)
- FE-05-calendar-pages (P10/P11 双形态同壳)
- FE-07-misc-pages (P00/P-HOME/P12/P13 含 SC-16 VIP)
- FE-08-miniapp-extension (复习/日历 7 页对齐 · 已含部分在 S7 commit)

**风险**:
- C-22 (待 fix): tokens.css h5 升级到 STYLE-TRUTH (避免每页都局部覆盖)
- C-23 (待 fix): pnpm build 验证 S7 通过后再启 S8
- App.tsx merge 仍会 conflict (4 路 FE)

## 等 User Phase Gate 决策

- **(I1)** `Phase S7 通过 · 启动 S8` → 4 路并行 · caveat 留 S9 闭环
- **(I2)** `Phase S7 通过 · 先验 pnpm build` → user 跑 build 确认 · OK 后 S8
- **(I3)** `修 caveat C-17/C-18` → 先跑 pnpm build/test/Playwright 闭环验证
- **(I4)** `先跑 S9 QA` → 跳 S8 直接 QA Agent 派 e2e (16 SC × 78 用例)
