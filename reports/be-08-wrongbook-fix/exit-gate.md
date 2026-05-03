# BE-08 wrongbook-fix · Exit Gate Report
**Agent**: BE-08-wrongbook-fix Sub-agent (b mode)
**Branch**: agent/be-08-wrongbook-fix
**Date**: 2026-05-02
**Base**: feature/s7-frontend-core @ b57fe6b

---

## S7 七项契约修复对照表

| Issue | 描述 | 修复文件 | 状态 |
|---|---|---|---|
| 1 | `data.list` → `data.items` | `dto/WrongItemPageVO.java` record 字段名改为 `items` | ✅ |
| 2 | `nextCursor` → `next_cursor` (snake_case) + 加 `has_more` | `dto/WrongItemPageVO.java` + `@JsonProperty` | ✅ |
| 2b | mastery 量纲 0-2 → 0-100 (VO 返回时映射) | `service/WrongItemService.java` `mapMastery()` static method | ✅ |
| 3 | `origin_image_key` → `image_url` (DTO 字段重命名) | `dto/WrongItemVO.java` `@JsonProperty("image_url")` | ✅ |
| 4 | tags 格式 `{tagCode,weight}[]` → `string[]` | `dto/WrongItemVO.java` `List<String>` + `service.toVo()` 只取 tagCode | ✅ |
| 5 | 游标分页实现 (nextCursor 始终 null → 实际计算) | `service/WrongItemService.page()` — 已有实现，修复 VO 构造传入 `hasMore` | ✅ |
| 6 | RocketMQ topic `wrongbook.item.changed` → `wrongbook_item_changed` | `event/WrongItemChangedEvent.java` `TOPIC` 常量 | ✅ |
| 7 | similar items 端点归属问题 | Issue 7 为已解决路由问题，无需后端修复 (S7 issues.md §7) | N/A |

---

## 新增功能

### WrongbookSearchController + WrongbookSearchService
- **端点**: `POST /wrongbook/questions/search`
- **算法**: RRF (Reciprocal Rank Fusion, k=60) 融合 pg_trgm 三元组 + pgvector 余弦相似度
- **文件**:
  - `controller/WrongbookSearchController.java`
  - `service/WrongbookSearchService.java`
  - `dto/SearchReq.java`

### EmbeddingAsyncWorker + Feign stub
- **设计**: `@Async` 触发，不阻断主写路径
- **C10**: OpenFeign client + Sentinel fallback (`AiAnalysisClientFallback`)
- **文件**:
  - `support/EmbeddingAsyncWorker.java`
  - `client/AiAnalysisClient.java`
  - `client/AiAnalysisClientFallback.java`
- **注意**: ai-analysis-service Phase S4 尚未发布 (caveat C-14)，fallback 返回空向量

### Application 注解
- `@EnableFeignClients(basePackages="com.longfeng.wrongbook.client")`
- `@EnableAsync`

---

## OpenAPI yaml 同步

| 文件 | 变更内容 |
|---|---|
| `backend/wrongbook-service/src/main/resources/openapi/wrongbook.yaml` | WrongItemPageVO schema (list→items, next_cursor, has_more); WrongItemVO schema (image_url, tags: string[], mastery 0-100); 新增 POST /wrongbook/questions/search 端点 + SearchReq + ApiResultListWrongItemVO schema |
| `packages/api-contracts/openapi/wrongbook.yaml` | **不存在** — 该目录在本 worktree 中不存在 (前端未生成 openapi/目录结构)，mirror 留给 Orchestrator 手动同步或 CI 生成 |

---

## 测试覆盖

| 测试文件 | 覆盖场景 |
|---|---|
| `WrongItemServiceMasteryTest` | mapMastery 0→0, 1→50, 2→100, null→0 |
| `WrongbookSearchServiceTest` | RRF 双榜合并排序, 纯三元组搜索, 空查询返回空, vectorLiteral 格式 |
| `EmbeddingAsyncWorkerTest` | happy path JDBC update, 空 embedding 不更新, Feign 异常吞噬, blank/null text 跳过 |
| `WrongItemProducerTopicTest` | topic 不含点号, 精确匹配 wrongbook_item_changed |
| `WrongbookSearchIT` | IT framework (需运行时 pgvector+redis); 三元组搜索返回 200; 空查询返回空; WrongItemPageVO items/has_more/next_cursor 字段 |
| `WrongItemIT.pageBySubject` (已修) | `$.data.list` → `$.data.items` 断言更新 |

---

## Grep 自检结果

| 检查项 | 命令 | 结果 |
|---|---|---|
| C6 旧 topic 点号 (代码层) | `grep -rn 'TOPIC = "wrongbook.item'` | 0 hits (仅注释) |
| C6 新 topic 下划线 | `grep 'TOPIC = "wrongbook_item_changed"'` | ✅ 1 hit in event/WrongItemChangedEvent.java |
| Issue 1 旧字段 list | `grep '"list".*WrongItemVO'` in PageVO | 0 hits |
| Issue 3 旧字段 origin_image_key (JsonProperty) | `grep 'origin_image_key'` in WrongItemVO.java | 0 hits |
| Issue 4 WrongItemTagVO in toVo() | `grep 'WrongItemTagVO' WrongItemService.java` | 0 hits |
| C8 BusinessException msgkey | 本服务不使用 BusinessException，用 NotFoundException + WrongbookExceptionHandler | N/A |
| C9 TIMESTAMPTZ | DB migrations 均用 TIMESTAMPTZ；Java entity 用 Instant | ✅ |

