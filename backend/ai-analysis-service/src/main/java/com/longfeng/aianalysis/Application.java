package com.longfeng.aianalysis;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/** ai-analysis-service entry point (S4 · §8 落地计划). */
@SpringBootApplication(scanBasePackages = {"com.longfeng.aianalysis", "com.longfeng.common"})
@EnableJpaAuditing
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
