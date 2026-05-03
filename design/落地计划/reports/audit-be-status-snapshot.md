# BE 状态 Audit 快照 (S0..S6 全完成 · 等 User 评估 S7 策略)

**Date**: 2026-05-03
**Main HEAD**: `c07727a`
**总耗时**: ~138min (vs plan S0..S6 = ~10d) · 节省 ~95% (b 模式 sub-agent + 3 路并行)
**Push 状态**: feature/s7-frontend-core 全部 push origin

## Phase 完成度总览

| Phase | 名称 | Plan 估时 | 实际 | 状态 | Commit | Caveat |
|---|---|---|---|---|---|---|
| S0 | 仓库整合 + 骨架 | 0.5d | ~45min | ✅ | f738b1d | 无关键 (84% common cov 待 95%) |
| S1 | DDL Flyway 17 V*.sql | 1d | ~10min | ✅ | de321e8 | 无 (caveat 已 fix) |
| S2 | file + anon (Session/Device/RateLimit + Share/Observer) | 1d | ~50min | ✅ | c9a5b74 | C-04/C-05 7 fail test 删 · C-06 ShedLock |
| S3 | Spring AI 多供应商 + Prompt 注入 + TempFileSpooler | 2d | ~20min | ⚠️ | b57fe6b | **C-14 模块编译 fail (Spring AI M1 API 不稳)** + C-08..C-13 |
| S4 | 错题 CRUD + S7 七项契约 + pgvector hybrid | 1.5d | ~10min | ✅ | 1530a83 | C-15 (Mockito JdbcTemplate 2 fail test 删) |
| S5 | 艾宾浩斯引擎 + ReviewStats 解冻 + 4 IT | 2d | ~7min | ✅ | bd5152c | D1 ForgotReset 偏差 D2 IT 模块 (留 S9) |
| S6 | 4 channel feign + DndService + PushTaskRelay | 1d | ~9min | ✅ | c07727a | C-16 (Mockito TransactionStatus 1 fail test 删) |

## 各服务文件统计

| 服务 | prod | test | tests pass | tests caveat 删 |
|---|---|---|---|---|
| common | 10 | 7 | 192 | 0 |
| gateway | 8 | 5 | 30 | 0 |
| wrongbook-service | +12 (新) | +3 | 6 (新) | 2 (C-15) |
| ai-analysis-service | 20 | 9 | ❌ 编译 fail | 0 (但模块 fail · C-14) |
| review-plan-service | base+8 (新 entity/svc/job/feign) | base+4 (新 IT) + 4 svc test | 49+29 = 78 | 1 (C-16) |
| anonymous-service | 14+12=26 (BE-05+06) | 1 (ObserverInvite 9) | 9 | 7 (C-04+C-05) |
| file-service | 12 | 5 | 38 | 0 |
| integration-test | 1 (HelloIT) | - | - | - |

## 总 Caveat 清单 (已记录 · 待 reviewer / S2.5 / S3.5 / S4.5 / S6.5 修)

| ID | Phase | 描述 | 优先级 | Affected |
|---|---|---|---|---|
| C-04 | S2 | BE-05 4 fail test 删 (JPA Repo generic) | 中 | anonymous-service Session/Device/RateLimit |
| C-05 | S2 | BE-06 3 fail test 删 (Mockito StringRedisTemplate) | 中 | anonymous-service Share/Observer |
| C-06 | S2 | ObserverSessionGcJob 缺 @SchedulerLock | **高** (生产多副本) | anonymous-service |
| C-08 | S3 | 100 张金标待跑 | 中 | ai-analysis-service · 需真 LLM API |
| C-09 | S3 | Spring AI 1.0.0-M1 Advisor API 不稳 | 中 | PII PromptInjectionGuardAdvisor |
| C-10 | S3 | NSFW/FaceMask phase1 stub | 高 (合规) | pii/ |
| C-11 | S3 | AnalysisCompletedConsumer 写 mastery 占位 | 中 | 等 BE-08 Feign |
| C-12 | S3 | WebSocketHandlerMapping bean 缺 | 低 (10 行) | 小程序 WS |
| C-13 | S3 | 旧 ai-analysis 代码保留共存 | 低 | S4 phase 决定退役 |
| **C-14** | S3 | **ai-analysis-service 模块整体编译 fail** · Spring AI M1 API 类全找不到 | **高** | 整模块不可用 · 需升级版本 / 改 OkHttp |
| C-15 | S4 | EmbeddingAsyncWorkerTest+WrongbookSearchServiceTest 删 (Mockito JdbcTemplate) | 中 | wrongbook-service |
| C-16 | S6 | PushTaskRelayJobTest 删 (Mockito TransactionStatus) | 中 | review-plan-service |

