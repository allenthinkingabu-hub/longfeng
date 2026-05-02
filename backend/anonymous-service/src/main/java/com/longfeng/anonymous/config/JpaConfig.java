package com.longfeng.anonymous.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * JPA configuration · anonymous-service · S2 BE-05.
 *
 * <p>Enables JPA auditing (for {@code created_at} / {@code updated_at} via
 * {@link org.springframework.data.annotation.CreatedDate} / {@link
 * org.springframework.data.annotation.LastModifiedDate}) and restricts entity/repo scanning to the
 * anonymous-service package tree.
 */
@Configuration
@EnableJpaRepositories(basePackages = {
    "com.longfeng.anonymous.session",
    "com.longfeng.anonymous.device",
    "com.longfeng.anonymous.ratelimit"
})
@EntityScan(basePackages = "com.longfeng.anonymous.entity")
@EnableJpaAuditing
public class JpaConfig {
  // intentionally empty — annotation-driven
}
