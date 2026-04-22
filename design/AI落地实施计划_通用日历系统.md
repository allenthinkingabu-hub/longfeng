# AI 可执行落地实施计划 — 通用日历系统（Java 云原生 / 支持多关联项）

> 本计划对应设计文档：`通用日历系统数据模型设计（支持多关联项：学习_任务_提醒_备忘）.md`
> 目标读者：执行型 AI Agent（Claude Code / Cowork / 其他自动化代理）
> 执行原则：**每一阶段 = 可执行命令 + 可验证断言 + 明确的回滚策略**。AI 在未通过本阶段 "验证步骤" 前，不得进入下一阶段。
> 工作根目录约定：`$PROJECT_ROOT = /Users/allenwang/build/longfeng/calendar-platform`
>
> **技术栈调整（v2）**：数据库统一使用 **PostgreSQL 16**；持久层统一使用 **Spring Data JPA（Hibernate 6.x）**，不引入 MyBatis / MyBatis-Plus。所有 DDL、连接串、分库分表、读写分离均按 PostgreSQL 方言落地。

---

## 0. 执行总览（Roadmap）

| 阶段 | 名称 | 预计耗时 | 产出物 | 强依赖 |
|------|------|---------|--------|--------|
| P0 | 环境体检与工具安装 | 10 min | 环境检查报告 `env-check.log` | 无 |
| P1 | Maven 多模块骨架 | 20 min | 8 个模块 `pom.xml` | P0 |
| P2 | 基础设施本地化（docker-compose） | 20 min | PostgreSQL/Redis/RocketMQ/Nacos 全部 healthy | P0 |
| P3 | 数据库 DDL + 初始化数据（PostgreSQL） | 30 min | Flyway 迁移全部 SUCCESS | P2 |
| P4 | 通用 common 模块（基类 / 工具 / 异常） | 40 min | `common-core` 构建通过 | P1 |
| P5 | Calendar Core Service（核心域） | 90 min | CRUD + 关联接口 e2e 通过 | P3, P4 |
| P6 | User Setting Service | 40 min | 用户偏好 / 时区接口通过 | P4 |
| P7 | Calendar Reminder Service + XXL-Job | 60 min | 定时扫描任务可跑通 | P5 |
| P8 | Notification Service（多渠道） | 40 min | Mock 渠道推送通过 | P5 |
| P9 | Spring Cloud Gateway | 30 min | 统一路由 + JWT + X-Timezone 透传 | P5~P8 |
| P10 | 国际化与时区全链路 | 30 min | i18n 单元测试 100% 通过 | P5 |
| P11 | 分层缓存（Caffeine + Redis） | 40 min | 缓存命中率测试 ≥ 90% | P5 |
| P12 | ShardingSphere 分库分表 | 50 min | 分片路由单测通过 | P3 |
| P13 | 集成测试 + 契约测试 | 60 min | Testcontainers 全绿 | P5~P9 |
| P14 | Kubernetes 部署清单 | 40 min | `kubectl apply --dry-run` 通过 | P9 |
| P15 | 可观测性（Prometheus/Grafana/ELK） | 30 min | `/actuator/prometheus` 返回指标 | P14 |
| P16 | 端到端冒烟用例 | 20 min | 冒烟脚本 `smoke.sh` 全部 PASS | 全部 |

**总里程碑**：P0→P16 全部 PASS 即视为 MVP 落地完成。

---

## P0. 环境体检与工具安装

### 目标
确认执行机上存在 JDK 17+、Maven 3.9+、Docker Desktop（含 Compose v2）、kubectl、Git，版本齐备。

### AI 执行步骤

1. 在 `$PROJECT_ROOT` 下创建目录：
   ```bash
   mkdir -p /Users/allenwang/build/longfeng/calendar-platform && cd $_
   ```
2. 写入环境检查脚本 `scripts/env-check.sh`：
   ```bash
   #!/usr/bin/env bash
   set -e
   {
     echo "=== JDK ===";        java -version 2>&1
     echo "=== Maven ===";      mvn -v
     echo "=== Docker ===";     docker --version
     echo "=== Compose ===";    docker compose version
     echo "=== kubectl ===";    kubectl version --client=true --output=yaml || true
     echo "=== Git ===";        git --version
   } | tee env-check.log
   ```
3. 执行：`bash scripts/env-check.sh`

### 验证步骤（AI 自校验断言）
- 断言 `env-check.log` 包含字符串：`openjdk version "17` 或更高、`Apache Maven 3.9`、`Docker version 24` 或更高、`Compose version v2`。
- 命令：
  ```bash
  grep -E "openjdk version \"(1[7-9]|[2-9][0-9])" env-check.log
  grep -E "Apache Maven (3\.9|[4-9])" env-check.log
  grep -E "Docker version (2[4-9]|[3-9][0-9])" env-check.log
  ```
- 任一 `grep` 返回非 0 即视为失败。

### 回滚
- 若 JDK/Maven 缺失：提示用户安装 SDKMAN `curl -s "https://get.sdkman.io" | bash && sdk install java 17.0.10-tem && sdk install maven 3.9.6`，AI 不可擅自 `sudo`。

---

## P1. Maven 多模块骨架

### 目标
生成符合 DDD 边界上下文的 8 模块工程：
```
calendar-platform/
├── pom.xml                        # 根 POM（dependencyManagement）
├── common-core/                   # 基类、异常、工具、TenantContext
├── common-api/                    # OpenFeign 客户端 + DTO
├── calendar-core-service/         # P5
├── user-setting-service/          # P6
├── calendar-reminder-service/     # P7
├── notification-service/          # P8
├── gateway-service/               # P9
└── integration-test/              # P13
```

### AI 执行步骤

