package com.longfeng.aianalysis;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Same fixture pattern as WrongbookIntegrationTestBase — shares s3-it-pg + s3-it-redis. */
public abstract class AiAnalysisIntegrationTestBase {

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
    r.add("rocketmq.name-server", () -> "127.0.0.1:9876");
    r.add("feature.llm-provider", () -> "dashscope");
    // Let AnalysisE2EIT.TestStubs override the production Provider beans.
    r.add("spring.main.allow-bean-definition-overriding", () -> "true");
  }
}
