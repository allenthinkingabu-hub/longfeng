# S7 微信登录 Hybrid 联调 · Sweep Report

**Sprint**: S7
**Task**: 修复 BUG-LF-09 (auth-service 0% 实现) · 全链路 E2E 联调微信登录
**Date**: 2026-05-04
**QA Orchestrator**: Claude Opus 4.7 (1M context)
**Supervisor**: Independent agent (PASS · 见 §6)
**Verdict**: ✅ **PASS** · 可交付

---

## §1 任务背景

5 天前 BUG-LF-09 暴露：FE 跑 64 mock-b TC 全 PASS · 自报 "45/46 PASS · 97.8%" · 但 BE `/api/auth/wechat-login` 等 4 个 endpoint **一个 controller 都没写** · MSW 在浏览器层全程兜底。

User 在新的 Initiation Protocol 下要求：
- ❌ 禁 MSW · 禁 mock
- ✅ 真前端 + 真后端 + 真 DB · hybrid 联调
- ✅ 中间件复用 Docker（不改 docker compose）
- ✅ 端口用 9 字头（落 9xxx 4 位 · 因 TCP 65535 上限）

## §2 范围（用户拍板）

| 登录方式 | 范围 | 状态 |
|---|---|---|
| 微信一键登录 | ✅ **本次必须打通** | ✅ PASS（本报告 §6） |
| dev 账密 form | ❌ QA fixture · 保留不动 | file BUG-LF-13 |
| 手机 OTP 登录 | ❌ spec P1 后续 | file BUG-LF-16 |
| 邮箱登录 | ❌ 产品未规划 | file BUG-LF-16 |
| Guest `/api/guest/session` | ❌ 不在本次范围 | file BUG-LF-12 |

---

## §3 4 栏 Sweep Schema (CLAUDE.md QA 第一条铁律)

| Suite | L1 (FE↔BE 契约对账) | L2 (mock-b UI) | L3 (hybrid 真链路) | 真实保证 |
|---|---|---|---|---|
| **SC-P00 微信登录** | ✅ FE `Auth/index.tsx:73-149` 调 `/api/auth/wechat-login` ↔ BE `WechatLoginController.@PostMapping("/api/auth/wechat-login")` 一致 | ✅ 7 TC 已有 (`sc-p00.spec.ts`) · 本次未跑（不属本范围） | ✅ **本次新增 4 TC 全 PASS** (`sc-p00-wechat-hybrid.spec.ts`) | ✅ Supervisor PASS (§6) |
| Guest 漏斗 | ⚠️ **未覆盖** · BUG-LF-12 文件登记 | ✅ MSW 兜底（mock-b） | ⚠️ **未覆盖** · BE 0% | ❌ file BUG-LF-12 |
| 账密登录 | ⚠️ FE 是纯 stub · 无 BE 端可对账 | ✅ MSW 兜底 | ❌ 不在范围 | ⚠️ file BUG-LF-13 |
| 手机 OTP / 邮箱 | ❌ FE 无 UI 入口 · BE 无 endpoint | ❌ 无 mock | ❌ 不在范围 | ❌ file BUG-LF-16 |
| Token refresh / logout / 401 | ⚠️ FE 留 placeholder · BE 0% | ❌ 无测 | ❌ 不在范围 | ❌ file BUG-LF-15 |

**显式覆盖 + 豁免对照**（per memory feedback `plan_explicit_exemption`）：
- ✅ **覆盖**：仅微信一键登录（用户拍板范围）
- ⚠️ **显式豁免**（已 file BUG · 不在本任务范围）：
  - LF-12 Guest session 0% 实现
  - LF-13 dev 账密 form 无 BE
  - LF-14 真 wechat OAuth 未对接（用 dev_code stub）
  - LF-15 token refresh/logout/异常映射缺失
  - LF-16 手机 OTP / 邮箱登录未实现
  - LF-11 BE username substring off-by-one (P3 cosmetic)

---

## §4 真链路证据（5 项铁证）