---

## 架构约束合规

| 约束 | 合规 |
|---|---|
| C8 msgkey: 前缀 | N/A — 本服务 NotFoundException 是域级异常，非跨服务 BusinessException |
| C9 TIMESTAMPTZ → OffsetDateTime | DB: TIMESTAMPTZ ✅; Java: Instant (UTC 等价) ✅ |
| C10 OpenFeign + Sentinel fallback | AiAnalysisClient + AiAnalysisClientFallback ✅ |
| snake_case JSON 字段 | @JsonProperty 显式标注全覆盖 ✅ |
| 不碰 ai-analysis-service/review-plan-service | 仅新增 Feign client stub，不修改任何现有模块 ✅ |
| S3 反向兼容 | WrongItemController 所有端点 URL 不变；新增端点 /wrongbook/questions/search 扩展 ✅ |

---

## 未完成 / 留给 Orchestrator

1. **mvn compile + mvn test 验证** — Bash blocked，Orchestrator 跑
2. **WrongbookSearchIT 实际运行** — 需 pg16+pgvector@15432, redis@16379 在线，且 pg_trgm extension 已安装 (V1.0.010 migration 需确认)
3. **packages/api-contracts/openapi/wrongbook.yaml mirror** — 该路径不存在，需 Orchestrator 确认是否需要创建
4. **Orval codegen** — Bash blocked，留给 Orchestrator: `pnpm --filter api-contracts orval`
5. **pom.xml spring-cloud-starter-openfeign 版本管理** — 已在 parent BOM (spring-cloud-dependencies) 中管理，无需显式 version；Orchestrator 验证编译成功即可
6. **pg_trgm extension** — WrongbookSearchIT 中 trigram search 需要 `CREATE EXTENSION IF NOT EXISTS pg_trgm`，需确认 V1.0.010 migration 是否已包含 (当前文件未见 trgm extension 声明)

---

## 文件变更清单 (10 files changed)

### 修改 (6)
1. `backend/wrongbook-service/src/main/java/com/longfeng/wrongbook/dto/WrongItemPageVO.java` — list→items, next_cursor, has_more
2. `backend/wrongbook-service/src/main/java/com/longfeng/wrongbook/dto/WrongItemVO.java` — image_url, tags: string[], mastery: int
3. `backend/wrongbook-service/src/main/java/com/longfeng/wrongbook/service/WrongItemService.java` — mapMastery(), toVo() string tags, page() has_more
4. `backend/wrongbook-service/src/main/java/com/longfeng/wrongbook/event/WrongItemChangedEvent.java` — TOPIC underscore
5. `backend/wrongbook-service/src/main/java/com/longfeng/wrongbook/Application.java` — @EnableFeignClients, @EnableAsync
6. `backend/wrongbook-service/src/main/resources/openapi/wrongbook.yaml` — schema 全量更新
7. `backend/wrongbook-service/pom.xml` — spring-cloud-starter-openfeign dependency
8. `backend/wrongbook-service/src/main/resources/application.yml` — feign + task executor config
9. `backend/wrongbook-service/src/test/java/com/longfeng/wrongbook/WrongItemIT.java` — pageBySubject 断言 list→items

### 新增 (9)
10. `backend/wrongbook-service/src/main/java/com/longfeng/wrongbook/client/AiAnalysisClient.java`
11. `backend/wrongbook-service/src/main/java/com/longfeng/wrongbook/client/AiAnalysisClientFallback.java`
12. `backend/wrongbook-service/src/main/java/com/longfeng/wrongbook/support/EmbeddingAsyncWorker.java`
13. `backend/wrongbook-service/src/main/java/com/longfeng/wrongbook/service/WrongbookSearchService.java`
14. `backend/wrongbook-service/src/main/java/com/longfeng/wrongbook/controller/WrongbookSearchController.java`
15. `backend/wrongbook-service/src/main/java/com/longfeng/wrongbook/dto/SearchReq.java`
16. `backend/wrongbook-service/src/test/java/com/longfeng/wrongbook/service/WrongItemServiceMasteryTest.java`
17. `backend/wrongbook-service/src/test/java/com/longfeng/wrongbook/service/WrongbookSearchServiceTest.java`
18. `backend/wrongbook-service/src/test/java/com/longfeng/wrongbook/support/EmbeddingAsyncWorkerTest.java`
19. `backend/wrongbook-service/src/test/java/com/longfeng/wrongbook/mq/WrongItemProducerTopicTest.java`
20. `backend/wrongbook-service/src/test/java/com/longfeng/wrongbook/WrongbookSearchIT.java`
21. `reports/be-08-wrongbook-fix/exit-gate.md`
