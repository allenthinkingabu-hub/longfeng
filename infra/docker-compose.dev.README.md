# longfeng-wrongbook · Dev Compose Stack

Dev-only local infrastructure for the AI 错题本 MVP.
Starts 6 core services with a `+10000` host-port offset to avoid conflicts with existing local services and the S7 dev stack (which uses default ports 5432 / 6379).

---

## Quick Start

### 0. Pre-flight: create volume host directories

```bash
mkdir -p ~/.longfeng-dev/{pg,redis,mq,minio,nacos,xxljob}
```

### 1. Start all services

```bash
COMPOSE_PROJECT_NAME=lf-dev docker compose -f infra/docker-compose.dev.yml up -d
```

### 2. Check health (expect all 6+ containers `healthy` within 60s)

```bash
docker compose -f infra/docker-compose.dev.yml ps
```

Expected output (all `(healthy)` or `Up`):

```
NAME                     STATUS             PORTS
lf-dev-postgres          Up (healthy)       0.0.0.0:15432->5432/tcp
lf-dev-redis             Up (healthy)       0.0.0.0:16379->6379/tcp
lf-dev-rocketmq-namesrv  Up (healthy)       0.0.0.0:19876->9876/tcp
lf-dev-rocketmq-broker   Up (healthy)       0.0.0.0:20911->10911/tcp, 0.0.0.0:20909->10909/tcp
lf-dev-minio             Up (healthy)       0.0.0.0:19000->9000/tcp, 0.0.0.0:19001->9001/tcp
lf-dev-minio-init        Exited (0)         (one-shot init, exit 0 = success)
lf-dev-nacos             Up (healthy)       0.0.0.0:18848->8848/tcp
lf-dev-xxljob            Up (healthy)       0.0.0.0:18080->8080/tcp
```

### 3. Verify PostgreSQL extensions

```bash
psql -h localhost -p 15432 -U postgres -d longfeng_dev \
  -c "SELECT extname FROM pg_extension WHERE extname IN ('vector','pg_trgm','btree_gin','pg_stat_statements') ORDER BY extname;"
```

Expected: 4 rows — `btree_gin`, `pg_stat_statements`, `pg_trgm`, `vector`

### 4. Verify MinIO buckets

```bash
# Using mc locally (brew install minio/stable/mc)
mc alias set lf-dev http://localhost:19000 minio minio12345
mc ls lf-dev/
```

Expected: `wrongbook-dev`, `guest-tmp-dev`, `shared-thumbnail-dev`

### 5. Verify UI endpoints

| Service | URL |
|---|---|
| Nacos console | http://localhost:18848/nacos (admin / nacos) |
| MinIO console | http://localhost:19001 (minio / minio12345) |
| XXL-Job admin | http://localhost:18080/xxl-job-admin/ (admin / 123456) |

---

## Stop / Restart

```bash
# Stop (keep volumes)
docker compose -f infra/docker-compose.dev.yml down

# Restart a single service
docker compose -f infra/docker-compose.dev.yml restart nacos

# View logs
docker compose -f infra/docker-compose.dev.yml logs -f nacos
```

## Full Teardown (WARNING: deletes all local dev data)

```bash
docker compose -f infra/docker-compose.dev.yml down -v
rm -rf ~/.longfeng-dev/{pg,redis,mq,minio,nacos,xxljob}
```

---

## Port Offset Table

`+10000` offset rule — avoids conflicts with host services and S7 existing stack (5432/6379).

| Service | Container Port | Host Port (dev) | Notes |
|---|---|---|---|
| PostgreSQL | 5432 | **15432** | pgvector + pg_trgm + btree_gin |
| Redis | 6379 | **16379** | rate-limit / Bloom / ShedLock |
| RocketMQ NameSrv | 9876 | **19876** | |
| RocketMQ Broker | 10911 | **20911** | also 10909→20909 (VIP) |
| MinIO API | 9000 | **19000** | OSS SPI endpoint |
| MinIO Console | 9001 | **19001** | Web UI |
| Nacos | 8848 | **18848** | also gRPC 9848→19848, 9849→19849 |
| XXL-Job Admin | 8080 | **18080** | |

