// Orchestrator · 顺序跑所有 miniapp e2e · 失败 collect 不 abort
// 使用：cd e2e && pnpm e2e:miniapp
import { runSmoke } from './all-pages-reachable';
import { close } from './launcher';

interface Suite {
  name: string;
  run: () => Promise<{ pass: number; total: number; details: string[] }>;
}

const SUITES: Suite[] = [
  {
    name: 'Phase 2 · 14 页可达性 smoke',
    run: async () => {
      const results = await runSmoke();
      return {
        pass: results.filter((r) => r.ok).length,
        total: results.length,
        details: results.map((r) => `${r.ok ? '✓' : '✗'} ${r.page}${r.reason ? ` — ${r.reason}` : ''}`),
      };
    },
  },
  // Phase 3 SC 套件占位 · 接入时取消注释
  // { name: 'SC-01 · 拍题入库',     run: async () => (await import('./sc-01-capture')).run() },
  // { name: 'SC-02 · 推送→执行',    run: async () => (await import('./sc-02-push-exec')).run() },
  // ... 16 SC × 28 test
];

async function main() {
  let totalPass = 0;
  let totalAll = 0;
  const summary: Array<{ suite: string; pass: number; total: number }> = [];

  for (const suite of SUITES) {
    console.log(`\n========== ${suite.name} ==========`);
    try {
      const { pass, total, details } = await suite.run();
      details.forEach((d) => console.log(d));
      totalPass += pass;
      totalAll += total;
      summary.push({ suite: suite.name, pass, total });
    } catch (e) {
      console.error(`FATAL [${suite.name}]:`, e);
      summary.push({ suite: suite.name, pass: 0, total: 1 });
      totalAll += 1;
    }
  }

  console.log('\n========== run-all summary ==========');
  for (const s of summary) {
    const ok = s.pass === s.total;
    console.log(`${ok ? '✓' : '✗'} ${s.suite}: ${s.pass}/${s.total}`);
  }
  console.log(`\n总计: ${totalPass}/${totalAll}`);

  await close();
  process.exit(totalPass === totalAll ? 0 : 1);
}

main().catch(async (e) => {
  console.error('run-all FATAL:', e);
  await close();
  process.exit(1);
});
