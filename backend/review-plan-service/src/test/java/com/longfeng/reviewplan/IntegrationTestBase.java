package com.longfeng.reviewplan;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * S5 IT backbone · 复用 S3 同款 pgvector/pg16 @ 127.0.0.1:15432（由 ops/scripts/it-stack-up.sh 预起）.
 *
 * <p>为避免 Boot 拉 Nacos/Feign/Sentinel · IT 关掉 feign.sentinel.enabled + spring.cloud.*.enabled.
 */
public abstract class IntegrationTestBase {

  protected static final String DB_URL = "jdbc:postgresql://127.0.0.1:15432/wrongbook";
  protected static final String DB_USER = "postgres";
  protected static final String DB_PASSWORD = "wb";

  @DynamicPropertySource
  static void props(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url", () -> DB_URL);
    r.add("spring.datasource.username", () -> DB_USER);
    r.add("spring.datasource.password", () -> DB_PASSWORD);
    r.add("spring.flyway.url", () -> DB_URL);
    r.add("spring.flyway.user", () -> DB_USER);
    r.add("spring.flyway.password", () -> DB_PASSWORD);
    r.add("spring.flyway.locations", () -> "classpath:db/migration");
    r.add("review.mq.enabled", () -> "false");
    r.add("review.feign.enabled", () -> "false");
    r.add("feign.sentinel.enabled", () -> "false");
    r.add("spring.cache.type", () -> "none"); // IT 关 @Cacheable · 避免跨 test 污染
    r.add("spring.cloud.nacos.discovery.enabled", () -> "false");
    r.add("spring.cloud.nacos.config.enabled", () -> "false");
    r.add("spring.cloud.discovery.enabled", () -> "false");
  }
}
