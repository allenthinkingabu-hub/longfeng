**Status:** OPEN
**Severity:** P1 (用户可见 + AC 阻塞 + 影响转化漏斗 KPI)
**Spec ref:** P-LANDING.spec.md §3 (B5 block) + §8 AC-LANDING-005 + §10 (埋点 anon_landing_view 含 kpi 渲染)
**Discovered in:** Round 0 baseline · TC = `e2e/specs/sc-11.spec.ts:17` happy path
**Filed by:** QA-Orchestrator (this session)

## Summary

P-LANDING (`/welcome`) 实现里**完全缺失** B5 KPI banner block —— spec 要求显示"已分析 100w+ 错题 · 7 日留存 47%"，但实现 `frontend/apps/h5/src/pages/Landing/index.tsx` 里查不到 `landing-kpi` / `landing-kpi-total` / `landing-kpi-retention` 任何 testid，整个 B5 区段不渲染。

## Reproduction

```bash
cd /Users/allenwang/build/longfeng-wrongbook/e2e
E2E_TRACK=mock-b BASE_URL=http://localhost:5173 npx playwright test --grep @sc-11 --reporter=list
```

输出：
```
✘  1 [h5-iphone-15-pro] › specs/sc-11.spec.ts:17:7 › SC-11 · 访客落地页 @sc-11 › happy path · 双 CTA + warm 区段 @smoke (8.2s)
   Locator: getByTestId('landing-kpi')
   Expected: visible
   Error: element(s) not found
```

静态确认（实现里所有 landing-* testid）：
```bash
grep -n "data-testid" frontend/apps/h5/src/pages/Landing/index.tsx | grep landing
# 结果只有: landing-page · -hero · -hero-logo · -hero-cta-login · -hero-headline ·
#           -samples · -samples-card-{1..3} · -three-step · -cta-bottom{,-btn} · -hero-cta-try
# 缺失: landing-kpi · landing-kpi-total · landing-kpi-retention
```

## Expected

依据 P-LANDING.spec.md：

§3 Block 清单 B5：
> | B5 | 价值数字 banner | info | warm | (Card + 大数字) | `landing-kpi` | `--tkn-color-encouragement-soft` / `--tkn-color-encouragement-DEFAULT` / `--tkn-type-display-hero` |

§8 AC 表 AC-LANDING-005：
> KPI banner 显示 "已分析 100w+ 错题" · 7 日留存数字渲染
> testid 验证点: `landing-kpi-total` 文本包含 "100w" · `landing-kpi-retention` 包含 "47%"

§4 数据契约：
```typescript
kpi: {
  totalQuestionsAnalyzed: number;   // 1_080_000
  retention7d: number;               // 0.47
  headline: string;                  // "已分析 100w+ 错题"
};
```

§5 API：
- GET `/api/landing/kpi` — 社区脱敏数据 · P95 200ms · CDN 强缓存 · 失败时 §6 状态机 DEGRADED 隐藏 B5 banner

## Actual

- B5 整块未实现 (无 `<section data-testid="landing-kpi">`)
- 推断未调 `/api/landing/kpi` 接口 (待 sub-agent 验证 network 层是否真发请求)
- §6 状态机 DEGRADED 路径无意义（因为 READY 路径都没渲染该 block）

## Root cause

`frontend/apps/h5/src/pages/Landing/index.tsx` lines 280-419 之间，完成了 B4 samples + B3 three-step + B6 cta-bottom，但跳过了 B5 KPI banner 的实现。

可能原因（待 sub-agent 确认）：
- spec 后期增补 B5，FE 没补
- B5 是 v2 新增 block，旧版 mockup `_archive/14_landing.html` 可能没有
- 三步漫画 + 样例卡之间的一段空白预留给 B5 但忘了填

## Handoff to Dev Agent (page-fixer 或 general-purpose)

### Required changes

1. 在 `frontend/apps/h5/src/pages/Landing/index.tsx` 三步漫画 (line 357 后) 与 social proof (line 361 之前) 之间插入 B5：
   ```tsx
   {kpi && (
     <section
       data-testid="landing-kpi"
       aria-label="社区数据"
       className={s.kpi}
     >
       <div data-testid="landing-kpi-total" className={s.kpiTotal}>
         {kpi.headline /* "已分析 100w+ 错题" */}
       </div>
       <div data-testid="landing-kpi-retention" className={s.kpiRetention}>
         7 日留存 {Math.round(kpi.retention7d * 100)}%
       </div>
     </section>
   )}
   ```

2. 在 `Landing.module.css` 加 `.kpi` / `.kpiTotal` / `.kpiRetention` 样式 — 用 token：
   - background: `--tkn-color-encouragement-soft`
   - color (total): `--tkn-color-encouragement-DEFAULT`
   - typography (total): `--tkn-type-display-hero`
   - radius: `--tkn-radius-lg`

3. 加 `useLandingKpi` data hook (或扩 existing landing data hook) 调 `GET /api/landing/kpi` —— mock-b 轨需要在 `frontend/apps/h5/src/__mocks__/handlers/landing.ts` (或类似文件) 加 handler 返：
   ```json
   { "totalQuestionsAnalyzed": 1080000, "retention7d": 0.47, "headline": "已分析 100w+ 错题" }
   ```

4. §6 DEGRADED 态：`/api/landing/kpi` 失败时 `kpi=null` → B5 不渲染 (上面 `kpi && (...)` 已覆盖)

### Required test (TDD - RED first)

跑现在的 `sc-11.spec.ts:17` happy path 必须 PASS：
```bash
E2E_TRACK=mock-b BASE_URL=http://localhost:5173 npx playwright test --grep "happy path · 双 CTA + warm 区段"
```

外加：扩展验 AC-LANDING-005 完整断言（在 `LandingPage.ts` 加方法）：
```typescript
async assertKpiVisibleWithCorrectText() {
  const kpi = this.byTestId('landing-kpi');
  await expect(kpi).toBeVisible();
  await expect(this.byTestId('landing-kpi-total')).toContainText(/100w|百万/);
  await expect(this.byTestId('landing-kpi-retention')).toContainText(/47/);
}
```

### Constraints

- ❌ 不要硬编码 hex 色 / px 值（per CLAUDE.md design 铁律）— 全走 `--tkn-*` token
- ❌ 不要触碰其他 spec block (B2/B3/B4/B6) · 范围严格限于 B5
- ❌ 不要修 mockup HTML (mockup 是真相)
- ✅ 在 worktree 内修：`git worktree add .claude/worktrees/qa-welcome-bug-01 feature/s7-frontend-core`
- ✅ 提交 author = `zhe.wang`（与当前 git config 一致）
- ✅ commit message 格式：`fix(s7/p-landing/bug-lf-01): add B5 KPI banner per spec §3+§8 AC-LANDING-005`

### Return contract

Return 给 QA：
- worktree 绝对路径
- branch 名（`qa/welcome-bug-01` 从 `feature/s7-frontend-core` 切）
- commit hash
- 改动文件清单
- 自跑 sc-11 happy path 的 last 10 行输出（要求 PASS）

## QA Verification Log

### Round 0 — discovered (2026-05-04 18:18)
- 发现于 baseline sc-11 happy path
- 待 dispatch sub-agent
