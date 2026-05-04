# BUG-LF-13 · dev 账密 form 纯 FE stub · 无 BE

**Severity**: P3 (仅 QA 用 · 不影响真用户)
**Status**: OPEN (设计上接受 · 待产品决策是否扩为正式登录)
**Discovered**: 2026-05-04 · S7 hybrid 联调
**Owner**: 产品 + BE

## 现象

P00 登录页 `Auth/index.tsx:194-234` 提供"其他登录方式" → 展开 dev 账密 form · 默认账号 `qa-normal@longfeng.test` 密码 `Qa!Normal2026`。

**点击"登录(dev)"按钮**：FE 直接 `btoa()` 拼一个假 JWT 写 `localStorage['lf:token']` 跳转 `/` —— **0 行 fetch**·**0 行 BE 调用**。

## 当前用途

- Playwright `loginAs()` fixture (`e2e/fixtures/student.ts`) 用这个 form 模拟 NORMAL/VIP/VIP_PLUS 三种 tier 测试
- "其他登录方式"链接对真用户其实是误导 —— 它后面的 form 不该被真用户看到

## 影响

- 真用户点击"其他登录方式" → 看到 dev 账密 form · 困惑（不该有这个）
- mock-b E2E 64 TC 都靠这个跳登录 · 隐藏了真账密登录 endpoint 不存在的事实

## 修复方向（待产品决策）

**方案 A · 如果要做账密登录**：
- BE 新建 `POST /api/auth/login` 接收 `{ account, password }` · 验 user_account 的 phone_hash/email_hash · 签 JWT
- FE dev form 改成真发 fetch
- 加 P00-PHONE 浮层（spec §7 已标 P1）

**方案 B · 如果不做**：
- 隐藏"其他登录方式"链接（仅 import.meta.env.DEV 下显示）· prod 看不到
- form 用更明确的 testid `dev-only-form` 警示

## 不在本任务范围

无产品决策 · 仅登记。
