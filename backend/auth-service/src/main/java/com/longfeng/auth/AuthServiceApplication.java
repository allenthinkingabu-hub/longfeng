package com.longfeng.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/** auth-service entry point · S7 BUG-LF-09 · WeChat login stub. */
@SpringBootApplication(scanBasePackages = {"com.longfeng.auth", "com.longfeng.common"})
@EnableJpaAuditing
@EnableJpaRepositories(basePackages = "com.longfeng.auth.repository")
public class AuthServiceApplication {

  public static void main(String[] args) {
    SpringApplication.run(AuthServiceApplication.class, args);
  }
}