完整证据落 `e2e/reports/login-trace/` + `export/login-trace/`：

| 文件 | 内容 | 证据强度 |
|---|---|---|
| `h1-msw-disabled.json` | `{"msw_disabled":true}` | ✅ MSW 真关 |
| `h2-network-trace.json` | request 9174 + response 200 + RTT 104ms + x-trace-id UUID | ✅ 真 BE 响应 |
| `h2-resp-body.json` | envelope `{code:0, message:OK, data:{access_token, student_id, is_new_user}, trace_id}` | ✅ BE 真签 |
| `h2-jwt-decoded.json` | header `{alg:RS256}` · payload `{sub, role, device_fp, tier, exp, iat}` 完整 | ✅ RS256 真签 |
| `h2-localstorage.json` | `lf:token=<JWT>` + `lf_user_tier=NORMAL` | ✅ FE 真写 |
| `h2-step1/2-*.png` | UI 截图（点击前 + 跳转后）| ✅ 视觉确认 |
| `export/login-trace/05-db-state.txt` | user_account row + user_token 5 row + Flyway V040/V041 success | ✅ DB 真落 |
| `export/login-trace/06-redis-state.txt` | Redis snapshot (auth:* keys 不存在 · BE 当前不缓存) | ⚠️ 不阻塞 |
| `export/login-trace/99-supervisor-verdict.md` | 5/5 铁证 + 5/5 交叉一致性 PASS | ✅ 独立审计 |

**交叉一致性（Supervisor 验证 5 处吻合）**：
```
JWT.sub = envelope.student_id = DB user_account.id = DB user_token.user_id = Snowflake "1864287010886614872"
```

---

## §5 实施过程关键修复（避免 LF-09 类隐患）

| Bug | 发现路径 | 修复 |
|---|---|---|
| FE handleWechatLogin 是 simulated stub（之前以为 MSW 兜底 · 实际根本没发 fetch） | 用户问"还有手机邮箱吗"暴露 · read 全文确认 | FE Sub-Agent 改 `Auth/index.tsx` 真发 fetch |
| BE auth-service module 0% 实现 | 3 个 Explore agent 独立证实 | BE Sub-Agent 建 module + Migration + RS256 JWT |
| BE 用 RS256 不是 HS256（hybrid spec 期望错） | 第一次 spec 跑 H2 fail · 看 BE log + Read JwtUtils | spec H2 改 `alg:RS256` · 加 role/device_fp/tier claim 验证 |
| Vite proxy 用 `process.env.VITE_API_PROXY_TARGET` (Node)而非 `import.meta.env` (浏览器) · `.env.hybrid` 不生效 | spec H2 fail 显示 ECONNREFUSED · 查 `vite.config.ts` 发现 | 启动 FE 时显式 `VITE_API_PROXY_TARGET=http://localhost:9880 pnpm dev --mode hybrid` |
| Gateway Redis 默认 6379 · 项目 Redis 在 16379 | 第一次 gateway 调 wechat-login 返 500 · grep `Caused by` 发现 RedisConnectionFailureException | 启动 gateway 时加 `REDIS_PORT=16379` env |
| Gateway 默认 9880 不是 plan 写的 9080 | 第一次 fail · `Port 9880 was already in use` 提示真端口 | plan 端口规划修正 · `.env.hybrid` proxy target → 9880 |
| BE Response envelope `{code, message, data, trace_id}` · FE/spec 期望直接 `{access_token}` | spec H2 fail · access_token undefined | FE Auth.tsx + mock handler + spec 三处都加 envelope 解析 |
| Sub-agent worktree 物理目录被 cleanup · 私钥 `docs/dev/jwt-dev-privkey.pem` 不见 | cherry-pick 时找不到私钥 | 重新生成 RSA 密钥对 · 公钥覆盖 git · 私钥本地 gitignored |
| `longfeng_dev` DB 在 sub-agent 跑后被回滚（容器内状态丢） | BE 启动 Flyway 报 `database does not exist` | `CREATE DATABASE longfeng_dev` · BE 重启自动跑 V001-V057 migration |

