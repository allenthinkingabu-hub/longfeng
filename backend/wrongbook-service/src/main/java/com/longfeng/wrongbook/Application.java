package com.longfeng.wrongbook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;

/**
 * wrongbook-service entry point · 落地计划 §6.
 *
 * <p>S2 scaffold: DataSource / JPA / Flyway excluded until S3 brings real repositories online.
 * {@code scanBasePackages} covers {@code com.longfeng.common} so {@code GlobalExceptionHandler} /
 * {@code TraceIdFilter} activate.
 */
@SpringBootApplication(
    scanBasePackages = {"com.longfeng.wrongbook", "com.longfeng.common"},
    exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class,
        FlywayAutoConfiguration.class
    })
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
