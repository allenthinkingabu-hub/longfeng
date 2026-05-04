# 手工测试指南 · 欢迎页全漏斗

> 给非自动化场景准备 · 让人能坐下来一步一步验证主路径

## 一、测试环境

| 服务 | 端口 | 启动命令 |
|---|---|---|
| H5 前端 (Vite) | 5173 | `cd frontend/apps/h5 && pnpm dev` |
| API mock (MSW) | 同前端 | 内嵌 service worker · 无需独立启动 |

baseURL: `http://localhost:5173`

## 二、测试账号

| 用途 | 账号 | 密码 | 入口 |
|---|---|---|---|
| dev 账密登录 (绕开微信) | 见 `e2e/fixtures/student.ts:37` | 同上 | P00 → "其他登录方式" → 表单 |
| 微信登录 | — | — | E2E 走 MSW mock · 真测需扫真码 |
| 匿名访客 | — | — | 默认 · 直接访问 `/welcome` 即可 |

## 三、关键路径一步步走

### TC-1 · 完整漏斗 (anonymous → guest → result)
1. 浏览器打开 `http://localhost:5173/welcome`
2. 等深蓝 hero 渲染完 + 看到"AI 帮你拍下错题"标题
3. 点白色 pill 按钮"试一试"
4. URL 应跳到 `/guest/capture`
5. 顶部 banner 显示"还剩 N 次"
6. 选学科 chip (math)
7. 点底部 78px 圆形快门
8. 等 5 秒看到分析结果
9. ✅ PASS

### TC-2 · 登录漏斗 (anonymous → login → home)
1. `/welcome`
2. 点蓝色 pill "登录"
3. URL 跳 `/auth`
4. 勾选底部协议复选框
5. 点"其他登录方式"
6. 填 dev 账号密码
7. 点登录
8. URL 跳 `/`
9. ✅ PASS

### TC-3 · 配额耗尽挡板
1. 浏览器开 devtools · network → request blocking 把 `/api/guest/quota` 改成 `{"quotaRemaining": 0}`
2. `/welcome` → 试一试 → `/guest/capture`
3. 应弹全屏挡板"配额已用完"
4. 点"立即注册" → 跳 `/auth?redirect=/guest/capture`
5. 完成登录后回到 `/guest/capture`
6. ✅ PASS

## 四、PDF/视觉验证工具

- 截图对比：`e2e/reports/mockup-diff/{P-LANDING,P-GUEST-CAPTURE}-{mockup,impl,diff}.png`
- VRT baseline：`e2e/specs/vrt-multi-viewport.spec.ts-snapshots/`
- Vision review JSON：`e2e/reports/design-review/*.json`

## 五、常见排查

| 现象 | 排查 |
|---|---|
| `/welcome` 白屏 | check 5173 是否启动 + console 看 React 报错 |
| 微信按钮没响应 | 是否勾选协议复选框 (P00 spec §8 AC-003) |
| 拍题后一直 ANALYZING | MSW worker 是否注册 · network 看 `/api/guest/analyze` 状态 |
| iPad 显示 iPhone 边框 | 重大设计回归 · 立刻报 BUG-LF-NN · 见 §15 实现边界 |
