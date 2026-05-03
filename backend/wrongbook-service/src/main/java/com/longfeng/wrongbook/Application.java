package com.longfeng.wrongbook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * wrongbook-service entry point · 错题主域（S3 · §7 落地计划）.
 *
 * <p>S3 activates DataSource / JPA / Flyway (previously excluded at S2). scanBasePackages
 * covers {@code com.longfeng.common} so filters / exception advice pick up.
 * S4: {@code @EnableFeignClients} for ai-analysis stub; {@code @EnableAsync} for embedding worker.
 */
@SpringBootApplication(scanBasePackages = {"com.longfeng.wrongbook", "com.longfeng.common"})
@EnableJpaAuditing
@EnableFeignClients(basePackages = "com.longfeng.wrongbook.client")
@EnableAsync
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
