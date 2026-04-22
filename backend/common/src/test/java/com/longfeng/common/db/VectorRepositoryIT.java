package com.longfeng.common.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.pgvector.PGvector;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S1 · V-S1-04 · pgvector 1024 维读写 · cosine {@code <=>} distance ∈ [0, 2].
 * refs plan §5.7 step 6 · §5.8 · solution doc §4.2.
 */
class VectorRepositoryIT {

    static PgvectorDockerFixture pg;

    @BeforeAll
    static void spinAndMigrate() throws Exception {
        pg = new PgvectorDockerFixture();
        pg.start();
        Flyway.configure()
                .dataSource(pg.jdbcUrl(), pg.user(), pg.password())
                .locations("classpath:db/migration")
                .baselineOnMigrate(false)
                .load()
                .migrate();
    }

    @AfterAll
    static void stopPg() {
        if (pg != null) {
            pg.close();
        }
    }

    @Test
    void insert_and_cosine_query_returns_valid_distance() throws Exception {
        try (Connection cx = pg.connect()) {
            PGvector.addVectorType(cx);

            try (PreparedStatement ps = cx.prepareStatement(
                    "INSERT INTO user_account(id, username, role) VALUES (?, ?, 'STUDENT')")) {
                ps.setLong(1, 1L);
                ps.setString(2, "tester-" + System.nanoTime());
                ps.executeUpdate();
            }

            float[] v1 = new float[1024];
            float[] v2 = new float[1024];
            v1[0] = 1.0f;
            v2[1] = 1.0f;

            try (PreparedStatement ps = cx.prepareStatement(
                    "INSERT INTO wrong_item(id, student_id, subject, source_type, embedding) "
                    + "VALUES (?, 1, 'math', 1, ?)")) {
                ps.setLong(1, 1001L);
                ps.setObject(2, new PGvector(v1));
                ps.executeUpdate();
                ps.setLong(1, 1002L);
                ps.setObject(2, new PGvector(v2));
                ps.executeUpdate();
            }

            try (PreparedStatement ps = cx.prepareStatement(
                    "SELECT id, embedding <=> ? AS cosine_dist FROM wrong_item "
                    + "ORDER BY cosine_dist ASC")) {
                ps.setObject(1, new PGvector(v1));
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getLong("id")).isEqualTo(1001L);
                    double d1 = rs.getDouble("cosine_dist");
                    assertThat(d1).isBetween(0.0, 2.0);
                    assertThat(d1).isLessThan(0.0001);

                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getLong("id")).isEqualTo(1002L);
                    double d2 = rs.getDouble("cosine_dist");
                    assertThat(d2).isBetween(0.0, 2.0);
                    assertThat(d2).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.0001));
                }
            }
        }
    }

    @SuppressWarnings("unused")
    private static ResultSet ignored() throws SQLException {
        return null;
    }
}
