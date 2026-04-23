package com.longfeng.reviewplan.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** 启用 JPA + Feign 扫描 · IT 可通过 {@code review.feign.enabled=false} 关 Feign 部分避免 Nacos 依赖. */
@Configuration
@EnableJpaRepositories(basePackages = "com.longfeng.reviewplan.repo")
@EntityScan(basePackages = "com.longfeng.reviewplan.entity")
@EnableJpaAuditing
public class FeignAndJpaConfig {

  @Configuration
  @EnableFeignClients(basePackages = "com.longfeng.reviewplan.feign")
  @ConditionalOnProperty(
      value = "review.feign.enabled",
      havingValue = "true",
      matchIfMissing = true)
  public static class FeignEnabled {}
}
