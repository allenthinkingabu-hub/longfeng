# Round 0 · 环境拓扑 + 端口分配

> 生成时间: 2026-05-04
> 任务: 严格全栈 E2E 联调 SC-11 + SC-12 + SC-01

---

## 复用现有 Infra (per user 指令 2026-05-04)

| 中间件 | 容器 | host 端口 | 用途 | 凭证 |
|---|---|---|---|---|
| PostgreSQL | s3-it-pg | 15432 | 主 DB · longfeng_dev (已建) | postgres/wb |
| Redis | s3-it-redis | 16379 | 限流/quota/Bloom | (无密码) |
| RocketMQ NS | s5.5-it-rmq-ns | 19876 | namesrv | — |
| RocketMQ Broker | s5.5-it-rmq-broker | 10911/10909 | broker | — |
| MinIO | lf-dev-minio | 19000 (API) / 19001 (console) | OSS · wrongbook-dev / guest-tmp-dev | minio/minio12345 |
| Nacos | lf-dev-nacos | 18848 / 19848 / 19849 | 注册中心 (dev 关闭使用) | nacos/nacos |
| XXL-Job | lf-dev-xxljob | 18080 | 定时任务 admin | — |

## 待启 BE 服务 (98xx 端口 · per user 指令)

| Service | port | health URL |
|---|---|---|
| gateway | 9880 | http://localhost:9880/actuator/health |
| wrongbook-service | 9881 | http://localhost:9881/actuator/health |
| ai-analysis-service | 9882 | http://localhost:9882/actuator/health |
| review-plan-service | 9883 | http://localhost:9883/actuator/health |
| file-service | 9884 | http://localhost:9884/actuator/health |
| anonymous-service | 9885 | http://localhost:9885/actuator/health |
| FE vite dev | 9873 | http://localhost:9873 (DEV=false 关 MSW) |

## BE 启动必传 env

```bash
# DB
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:15432/longfeng_dev
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=wb

# Redis
export SPRING_DATA_REDIS_HOST=localhost
export SPRING_DATA_REDIS_PORT=16379

# RocketMQ
export ROCKETMQ_NAME_SERVER=localhost:19876

# MinIO (file-service only)
export STORAGE_PROVIDER=minio
export STORAGE_ENDPOINT=http://localhost:19000
export STORAGE_ACCESS_KEY=minio
export STORAGE_SECRET_KEY=minio12345

# LLM (ai-analysis-service only · user 已提供 · 仅 shell env)
export LONGFENG_QIANWEN_KEY=<DashScope key from user 对话>
export LONGFENG_AI_PROVIDER=qianwen
```

## 临时停止的 user 容器 (任务结束可一键恢复)

无 — 全部使用现有 (上面表格已列)。早些时候为消除冲突短暂 stop 的 s3-it-pg / s3-it-redis / s5.5-it-rmq-ns 已 docker start 恢复正常运行。

## L1 静态对账结果 (Round 0)

参见 `round-0-l1-coverage.md` · 总 21 endpoint · 0 完整 · 21 待修。
本次任务覆盖范围 (SC-11/12/01)：9 个核心 endpoint 必修：
- GET /api/landing/samples
- GET /api/landing/kpi
- POST /api/analytics/event (10+ caller)
- GET /api/guest/quota
- POST /api/guest/analyze
- POST /api/file/presign (BE 当前 /api/files · 多 s)
- GET /api/ai/stream/:id (BE 已实现 · 仅需 gateway 路由)
- GET /api/wb/questions/:id (BE 当前 /wrongbook/items/:id · 完全错)
- POST /api/wb/questions/:id/save (BE 完全缺)

其余 12 个 endpoint (Calendar/Review/EventDetail/Settings/Shared 等) 不在本任务范围 · 留给后续 Sprint 处理。
