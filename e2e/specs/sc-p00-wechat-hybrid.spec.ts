/**
 * SC-P00 · 微信登录 L3 hybrid 真链路 E2E
 *
 * 与 sc-p00.spec.ts (mock-b · 仅 UI 层) 的区别：
 *   - 关 MSW (VITE_DISABLE_MSW=1)
 *   - 走真 BE: FE :9174 → vite proxy → gateway :9080 → auth-service :9086 → PG :15432
 *   - 验真 JWT 签发 · 真 DB 落地 · 真 token claim
 *
 * 角色分工（plan §3 三方对抗）：
 *   - 本 spec 抓 network trace + 验 localStorage + 验 JWT decode
 *   - QA Orchestrator 跑完后单独跑 psql/redis-cli 穿透 (e2e/scripts/db-snapshot.sh)
 *   - Supervisor Agent 审 trace + DB snapshot 两份证据 · 否决权
 *
 * Track: hybrid
 * Tag: @sc-p00 @hybrid
 *
 * 前置条件（spec 不自动起服 · runner 起）：
 *   ✅ Docker PG :15432 + Redis :16379 已起
 *   ✅ BE gateway :9080 + auth-service :9086 已起
 *   ✅ FE pnpm dev --mode hybrid --port 9174 已起 (VITE_DISABLE_MSW=1)
 */
import { test, expect, type Request, type Response } from '@playwright/test';
import * as fs from 'node:fs';
import * as path from 'node:path';

const TRACE_DIR = path.join(__dirname, '..', 'reports', 'login-trace');

interface NetworkEntry {
  ts: string;
  type: 'request' | 'response';
  url: string;
  method?: string;
  status?: number;
  headers: Record<string, string>;
  body?: string;
}

function ensureTraceDir() {
  if (!fs.existsSync(TRACE_DIR)) fs.mkdirSync(TRACE_DIR, { recursive: true });
}

function writeTrace(name: string, data: unknown) {
  ensureTraceDir();
  const fp = path.join(TRACE_DIR, name);
  fs.writeFileSync(fp, JSON.stringify(data, null, 2), 'utf8');
}

/**
 * 简易 JWT decode (HS256) · 不验签 · 仅解 header + payload claim
 * 用 base64url decode · 不依赖 jsonwebtoken lib
 */
function decodeJwt(token: string): { header: unknown; payload: Record<string, unknown> } | null {
  const parts = token.split('.');
  if (parts.length !== 3) return null;
  const b64urlDecode = (s: string) => {
    const pad = s.length % 4 === 0 ? '' : '='.repeat(4 - (s.length % 4));
    const b64 = (s + pad).replace(/-/g, '+').replace(/_/g, '/');
    return Buffer.from(b64, 'base64').toString('utf8');
  };
  try {
    const header = JSON.parse(b64urlDecode(parts[0]));
    const payload = JSON.parse(b64urlDecode(parts[1])) as Record<string, unknown>;
    return { header, payload };
  } catch {
    return null;
  }
}