1. 生成根 `pom.xml`：`<packaging>pom</packaging>`，`<modules>` 列出上面 8 个；`dependencyManagement` 中固定：
   - Spring Boot `3.2.5`
   - Spring Cloud `2023.0.1`
   - Spring Cloud Alibaba `2023.0.1.0`
   - **Spring Data JPA**（随 `spring-boot-starter-data-jpa`，Hibernate 6.4.x）
   - **PostgreSQL JDBC** `42.7.3`（`org.postgresql:postgresql`）
   - **Flyway** `10.10.0` + `flyway-database-postgresql`
   - **QueryDSL-JPA** `5.0.0`（复杂查询用，避免回到 MyBatis）
   - ShardingSphere-JDBC `5.4.1`（PostgreSQL 方言）
   - Redisson `3.27.0`
   - XXL-Job `2.4.1`
   - Testcontainers `1.19.7`（含 `testcontainers:postgresql`）
2. 每个子模块的 `pom.xml`：声明 `<parent>` 指向根 POM；禁止在子模块重复声明 Spring Boot 版本。
3. **持久层约束（强制）**：业务服务统一引入 `spring-boot-starter-data-jpa` + `org.postgresql:postgresql`；**禁止** 引入任何 `mybatis-*` / `mybatis-plus-*` / `mysql-connector-*` 依赖；CI 通过 grep 脚本硬校验。
4. 在根目录建立 `.editorconfig`、`.gitignore`（忽略 `target/`、`*.iml`、`logs/`）、`README.md`（指向本计划文件）。

### 验证步骤
```bash
cd /Users/allenwang/build/longfeng/calendar-platform
mvn -q -DskipTests -N verify         # 根 POM 可解析
mvn -q -DskipTests validate          # 全部子模块结构合法
test -f common-core/pom.xml && test -f gateway-service/pom.xml

# 持久层硬约束：整个仓库不得出现 mybatis / mysql-connector 依赖
! grep -RnoE 'mybatis|mysql-connector' --include=pom.xml .
# 必须存在 JPA + PostgreSQL
grep -Rq 'spring-boot-starter-data-jpa' --include=pom.xml . && \
grep -Rq 'postgresql' --include=pom.xml .
```
断言：8 个子模块 `pom.xml` 均存在、`mvn validate` 退出码为 0、无 MyBatis/MySQL 残留、JPA+PostgreSQL 依赖到位。

### 回滚
- 删除 `$PROJECT_ROOT` 并重跑 P1。

---

## P2. 基础设施本地化（docker-compose）

### 目标
一键拉起 **PostgreSQL 16（1 主 1 流复制从）**、**PgBouncer**（连接池）、Redis Cluster（3M3S）、RocketMQ（1 nameserver + 1 broker）、Nacos 2.3（后端存储指向 PostgreSQL）、XXL-Job-Admin、Elasticsearch 8、Kibana、Prometheus、Grafana，全部 healthy。

### AI 执行步骤

1. 写入 `infra/docker-compose.yml`；关键要点：
   - **`postgres-primary`**：`image: postgres:16-alpine`，`POSTGRES_USER=calendar`、`POSTGRES_PASSWORD=calendar`、`POSTGRES_DB=calendar_db`；挂载 `init/00-extensions.sql`（`CREATE EXTENSION IF NOT EXISTS pgcrypto; pg_trgm; btree_gin;`）和 `init/01-wal.conf`（`wal_level=replica`、`max_wal_senders=8`、`hot_standby=on`）。
   - **`postgres-standby`**：`image: postgres:16-alpine`，启动脚本执行 `pg_basebackup -h postgres-primary -U replicator -D /var/lib/postgresql/data -Fp -Xs -P -R`；同一 `calendar-net` 内通过主机名解析。
   - **`pgbouncer`**：`image: edoburu/pgbouncer`，`pool_mode=transaction`，分别暴露到主（5433）和从（5434）。
   - Redis 使用 `redis:7.2` + `redis-cli --cluster create ...` 初始化 6 实例。
   - **Nacos**：`MODE=standalone`、`SPRING_DATASOURCE_PLATFORM=postgresql`（Nacos 2.3+ 原生支持 PG）；挂载官方 `nacos-pg-schema.sql`。
   - 统一网络 `calendar-net`，所有端口 bind `127.0.0.1` 避免暴露。
2. 写入启动脚本 `infra/up.sh`：
   ```bash
   #!/usr/bin/env bash
   set -e
   docker compose -f infra/docker-compose.yml up -d
   echo "Waiting for PostgreSQL primary..."
   for i in $(seq 1 30); do
     docker compose -f infra/docker-compose.yml exec -T postgres-primary \
       pg_isready -U calendar -d calendar_db >/dev/null 2>&1 && break
     sleep 2
   done
   echo "Waiting for PostgreSQL standby replication..."
   for i in $(seq 1 30); do
     docker compose -f infra/docker-compose.yml exec -T postgres-primary \
       psql -U calendar -d calendar_db -tAc \
       "select count(*) from pg_stat_replication" | grep -q '^1$' && break
     sleep 2
   done
   echo "Waiting for Nacos..."
   for i in $(seq 1 60); do
     curl -sf http://127.0.0.1:8848/nacos/actuator/health >/dev/null && break
     sleep 2
   done
   ```

### 验证步骤
```bash
bash infra/up.sh
docker compose -f infra/docker-compose.yml ps --format json | \
  jq -r '.[] | select(.State!="running") | .Name' | \
  (! grep .)                             # 没有非 running 容器

# PG 主库可连 + 版本正确
PGPASSWORD=calendar psql -h 127.0.0.1 -p 5432 -U calendar -d calendar_db -tAc \
  "select version()" | grep -qE 'PostgreSQL 16'

# 流复制在线
PGPASSWORD=calendar psql -h 127.0.0.1 -p 5432 -U calendar -d calendar_db -tAc \
  "select state from pg_stat_replication" | grep -q 'streaming'

# PgBouncer 可达
PGPASSWORD=calendar psql -h 127.0.0.1 -p 5433 -U calendar -d calendar_db -tAc "select 1" | grep -q '^1$'

curl -fsS http://127.0.0.1:8848/nacos/actuator/health | grep -q '"status":"UP"'
redis-cli -h 127.0.0.1 -p 7000 cluster info | grep -q 'cluster_state:ok'
curl -fsS http://127.0.0.1:9200 | grep -q '"tagline"'
```
全部断言通过 = P2 PASS。

