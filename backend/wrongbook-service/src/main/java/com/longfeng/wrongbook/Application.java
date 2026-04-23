package com.longfeng.wrongbook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * wrongbook-service entry point · 错题主域（S3 · §7 落地计划）.
 *
 * <p>S3 activates DataSource / JPA / Flyway (previously excluded at S2). scanBasePackages
 * covers {@code com.longfeng.common} so filters / exception advice pick up.
 */
@SpringBootApplication(scanBasePackages = {"com.longfeng.wrongbook", "com.longfeng.common"})
@EnableJpaAuditing
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