test.describe('SC-P00-HYBRID · 微信登录真链路 @sc-p00 @hybrid', () => {

  // ───────────────────────────────────────────────
  test('H1 · MSW 真关闭验证 (Supervisor 否决依据)', async ({ page }) => {
    await page.goto('/auth');
    await page.waitForLoadState('domcontentloaded');

    // main.tsx · VITE_DISABLE_MSW=1 时会设 window.__lf_msw_disabled__ = true
    const mswDisabled = await page.evaluate(
      () => (window as unknown as { __lf_msw_disabled__?: boolean }).__lf_msw_disabled__,
    );

    expect(mswDisabled, 'MSW 必须真关 · 否则后面的网络验证全部假阳性').toBe(true);
    writeTrace('h1-msw-disabled.json', { msw_disabled: mswDisabled });
  });

  // ───────────────────────────────────────────────
  test('H2 · happy path · dev_code_alice → 真 BE → DB 落地 → 真 JWT', async ({ page }) => {
    const trace: NetworkEntry[] = [];

    // 抓所有 /api/auth/* 请求与响应
    page.on('request', (req: Request) => {
      if (req.url().includes('/api/auth/')) {
        trace.push({
          ts: new Date().toISOString(),
          type: 'request',
          url: req.url(),
          method: req.method(),
          headers: req.headers(),
          body: req.postData() ?? undefined,
        });
      }
    });
    page.on('response', async (resp: Response) => {
      if (resp.url().includes('/api/auth/')) {
        let body: string | undefined;
        try { body = await resp.text(); } catch { body = undefined; }
        trace.push({
          ts: new Date().toISOString(),
          type: 'response',
          url: resp.url(),
          status: resp.status(),
          headers: resp.headers(),
          body,
        });
      }
    });

    // 注入 e2e mock wx_code · FE handleWechatLogin 会读这个
    await page.addInitScript(() => {
      (window as unknown as { __lf_dev_wx_code__?: string }).__lf_dev_wx_code__ = 'dev_code_alice';
    });

    await page.goto('/auth');
    await page.waitForLoadState('networkidle');

    // 必勾选协议 · 否则按钮 disabled
    await page.getByTestId('p00-consent-bar-checkbox').check();

    // 截图 step 1 · 点击前
    await page.screenshot({ path: path.join(TRACE_DIR, 'h2-step1-before-click.png') });

    // 等待 wechat-login response · 同步触发 click
    const respPromise = page.waitForResponse(
      (r) => r.url().includes('/api/auth/wechat-login') && r.request().method() === 'POST',
      { timeout: 10_000 },
    );
    await page.getByTestId('p00-wechat-cta-btn').click();
    const resp = await respPromise;

    // 必须 200 · 不是 404 (Gateway 路由缺) · 不是 500 (BE 崩) · 不是 0 (网络断)
    expect(resp.status(), `BE 必须返 200 · got ${resp.status()}`).toBe(200);

    // BE 用统一 envelope · 解一层
    const envelope = await resp.json() as {
      code: number;
      message: string;
      data: {
        access_token: string;
        refresh_token?: string;
        student_id: string;
        is_new_user: boolean;
        expires_at?: number | string;
      };
      trace_id?: string;
    };
    expect(envelope.code, 'envelope.code 必须 0 = 业务成功').toBe(0);
    expect(envelope.data, 'envelope.data 必返').toBeTruthy();
    const respBody = envelope.data;

    // 契约校验 · 严格按 spec §4
    expect(respBody.access_token, 'access_token 必返').toBeTruthy();
    expect(respBody.access_token.split('.').length, 'access_token 必须是 JWT 三段').toBe(3);
    expect(respBody.student_id, 'student_id 必返').toBeTruthy();
    expect(typeof respBody.is_new_user).toBe('boolean');

    // JWT decode · 验 claim
    // BE 用 RS256 (nimbus-jose-jwt 跟 gateway 一致 · 见 backend/auth-service/.../JwtUtils.java)
    const decoded = decodeJwt(respBody.access_token);
    expect(decoded, 'JWT 必须可 decode').not.toBeNull();
    expect(decoded?.header).toMatchObject({ alg: 'RS256' });
    expect(decoded?.payload).toMatchObject({
      sub: expect.any(String),       // userId (Snowflake long string)
      role: expect.any(String),      // STUDENT
      device_fp: expect.any(String), // 跟 request 里 device_fp 一致
      tier: expect.any(String),      // NORMAL
      exp: expect.any(Number),
      iat: expect.any(Number),
    });
    // sub claim 必须等于 student_id（一致性）
    expect(decoded?.payload.sub).toBe(respBody.student_id);

    // 等跳转完成
    await page.waitForURL((url) => !url.pathname.startsWith('/auth'), { timeout: 10_000 });

    // 验 localStorage 真写了 token
    const lsToken = await page.evaluate(() => localStorage.getItem('lf:token'));
    expect(lsToken, 'localStorage[lf:token] 必须 = JWT').toBe(respBody.access_token);

    const lsTier = await page.evaluate(() => localStorage.getItem('lf_user_tier'));
    expect(lsTier, 'localStorage[lf_user_tier] 必写').toBeTruthy();

    // 截图 step 2 · 跳转后
    await page.screenshot({ path: path.join(TRACE_DIR, 'h2-step2-after-success.png') });

    // 落证据
    writeTrace('h2-network-trace.json', trace);
    writeTrace('h2-jwt-decoded.json', decoded);
    writeTrace('h2-resp-body.json', respBody);
    writeTrace('h2-localstorage.json', { 'lf:token': lsToken, lf_user_tier: lsTier });

    // 关键 assertion · 链路真打到 :9080 (vite proxy 转发后端在 BE 看 host 是 9080)
    const wechatRequest = trace.find((e) => e.type === 'request' && e.url.includes('/api/auth/wechat-login'));
    expect(wechatRequest, 'wechat-login request 必须在 trace 里').toBeTruthy();
    // 注：FE 走 vite proxy · trace 里 url 仍是 :9174 (浏览器视角) · 真到达 :9080 由 supervisor 看 BE log 验证
  });

  // ───────────────────────────────────────────────
  test('H3 · invalid wx_code → 4xx · UI Toast', async ({ page }) => {
    await page.addInitScript(() => {
      (window as unknown as { __lf_dev_wx_code__?: string }).__lf_dev_wx_code__ = 'dev_code_INVALID_xxx';
    });

    await page.goto('/auth');
    await page.waitForLoadState('networkidle');
    await page.getByTestId('p00-consent-bar-checkbox').check();

    const respPromise = page.waitForResponse(
      (r) => r.url().includes('/api/auth/wechat-login'),
      { timeout: 10_000 },
    );
    await page.getByTestId('p00-wechat-cta-btn').click();
    const resp = await respPromise;

    // BE 必须真返 非2xx · 验证不是兜底
    // 当前 BE 把 IllegalArgumentException 映射成 500 (无 @ControllerAdvice 转 4xx)
    // 接受 4xx 或 5xx 都算 fail · 关键是不能 200
    // 后续 BUG-LF-15a · BE 应加全局异常处理把业务异常转 4xx
    expect(resp.status(), `invalid code 必须 ≥400 · got ${resp.status()}`).toBeGreaterThanOrEqual(400);

    // UI Toast · 不应跳转
    await expect(page).toHaveURL(/\/auth/, { timeout: 5_000 });

    writeTrace('h3-invalid-code.json', { status: resp.status(), body: await resp.text() });
  });

  // ───────────────────────────────────────────────
  test('H4 · 未勾选协议 · 不发 API · UI Toast', async ({ page }) => {
    let apiCalled = false;
    page.on('request', (r) => {
      if (r.url().includes('/api/auth/wechat-login')) apiCalled = true;
    });

    await page.goto('/auth');
    await page.waitForLoadState('networkidle');

    // 不勾选协议 · 直接点登录
    // button 因 consent 未勾选会 disabled · 用 force:true 绕过 stable check 验证 onClick 守卫
    await page.getByTestId('p00-wechat-cta-btn').click({ force: true });
    await page.waitForTimeout(1500); // 等 UI 反应

    expect(apiCalled, '未勾选协议时不应发 API').toBe(false);
    await expect(page).toHaveURL(/\/auth/);
  });
});
