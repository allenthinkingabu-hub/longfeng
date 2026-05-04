// V-MA-S2-1 · miniapp 14 页可达性 smoke
// 跑前：先启 WeChat Dev Tools GUI · 见 launcher.ts header
import { launch, close } from './launcher';

interface PageSpec { path: string; rootSelector?: string }

const PAGES: PageSpec[] = [
  { path: 'pages/landing/index/index',          rootSelector: '.landing-root' },
  { path: 'pages/camera/capture/capture',       rootSelector: '.capture-root' },
  { path: 'pages/camera/analyzing/analyzing',   rootSelector: '.analyzing-root' },
  { path: 'pages/camera/result/result',         rootSelector: '.result-root' },
  { path: 'pages/wrongbook/list/list',          rootSelector: '.list-root' },
  { path: 'pages/wrongbook/detail/detail',      rootSelector: '.detail-root' },
  { path: 'pages/review/today/today',           rootSelector: '.today-root' },
  { path: 'pages/review/exec/exec',             rootSelector: '.exec-root' },
  { path: 'pages/review/done/done',             rootSelector: '.done-root' },
  { path: 'pages/calendar/month/month',         rootSelector: '.month-root' },
  { path: 'pages/calendar/event/event',         rootSelector: '.event-root' },
  { path: 'pages/notification/index/index',     rootSelector: '.notification-root' },
  { path: 'pages/me/settings/settings',         rootSelector: '.settings-root' },
  { path: 'pages/me/ai-model-pref/ai-model-pref', rootSelector: '.ai-model-pref-root' },
];

interface Result { page: string; ok: boolean; reason?: string }

export async function runSmoke(): Promise<Result[]> {
  const mp = await launch();
  const results: Result[] = [];
  for (const { path, rootSelector } of PAGES) {
    try {
      const p = await mp.reLaunch(`/${path}`);
      if (!p) { results.push({ page: path, ok: false, reason: 'reLaunch returned null' }); continue; }
      if (rootSelector) {
        const root = await p.$(rootSelector);
        if (!root) { results.push({ page: path, ok: false, reason: `root ${rootSelector} not found` }); continue; }
      }
      results.push({ page: path, ok: true });
    } catch (e) {
      results.push({ page: path, ok: false, reason: (e as Error).message });
    }
  }
  return results;
}

if (require.main === module) {
  runSmoke()
    .then(async (results) => {
      const pass = results.filter((r) => r.ok).length;
      console.log(`\n========== miniapp smoke ${pass}/${results.length} ==========`);
      for (const r of results) {
        console.log(`${r.ok ? '✓' : '✗'} ${r.page}${r.reason ? ` — ${r.reason}` : ''}`);
      }
      await close();
      process.exit(pass === results.length ? 0 : 1);
    })
    .catch(async (e) => {
      console.error('FATAL:', e);
      await close();
      process.exit(1);
    });
}
