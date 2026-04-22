package com.longfeng.common.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S1 · V-S1-01/02/03 · Flyway migrate on pgvector/pgvector:pg16, assert table/index counts and ivfflat.
 * refs plan §5.7 step 5 · §5.8.
 *
 * <p>Uses {@link PgvectorDockerFixture} directly (docker run via ProcessBuilder) because
 * Testcontainers 1.20.4 is incompatible with Docker Desktop 4.64.0 / Engine 29.2.1
 * (returns 400 BadRequest on /info probe).
 */
class FlywayMigrateIT {

    static PgvectorDockerFixture pg;

    @BeforeAll
    static void spinPg() throws Exception {
        pg = new PgvectorDockerFixture();
        pg.start();
    }

    @AfterAll
    static void stopPg() {
        if (pg != null) {
            pg.close();
        }
    }

    @Test
    void migrate_all_v_scripts_and_assert_schema() throws Exception {
        Flyway flyway = Flyway.configure()
                .dataSource(pg.jdbcUrl(), pg.user(), pg.password())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load();

        MigrateResult result = flyway.migrate();
        assertThat(result.success).isTrue();
        assertThat(result.migrationsExecuted).isGreaterThanOrEqualTo(22);

        try (Connection cx = pg.connect(); Statement st = cx.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT count(*) FROM pg_tables WHERE schemaname='public' "
                    + "AND tablename <> 'flyway_schema_history'")) {
                rs.next();
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(18);
            }

            try (ResultSet rs = st.executeQuery(
                    "SELECT count(*) FROM pg_indexes WHERE schemaname='public'")) {
                rs.next();
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(23);
            }

            try (ResultSet rs = st.executeQuery(
                    "SELECT count(*) FROM pg_constraint c "
                    + "JOIN pg_class cl ON c.conrelid=cl.oid "
                    + "JOIN pg_namespace n ON cl.relnamespace=n.oid "
                    + "WHERE c.contype='c' AND n.nspname='public'")) {
                rs.next();
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(12);
            }

            try (ResultSet rs = st.executeQuery(
                    "SELECT count(*) FROM pg_constraint c "
                    + "JOIN pg_class cl ON c.conrelid=cl.oid "
                    + "JOIN pg_namespace n ON cl.relnamespace=n.oid "
                    + "WHERE c.contype='f' AND n.nspname='public'")) {
                rs.next();
                assertThat(rs.getInt(1)).isGreaterThanOrEqualTo(14);
            }

            try (ResultSet rs = st.executeQuery(
                    "SELECT extname FROM pg_extension WHERE extname='vector'")) {
                assertThat(rs.next()).as("pgvector extension").isTrue();
                assertThat(rs.getString(1)).isEqualTo("vector");
            }

            try (ResultSet rs = st.executeQuery(
                    "SELECT indexname, indexdef FROM pg_indexes "
                    + "WHERE indexname='idx_wrong_item_embedding'")) {
                assertThat(rs.next()).as("ivfflat index").isTrue();
                String def = rs.getString("indexdef");
                assertThat(def).containsIgnoringCase("ivfflat");
                assertThat(def).containsIgnoringCase("vector_cosine_ops");
            }
        }

        MigrateResult rerun = flyway.migrate();
        assertThat(rerun.migrationsExecuted).isZero();
    }
}
