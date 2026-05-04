# BUG-LF-15 · auth-service 缺 token refresh / logout / 401 / 异常映射

**Severity**: P1 (上线前必须补 token 生命周期)
**Status**: OPEN
**Discovered**: 2026-05-04 · S7 hybrid 联调
**Owner**: BE 团队 (auth-service)

## 现象

本次 BUG-LF-09 修复后 · auth-service 仅实现 `POST /api/auth/wechat-login`。其他 token 生命周期 endpoint **全缺**：

| Endpoint | FE 是否调 | BE 实现 |
|---|---|---|
| `POST /api/auth/wechat-login` | ✅ | ✅ (本次实现) |
| `POST /api/auth/refresh-token` | 设计 spec §4 含 refresh_token 字段 · 暗示有 refresh | ❌ 未实现 (refresh_token 当前返空字符串) |
| `POST /api/auth/logout` | 设计 P-SETTINGS 应有"退出登录" | ❌ 未实现 |
| `GET /api/auth/me` | 取当前用户信息 (FE 显示头像/昵称) | ❌ 未实现 |
| 401 中间件（token 过期统一处理）| FE Auth/index.tsx 已留 `// 401 特殊` 分支 | ❌ Gateway 配置未验 |
| 业务异常 → 4xx 映射 | spec § 9 异常路径（invalid code · expired code · ...）| ⚠️ **当前 IllegalArgumentException 直接 500** · 应转 400 |

## 关键证据 (本次 hybrid spec H3)

```
H3 invalid wx_code spec：
- 期望: 4xx (业务错误)
- 实际: 500 (Internal Server Error)
- 原因: WechatLoginService throw IllegalArgumentException 没被 @ControllerAdvice 捕获 · Spring 默认转 500
```

## 修复方向

1. **加全局异常处理**：`@RestControllerAdvice` 类 · 把 IllegalArgumentException → 400 · 业务 enum 错 (微信 API code 40029) → 401/403 / EntityNotFoundException → 404 / 等
2. **新建 Controller** · 4 个 endpoint：
   - `POST /api/auth/refresh-token` 接 refresh_token · 验 user_token 表 · 旧 token revoke · 签新 token
   - `POST /api/auth/logout` 接 Authorization · revoke user_token (set revoked_at=now)
   - `GET /api/auth/me` 接 Authorization · 返 user_account public 字段（id / username / role / tier）
3. **Gateway 401 中间件**：JWT 过期/被 revoke 统一 401 · 不让 FE 看到 500
4. **加 user_token revoked_at 索引**（已有字段 · 缺 INDEX）

## 不在本任务范围

本次仅微信登录闭环。token 生命周期下一个 sprint 处理 · 配合 P-SETTINGS / refresh 机制设计。