### 回滚
`docker compose -f infra/docker-compose.yml down -v` 后重试；若仍失败，打印 `docker compose logs` 最后 200 行并停止。

---

## P3. 数据库 DDL 与 Flyway 初始化（PostgreSQL 16）

### 目标
将设计文档第二章的 DDL 以 **PostgreSQL 方言** 完整落地为 Flyway 迁移脚本，并在 PG 主库执行成功。

### 方言差异对照（必须遵守）
| MySQL 语法 | PostgreSQL 替换 |
|-----------|----------------|
| `AUTO_INCREMENT` | `GENERATED ALWAYS AS IDENTITY` 或 `BIGSERIAL` |
| `tinyint` | `smallint`（或 `boolean` 当仅 0/1） |
| `datetime` | `timestamp(6) without time zone`（**全部存 UTC**） |
| `json` | `jsonb` |
| 反引号 `` ` `` | 全部去掉，必要时双引号 `"` |
| `ENGINE=InnoDB ... utf8mb4` | 移除（PG 集群建库时 `ENCODING 'UTF8'` 一次性指定） |
| `COMMENT '...'`（行尾） | `COMMENT ON COLUMN ... IS '...'` 独立语句 |
| `show index` | `\d+ table_name` 或查询 `pg_indexes` |
| 布尔型 `tinyint(1)` | `boolean`（推荐；`is_deleted`、`is_valid`、`is_triggered`） |

### AI 执行步骤

1. 在 `calendar-core-service/src/main/resources/db/migration/` 下创建（PostgreSQL 版本）：
   - `V1__create_calendar_main.sql`
   - `V2__create_calendar_relation.sql`
   - `V3__create_business_tables.sql`（`kp_learn` / `task_info` / `remind_info` / `memo_info`）
   - `V4__create_calendar_reminder.sql`
   - `V5__create_calendar_notify_record.sql`（唯一键 `uk_tenant_calendar_channel`）
   - `V6__create_calendar_operation_log.sql`
   - `V7__create_sys_user_and_setting.sql`

2. **`V1__create_calendar_main.sql` 参考实现（PostgreSQL）**：
   ```sql
   CREATE TABLE calendar_main (
     id                     bigint      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
     tenant_id              varchar(32) NOT NULL,
     user_id                bigint      NOT NULL,
     calendar_title         varchar(128) NOT NULL,
     calendar_date          date        NOT NULL,
     calendar_time          time        NULL,
     status                 smallint    NOT NULL DEFAULT 0,
     is_valid               boolean     NOT NULL DEFAULT true,
     remind_ahead_minute    integer     NOT NULL,
     notify_channels        jsonb       NULL,
     relation_id            bigint      NOT NULL,
     sort                   integer     NOT NULL DEFAULT 99,
     is_deleted             boolean     NOT NULL DEFAULT false,
     create_time            timestamp(6) NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
     update_time            timestamp(6) NOT NULL DEFAULT (now() AT TIME ZONE 'UTC'),
     create_by              varchar(64) NOT NULL,
     last_modified_by       varchar(64) NOT NULL
   );
   COMMENT ON TABLE  calendar_main                      IS '日历主表-统一事项载体';
   COMMENT ON COLUMN calendar_main.calendar_date        IS '事项日期(UTC+0)';
   COMMENT ON COLUMN calendar_main.status               IS '0-待执行 1-已完成 2-已跳过 3-逾期 4-已顺延';
   COMMENT ON COLUMN calendar_main.notify_channels      IS 'JSONB 数组,覆盖全局渠道配置';

   CREATE UNIQUE INDEX uk_tenant_user_date_relation
     ON calendar_main (tenant_id, user_id, calendar_date, relation_id)
     WHERE is_deleted = false;

   CREATE INDEX idx_tenant_user_date_status
     ON calendar_main (tenant_id, user_id, calendar_date, status)
     WHERE is_deleted = false;

   CREATE INDEX idx_tenant_create_time
     ON calendar_main (tenant_id, create_time);

   -- update_time 触发器（PG 没有 ON UPDATE 语法糖）
   CREATE OR REPLACE FUNCTION set_update_time() RETURNS trigger AS $$
   BEGIN
     NEW.update_time := (now() AT TIME ZONE 'UTC');
     RETURN NEW;
   END; $$ LANGUAGE plpgsql;

   CREATE TRIGGER trg_calendar_main_update
     BEFORE UPDATE ON calendar_main
     FOR EACH ROW EXECUTE FUNCTION set_update_time();
   ```

3. 其余表沿用同一模板：`bigint GENERATED ALWAYS AS IDENTITY` 主键、`boolean is_deleted`、`timestamp UTC` 时间列、`jsonb` 数组列、`set_update_time` 触发器。软删除条件索引（`WHERE is_deleted = false`）代替 MySQL 的普通索引 + Hibernate Filter，进一步降低索引体积。

4. **Hibernate 配置**（`application.yml`）：
   ```yaml
   spring:
     datasource:
       url: jdbc:postgresql://127.0.0.1:5432/calendar_db?reWriteBatchedInserts=true
       username: calendar
       password: calendar
       driver-class-name: org.postgresql.Driver
     jpa:
       database-platform: org.hibernate.dialect.PostgreSQLDialect
       hibernate.ddl-auto: validate        # 由 Flyway 管理 DDL，Hibernate 只做校验
       properties:
         hibernate.jdbc.time_zone: UTC
         hibernate.type.preferred_jdbc_type_code_for_java_time: TIMESTAMP
         hibernate.jdbc.batch_size: 50
     flyway:
       enabled: true
       locations: classpath:db/migration
       baseline-on-migrate: true
   ```

