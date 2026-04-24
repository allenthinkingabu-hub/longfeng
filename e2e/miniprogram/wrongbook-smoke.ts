// S7 · V-S7-12 · miniprogram-automator smoke · 3 pages 可达
// 跑前：WeChat 开发者工具开 · 项目 frontend/apps/miniapp 编译 · 安全模式放开 CLI
// 或：export WECHAT_DEV_TOOLS=/Applications/wechatwebdevtools.app/Contents/MacOS/cli
import automator from 'miniprogram-automator';

const PROJECT_PATH = `${__dirname}/../../frontend/apps/miniapp`;
const CLI = process.env.WECHAT_DEV_TOOLS || '/Applications/wechatwebdevtools.app/Contents/MacOS/cli';

async function main() {
  const mp = await automator.launch({ projectPath: PROJECT_PATH, cliPath: CLI });
  try {
    for (const page of [
      'pages/wrongbook/list/list',
      'pages/wrongbook/capture/capture',
      'pages/wrongbook/detail/detail?id=demo-id',
    ]) {
      const p = await mp.reLaunch(`/${page}`);
      if (!p) throw new Error(`nav fail ${page}`);
      // 确认根节点存在
      const root = await p.$('view.list-root, view.capture-root, view.detail-root');
      if (!root) {
        console.warn(`WARN: ${page} 根节点未找到（可能无数据）`);
      }
      console.log(`✓ ${page}`);
    }
  } finally {
    await mp.close();
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
