package com.longfeng.anonymous;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * anonymous-service entry point · S2 BE-05 · GuestSession / Device / RateLimit · plan §5.S2.
 *
 * <p>JPA + Flyway auto-config enabled (no excludes). ConditionalOnProperty guards XXL-Job job
 * beans so the service starts without an XXL-Job admin in dev/test.
 */
@SpringBootApplication(scanBasePackages = {"com.longfeng.anonymous", "com.longfeng.common"})
@EnableScheduling
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
