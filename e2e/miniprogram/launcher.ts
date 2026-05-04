// 共享 launcher · WeChat Dev Tools CLI 启动 + cleanup
// 调用前置：
//   1. WeChat 开发者工具 GUI 已启动
//   2. 设置→安全→「服务端口」勾上 (默认 9420)
//   3. project.config.json 的 appid 已填真实测试 appid (非 wxREPLACEME_LOCAL_DEV)
import automator, { type MiniProgram } from 'miniprogram-automator';
import path from 'node:path';

const PROJECT_PATH = path.resolve(__dirname, '../../frontend/apps/miniapp');
const CLI = process.env.WECHAT_DEV_TOOLS || '/Applications/wechatwebdevtools.app/Contents/MacOS/cli';
const PORT = Number(process.env.WX_AUTOMATOR_PORT || 9420);

let _mp: MiniProgram | null = null;

export async function launch(): Promise<MiniProgram> {
  if (_mp) return _mp;
  _mp = await automator.launch({
    projectPath: PROJECT_PATH,
    cliPath: CLI,
    port: PORT,
    timeout: 60_000,
  });
  return _mp;
}

export async function close(): Promise<void> {
  if (_mp) {
    await _mp.close();
    _mp = null;
  }
}

export const PROJECT_INFO = { PROJECT_PATH, CLI, PORT } as const;