5. 运行 Flyway：
   ```bash
   mvn -pl calendar-core-service flyway:migrate \
     -Dflyway.url=jdbc:postgresql://127.0.0.1:5432/calendar_db \
     -Dflyway.user=calendar -Dflyway.password=calendar
   ```

### 验证步骤
```bash
export PGPASSWORD=calendar
PSQL="psql -h 127.0.0.1 -p 5432 -U calendar -d calendar_db -tAc"

# 1. 所有表存在
$PSQL "select tablename from pg_tables where schemaname='public' order by 1" > /tmp/tables.txt
for t in calendar_main calendar_relation calendar_reminder calendar_notify_record \
         calendar_operation_log kp_learn task_info remind_info memo_info user_setting; do
  grep -qx "$t" /tmp/tables.txt || { echo "MISSING: $t"; exit 1; }
done

# 2. 索引存在
$PSQL "select indexname from pg_indexes where tablename='calendar_main'" \
  | grep -q 'idx_tenant_user_date_status'

# 3. jsonb 列类型正确（而不是 json 或 text）
$PSQL "select data_type from information_schema.columns
       where table_name='calendar_main' and column_name='notify_channels'" \
  | grep -qx 'jsonb'

# 4. Hibernate 校验通过（ddl-auto=validate 启动不抛异常）
mvn -pl calendar-core-service spring-boot:run >/tmp/boot.log 2>&1 &
BOOT_PID=$!
for i in $(seq 1 40); do
  curl -fsS http://127.0.0.1:8081/actuator/health | grep -q '"status":"UP"' && break
  sleep 2
done
kill $BOOT_PID
! grep -q 'Schema-validation' /tmp/boot.log
```
全部断言通过 = P3 PASS。

### 回滚
```bash
# Flyway clean（仅开发环境允许）
mvn -pl calendar-core-service flyway:clean -Dflyway.cleanDisabled=false \
  -Dflyway.url=jdbc:postgresql://127.0.0.1:5432/calendar_db \
  -Dflyway.user=calendar -Dflyway.password=calendar
```
修正 SQL 后重跑。

---

## P4. common-core 模块

### 目标
沉淀跨服务复用能力：`TenantAuditEntity`（JPA 基类）/ `TenantContextHolder` / `TimeZoneContextHolder` / `BusinessException` / `ApiResponse<T>` / `IdGenerator`（雪花，备用）/ `TimeUtils`（UTC↔Zone）/ `JsonUtils` / `JsonbAttributeConverter`（`List<String>` ↔ `jsonb`）/ `RequestIdFilter`。

### AI 执行步骤

1. `TenantAuditEntity`：严格按设计文档 §2.1.1 的 Hibernate 风格落地，但主键策略改为 **`GenerationType.IDENTITY`** 对应 PG 的 `GENERATED ALWAYS AS IDENTITY`；`@FilterDef(name="tenantFilter", parameters=@ParamDef(name="tenantId", type=String.class))` + `@Filter(name="tenantFilter", condition="tenant_id = :tenantId")`；`@EntityListeners(AuditingEntityListener.class)` 保持不变。
2. **`jsonb` 映射策略**：为 `List<String> notifyChannels` 提供 `@Convert(converter = JsonbStringListConverter.class)`；Converter 内部调用 Jackson 并把结果作为字符串写入；字段 DDL 已是 `jsonb`，Hibernate 通过 `@JdbcTypeCode(SqlTypes.JSON)` 也可替代，任选其一但**全仓库统一**。示例：
   ```java
   @Converter
   public class JsonbStringListConverter implements AttributeConverter<List<String>, String> {
       public String convertToDatabaseColumn(List<String> attr) { return JsonUtils.toJson(attr); }
       public List<String> convertToEntityAttribute(String db) { return JsonUtils.fromJsonList(db, String.class); }
   }
   ```
3. **Spring Data JPA 启用**：`@EnableJpaAuditing(auditorAwareRef = "tenantAuditorAware")`；`AuditorAware<String>` 返回 `tenantId + ":" + userId`。
4. **仓储约束**：所有业务 Repository 统一继承 `JpaRepository<E, Long>` + `JpaSpecificationExecutor<E>`；复杂查询用 **QueryDSL**（`JPAQueryFactory`），禁止手写 SQL 拼接；必要时用 `@Query(nativeQuery = true, value = "...")` 但必须标注 `-- reason:` 说明原因。
5. `TenantContextHolder` / `TimeZoneContextHolder`：基于 `TransmittableThreadLocal`（com.alibaba:transmittable-thread-local:2.14.5），保证 `@Async` / RocketMQ 消费线程可透传。
6. 在 `spring.factories`（或 `@AutoConfiguration`）注册自动配置，业务服务引入依赖即可生效。
7. 提供 `TenantFilter` Servlet 过滤器：从 JWT 里取 `tenantId`、`userId`、`timeZone`、`locale`，写入上下文；同时 `sessionFactory.unwrap(Session.class).enableFilter("tenantFilter").setParameter("tenantId", tenantId)` 注入 Hibernate Filter；`finally` 中 `clear()`。

### 验证步骤
```bash
mvn -pl common-core -am test
# 断言关键类存在
for c in TenantAuditEntity TenantContextHolder TimeZoneContextHolder \
         ApiResponse BusinessException RequestIdFilter JsonbStringListConverter; do
  find common-core/src -name "$c.java" | grep -q . || { echo "MISSING: $c"; exit 1; }
done
# 禁止任何 mybatis 相关类出现
! grep -RnE 'org\.apache\.ibatis|mybatis' common-core/src
```
同时编写单元测试覆盖：
- UTC → `Asia/Shanghai` 转换；
- ThreadLocal 在 `CompletableFuture.runAsync` 中透传；
- `JsonbStringListConverter` 往返序列化；
- `TenantAuditEntity` 在嵌入式 PG（Testcontainers）中插入/更新时间自动填充。覆盖率 ≥ 80%（JaCoCo）。

### 回滚
修正后重跑 `mvn test`；若 JPA 依赖冲突，收敛到根 POM `dependencyManagement`。

