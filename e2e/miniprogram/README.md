# miniapp e2e · WeChat 小程序自动化

跑前 GUI 准备（一次性，由用户手动）：

## 1. 启动 WeChat 开发者工具
- 打开 `/Applications/wechatwebdevtools.app`
- 微信扫码登录

## 2. 开 CLI 服务端口
- 设置 → 安全 → 勾选「服务端口」
- 默认端口 9420（automator 默认连这个）
- 验证：`curl http://127.0.0.1:9420/heartbeat` → 200

## 3. 导入 miniapp 项目
- 工具栏「导入项目」→ 选 `frontend/apps/miniapp` 目录
- AppID 输入：你的测试 AppID（或 touristappid `wx0`，但部分 wx.* API 可能不可用）
- 等待编译完成

## 4. 替换 project.config.json 的 appid
当前是占位符 `wxREPLACEME_LOCAL_DEV` · 改为你的真实测试 AppID：
```json
{ "appid": "wx你的真实appid" }
```

## 5. BE 全栈起 (5 service)
- gateway:8080
- wrongbook-service:8081
- ai-analysis-service:8082
- review-plan-service:8083
- file-service:8084

启动验证：
```bash
for p in 8080 8081 8082 8083 8084; do
  curl -sf http://localhost:$p/actuator/health | head -1
done
```

## 6. 跑

```bash
cd e2e
pnpm e2e:miniapp:smoke   # 14 页可达性
pnpm e2e:miniapp         # full · smoke + Phase 3 SC
```

## 调试

| 错误 | 原因 | 解 |
|---|---|---|
| `connect ECONNREFUSED 127.0.0.1:9420` | CLI 端口未开 | 见 §2 |
| `Cannot find project` | 项目路径错 | 检查 `WECHAT_DEV_TOOLS` 环境变量 |
| `appid invalid` | placeholder 没换 | 见 §4 |
| `reLaunch returned null` | 页面路径写错 | 对照 `frontend/apps/miniapp/src/app.json` |

## 限制

| miniprogram-automator 不支持 | 替代 |
|---|---|
| axe-core a11y | 跳过 · 小程序无标准 a11y 规范 |
| screenshot pixel diff | 跳过 · automator screenshot 仅 DevTools 内部 |
| OAuth 真接 | 用 `wx.login` mock + storage 注入 token |
