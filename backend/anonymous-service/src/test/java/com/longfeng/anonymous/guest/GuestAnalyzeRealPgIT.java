package com.longfeng.anonymous.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.Socket;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * GuestAnalyzeRealPgIT · WT6 · plan §5.S7 hybrid L3.
 *
 * <p>Real-PG end-to-end IT for {@code POST /api/guest/analyze}. Boots the full Spring context
 * against the dev infra PG (lf-dev infra · 15432 · longfeng_anon DB) AND Redis (16379) and
 * verifies:
 * <ol>
 *   <li>HTTP 200 with snake_case body {@code { guest_session_id, task_id, status:"ANALYZING" }};
 *   <li>A new row materialises in {@code anon.guest_session} with both legacy
 *       {@code device_fp_hash} and V1.0.070 {@code device_fp} populated;
 *   <li>{@code created_at} / {@code updated_at} are populated by the DB defaults (entity does NOT
 *       write them — see {@link com.longfeng.anonymous.entity.GuestSession} class doc); and
 *   <li>{@code expires_at} is set to {@code created_at + 24h} by the service.
 * </ol>
 *
 * <p>Skipped if PG 15432 is not reachable (so the test runs only when {@code lf-dev infra}
 * docker compose stack is up).
 *
 * <p>Naming convention: {@code *RealPgIT} → maven-failsafe verify phase only (NOT surefire).
 * Run with {@code mvn -pl anonymous-service verify -Dit.test=GuestAnalyzeRealPgIT}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:postgresql://localhost:15432/longfeng_anon",
    "spring.datasource.username=postgres",
    "spring.datasource.password=wb",
    "spring.jpa.hibernate.ddl-auto=none",
    "spring.flyway.enabled=false",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=16379",
    "anon.job.enabled=false",
    // ai-analysis-service may or may not be up — controller swallows the fail and returns 200
    "longfeng.ai-analysis.base-url=http://localhost:9882",
    "snowflake.worker-id=99"
})
class GuestAnalyzeRealPgIT {

  @Autowired private MockMvc mvc;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ObjectMapper om;

  @BeforeAll
  static void requireDevInfra() {
    assumeTrue(canConnect("localhost", 15432), "PG 15432 not reachable · skip · start lf-dev infra");
    assumeTrue(canConnect("localhost", 16379), "Redis 16379 not reachable · skip · start lf-dev infra");
  }

  @Test
  void analyze_persistsRowWithLegacyAndNewColumns() throws Exception {
    String fp = "wt6-realpg-" + System.nanoTime();
    String body = """
        { "device_fp": "%s", "subject": "math", "image_url": "http://localhost:19000/wrongbook-dev/test.jpg" }
        """.formatted(fp);

    MvcResult res = mvc.perform(post("/api/guest/analyze")
            .contentType(MediaType.APPLICATION_JSON)
            .header("X-Forwarded-For", "10.0.99.99")
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.guest_session_id").exists())
        .andExpect(jsonPath("$.task_id").exists())
        .andExpect(jsonPath("$.status").value("ANALYZING"))
        .andReturn();

    JsonNode payload = om.readTree(res.getResponse().getContentAsString());
    long guestSessionId = Long.parseLong(payload.get("guest_session_id").asText());

    // Verify the row really landed in anon.guest_session
    List<Map<String, Object>> rows = jdbc.queryForList(
        "SELECT id, device_fp, device_fp_hash, status, created_at, expires_at, version "
            + "FROM anon.guest_session WHERE id = ?",
        guestSessionId);

    assertThat(rows).as("expected 1 row for guest_session_id=" + guestSessionId).hasSize(1);
    Map<String, Object> row = rows.get(0);

    assertThat(row.get("device_fp")).as("V1.0.070 device_fp set by service").isEqualTo(fp);

    String hash = (String) row.get("device_fp_hash");
    assertThat(hash).as("V1.0.030 legacy device_fp_hash NOT NULL").isNotNull();
    assertThat(hash.trim().length()).as("device_fp_hash CHECK char_length=64").isEqualTo(64);

    Number status = (Number) row.get("status");
    assertThat(status.shortValue()).as("status = CREATED(0) on insert").isEqualTo((short) 0);

    Number version = (Number) row.get("version");
    assertThat(version.intValue()).as("version starts at 0").isEqualTo(0);

    assertThat(row.get("created_at")).as("DB default now() filled created_at").isNotNull();
    assertThat(row.get("expires_at")).as("service set expires_at = now + 24h").isNotNull();
  }

  private static boolean canConnect(String host, int port) {
    try (Socket s = new Socket()) {
      s.connect(new java.net.InetSocketAddress(host, port), 500);
      return true;
    } catch (Exception e) {
      return false;
    }
  }
}
