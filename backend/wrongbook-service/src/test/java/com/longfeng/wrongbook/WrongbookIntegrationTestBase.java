package com.longfeng.wrongbook;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * IT backbone — talks to a pgvector/pg16 + redis:7 pair expected to be running on fixed ports
 * (15432 / 16379). Provisioned by ops/scripts/it-stack-up.sh (CI) or by hand during local dev.
 *
 * <p>Rationale (S3 drift-note): Testcontainers 1.20.x currently returns {@code Status 400} when
 * probing Docker Desktop 4.64+ (API 1.53 min-1.44). Running the same two images via plain
 * {@code docker run} lets ITs exercise real Flyway + pgvector + Redis without waiting for an
 * upstream Testcontainers fix. When Testcontainers regains compat, this base can revert to the
 * {@code @Testcontainers} pattern.
 */
public abstract class WrongbookIntegrationTestBase {

  protected static final String DB_URL = "jdbc:postgresql://127.0.0.1:15432/wrongbook";
  protected static final String DB_USER = "postgres";
  protected static final String DB_PASSWORD = "wb";
  protected static final String REDIS_HOST = "127.0.0.1";
  protected static final int REDIS_PORT = 16379;

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> DB_URL);
    r.add("spring.datasource.username", () -> DB_USER);
    r.add("spring.datasource.password", () -> DB_PASSWORD);
    r.add("spring.flyway.url", () -> DB_URL);
    r.add("spring.flyway.user", () -> DB_USER);
    r.add("spring.flyway.password", () -> DB_PASSWORD);
    r.add("spring.data.redis.host", () -> REDIS_HOST);
    r.add("spring.data.redis.port", () -> REDIS_PORT);
    r.add("rocketmq.name-server", () -> "127.0.0.1:9876");
  }
}