---

## Agent Worktree Namespace Isolation

When multiple Builder Agents run in parallel (see plan §4.3), each uses its own Docker namespace with an **additional +100 port offset** to avoid collisions:

| Agent ID | COMPOSE_PROJECT_NAME | Extra Port Offset | PG Host Port | Redis Host Port |
|---|---|---|---|---|
| `lf-dev` (base dev) | `lf-dev` | +0 | 15432 | 16379 |
| `be-01-common` | `lf-be01` | +100 | 15532 | 16479 |
| `be-02-gateway` | `lf-be02` | +200 | 15632 | 16579 |
| `be-03-flyway` | `lf-be03` | +300 | 15732 | 16679 |
| `be-04-file` | `lf-be04` | +400 | 15832 | 16779 |
| `fe-XX-*` | `lf-feXX` | +X00 | ... | ... |

**To launch an Agent-scoped stack:**

```bash
# Example: be-01-common agent
COMPOSE_PROJECT_NAME=lf-be01 \
  POSTGRES_HOST_PORT=15532 \
  REDIS_HOST_PORT=16479 \
  docker compose -f infra/docker-compose.dev.yml up -d postgres redis
```

> Note: Individual Agent stacks typically only need PG + Redis. Full 6-service dev stack is shared (COMPOSE_PROJECT_NAME=lf-dev). The per-agent offset is reserved for isolated IT runs that need a clean DB.

**Rule summary (from plan §4.3):**
- `COMPOSE_PROJECT_NAME` must be `lf-<agent-id>` — prevents container name and network collision
- Port offset +100 per agent ordinal — prevents host-port binding conflicts on a single dev machine
- Each agent gets its own PG schema (e.g., `anon_be05`) within the shared PG instance — Flyway holds write lock per schema
- OSS buckets per agent: `dev-<agent-id>` (MinIO)

---

## Service Versions

| Service | Image | Version | Notes |
|---|---|---|---|
| PostgreSQL | `pgvector/pgvector` | `pg16` | pgvector + pg_trgm + btree_gin included |
| Redis | `redis` | `7.2-alpine` | Stable LTS |
| RocketMQ | `apache/rocketmq` | `5.3.1` | Latest 5.x stable |
| MinIO | `minio/minio` | `RELEASE.2024-11-07T00-52-20Z` | Pinned release tag |
| MinIO MC | `minio/mc` | `RELEASE.2024-11-05T11-29-45Z` | Paired with MinIO release |
| Nacos | `nacos/nacos-server` | `v2.3.2-slim` | Standalone embedded mode |
| XXL-Job Admin | `xuxueli/xxl-job-admin` | `2.4.1` | Uses PostgreSQL (no MySQL needed) |

---

## Init Scripts

| File | Purpose |
|---|---|
| `infra/init/pg-init.sql` | Creates 4 PG extensions on first boot |
| `infra/init/minio-buckets.sh` | Creates 3 MinIO buckets (one-shot via `minio-init` container) |
| `infra/init/nacos-bootstrap-config.json` | Nacos config placeholder structure (S3+ fills real values) |

---

## Connection Strings (Spring application.yaml dev profile)

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:15432/longfeng_dev
    username: postgres
    password: dev
  data:
    redis:
      host: localhost
      port: 16379

rocketmq:
  name-server: localhost:19876

longfeng:
  storage:
    provider: minio
    minio:
      endpoint: http://localhost:19000
      bucket: wrongbook-dev
      access-key: minio
      secret-key: minio12345

spring.cloud.nacos:
  discovery:
    server-addr: localhost:18848
  config:
    server-addr: localhost:18848
    namespace: dev
```