---

## §6 最终判定（Supervisor 独立审签）

```
Verdict: PASS
Audit time: 2026-05-04T19:45:00Z
Auditor: Independent Supervisor Agent

5 项铁证: 5/5 ✅
交叉一致性: 5/5 ✅
跟 BUG-LF-09 本质区别: ✅ DB 真写 · JWT 真签 · 不是 MSW 伪造
```

完整 verdict: `export/login-trace/99-supervisor-verdict.md`

---

## §7 部署 / 运行参考（QA 复跑链路）

```bash
# 前置：Docker 中间件已起（s3-it-pg :15432 · s3-it-redis :16379）
# 一次性 DB 初始化（如缺）
docker exec s3-it-pg psql -U postgres -d postgres -c "CREATE DATABASE longfeng_dev OWNER postgres;"

# 一次性密钥生成（如本地缺）
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out docs/dev/jwt-dev-privkey.pem
openssl pkey -in docs/dev/jwt-dev-privkey.pem -pubout -out docs/dev/jwt-dev-pubkey.pem
# 注：私钥已 .gitignore · 公钥需 commit (gateway 验签依赖)

# 起 BE auth-service（cwd=项目根 · 因 JWT 路径相对）
mvn -f backend/auth-service/pom.xml spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Dauth.jwt.private-key-path=$(pwd)/docs/dev/jwt-dev-privkey.pem" &

# 起 BE gateway（注意 REDIS_PORT 必须显式给 16379 · 否则连 6379 fail）
REDIS_PORT=16379 mvn -f backend/gateway/pom.xml spring-boot:run \
  -Dspring-boot.run.jvmArguments="-Djwt.public-key-path=$(pwd)/docs/dev/jwt-dev-pubkey.pem" &

# 等 health
until curl -fs http://localhost:9086/actuator/health && curl -fs http://localhost:9880/actuator/health; do sleep 2; done

# 起 FE hybrid 模式（必须显式 VITE_API_PROXY_TARGET · .env.hybrid 不读 Node env）
cd frontend/apps/h5 && \
  VITE_API_PROXY_TARGET=http://localhost:9880 pnpm dev --mode hybrid --port 9174 --host &

# 跑 hybrid spec
cd e2e && BASE_URL=http://localhost:9174 \
  pnpm playwright test --config=playwright.config.hybrid.ts --reporter=list

# DB 穿透
bash e2e/scripts/db-snapshot.sh
```

---

## §8 后续 (按优先级)

1. **P0 上线前必须修**：
   - LF-14 真微信 OAuth 对接
   - LF-15 token refresh + logout + 401 + 异常映射 4xx
2. **P1 后续 sprint**：
   - LF-12 Guest session BE 实现
   - LF-16 手机 OTP（spec P1）
3. **P2/P3 nice-to-have**：
   - LF-11 username substring 修
   - LF-13 dev 账密 form 决策
   - LF-16 邮箱登录决策（数据驱动）

---

## §9 任务完成度

- [x] Phase 1 三个 Explore agent 摸清现状
- [x] Phase 2 用户拍板路径 B + 范围 + 端口
- [x] Phase 3 写 plan 文件 + 用户确认
- [x] Phase 4 BE Sub-Agent 建 auth-service module + 5 单测 PASS
- [x] Phase 5 FE Sub-Agent 改 Auth.tsx 真发 fetch
- [x] Phase 6 cherry-pick BE + 解 conflict + 建测试基础设施
- [x] Phase 7 起 docker / BE / FE 跑 hybrid spec
- [x] Phase 8 4 个 hybrid TC 全 PASS + DB 穿透
- [x] Phase 9 Supervisor 独立审签 PASS
- [x] Phase 10 file 6 个 BUG 报告
- [x] Phase 11 写 sweep report (本文件)
- [ ] Phase 12 commit + 收尾

任务接近完成 · 仅剩 commit 步骤。
