package com.longfeng.integration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.springframework.beans.factory.annotation.Autowired;

/**
 * S0 smoke test — verifies that:
 * <ol>
 *   <li>A PostgreSQL 16 Testcontainer starts successfully.</li>
 *   <li>Spring Boot Test can bootstrap with the container's datasource.</li>
 *   <li>A trivial {@code SELECT 1} round-trip returns 1.</li>
 * </ol>
 *
 * <p>Naming: {@code *IT} suffix → picked up by maven-surefire {@code includes} override
 * and maven-failsafe {@code **∕*IT.java} pattern (TDD §14.5 CI gate).
 * {@code @Tag("integration")} allows selective execution in multi-module builds:
 * {@code mvn -pl integration-test test -Dgroups=integration}
 *
 * <p>S1 note: This class is the skeleton only. Real IT classes
 * ({@code EbbinghausEndToEndIT}, {@code GuestClaimE2EIT}, etc.) will be added
 * in S1 once Flyway migrations (21 tables + outbox) are in place (TDD §14.3).
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(
        classes = HelloIT.TestApp.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE
)
class HelloIT {

    /**
     * Minimal Spring Boot application definition — lives inside the test class
     * so this module stays source-free (no {@code src/main/java}).
     * S1 builder agents will replace this with a proper multi-service context.
     */
    @SpringBootApplication
    static class TestApp {
        // intentionally empty — auto-configuration does the wiring
    }

    /** Shared PostgreSQL 16 container — reused across all tests in this class. */
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("wrongbook_it")
                    .withUsername("it_user")
                    .withPassword("it_pass");

    /**
     * Feed the container's JDBC URL / credentials into Spring's datasource
     * auto-configuration before the application context is created.
     */
    @DynamicPropertySource
    static void overrideDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name",
                () -> "org.postgresql.Driver");
        // Disable Flyway auto-run in S0 — no migrations exist yet
        registry.add("spring.flyway.enabled", () -> "false");
        // Disable Hibernate DDL — nothing to validate in S0
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @Autowired
    private DataSource dataSource;

    /**
     * Verifies that the PG container is reachable and a trivial query succeeds.
     * This is the single required test for S0 exit gate (TDD §14.3 row 0).
     */
    @Test
    void selectOneShouldReturnOne() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT 1")) {
            rs.next();
            int result = rs.getInt(1);
            assertEquals(1, result, "SELECT 1 must return 1 — PG container is up");
        }
    }
}
