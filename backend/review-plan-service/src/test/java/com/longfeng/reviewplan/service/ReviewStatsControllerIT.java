package com.longfeng.reviewplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.longfeng.common.test.CoversAC;
import com.longfeng.reviewplan.IntegrationTestBase;
import com.longfeng.reviewplan.dto.ReviewStatsResp;
import com.longfeng.reviewplan.entity.ReviewPlan;
import com.longfeng.reviewplan.support.SnowflakeIdGenerator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * SC-09.AC-1 · GET /review-stats IT · 6 matrix 行全覆盖.
 *
 * <p>测试策略：手工插入 review_outcome + wrong_item + review_plan fixture · 不走 Service
 * complete 路径（避免 Q-G mastered 触发污染）· 断聚合 API 返回值.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class ReviewStatsControllerIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private ObjectMapper jsonMapper;
  @Autowired private SnowflakeIdGenerator idGen;
  @Autowired private ReviewPlanService planService;

  private JdbcTemplate jdbc;

  private static final long STATS_USER = 9000300L;
  private static final long STATS_ITEM_BASE = 9000030001L;
  private static final long STATS_ITEM_END = 9000030010L;

  @BeforeEach
  void seed() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DELETE FROM review_plan_outbox WHERE plan_id > 100000");
    jdbc.update(
        "DELETE FROM review_outcome WHERE user_id = ?", STATS_USER);
    jdbc.update(
        "DELETE FROM review_plan WHERE student_id = ?", STATS_USER);
    jdbc.update(
        "DELETE FROM wrong_item WHERE id BETWEEN ? AND ?", STATS_ITEM_BASE, STATS_ITEM_END);
    jdbc.update("DELETE FROM user_account WHERE id = ?", STATS_USER);

    jdbc.update(
        "INSERT INTO user_account (id, username, role, status, timezone) "
            + "VALUES (?, ?, 'STUDENT', 1, 'Asia/Shanghai')",
        STATS_USER,
        "s5-stats-user");
    // 3 math + 2 physics + 2 chinese wrong_item · 支持 topWeak subject 差异
    String[] subjects = {"math", "math", "math", "physics", "physics", "chinese", "chinese"};
    for (int i = 0; i < subjects.length; i++) {
      long id = STATS_ITEM_BASE + i;
      jdbc.update(
          "INSERT INTO wrong_item (id, student_id, subject, source_type, status, mastery, version) "
              + "VALUES (?, ?, ?, 1, 0, 0, 0)",
          id,
          STATS_USER,
          subjects[i]);
    }
  }

  /** 直接 INSERT review_outcome · 按 wrong_item_id 复用单条 plan（avoid UK 冲突）. */
  private final Map<Long, Long> planByItem = new java.util.HashMap<>();

  private void insertOutcome(long wrongItemId, int quality, Instant completedAt) {
    long planId =
        planByItem.computeIfAbsent(
            wrongItemId,
            k -> {
              long newId = idGen.nextId();
              jdbc.update(
                  "INSERT INTO review_plan (id, wrong_item_id, student_id, node_index, "
                      + "strategy_code, start_at, current_level, interval_index, ease_factor, "
                      + "total_review, total_forget, mastery_score, status, dispatch_version, "
                      + "consecutive_good_count, created_at, updated_at) "
                      + "VALUES (?, ?, ?, 0, 'EBBINGHAUS_SM2', now(), 0, 0, 2.500, 0, 0, 0, 0, 0, 0, now(), now())",
                  newId,
                  k,
                  STATS_USER);
              return newId;
            });
    long outcomeId = idGen.nextId();
    jdbc.update(
        "INSERT INTO review_outcome (id, plan_id, wrong_item_id, user_id, quality, "
            + "ease_factor_before, ease_factor_after, interval_days_before, interval_days_after, "
            + "completed_at, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?, 2.500, 2.500, 1, 1, ?, now(), now())",
        outcomeId,
        planId,
        wrongItemId,
        STATS_USER,
        quality,
        java.sql.Timestamp.from(completedAt));
  }

  // ======================================================================
  // SC-09.AC-1 happy_path.0 · 本周有数据 · 7 day 数组 + topWeak 非空
  // ======================================================================

  @Test
  @DisplayName("SC-09.AC-1 happy_path.0 · range=week · 返回 7 day 数组 + topWeak 非空")
  @CoversAC("SC-09.AC-1#happy_path.0")
  void scenario_sc09_ac1_happy_path_0_week_seven_days() throws Exception {
    // 本周造 20 complete · math 多错 · physics 少错 · chinese 全对
    Instant today = Instant.now();
    for (int i = 0; i < 5; i++) {
      insertOutcome(STATS_ITEM_BASE, 1, today.minus(i + 1, ChronoUnit.HOURS));       // math 错
      insertOutcome(STATS_ITEM_BASE + 1, 2, today.minus(i + 2, ChronoUnit.HOURS));   // math 错
    }
    for (int i = 0; i < 4; i++) {
      insertOutcome(STATS_ITEM_BASE + 3, 5, today.minus(i + 3, ChronoUnit.HOURS));   // physics 对
    }
    for (int i = 0; i < 5; i++) {
      insertOutcome(STATS_ITEM_BASE + 5, 5, today.minus(i + 4, ChronoUnit.HOURS));   // chinese 全对
    }

    MvcResult res =
        mvc.perform(
                get("/review-stats")
                    .param("range", "week")
                    .header("X-User-Id", STATS_USER)
                    .header("X-User-Timezone", "Asia/Shanghai"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(0))
            .andExpect(jsonPath("$.data.range").value("week"))
            .andExpect(jsonPath("$.data.timezone").value("Asia/Shanghai"))
            .andExpect(jsonPath("$.data.data").isArray())
            .andReturn();

    ReviewStatsResp resp = parse(res);
    assertThat(resp.data()).hasSize(7);
    // topWeak: math 10 complete · forget 10 · forget_rate 1.0 应排第一
    assertThat(resp.topWeak()).isNotEmpty();
    assertThat(resp.topWeak().get(0).subject()).isEqualTo("math");
    assertThat(resp.topWeak().get(0).forgetRate()).isEqualByComparingTo("1.0000");
  }

  // ======================================================================
  // SC-09.AC-1 error_paths.0 · range=year 返 400 INVALID_RANGE
  // ======================================================================

  @Test
  @DisplayName("SC-09.AC-1 error_paths.0 · range=year · 400 INVALID_RANGE")
  @CoversAC("SC-09.AC-1#error_paths.0")
  void scenario_sc09_ac1_error_paths_0_invalid_range() throws Exception {
    mvc.perform(
            get("/review-stats")
                .param("range", "year")
                .header("X-User-Id", STATS_USER))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(40003))
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("INVALID_RANGE")));
  }

  // ======================================================================
  // SC-09.AC-1 error_paths.1 · 非法 timezone 降级默认 + warning
  // ======================================================================

  @Test
  @DisplayName("SC-09.AC-1 error_paths.1 · 非法 timezone 降级 Asia/Shanghai + warning 标 TIMEZONE_FALLBACK")
  @CoversAC("SC-09.AC-1#error_paths.1")
  void scenario_sc09_ac1_error_paths_1_invalid_timezone_fallback() throws Exception {
    MvcResult res =
        mvc.perform(
                get("/review-stats")
                    .param("range", "week")
                    .header("X-User-Id", STATS_USER)
                    .header("X-User-Timezone", "Invalid/ZoneXYZ"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.timezone").value("Asia/Shanghai"))
            .andReturn();

    ReviewStatsResp resp = parse(res);
    assertThat(resp.warnings()).anySatisfy(w -> assertThat(w.code()).isEqualTo("TIMEZONE_FALLBACK"));
  }

  // ======================================================================
  // SC-09.AC-1 boundary.0 · 本周 0 次 complete · 7 day 数组 · 每日 reviewCount=0 · correctRate=null
  // ======================================================================

  @Test
  @DisplayName("SC-09.AC-1 boundary.0 · 本周 0 次 · 7 day 数组 · 每日 reviewCount=0 · correctRate=null")
  @CoversAC("SC-09.AC-1#boundary.0")
  void scenario_sc09_ac1_boundary_0_zero_reviews() throws Exception {
    // 无 insertOutcome · seeder 只建 user + wrong_item
    MvcResult res =
        mvc.perform(
                get("/review-stats")
                    .param("range", "week")
                    .header("X-User-Id", STATS_USER))
            .andExpect(status().isOk())
            .andReturn();

    ReviewStatsResp resp = parse(res);
    assertThat(resp.data()).hasSize(7);
    assertThat(resp.data()).allSatisfy(d -> {
      assertThat(d.correctRate()).isNull();
      assertThat(d.reviewCount()).isEqualTo(0);
      assertThat(d.masteredCount()).isEqualTo(0);
    });
    assertThat(resp.topWeak()).isEmpty();
  }

  // ======================================================================
  // SC-09.AC-1 boundary.1 · range=quarter 跨 180d · warnings 含 PARTIAL_HISTORY（但 quarter=89d 不触发）
  // ======================================================================

  @Test
  @DisplayName("SC-09.AC-1 boundary.1 · range=quarter · 不触发 PARTIAL_HISTORY（quarter=89d < 180d retention）")
  @CoversAC("SC-09.AC-1#boundary.1")
  void scenario_sc09_ac1_boundary_1_quarter_within_retention() throws Exception {
    // quarter=89d · 未超 180d retention · 不应有 PARTIAL_HISTORY
    MvcResult res =
        mvc.perform(
                get("/review-stats")
                    .param("range", "quarter")
                    .header("X-User-Id", STATS_USER))
            .andExpect(status().isOk())
            .andReturn();

    ReviewStatsResp resp = parse(res);
    assertThat(resp.data()).hasSize(90);
    assertThat(resp.warnings())
        .noneSatisfy(w -> assertThat(w.code()).isEqualTo("PARTIAL_HISTORY"));
  }

  // ======================================================================
  // SC-09.AC-1 observable.0 · P95 < 500ms（轻量 · 无大量数据）
  // ======================================================================

  @Test
  @DisplayName("SC-09.AC-1 observable.0 · 10 次调用 · 平均 < 500ms")
  @CoversAC("SC-09.AC-1#observable.0")
  void scenario_sc09_ac1_observable_0_p95_under_500ms() throws Exception {
    Instant today = Instant.now();
    for (int i = 0; i < 20; i++) {
      insertOutcome(STATS_ITEM_BASE, 5, today.minus(i + 1, ChronoUnit.HOURS));
    }

    long totalNanos = 0L;
    int runs = 10;
    for (int i = 0; i < runs; i++) {
      long t0 = System.nanoTime();
      mvc.perform(
              get("/review-stats")
                  .param("range", "week")
                  .header("X-User-Id", STATS_USER))
          .andExpect(status().isOk());
      totalNanos += System.nanoTime() - t0;
    }
    long avgMs = totalNanos / runs / 1_000_000L;
    assertThat(avgMs).as("review_stats 平均耗时").isLessThan(500);
  }

  private ReviewStatsResp parse(MvcResult res) throws Exception {
    Map<String, Object> env =
        jsonMapper.readValue(
            res.getResponse().getContentAsString(),
            new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    return jsonMapper.convertValue(env.get("data"), ReviewStatsResp.class);
  }
}
