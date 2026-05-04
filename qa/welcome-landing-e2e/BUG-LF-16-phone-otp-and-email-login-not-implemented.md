# BUG-LF-16 · 手机 OTP 登录 / 邮箱登录 未实现

**Severity**: P2 (产品规划阶段决定 · 不阻塞 S7 上线)
**Status**: OPEN
**Discovered**: 2026-05-04 · S7 hybrid 联调（用户问"还有手机/邮箱登录吗"暴露）
**Owner**: 产品 + BE

## 现象

`design/system/pages/P00.spec.md` §7 跳转图 L153 写：
> `[B4 其他方式]→────P00-PHONE 浮层（P1）`

即"手机登录"是 spec 标的 P1 后续功能 · **未实现**：
- 无 P00-PHONE 浮层 UI
- 无 `POST /api/auth/phone-otp/send`
- 无 `POST /api/auth/phone-otp/verify`
- 无 SMS provider 集成

**邮箱登录** 完全未规划：
- spec §4 数据契约只定义 WechatLoginReq/Resp
- DB `user_account` 表 V1.0.002 有预留 `email_hash` 列 · 但无对应 controller / API
- 产品未做决策（个人/家长教育产品 · 邮箱用户可能少）

## 当前 P00 给真用户的入口（修正认知）

| 入口 | UI 上可见 | 真实可用 |
|---|---|---|
| 微信一键登录 | ✅ | ✅（本次 LF-09 修复 + hybrid 验证）|
| "其他登录方式" 链接 | ✅ | ⚠️ 误导 → 后面的 dev 账密 form 实际是 QA fixture 入口（BUG-LF-13）|
| 手机 OTP 登录 | ❌ 无 UI 入口 | ❌ |
| 邮箱登录 | ❌ 无 UI 入口 | ❌ |
| Guest 模式 | ✅（在 P-LANDING "试一下"按钮 · 不在 P00）| ❌ BE 0% 实现（BUG-LF-12）|

## 修复方向（按产品优先级）

1. **手机 OTP（P1 已规划）**：
   - BE 集成 SMS provider（阿里云 / 腾讯云 SMS · 配 K8s Secret）
   - 表 `phone_otp` 加 (phone_hash, code_hash, expires_at, attempts)
   - 2 个 endpoint：send (限频 60s/次) + verify (3 次失败锁 5min)
   - FE 加 P00-PHONE 浮层 · spec §6 状态机扩 OTP_SENDING / OTP_VERIFYING 等
2. **邮箱登录（产品未决策）**：
   - 教育用户群体可能不优先 · 留待数据驱动决策
   - 如做：邮箱 verify 链接 / OTP 二选一 · 邮件 provider 集成（SES / Mailgun）

## 不在本任务范围

本次范围由 user 拍板 = "仅微信一键登录"。手机/邮箱属于产品后续 backlog。
