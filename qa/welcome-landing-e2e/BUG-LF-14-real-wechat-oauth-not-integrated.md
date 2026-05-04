# BUG-LF-14 · 真微信 OAuth (wx.login) 未对接 · 用 dev stub 替代

**Severity**: P0 (上线必须修)
**Status**: OPEN (本次任务 hybrid 联调用 stub · 真对接留待 prod readiness)
**Discovered**: 2026-05-04 · S7 hybrid 联调
**Owner**: BE + 微信开放平台账号管理

## 现象

`backend/auth-service/src/main/java/com/longfeng/auth/service/WechatLoginService.java` 的 `verifyWechatCode(code)` 当前是 dev stub：

```java
// dev profile 下 hard-code mapping
"dev_code_alice"  → openid: "openid_alice", unionid: "unionid_alice"
"dev_code_bob"    → openid: "openid_bob",   unionid: "unionid_bob"
其他 code         → throw IllegalArgumentException
```

**没有真调用** 微信开放平台 `https://api.weixin.qq.com/sns/jscode2session?appid=...&secret=...&js_code=<code>`。

FE side 同样 `Auth/index.tsx` 用 `window.__lf_dev_wx_code__ ?? 'dev_code_alice'` 注入 stub code · **没真调** `wx.login()`（小程序内）。

## 为什么本次接受 stub

- 本次任务范围（用户 Initiation Protocol 锁定）= 修复 BUG-LF-09 · 验证 FE → Gateway → auth-service → DB → JWT 全链路真通
- 真微信 OAuth 需要：①注册的微信开放平台账号 ②AppID / AppSecret 凭据 ③小程序签名审核
- 这些是产品 + DevOps + 微信运营的工作 · 不是 hybrid 联调的工作
- Stub 模式让 hybrid 联调链路真打通（DB / JWT / 中间件全真）· 仅微信 code 验证那一步是假的

## 上线前必须做的事

1. **BE**：
   - `WechatLoginService.verifyWechatCode()` dev profile 用 stub · prod profile 调真微信 API
   - prod 添加 `application-prod.yml` · 配 `wechat.appid` + `wechat.appsecret`（凭据从 K8s Secret 读 · 不入仓）
   - 处理微信 API 错误码（40029 invalid code · 45011 频率限制 · etc）
2. **FE**：
   - `Auth/index.tsx` 在小程序环境下调真 `wx.login()` 拿 `code`
   - H5 环境保留 stub（H5 demo 用）· 或者 H5 走"扫码登录"
3. **QA**：
   - 加 A 轨 spec：用真 staging 微信账号扫码完成登录
   - hybrid 轨保留 stub（不依赖外部服务）

## 当前 hybrid 联调可信度

QA Orchestrator 已显式声明：本次 hybrid PASS = "FE→Gateway→auth-service→PG→JWT→FE" 闭环通 · **不等于"真微信登录可用"**。

## 不在本任务范围

留 BE 团队接手 · 跟微信运营 + DevOps 协调凭据。