---

## P5. Calendar Core Service（核心领域服务）

### 目标
实现设计文档 §5.1/5.2/5.3 的 3 大流程：多关联项接入、日历可视化、动态调整。

### AI 执行步骤（子阶段）

**P5.1 领域层**
- 实体：`CalendarMain`、`CalendarRelation`、`KpLearn`、`TaskInfo`、`RemindInfo`、`MemoInfo`。
- 值对象：`RelationType`（枚举 1-4）、`CalendarStatus`（枚举 0-4）。
- 领域事件：`CalendarEventCreatedEvent`、`CalendarEventUpdatedEvent`。

**P5.2 仓储层（纯 Spring Data JPA + QueryDSL）**
- **全部写路径**走 `JpaRepository`（自动审计 / Hibernate Filter 过滤租户 & 软删）。
- **全部读路径**走 `JpaRepository` + `JpaSpecificationExecutor` + QueryDSL `JPAQueryFactory`（复杂动态条件）。
- 不允许引入 MyBatis / MyBatis-Plus；必须手写 SQL 的场景使用 `@Query(nativeQuery=true)`，并在注释中标注原因。
- `CalendarMainRepository`：
  ```java
  public interface CalendarMainRepository extends JpaRepository<CalendarMain, Long>,
                                                   JpaSpecificationExecutor<CalendarMain> {
      @Query("""
          select c from CalendarMain c
          where c.tenantId = :tenantId
            and c.userId   = :userId
            and c.calendarDate between :start and :end
            and c.isValid  = true
          order by c.calendarDate, c.sort
      """)
      List<CalendarMain> findDayRange(@Param("tenantId") String tenantId,
                                       @Param("userId") Long userId,
                                       @Param("start") LocalDate start,
                                       @Param("end")   LocalDate end);
  }
  ```
  该 JPQL 会被 Hibernate 编译为使用覆盖索引 `idx_tenant_user_date_status` 的 SQL（通过 `EXPLAIN` 验证）。
- `jsonb` 字段查询（如按通知渠道筛选）使用 PG 原生操作符，走 `@Query(nativeQuery=true)`：
  ```java
  @Query(value = "select * from calendar_main where tenant_id=:t and notify_channels @> :ch::jsonb",
         nativeQuery = true)
  List<CalendarMain> findByChannelContains(@Param("t") String t, @Param("ch") String channelJson);
  ```

**P5.3 应用层（Service）**
- `CalendarEventAppService.createEvent(CreateEventCmd)`：
  1. 入参校验（`@Valid`）。
  2. 幂等：读取请求头 `Request-Id`，先查 `calendar_operation_log`。
  3. 写业务子表 → `@PostPersist` 发 `CalendarEventCreatedEvent`。
  4. 监听器写 `calendar_main` + `calendar_relation`（同一本地事务）。
  5. 发送 RocketMQ `calendar-event-created` 事件。
- `CalendarEventAppService.changeStatus(ChangeStatusCmd)`：Redisson 锁 `calendar:lock:{id}` + 状态机校验。

**P5.4 接口层（REST）**
- `POST /api/v1/calendar/events`（创建）
- `GET  /api/v1/calendar/events?startDate&endDate&view=day|week|month`
- `PATCH /api/v1/calendar/events/{id}/status`
- `DELETE /api/v1/calendar/events/{id}`（软删）
- 统一响应 `ApiResponse<T>`；错误码分段：`CAL-1xxx`（参数）、`CAL-2xxx`（业务）、`CAL-5xxx`（系统）。

### 验证步骤

1. 单元测试：`mvn -pl calendar-core-service test`，覆盖率 ≥ 75%。
2. SpringBootTest + Testcontainers（PostgreSQL 16 + Redis + RocketMQ）端到端：
   ```java
   @Test void createLearnEvent_shouldGenerateCalendarAndRelation() { ... }
   @Test void changeStatus_concurrentUpdate_onlyOneWins() { ... } // 用 CountDownLatch
   @Test void dayView_returnsUserTimezoneTime() { ... }          // Asia/Shanghai
   ```
3. 启动服务：
   ```bash
   mvn -pl calendar-core-service spring-boot:run &
   sleep 20
   curl -fsS -XPOST http://127.0.0.1:8081/api/v1/calendar/events \
     -H "Content-Type: application/json" -H "Request-Id: t-001" \
     -H "Authorization: Bearer <test-jwt>" \
     -d '{"title":"复习Java","relationType":1,"calendarDate":"2026-04-20","calendarTime":"14:30:00","timeZone":"Asia/Shanghai"}' \
     | jq -e '.code=="0"'
   ```

### 回滚
保留 Flyway 记录，清空业务表（PG 语法，级联且重置序列）：
```sql
TRUNCATE TABLE calendar_main, calendar_relation, kp_learn, task_info, remind_info, memo_info,
               calendar_reminder, calendar_notify_record, calendar_operation_log
  RESTART IDENTITY CASCADE;
```

---

## P6. User Setting Service

### 目标
管理用户的 `time_zone` / `locale` / `default_remind_minute` / `notify_channels`，供其他服务 Feign 调用。

### AI 执行步骤
1. 领域：`UserSetting`（1:1 与 `sys_user`）。
2. 接口：
   - `GET /api/v1/user-settings/{userId}`
   - `PUT /api/v1/user-settings/{userId}`
3. 暴露 Feign 客户端到 `common-api`：`UserSettingFeignClient`，带 Sentinel fallback（返回系统默认值）。
4. 内置缓存：Caffeine（10 min）+ Redis（24h），Key `user:setting:{tenantId}:{userId}`。

### 验证步骤
- 单测：更新偏好后 fallback 不生效，缓存命中。
- 端到端：
  ```bash
  curl -fsS http://127.0.0.1:8082/api/v1/user-settings/456 | jq -e '.data.timeZone'
  ```

---

## P7. Calendar Reminder Service + XXL-Job

### 目标
实现设计文档 §5.4 精准提醒流程：每分钟扫描，分片、幂等、重试、多渠道。

