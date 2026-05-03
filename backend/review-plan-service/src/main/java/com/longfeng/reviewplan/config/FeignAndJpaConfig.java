package com.longfeng.reviewplan.config;

import com.longfeng.reviewplan.feign.CalendarFeignClient;
import com.longfeng.reviewplan.feign.NotificationFeignClient;
import java.util.Collections;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
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

  /**
   * IT stub · review.feign.enabled=false 时注入空实现，避免 controller 构造器 wiring 失败.
   *
   * <p>同时提供 NotificationFeignClient stub：4 channel 均返回 success=true（幂等空实现）。
   */
  @Configuration
  @ConditionalOnProperty(value = "review.feign.enabled", havingValue = "false")
  public static class FeignDisabled {

    @Bean
    CalendarFeignClient calendarFeignClientStub() {
      return date -> Collections.emptyList();
    }

    @Bean
    NotificationFeignClient notificationFeignClientStub() {
      return new NotificationFeignClient() {
        private final SendResp OK = new SendResp(true, "stub-req-id", null, null);

        @Override
        public SendResp sendWxMp(SendReq req) { return OK; }

        @Override
        public SendResp sendApp(SendReq req) { return OK; }

        @Override
        public SendResp sendEmail(SendReq req) { return OK; }

        @Override
        public SendResp sendSms(SendReq req) { return OK; }
      };
    }
  }
}
