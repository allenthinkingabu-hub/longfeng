# BUG-LF-12 · `/api/guest/session` BE 0% 实现 (Guest 模式断链)

**Severity**: P1 (Guest 模式漏斗起点失败)
**Status**: OPEN
**Discovered**: 2026-05-04 · S7 hybrid 联调（同根 BUG-LF-09）
**Owner**: BE 团队 (anonymous-service)

## 现象

FE `frontend/apps/h5/src/pages/GuestCapture/index.tsx:7` 调用 `POST /api/guest/session` 创建匿名会话 · 写入 `localStorage['guest_session_token']` · 用于 P-LANDING → 拍题 → 注册 claim 的漏斗。

但 `backend/anonymous-service` 仅有 `HealthController`（/ready /live）· **`/api/guest/session` 0 行 controller 代码 · 0 行 service 代码**。

## 现状对比

| 链路点 | FE | BE |
|---|---|---|
| `POST /api/guest/session` | ✅ 调（GuestCapture/index.tsx:7）| ❌ 无 controller |
| `POST /api/guest/claim` | ✅ 调（Auth/index.tsx claim 分支）| ❌ 无 controller |
| `guest_session` 表 | — | ✅ 已有 migration `V1.0.030__guest_session.sql` |
| MSW handler `guest.ts` | ✅ 兜底 | — |

## 影响

mock-b 轨 E2E 全 PASS · 但 hybrid/A 轨上线后 Guest 模式整条漏斗 404。LF-09 同款 bug。

## 修复方向

参考本次 LF-09 修复模式（auth-service）· 在 anonymous-service 内新建：
- `controller/GuestSessionController.java` · POST `/api/guest/session`
- `service/GuestSessionService.java` · 写入 `guest_session` 表 + 签发 guest_session_token (HS256 或 JWT) + 返 `{ session_id, token }`
- `controller/GuestClaimController.java` · POST `/api/guest/claim` · 校验 device_fp 跟 session 匹配 · 把 anonymous_question 转给登录 user

## 不在本任务范围

仅微信登录在本次范围。Guest 链路下一个 sprint 处理。