### AI 执行步骤
1. 引入 `xxl-job-core`，配置 `xxl.job.admin.addresses=http://127.0.0.1:8089/xxl-job-admin`。
2. `ReminderScanJobHandler`：
   - `@XxlJob("reminderScanJob")`
   - 分片参数：`XxlJobHelper.getShardIndex()` / `getShardTotal()`，SQL where `mod(crc32(tenant_id), #{total}) = #{index}`。
   - Redisson 锁 `reminder:lock:{reminderId}`（TTL 10s）。
   - 幂等：`update ... where is_triggered=0`（affected rows = 1 才继续）。
3. 调用 `NotificationFeignClient.push(...)`。

### 验证步骤
```bash
# 在 XXL-Job Admin UI 中新增执行器 + 任务，或通过 API：
curl -XPOST http://127.0.0.1:8089/xxl-job-admin/jobinfo/add -d '...'
# 手动触发一次
curl -XPOST 'http://127.0.0.1:8089/xxl-job-admin/jobinfo/trigger?id=1'
# 断言：提醒记录 is_triggered 变为 1
PGPASSWORD=calendar psql -h 127.0.0.1 -p 5432 -U calendar -d calendar_db -tAc \
  "select count(*) from calendar_reminder where is_triggered=true" | grep -qv '^0$'
```

---

## P8. Notification Service（多渠道）

### 目标
统一封装 SMS / APP / EMAIL / INBOX 四通道，支持 Mock 模式便于本地验证。

### AI 执行步骤
1. 策略模式：`ChannelPusher` 接口 + 4 个实现，`@ConditionalOnProperty` 控制；所有实现发送失败时抛 `NotifyException`，由外层重试 3 次（Spring Retry）。
2. Mock 模式：写入本地文件 `logs/mock-notify.log`，便于 P16 E2E 断言。
3. 防重推：`calendar_notify_record` 唯一键兜底 + Redis `SETNX ntf:{cid}:{ch}`。

### 验证步骤
```bash
curl -XPOST http://127.0.0.1:8084/api/v1/notify/push \
  -H 'Content-Type: application/json' \
  -d '{"calendarId":1,"channels":["sms","app"],"content":"test"}' | jq -e '.code=="0"'
grep -c "calendarId=1" logs/mock-notify.log | grep -qv '^0$'
```

---

## P9. Spring Cloud Gateway

### 目标
统一入口，承担：路由、JWT 校验、`X-Timezone` 透传、Sentinel 限流、CORS。

### AI 执行步骤
1. 路由：
   - `/api/v1/calendar/**` → `calendar-core-service`
   - `/api/v1/user-settings/**` → `user-setting-service`
   - `/api/v1/notify/**` → `notification-service`
2. 全局 Filter `JwtAuthGlobalFilter`：解析 JWT，将 `tenantId/userId/timeZone/locale` 注入下游 header。
3. Sentinel：`spring-cloud-starter-alibaba-sentinel-gateway`，规则通过 Nacos 下发。

### 验证步骤
```bash
curl -fsS -H "Authorization: Bearer <jwt>" http://127.0.0.1:8080/api/v1/calendar/events?startDate=2026-04-20&endDate=2026-04-20
# 未带 token 应返回 401
curl -o /dev/null -w "%{http_code}" http://127.0.0.1:8080/api/v1/calendar/events | grep -q 401
```

---

## P10. 国际化与时区全链路

### 目标
全链路 UTC 存储 + 动态时区展示 + i18n 文案。

### AI 执行步骤
1. `i18n/messages_{zh_CN,en_US,ja_JP}.properties` 至少覆盖：`calendar.title`、`calendar.status.{0..4}`、错误码 `CAL-1xxx~CAL-5xxx`。
2. `MessageSource` 配置 `basename=i18n/messages, encoding=UTF-8, cache-duration=3600s`。
3. `LocaleResolver`：基于 `Accept-Language` 头 + 用户偏好兜底。
4. Jackson `ObjectMapper` 配置：序列化 `ZonedDateTime` 为 ISO-8601，反序列化支持多种格式。

### 验证步骤
```java
@Test void format_asiaShanghai_returnsLocal() { /* ... */ }
@Test void format_americaNewYork_returnsLocal() { /* ... */ }
@Test void i18n_switchLocale_returnsTranslated() { /* ... */ }
```
```bash
mvn -pl calendar-core-service -Dtest='I18nAndTimezoneIT' test
```

---

## P11. 分层缓存

### 目标
Caffeine（JVM）+ Redis Cluster 双层 + 主动失效 + 缓存预热 + 防穿透/击穿/雪崩。

### AI 执行步骤
1. `@Cacheable(cacheManager="twoLevelCacheManager")`，Key `tenantId:user:{uid}:date:yyyyMMdd`。
2. 空值缓存（value 占位 `__NULL__`，TTL 60s）防穿透。
3. 热点 Key 互斥锁（Redisson `tryLock(3, 10, SECONDS)`）防击穿。
4. TTL 随机抖动 `24h ± 30min` 防雪崩。
5. `ApplicationReadyEvent` 时为最近 7 天活跃租户预热。

### 验证步骤
- JMH 或简单压测脚本：1000 次查询命中率 ≥ 90%。
- 单元测试：状态变更后缓存被删除（`cache.get(key) == null`）。

---

## P12. ShardingSphere 分库分表（PostgreSQL）

### 目标
`tenant_id` 一致性哈希分 8 库；`calendar_main` 按 `calendar_date` 月份分表。**所有 8 个库均为 PostgreSQL 16，可用 PG 原生分区表（`PARTITION BY RANGE`）作为补充方案。**

