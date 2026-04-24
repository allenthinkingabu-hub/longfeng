package com.longfeng.fileservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** file-service entry point · 落地计划 §10. */
@SpringBootApplication(scanBasePackages = {"com.longfeng.fileservice", "com.longfeng.common"})
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
