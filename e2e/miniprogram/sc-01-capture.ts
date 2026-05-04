// SC-01 · 拍题 → 入库 → 首节点（miniapp 版 · 对齐 H5 e2e/specs/sc-01.spec.ts）
//
// 转译要点：
//   - H5 page.goto + URL 路径 → mp.reLaunch + miniapp page path
//   - H5 testid 选择器 → miniapp class/id 选择器（automator 不支持 data-testid）
//   - H5 setExtraHTTPHeaders → wx.request 拦截需 Mock 模式 OR 真后端 inject 测试 token
//   - H5 file upload 真照片 → mp.mockWxMethod('chooseMedia', ...)
//   - axe a11y → 跳过（miniapp 无标准）
//
// 真后端依赖：5 service UP · qa-normal user (id=9100001) seeded
import { launch } from './launcher';

const QA_NORMAL_TOKEN = process.env.QA_NORMAL_TOKEN || 'dev-jwt-9100001-NORMAL';

interface TestResult { name: string; ok: boolean; reason?: string }

async function testHappyPath(): Promise<TestResult> {
  const name = 'SC-01 · happy path 拍照→SSE→保存→列表+1';
  try {
    const mp = await launch();
    // 注入 token
    await mp.evaluate(function (token: string) {
      // @ts-ignore wx 全局
      wx.setStorageSync('lf:token', token);
    }, [QA_NORMAL_TOKEN]);

    // 1) 列表初始 count
    let p = await mp.reLaunch('/pages/wrongbook/list/list');
    await p.waitFor(1000);
    const beforeItems = await p.$$('.list-item');
    const before = beforeItems.length;

    // 2) 进 P02 capture
    p = await mp.reLaunch('/pages/camera/capture/capture');
    await p.waitFor('.capture-root', 5000);

    // 3) Mock chooseMedia
    await mp.mockWxMethod('chooseMedia', {
      tempFiles: [{ tempFilePath: '/tmp/sample.jpg', size: 1024 }],
    });

    // 4) 选学科 + 触发拍照
    const mathBtn = await p.$('.subject-math');
    if (mathBtn) await mathBtn.tap();
    const shutter = await p.$('.shutter-button');
    if (!shutter) return { name, ok: false, reason: '.shutter-button 未找到' };
    await shutter.tap();

    // 5) 等 P03 analyzing（SSE 进度）
    await p.waitFor(3000);
    const cur = await mp.currentPage();
    if (!cur.path.includes('analyzing')) {
      return { name, ok: false, reason: `期望 analyzing · 实际 ${cur.path}` };
    }

    // 6) 等 SSE 4 step + 跳 P04
    await p.waitFor(15000);
    const cur2 = await mp.currentPage();
    if (!cur2.path.includes('result')) {
      return { name, ok: false, reason: `期望 result · 实际 ${cur2.path}` };
    }

    // 7) 点保存
    const saveBtn = await cur2.$('.save-button');
    if (saveBtn) await saveBtn.tap();
    await cur2.waitFor(2000);

    // 8) 列表 +1
    p = await mp.reLaunch('/pages/wrongbook/list/list');
    await p.waitFor(1500);
    const afterItems = await p.$$('.list-item');
    const after = afterItems.length;

    if (after !== before + 1) {
      return { name, ok: false, reason: `列表 expected ${before + 1} · actual ${after}` };
    }
    return { name, ok: true };
  } catch (e) {
    return { name, ok: false, reason: (e as Error).message };
  }
}

async function testCancelMidway(): Promise<TestResult> {
  const name = 'SC-01 · cancel · SSE 中途退出 → 回 P02';
  try {
    const mp = await launch();
    let p = await mp.reLaunch('/pages/camera/capture/capture');
    await p.waitFor('.capture-root', 5000);

    await mp.mockWxMethod('chooseMedia', { tempFiles: [{ tempFilePath: '/tmp/s.jpg', size: 1024 }] });
    const shutter = await p.$('.shutter-button');
    if (shutter) await shutter.tap();

    await p.waitFor(2000);
    const analyzing = await mp.currentPage();

    const cancelBtn = await analyzing.$('.cancel-button');
    if (!cancelBtn) return { name, ok: false, reason: 'analyzing .cancel-button 未找到' };
    await cancelBtn.tap();
    await analyzing.waitFor(1500);

    // 弹确认
    const confirmBtn = await mp.currentPage().then((page) => page.$('.confirm-cancel'));
    if (confirmBtn) await confirmBtn.tap();
    await analyzing.waitFor(1500);

    const back = await mp.currentPage();
    if (!back.path.includes('capture')) {
      return { name, ok: false, reason: `期望 capture · 实际 ${back.path}` };
    }
    return { name, ok: true };
  } catch (e) {
    return { name, ok: false, reason: (e as Error).message };
  }
}

async function testPresignFail(): Promise<TestResult> {
  const name = 'SC-01 · 异常 · presign 失败 → banner';
  try {
    const mp = await launch();
    // mock presign 拒绝（注入 storage 标记 · capture page 应读后走 fail 分支）
    await mp.evaluate(function () {
      // @ts-ignore
      wx.setStorageSync('e2e:fail-presign', '1');
    });

    let p = await mp.reLaunch('/pages/camera/capture/capture');
    await p.waitFor('.capture-root', 5000);
    await mp.mockWxMethod('chooseMedia', { tempFiles: [{ tempFilePath: '/tmp/s.jpg', size: 1024 }] });

    const shutter = await p.$('.shutter-button');
    if (shutter) await shutter.tap();
    await p.waitFor(3000);

    const banner = await p.$('.error-banner');
    if (!banner) return { name, ok: false, reason: '.error-banner 未显示' };

    const cur = await mp.currentPage();
    if (cur.path.includes('analyzing')) {
      return { name, ok: false, reason: '不应跳 analyzing' };
    }

    // 清理
    await mp.evaluate(function () {
      // @ts-ignore
      wx.removeStorageSync('e2e:fail-presign');
    });
    return { name, ok: true };
  } catch (e) {
    return { name, ok: false, reason: (e as Error).message };
  }
}

export async function run() {
  const results: TestResult[] = [];
  results.push(await testHappyPath());
  results.push(await testCancelMidway());
  results.push(await testPresignFail());
  return {
    pass: results.filter((r) => r.ok).length,
    total: results.length,
    details: results.map((r) => `${r.ok ? '✓' : '✗'} ${r.name}${r.reason ? ` — ${r.reason}` : ''}`),
  };
}

if (require.main === module) {
  run().then((r) => {
    console.log(`\nSC-01 ${r.pass}/${r.total}`);
    r.details.forEach((d) => console.log(d));
    process.exit(r.pass === r.total ? 0 : 1);
  });
}
