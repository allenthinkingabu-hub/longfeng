package com.longfeng.fileservice;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * S6 IT backbone · 复用常驻容器（与 S3 同款规避 Testcontainers 1.20 + Docker Desktop 4.64+ 不兼容）：
 *
 * <ul>
 *   <li>PostgreSQL 15432 · {@code s3-it-pg}（ops/scripts/it-stack-up.sh 拉起）
 *   <li>MinIO 9000/9001 · {@code s6-it-minio}（本 Phase 新增常驻 · 手工启动见 §10.7 Step 3b）
 * </ul>
 *
 * <p>启动命令：
 *
 * <pre>
 * docker run -d --name s6-it-minio -p 9000:9000 -p 9001:9001 \
 *   -e MINIO_ROOT_USER=minio -e MINIO_ROOT_PASSWORD=minio12345 \
 *   minio/minio:RELEASE.2024-05-01T01-11-10Z server /data --console-address :9001
 * </pre>
 *
 * <p>Testcontainers 恢复 Docker Desktop 兼容后 · 本 backbone 可改回 @Testcontainers 模式.
 */
public abstract class IntegrationTestBase {

  protected static final String DB_URL = "jdbc:postgresql://127.0.0.1:15432/wrongbook";
  protected static final String DB_USER = "postgres";
  protected static final String DB_PASSWORD = "wb";
  protected static final String MINIO_ENDPOINT = "http://127.0.0.1:9000";
  protected static final String MINIO_USER = "minio";
  protected static final String MINIO_PASSWORD = "minio12345";

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> DB_URL);
    r.add("spring.datasource.username", () -> DB_USER);
    r.add("spring.datasource.password", () -> DB_PASSWORD);
    r.add("spring.flyway.url", () -> DB_URL);
    r.add("spring.flyway.user", () -> DB_USER);
    r.add("spring.flyway.password", () -> DB_PASSWORD);
    r.add("spring.flyway.locations", () -> "classpath:db/migration");
    r.add("spring.cache.type", () -> "none");

    r.add("file-service.storage.provider", () -> "minio");
    r.add("file-service.storage.endpoint", () -> MINIO_ENDPOINT);
    r.add("file-service.storage.bucket", () -> "s6-it-bucket");
    r.add("file-service.storage.access-key", () -> MINIO_USER);
    r.add("file-service.storage.secret-key", () -> MINIO_PASSWORD);
    r.add("file-service.storage.presign-ttl-seconds", () -> "900");
    r.add("file-service.storage.max-upload-size", () -> "10485760");
  }
}
