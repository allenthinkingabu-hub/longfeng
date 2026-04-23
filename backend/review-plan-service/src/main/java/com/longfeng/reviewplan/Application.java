package com.longfeng.reviewplan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/** review-plan-service entry point · 落地计划 §9. */
@SpringBootApplication(scanBasePackages = {"com.longfeng.reviewplan", "com.longfeng.common"})
@EnableCaching
@EnableAsync
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
