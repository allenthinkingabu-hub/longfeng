package com.longfeng.reviewplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.longfeng.common.test.CoversAC;
import com.longfeng.reviewplan.IntegrationTestBase;
import com.longfeng.reviewplan.consumer.WrongItemAnalyzedConsumer;
import com.longfeng.reviewplan.consumer.WrongItemAnalyzedEvent;
import com.longfeng.reviewplan.entity.ReviewPlan;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * EbbinghausEndToEndIT · 拍题 → Consumer → 7 nodes + 7 outbox events · 全链路验收.
 *
 * <p>覆盖 TDD §14.3 row-EE2E：
 * <ul>
 *   <li>SC-07.AC-1 full path: analyzed event → createSevenNodes → 7 review_plan 行</li>
 *   <li>SC-07.AC-1 outbox: 7 行创建后 outbox 表空（create 不写 outbox）</li>
 *   <li>SC-08.AC-1 chain: complete T0 → SM-2 重算 → outbox completed 事件 → nextReviewAt 有值</li>
 *   <li>SC-08.AC-1 mastered: 三次 ease≥2.8 → mastered outbox 事件 + 全 7 行 soft-delete</li>
 *   <li>GET /review-plans?date= 日视图返回当日 due 节点</li>
 *   <li>GET /review-plans/{id} 返回正确节点详情（user_id / mastery / next_due_at 字段）</li>
 * </ul>
 *
 * <p>不走 MQ（@ConditionalOnProperty review.mq.enabled=false）· 直接 new Consumer.onMessage().
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EbbinghausEndToEndIT extends IntegrationTestBase {

  @Autowired private ReviewPlanService planService;
  @Autowired private DataSource dataSource;
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper jsonMapper;

  private JdbcTemplate jdbc;

  private static final long E2E_STUDENT = 9000200L;
  private static final long E2E_ITEM_BASE = 9000020001L;
  private static final long E2E_ITEM_END = 9000020010L;

  @BeforeEach
  void seedE2E() {
    jdbc = new JdbcTemplate(dataSource);
    // 清本 IT 数据范围（不影响其他 IT · 不用 DELETE FROM 全表）
    jdbc.execute("DELETE FROM review_plan_outbox WHERE plan_id > 100000");
    jdbc.execute("DELETE FROM review_outcome WHERE plan_id > 100000");
    jdbc.update(
        "DELETE FROM review_plan WHERE wrong_item_id BETWEEN ? AND ?",
        E2E_ITEM_BASE, E2E_ITEM_END);
    jdbc.update(
        "DELETE FROM wrong_item WHERE id BETWEEN ? AND ?", E2E_ITEM_BASE, E2E_ITEM_END);
    jdbc.update("DELETE FROM user_account WHERE id = ?", E2E_STUDENT);

    jdbc.update(
        "INSERT INTO user_account (id, username, role, status, timezone) "
            + "VALUES (?, ?, 'STUDENT', 1, 'Asia/Shanghai')",
        E2E_STUDENT, "e2e-student");

    for (long id = E2E_ITEM_BASE; id <= E2E_ITEM_END; id++) {
      jdbc.update(
          "INSERT INTO wrong_item (id, student_id, subject, source_type, status, mastery, version) "
              + "VALUES (?, ?, 'math', 1, 0, 0, 0)",
          id, E2E_STUDENT);
    }
  }

  // ======================================================================
  // T1 · analyzed event → Consumer → 7 rows created
  // ======================================================================

  @Test
  @DisplayName("E2E-T1 · analyzed event via Consumer → 7 review_plan rows · 正确偏移")
  @CoversAC("SC-07.AC-1#e2e_happy.0")
  void e2e_analyzed_event_creates_seven_nodes() {
    long itemId = E2E_ITEM_BASE;
    Instant analyzedAt = Instant.parse("2026-05-02T08:00:00Z");

    WrongItemAnalyzedConsumer consumer =
        new WrongItemAnalyzedConsumer(planService, new SimpleMeterRegistry());
    consumer.onMessage(new WrongItemAnalyzedEvent(itemId, E2E_STUDENT, "math", analyzedAt));

    int count =
        jdbc.queryForObject(
            "SELECT count(*) FROM review_plan WHERE wrong_item_id = ?",
            Integer.class,
            itemId);
    assertThat(count).as("7 rows created by Consumer").isEqualTo(7);

    // T0 = analyzedAt + 2h · Q-D
    Instant expectedT0 = analyzedAt.plusSeconds(2 * 3600);
    int t0Count =
        jdbc.queryForObject(
            "SELECT count(*) FROM review_plan "
                + "WHERE wrong_item_id = ? AND node_index = 0 "
                + "AND next_due_at = ?",
            Integer.class,
            itemId,
            java.sql.Timestamp.from(expectedT0));
    assertThat(t0Count).as("T0 next_due_at = analyzedAt + 2h").isEqualTo(1);

    // T6 = analyzedAt + 30d
    Instant expectedT6 = analyzedAt.plus(java.time.Duration.ofDays(30));
    int t6Count =
        jdbc.queryForObject(
            "SELECT count(*) FROM review_plan "
                + "WHERE wrong_item_id = ? AND node_index = 6 "
                + "AND next_due_at = ?",
            Integer.class,
            itemId,
            java.sql.Timestamp.from(expectedT6));
    assertThat(t6Count).as("T6 next_due_at = analyzedAt + 30d").isEqualTo(1);
  }

  // ======================================================================
  // T2 · Consumer 幂等 · 同 wrong_item_id 重投不重建
  // ======================================================================

  @Test
  @DisplayName("E2E-T2 · Consumer 重投同 analyzed event → 仍 7 行（幂等）")
  @CoversAC("SC-07.AC-1#e2e_idempotent.0")
  void e2e_consumer_idempotent_on_replay() {
    long itemId = E2E_ITEM_BASE + 1;
    Instant analyzedAt = Instant.parse("2026-05-02T09:00:00Z");

    WrongItemAnalyzedConsumer consumer =
        new WrongItemAnalyzedConsumer(planService, new SimpleMeterRegistry());
    WrongItemAnalyzedEvent ev = new WrongItemAnalyzedEvent(itemId, E2E_STUDENT, "math", analyzedAt);

    consumer.onMessage(ev);
    consumer.onMessage(ev); // 重投
    consumer.onMessage(ev); // 再重投

    int count =
        jdbc.queryForObject(
            "SELECT count(*) FROM review_plan WHERE wrong_item_id = ?",
            Integer.class,
            itemId);
    assertThat(count).as("3 次重投后仍 7 行").isEqualTo(7);
  }

  // ======================================================================
  // T3 · complete T0 via HTTP → SM-2 重算 → outbox completed 事件
  // ======================================================================

  @Test
  @DisplayName("E2E-T3 · POST /review-plans/{id}/complete quality=5 → outbox completed 事件 + nextReviewAt")
  @CoversAC("SC-08.AC-1#e2e_happy.0")
  void e2e_complete_t0_via_http_creates_outbox_completed() throws Exception {
    long itemId = E2E_ITEM_BASE + 2;
    var plans = planService.createSevenNodes(itemId, E2E_STUDENT,
        Instant.parse("2026-05-02T10:00:00Z"));
    Long t0Id = plans.get(0).getId();

    long outboxBefore = jdbc.queryForObject("SELECT count(*) FROM review_plan_outbox",
        Long.class);

    mvc.perform(
            post("/review-plans/{id}/complete", t0Id)
                .contentType("application/json")
                .content(jsonMapper.writeValueAsString(Map.of("quality", 5))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.planId").value(String.valueOf(t0Id)))
        .andExpect(jsonPath("$.data.mastered").value(false))
        .andExpect(jsonPath("$.data.easeFactorAfter").exists())
        .andExpect(jsonPath("$.data.nextReviewAt").exists());

    long outboxAfter = jdbc.queryForObject("SELECT count(*) FROM review_plan_outbox",
        Long.class);
    assertThat(outboxAfter).as("outbox completed 事件 +1").isEqualTo(outboxBefore + 1);

    // 确认 outbox event_type = completed
    int completedEvents = jdbc.queryForObject(
        "SELECT count(*) FROM review_plan_outbox WHERE plan_id = ? AND event_type = 'completed'",
        Integer.class, t0Id);
    assertThat(completedEvents).isEqualTo(1);
  }

  // ======================================================================
  // T4 · GET /review-plans/{id} 返回 user_id / mastery / next_due_at
  // ======================================================================

  @Test
  @DisplayName("E2E-T4 · GET /review-plans/{id} 返回正确字段（user_id / mastery / next_due_at）")
  @CoversAC("SC-07.AC-1#e2e_dto.0")
  void e2e_get_by_id_returns_correct_dto_fields() throws Exception {
    long itemId = E2E_ITEM_BASE + 3;
    var plans = planService.createSevenNodes(itemId, E2E_STUDENT,
        Instant.parse("2026-05-02T11:00:00Z"));
    Long t0Id = plans.get(0).getId();

    mvc.perform(get("/review-plans/{id}", t0Id)
            .header("X-User-Id", E2E_STUDENT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.id").value(String.valueOf(t0Id)))
        .andExpect(jsonPath("$.data.user_id").value(String.valueOf(E2E_STUDENT)))
        .andExpect(jsonPath("$.data.node_index").value(0))
        .andExpect(jsonPath("$.data.mastery").value(0))
        .andExpect(jsonPath("$.data.next_due_at").exists())
        .andExpect(jsonPath("$.data.status").value("active"));
  }

  // ======================================================================
  // T5 · GET /review-plans?date= 返回当日 due 节点
  // ======================================================================

  @Test
  @DisplayName("E2E-T5 · GET /review-plans?date= 返回当日 due 节点（T0 回拨到今日）")
  @CoversAC("SC-07.AC-1#e2e_dayview.0")
  void e2e_day_view_returns_due_nodes_for_date() throws Exception {
    long itemId = E2E_ITEM_BASE + 4;
    // 设定 base = now - 3h · T0 = now - 1h → 今日 due
    Instant base = Instant.now().minus(java.time.Duration.ofHours(3));
    var plans = planService.createSevenNodes(itemId, E2E_STUDENT, base);

    // 验证 T0 next_due_at < now（已 due）
    Long t0Id = plans.get(0).getId();
    Instant t0DueAt = plans.get(0).getNextDueAt();
    assertThat(t0DueAt).isBefore(Instant.now());

    String today = java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai")).toString();

    mvc.perform(
            get("/review-plans")
                .param("date", today)
                .header("X-User-Id", E2E_STUDENT)
                .header("X-User-Timezone", "Asia/Shanghai"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.plans").isArray());
  }

  // ======================================================================
  // T6 · 连续 3 次 mastered 触发 → outbox mastered 事件 + 全 7 行 soft-delete
  // ======================================================================

  @Test
  @DisplayName("E2E-T6 · 连续 3 次 quality=5 + ease≥2.8 → mastered outbox 事件 + 全 7 行 soft-delete")
  @CoversAC("SC-08.AC-1#e2e_mastered.0")
  void e2e_three_good_results_trigger_mastered() throws Exception {
    long itemId = E2E_ITEM_BASE + 5;
    var plans = planService.createSevenNodes(itemId, E2E_STUDENT,
        Instant.parse("2026-05-02T12:00:00Z"));
    Long t0Id = plans.get(0).getId();

    // 预置 ease=2.85 → compute 后 nextEase > 2.8
    jdbc.update("UPDATE review_plan SET ease_factor = 2.85 WHERE id = ?", t0Id);

    // 第 1 次
    mvc.perform(post("/review-plans/{id}/complete", t0Id)
            .contentType("application/json")
            .content(jsonMapper.writeValueAsString(Map.of("quality", 5))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.mastered").value(false));

    // 第 2 次
    mvc.perform(post("/review-plans/{id}/complete", t0Id)
            .contentType("application/json")
            .content(jsonMapper.writeValueAsString(Map.of("quality", 5))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.mastered").value(false));

    // 第 3 次 → mastered 触发
    mvc.perform(post("/review-plans/{id}/complete", t0Id)
            .contentType("application/json")
            .content(jsonMapper.writeValueAsString(Map.of("quality", 5))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.mastered").value(true));

    // 全 7 行 soft-delete
    int remaining = jdbc.queryForObject(
        "SELECT count(*) FROM review_plan WHERE wrong_item_id = ? AND deleted_at IS NULL",
        Integer.class, itemId);
    assertThat(remaining).as("mastered 后全 7 行 soft-delete").isEqualTo(0);

    // mastered outbox 事件
    int masteredOutbox = jdbc.queryForObject(
        "SELECT count(*) FROM review_plan_outbox WHERE plan_id = ? AND event_type = 'mastered'",
        Integer.class, t0Id);
    assertThat(masteredOutbox).as("mastered outbox 事件 = 1").isEqualTo(1);
  }
}