### AI 执行步骤
1. 引入 `shardingsphere-jdbc-core`，并在 `pom.xml` 排除 MySQL 驱动（仅保留 `org.postgresql:postgresql`）。
2. `application-sharding.yml`（**PostgreSQL 方言**）：
   ```yaml
   dataSources:
     ds_0: { driverClassName: org.postgresql.Driver, jdbcUrl: jdbc:postgresql://127.0.0.1:5432/calendar_db_0, username: calendar, password: calendar }
     # ds_1 ... ds_7 同上，DB 名递增
   rules:
     - !SHARDING
       tables:
         calendar_main:
           actualDataNodes: ds_${0..7}.calendar_main_${202601..202712}
           databaseStrategy: { standard: { shardingColumn: tenant_id,    shardingAlgorithmName: db-consistent-hash } }
           tableStrategy:    { standard: { shardingColumn: calendar_date, shardingAlgorithmName: tbl-month } }
         calendar_relation:
           actualDataNodes: ds_${0..7}.calendar_relation
           databaseStrategy: { standard: { shardingColumn: tenant_id, shardingAlgorithmName: db-consistent-hash } }
       bindingTables:
         - calendar_main, calendar_relation
       shardingAlgorithms:
         db-consistent-hash:
           type: CLASS_BASED
           props:
             strategy: STANDARD
             algorithmClassName: com.lf.calendar.sharding.ConsistentHashAlgorithm
         tbl-month:
           type: INTERVAL
           props:
             datetime-pattern: yyyy-MM-dd
             datetime-lower: 2026-01-01
             datetime-upper: 2027-12-31
             sharding-suffix-pattern: yyyyMM
             datetime-interval-amount: 1
             datetime-interval-unit: MONTHS
   props:
     sql-show: true
   ```
3. **JPA 与 ShardingSphere 协作**：将 ShardingSphere 提供的 `DataSource` 直接注入 `LocalContainerEntityManagerFactoryBean`；Hibernate 仍使用 `PostgreSQLDialect`，无需额外方言适配。
4. **替代方案（可选）**：单库内若数据量更大，可对 `calendar_main_yyyyMM` 进一步使用 PG 原生 `PARTITION BY RANGE (calendar_date)`，由 `pg_partman` 自动维护分区；ShardingSphere 仍负责跨库路由。
5. `calendar_relation`、`calendar_reminder` 通过 `bindingTables` 与 `calendar_main` 绑定，避免笛卡尔积。

### 验证步骤
- 单测：插入 `tenantId=t-001, calendarDate=2026-04-20` → 路由到 `ds_X.calendar_main_202604`；通过 `props.sql-show=true` 或 `SQLHintInterceptor` 抓取真实 SQL 断言。
- 命令：
  ```bash
  mvn -pl calendar-core-service -Dtest='ShardingRouteTest' test
  # 8 个库的实际表存在性
  for i in 0 1 2 3 4 5 6 7; do
    PGPASSWORD=calendar psql -h 127.0.0.1 -p 5432 -U calendar -d calendar_db_$i -tAc \
      "select count(*) from pg_tables where tablename like 'calendar_main_%'" \
      | awk -v i=$i '$1<1{print "ds_"i" missing tables"; exit 1}'
  done
  ```

---

## P13. 集成测试（Testcontainers）

### 目标
用 `integration-test` 模块跑全链路场景，不依赖本地 docker-compose。

### AI 执行步骤
1. Testcontainers：**`PostgreSQLContainer<>("postgres:16-alpine")`**、GenericContainer(Redis)、RocketMQ 自研 container 或 Nacos embedded；通过 `@DynamicPropertySource` 注入 `spring.datasource.url`、`spring.flyway.url`，确保每个测试类独立的隔离 DB。
2. 场景用例：
   - C1：创建学习项 → 自动生成日历 + 关联 + 提醒记录。
   - C2：跨时区（Asia/Shanghai、America/New_York）查询日视图，时间正确。
   - C3：并发更新状态，只有一次成功。
   - C4：提醒定时任务触发 → Mock Notification 写入 log。
   - C5：软删后查询不可见；带 `includeDeleted=true` 参数可见。

### 验证步骤
```bash
mvn -pl integration-test verify
# surefire/failsafe 报告：0 failure, 0 error
```

---

## P14. Kubernetes 部署清单

### 目标
每个微服务：`Deployment` + `Service`（ClusterIP）+ `HPA` + `ConfigMap` + `Secret`；对外：`Ingress`。

### AI 执行步骤
1. 生成 `k8s/` 目录，按服务拆分：`calendar-core.yaml` 等。
2. 资源配额：`requests: cpu=1, memory=2Gi`；`limits: cpu=2, memory=4Gi`。
3. 健康检查：`livenessProbe` / `readinessProbe` 均指向 `/actuator/health`，`initialDelaySeconds=30/5`。
4. HPA：`targetCPUUtilizationPercentage=70`，`minReplicas=2, maxReplicas=10`。
5. `Ingress`：`nginx.ingress.kubernetes.io/ssl-redirect: "true"`。

### 验证步骤
```bash
kubectl apply --dry-run=client -f k8s/              # 全部 created (dry run)
kubeval k8s/                                       # schema 校验
conftest test k8s/ --policy policies/              # OPA 策略（可选）
```

### 回滚
修正 YAML，`dry-run` 通过即视为 PASS。无需真正 apply。

---

## P15. 可观测性

### 目标
`/actuator/prometheus` 暴露业务指标；Grafana 仪表盘有 QPS/RT/错误率/缓存命中率。

### AI 执行步骤
1. 引入 `spring-boot-starter-actuator` + `micrometer-registry-prometheus`。
2. 自定义指标：`calendar.event.create.count`、`calendar.cache.hit`、`reminder.push.fail`。
3. `prometheus.yml` 拉取目标：`calendar-core-service:8081/actuator/prometheus` 等。
4. Grafana 导入 JSON 模板（随方案交付 `infra/grafana/calendar-dashboard.json`）。

### 验证步骤
```bash
curl -fsS http://127.0.0.1:8081/actuator/prometheus | grep -q 'calendar_event_create_count'
curl -fsS http://127.0.0.1:9090/api/v1/targets | jq -e '.data.activeTargets | map(.health) | all(.=="up")'
```

