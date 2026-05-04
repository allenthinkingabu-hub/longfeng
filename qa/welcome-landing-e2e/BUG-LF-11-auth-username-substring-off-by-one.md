# BUG-LF-11 · auth-service 用户名 substring off-by-one

**Severity**: P3 (cosmetic / 不阻塞登录链路)
**Status**: OPEN
**Discovered**: 2026-05-04 · S7 hybrid 联调
**Owner**: BE 团队

## 现象

新建 user_account 时 `username` 字段被截掉一个字符：
- `wechat_openid='openid_alice'` → 写入 `username='wx_enid_alice'`（应为 `wx_alice`）
- `wechat_openid='openid_bob'` → 预期写入 `username='wx_enid_bob'`（应为 `wx_bob`）

## 证据

```
$ psql -c "SELECT username, wechat_openid FROM lfwb.user_account"
   username    | wechat_openid
---------------+---------------
 wx_enid_alice | openid_alice
```

## 根因

`backend/auth-service/src/main/java/com/longfeng/auth/service/WechatLoginService.java`：

```java
// 实际逻辑
username = "wx_" + openid.substring(7)   // openid="openid_alice" · substring(7)="enid_alice"
// 期望逻辑
username = "wx_" + openid.substring(6)   // substring(6)="_alice" → 还要去 "_" 才对
// 推测正确写法
username = "wx_" + openid.replaceFirst("^openid_", "")
```

`"openid_alice".length()=12` · `substring(7)` 跳过 7 个字符 = `"enid_alice"`（前 7 个字符是 `o-p-e-n-i-d-_`）—— 实际想跳的是前 7 个字符（含下划线）= `"openid_"` · 但 substring(7) 起点是 index 7 · 即第 8 个字符（`a`）—— 等等数一下：

| index | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 |
| char  | o | p | e | n | i | d | _ | a | l | i |

`substring(7)` 应该返回 `"alice"` —— 但实际拿到 `"enid_alice"`。**说明 BE 实际写的不是 substring(7) 而是 substring(2) 或类似**。需要看源码确认。

## 影响

- 登录链路不阻塞（DB 写入成功 · JWT 签发正确）
- 但用户的 username 显示会错（hello, wx_enid_alice 而非 wx_alice）

## 修复方向

- 用更明确的 prefix 移除：`openid.replaceFirst("^openid_", "")`
- 或显式 `openid.length() > 7 ? openid.substring(7) : openid`（确认 substring 起点）
- 加 unit test：`openid_alice → wx_alice`、`openid_bob → wx_bob`

## 不在本任务范围

仅登记 · 由 BE 团队后续修复。本任务（hybrid 联调验证 BUG-LF-09 修复）已通过 Supervisor 审签。
