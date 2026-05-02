package com.longfeng.common.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ShedLock configuration for distributed scheduling · TDD §0.8 / §3.1 / plan §5.S0 BE-01.
 *
 * <p>Enables exactly-once job execution across multiple Kubernetes pod replicas (HPA). Works
 * together with XXL-Job: ShedLock prevents concurrent execution at the JVM level when the same
 * trigger fires on multiple replicas before XXL-Job's own shard assignment completes.
 *
 * <p><b>Prerequisites</b>: the {@code shedlock} table must exist. Create it via Flyway:
 *
 * <pre>{@code
 * CREATE TABLE IF NOT EXISTS shedlock (
 *   name       VARCHAR(64)  NOT NULL,
 *   lock_until TIMESTAMPTZ  NOT NULL,
 *   locked_at  TIMESTAMPTZ  NOT NULL,
 *   locked_by  VARCHAR(255) NOT NULL,
 *   PRIMARY KEY (name)
 * );
 * }</pre>
 *
 * <p>Table creation is delegated to BE-03-Flyway (plan §5.S1) and is not duplicated here.
 *
 * <p>This bean is only registered when both {@link JdbcTemplate} and
 * {@link JdbcTemplateLockProvider} are on the classpath, making it safe to include in
 * {@code common} without forcing a JDBC dependency on services that don't need scheduling.
 */
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M", defaultLockAtLeastFor = "PT5S")
@ConditionalOnClass({JdbcTemplate.class, JdbcTemplateLockProvider.class})
public class ShedLockConfig {

  private static final Logger log = LoggerFactory.getLogger(ShedLockConfig.class);

  /**
   * Creates the {@link LockProvider} backed by PostgreSQL {@code shedlock} table via JDBC.
   *
   * <p>Uses {@link JdbcTemplateLockProvider.Configuration} with
   * {@code usingDbTime()} to avoid clock-skew issues across pod replicas — the lock expiry is
   * calculated using the database server's clock, not the JVM clock.
   *
   * @param jdbcTemplate auto-wired Spring JDBC template (backed by the application DataSource)
   * @return the lock provider
   */
  @Bean
  public LockProvider lockProvider(JdbcTemplate jdbcTemplate) {
    log.info("Registering ShedLock JdbcTemplateLockProvider on table 'shedlock'");
    return new JdbcTemplateLockProvider(
        JdbcTemplateLockProvider.Configuration.builder()
            .withJdbcTemplate(jdbcTemplate)
            .usingDbTime()          // Use DB server clock to avoid pod clock skew
            .build()
    );
  }
}