---

## P16. 端到端冒烟（MUST PASS）

### 目标
一条命令跑完：创建事件 → 查询日视图 → 触发提醒 → 状态变更 → 缓存失效 → 多租户隔离。

### AI 执行步骤（脚本 `scripts/smoke.sh`）
```bash
#!/usr/bin/env bash
set -euo pipefail
BASE=http://127.0.0.1:8080
T1_JWT=$(./scripts/gen-jwt.sh tenant-A user-1 Asia/Shanghai zh_CN)
T2_JWT=$(./scripts/gen-jwt.sh tenant-B user-2 America/New_York en_US)

# 1. 租户A 创建学习事件
CID=$(curl -fsS -XPOST $BASE/api/v1/calendar/events \
  -H "Authorization: Bearer $T1_JWT" -H "Request-Id: smk-1" \
  -H "Content-Type: application/json" \
  -d '{"title":"Hibernate 复习","relationType":1,"calendarDate":"2026-04-20","calendarTime":"14:30:00"}' \
  | jq -r '.data.id')

# 2. 日视图查询（上海时区 14:30）
curl -fsS "$BASE/api/v1/calendar/events?startDate=2026-04-20&endDate=2026-04-20" \
  -H "Authorization: Bearer $T1_JWT" | jq -e '.data[0].calendarTime=="14:30:00"'

# 3. 租户B 不应看到 CID
curl -fsS "$BASE/api/v1/calendar/events?startDate=2026-04-20&endDate=2026-04-20" \
  -H "Authorization: Bearer $T2_JWT" | jq -e ".data | map(.id==$CID) | any | not"

# 4. 标记完成
curl -fsS -XPATCH $BASE/api/v1/calendar/events/$CID/status \
  -H "Authorization: Bearer $T1_JWT" -H "Request-Id: smk-2" \
  -H "Content-Type: application/json" -d '{"newStatus":1}' | jq -e '.code=="0"'

# 5. 手动触发提醒扫描
curl -XPOST 'http://127.0.0.1:8089/xxl-job-admin/jobinfo/trigger?id=1' >/dev/null
sleep 3
grep -q "calendarId=$CID" logs/mock-notify.log

echo "SMOKE PASS"
```

### 验证步骤
```bash
bash scripts/smoke.sh && echo "P16 OK"
```
**退出码非 0 = 全部计划失败，必须从失败阶段回退重试。**

---

## 附录 A：目录最终结构

```
calendar-platform/
├── AI落地实施计划_通用日历系统.md   ← 本文件
├── pom.xml
├── common-core/
├── common-api/
├── calendar-core-service/
├── user-setting-service/
├── calendar-reminder-service/
├── notification-service/
├── gateway-service/
├── integration-test/
├── infra/
│   ├── docker-compose.yml
│   ├── up.sh
│   └── grafana/calendar-dashboard.json
├── k8s/
│   ├── calendar-core.yaml
│   ├── user-setting.yaml
│   ├── calendar-reminder.yaml
│   ├── notification.yaml
│   ├── gateway.yaml
│   └── ingress.yaml
├── scripts/
│   ├── env-check.sh
│   ├── smoke.sh
│   └── gen-jwt.sh
└── logs/                 ← .gitignore
```

## 附录 B：失败处理通用原则

1. **阶段级隔离**：每阶段成功后必须 `git commit`，便于 `git reset --hard <tag>` 回退。打标 `v0.P0`、`v0.P1` … `v0.P16`。
2. **错误预算**：单阶段重试不超过 3 次，累计失败 2 个阶段即暂停并输出诊断报告给用户。
3. **AI 严格遵循**：任何偏离计划的"创造性"改动需先在 PR 描述里写清理由，否则拒绝合入。
4. **禁止跳过验证步骤**：AI 不能以"预期会通过"代替实际执行验证命令；每一步验证命令必须真实执行并将 stdout/stderr 附在阶段交付物中。

## 附录 C：AI 执行契约（必须遵守）

- 所有命令均以非交互方式执行（`-y`、`--batch`、`--no-tty` 等）。
- 所有阻塞等待使用 `for/sleep` + 探测，最多 5 分钟；超时即判失败。
- 不得调用 `sudo`；需要提权的操作一律记录为"人工介入"并停止。
- 不得读取或上传用户真实敏感数据；测试数据使用 `tenant-A/B` 占位。
- 每阶段完成后写入 `docs/progress/P{n}.md`：包含命令、stdout 摘要、验证结论、耗时。
- **持久层硬约束**：整个仓库必须同时满足 (a) 不存在 `mybatis` / `mysql-connector` 依赖；(b) 不存在 `Mapper.xml` 或 `@Mapper`；(c) 所有 `@Entity` 子类继承 `TenantAuditEntity`；(d) DDL 仅通过 Flyway 管理，Hibernate `ddl-auto=validate`。CI 脚本：
  ```bash
  ! grep -RnE 'mybatis|mysql-connector' --include='pom.xml' .
  ! find . -name '*Mapper.xml'
  ! grep -RnE '@Mapper|org\.apache\.ibatis' --include='*.java' .
  grep -Rq 'ddl-auto: validate' --include='application*.yml' .
  ```

---

**本方案一共 17 个阶段（P0–P16），每阶段均有可执行命令 + 可验证断言 + 失败回滚。AI 按序执行并在每阶段通过后方可进入下一阶段，最终通过 P16 冒烟测试视为 MVP 落地完成。**

**v2 变更摘要**：数据库统一为 **PostgreSQL 16**（主从流复制 + PgBouncer）；持久层统一为 **Spring Data JPA + Hibernate 6.4 + QueryDSL**，移除 MyBatis-Plus 及所有 MySQL 驱动；DDL 采用 PG 方言（`IDENTITY`、`jsonb`、`boolean`、触发器维护 `update_time`、软删条件索引）；分库分表由 ShardingSphere-JDBC 以 PG 方言驱动 8 库；测试容器切换为 `PostgreSQLContainer`。