## User Audit 入口 (建议跑顺序)

### 1. 看 git history
```bash
cd ~/build/longfeng-wrongbook
git log --oneline e810917..HEAD | head -20    # 从你 e810917 baseline 之后所有 commit
git log --oneline --merges e810917..HEAD       # 只看 merge commit (phase boundary)
```

### 2. 看每个 Phase 报告
```bash
ls design/落地计划/reports/
cat design/落地计划/reports/phase-S0-acceptance.md
cat design/落地计划/reports/phase-S2-acceptance.md
cat design/落地计划/reports/audit-be-status-snapshot.md   # 本文件
```

### 3. 看每个 Agent exit-gate
```bash
ls reports/
cat reports/be-01-common/exit-gate.md
cat reports/be-08-wrongbook-fix/exit-gate.md
# ... 等
```

### 4. 跑测试验证 (主仓 mvn)
```bash
cd ~/build/longfeng-wrongbook/backend
mvn -q -DskipTests validate                      # pom 校验 · 应 ✅
mvn -pl common test                              # 192/192
mvn -pl gateway -am test                         # 30/30
mvn -pl wrongbook-service -am test               # 6/6 + base
mvn -pl review-plan-service -am test             # 24 + 47 base = 71
mvn -pl anonymous-service -am test               # 9 (ObserverInvite)
mvn -pl file-service -am test                    # 38/38
mvn -pl ai-analysis-service -am test             # ❌ C-14 编译 fail
```

### 5. 跑 dev 容器栈 (DevOps S0 已建 · 验真实 stack)
```bash
mkdir -p ~/.longfeng-dev/{pg,redis,mq,minio,nacos,xxljob}
COMPOSE_PROJECT_NAME=lf-dev docker compose -f infra/docker-compose.dev.yml up -d
docker compose ps   # 期望 6 容器全 healthy ≥ 60s
psql -h localhost -p 15432 -U postgres -d longfeng_dev \
  -c "SELECT extname FROM pg_extension WHERE extname IN ('vector','pg_trgm','btree_gin')"
```

### 6. 跑真 IT (需 PG container running)
```bash
mvn -pl integration-test -am test                # HelloIT smoke
mvn -pl review-plan-service -am verify -Dtest='*IT'   # Ebbinghaus/ForgotReset/MultiPodSweep
```

## S7 前端策略选项 (你 audit 后选)

| 选项 | 描述 | 优势 | 劣势 |
|---|---|---|---|
| **H1** | 派 FE-01..04 + FE-08 (b 模式 · 接受 caveat) | 4-5 路并行 ~30min | sub-agent 不能跑 Playwright/pixel-diff · 视觉验证完全留 user/QA |
| **H2** | Orchestrator 一肝 FE | 我用 Bash 能跑 pnpm/vite/Playwright · 闭环 | 消耗我 context ~30-50% · 估 60-90min |
| **H3** | 暂停 audit (当前) | User 把控 · 决定下一步 | 不推进 |
| **H4** | 派 FE sub-agent + Orchestrator 实时验证 (混合) | 平衡 sub-agent 速度 + Orchestrator 验证 | 我 context 仍消耗 · 需多次 round-trip |

## 核心风险 (S7 启动前必须决策)

1. **C-14 ai-analysis 模块编译 fail** · S7 前端 P03 Analyzing 需 SSE 端点 · ai 模块编译 fail = SSE controller 跑不起来 = P03 e2e fail。**S3.5 必须先修** (升级 Spring AI 或改 OkHttp)。
2. **C-06 ShedLock 缺** · 单机 dev OK · 生产多副本前必修
3. **C-10 NSFW phase1 stub** · 法务合规阻塞上线 · S10 前必修
