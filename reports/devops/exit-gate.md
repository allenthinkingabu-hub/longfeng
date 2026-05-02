# DevOps Agent · S0 Exit Gate Report

**Agent**: DevOps  
**Phase**: S0 · dev profile docker-compose + port/namespace offset rules  
**Branch**: `agent/devops`  
**Base commit**: `795360a` (feature/s7-frontend-core)  
**Report date**: 2026-05-02

---

## Self-Check ✅/❌ Table

| # | Gate | Status | Notes |
|---|---|---|---|
| 1 | `infra/docker-compose.dev.yml` 含 6 个 service + healthcheck + volume 挂 | ✅ | 7 service 定义（postgres/redis/rocketmq-namesrv/rocketmq-broker/minio/nacos/xxl-job-admin）+ 1 one-shot init（minio-init）；每个 service 均有 healthcheck |
| 2 | `docker compose ps` 6 容器全 healthy（≥ 60s 稳定）| ⚠️ **未实际执行** | Docker daemon 已确认 running（`docker info` 29.2.1 OK）；Agent 会话内 Bash chmod/docker 权限未授予，无法实际拉镜像启动。**User 需手动运行验证命令（见下）** |
| 3 | `psql` 验证 pgvector / pg_trgm 扩展 | ⚠️ 待 User 执行 | `pg-init.sql` 已含 4 扩展 + ASSERT 验证块 |
| 4 | `mc ls lf-dev/` 看到 3 个 bucket | ⚠️ 待 User 执行 | `minio-buckets.sh` one-shot container 会在 minio healthy 后自动创建 3 bucket |
| 5 | Nacos http://localhost:18848/nacos 可访问 | ⚠️ 待 User 执行 | image: nacos/nacos-server:v2.3.2-slim standalone 模式 |
| 6 | XXL-Job http://localhost:18080/xxl-job-admin/ 可访问 | ⚠️ 待 User 执行 | image: xuxueli/xxl-job-admin:2.4.1 H2 embedded dev 模式 |
| 7 | README 含完整启停 + 端口偏移 + Agent 命名空间规则 | ✅ | `infra/docker-compose.dev.README.md` 含全部章节 |

---

## 发现问题（Known Issues）

### I-01 · Bash 权限限制 — docker compose 未实际执行

**现象**：Agent 会话中 `mkdir`、`chmod`、`docker compose up` 等命令被权限控制阻断。Docker daemon 本身已确认运行（version 29.2.1）。所有 yml/init 文件均已按规范产出。

**影响**：出口门禁第 2-6 项（容器 healthy / 扩展验证 / bucket 验证 / UI 端点）无法由 Agent 自动完成，需 User 手动执行。

**处置**：提供完整验证命令（见下方「User 验证步骤」段）。

### I-02 · XXL-Job 2.4.1 — 使用 H2 Embedded 而非 MySQL/PG

**决策**：官方镜像仅内置 MySQL 驱动；直连 PG 需自定义镜像（不必要的复杂度）。改用 H2 file-based 模式，DB 文件持久化到 bind-mount volume `~/.longfeng-dev/xxljob/`。

**影响**：dev 环境调度数据与 staging/prod（MySQL）不同源，但调度任务本身完全兼容。S10 Helm 阶段再用 MySQL 或 PG 替换。

### I-03 · RocketMQ Broker healthcheck 策略

**现象**：RocketMQ 5.3.1 broker 的健康探测方式有限（无官方 HTTP health 端点）。使用 `curl` 连接 10911 端口返回非 curl 错误码（exit 7 = 连接被拒，其他任何返回码均视为"存活"）。

**影响**：可能出现 broker 进程启动了但未完全初始化时也报 healthy 的边界情况。Start period 设为 60s 以给足初始化时间。

---

## User 验证步骤

### 前置

```bash
# 创建 volume 挂载目录
mkdir -p ~/.longfeng-dev/{pg,redis,mq,minio,nacos,xxljob}
```

### 启动

```bash
COMPOSE_PROJECT_NAME=lf-dev docker compose -f infra/docker-compose.dev.yml up -d
```

### 健康检查（等待约 60-90s）

```bash
docker compose -f infra/docker-compose.dev.yml ps
```

### 验证 PG 扩展

```bash
psql -h localhost -p 15432 -U postgres -d longfeng_dev \
  -c "SELECT extname FROM pg_extension WHERE extname IN ('vector','pg_trgm','btree_gin','pg_stat_statements') ORDER BY extname;"
# 期望: 4 行
```

### 验证 MinIO bucket（需本地安装 mc）

```bash
mc alias set lf-dev http://localhost:19000 minio minio12345
mc ls lf-dev/
# 期望: wrongbook-dev / guest-tmp-dev / shared-thumbnail-dev
```

### 验证 UI 端点

```bash
curl -sf http://localhost:18848/nacos/v1/console/health/readiness && echo "Nacos OK"
curl -sf http://localhost:18080/xxl-job-admin/ | grep -c "XXL-JOB" && echo "XXL-Job OK"
```

---

## 提交记录

| Commit SHA | 说明 |
|---|---|
| `966eb89` | feat(s0/devops): add dev compose stack · 6 services + port +10000 offset |
| `914b60a` | feat(s0/devops): add init scripts · pg extensions + minio buckets + nacos placeholder |

```
$ git log --oneline agent/devops ^795360a
914b60a feat(s0/devops): add init scripts · pg extensions + minio buckets + nacos placeholder
966eb89 feat(s0/devops): add dev compose stack · 6 services + port +10000 offset
```

---

## 文件清单

```
infra/
├── docker-compose.dev.yml                 # 主文件：7 services（含 minio-init one-shot）
├── docker-compose.dev.README.md           # 启停 + 端口偏移表 + Agent 命名空间规则
└── init/
    ├── pg-init.sql                        # 4 PG extensions (vector/pg_trgm/btree_gin/pg_stat_statements)
    ├── minio-buckets.sh                   # 3 bucket 创建脚本 (mc-based one-shot)
    └── nacos-bootstrap-config.json        # S0 配置骨架 (S3+ 填 real values)
reports/devops/
└── exit-gate.md                           # 本报告
```

---

## 端口偏移表（汇总）

| Service | Container Port | Host Port (lf-dev) | Rule |
|---|---|---|---|
| PostgreSQL | 5432 | 15432 | +10000 |
| Redis | 6379 | 16379 | +10000 |
| RocketMQ NS | 9876 | 19876 | +10000 |
| RocketMQ Broker | 10911 | 20911 | +10000 |
| RocketMQ Broker VIP | 10909 | 20909 | +10000 |
| MinIO API | 9000 | 19000 | +10000 |
| MinIO Console | 9001 | 19001 | +10000 |
| Nacos HTTP | 8848 | 18848 | +10000 |
| Nacos gRPC | 9848 | 19848 | +10000 |
| Nacos gRPC TLS | 9849 | 19849 | +10000 |
| XXL-Job Admin | 8080 | 18080 | +10000 |

**Agent worktree 额外偏移**：`COMPOSE_PROJECT_NAME=lf-<agent-id>` + host port +100 per agent ordinal（详见 README）。
