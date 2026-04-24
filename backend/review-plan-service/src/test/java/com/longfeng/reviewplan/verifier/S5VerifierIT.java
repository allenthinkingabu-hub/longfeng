package com.longfeng.reviewplan.verifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.longfeng.common.test.CoversAC;
import com.longfeng.reviewplan.IntegrationTestBase;
import com.longfeng.reviewplan.consumer.WrongItemAnalyzedConsumer;
import com.longfeng.reviewplan.consumer.WrongItemAnalyzedEvent;
import com.longfeng.reviewplan.service.ReviewPlanService;
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
import org.springframework.test.web.servlet.MockMvc;

/**
 * S5 Verifier · 独立复写 critical AC (§25 Playbook Step 8 · §1.5 通用约束 #14).
 *
 * <p>独立路径（与 Builder Agent 的 ReviewPlanServiceIT 测试代码隔离 · 相同 oracle 不同实现）：
 *
 * <ul>
 *   <li>SC-07.AC-1 · 走 Consumer.onMessage（非直接调 Service.createSevenNodes）
 *   <li>SC-08.AC-1 · 走 MockMvc POST Controller（非直接调 Service.complete）
 *   <li>SC-10.AC-1 · fallback UT 已独立覆盖 · 本 IT 不再重复
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class S5VerifierIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private ReviewPlanService service;
  @Autowired private DataSource dataSource;
  @Autowired private ObjectMapper jsonMapper;

  private JdbcTemplate jdbc;

  private static final long V_STUDENT = 9000099L;
  private static final long V_ITEM_BASE = 9000009001L;
  private static final long V_ITEM_END = 9000009010L;

  @BeforeEach
  void seed() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DELETE FROM review_plan_outbox WHERE plan_id > 100000");
    jdbc.execute("DELETE FROM review_outcome WHERE plan_id > 100000");
    jdbc.update(
        "DELETE FROM review_plan WHERE wrong_item_id BETWEEN ? AND ?", V_ITEM_BASE, V_ITEM_END);
    jdbc.update("DELETE FROM wrong_item WHERE id BETWEEN ? AND ?", V_ITEM_BASE, V_ITEM_END);
    jdbc.update("DELETE FROM user_account WHERE id = ?", V_STUDENT);

    jdbc.update(
        "INSERT INTO user_account (id, username, role, status, timezone) "
            + "VALUES (?, ?, 'STUDENT', 1, 'Asia/Shanghai')",
        V_STUDENT,
        "s5-verifier");
    for (long id = V_ITEM_BASE; id <= V_ITEM_END; id++) {
      jdbc.update(
          "INSERT INTO wrong_item (id, student_id, subject, source_type, status, mastery, version) "
              + "VALUES (?, ?, 'math', 1, 0, 0, 0)",
          id,
          V_STUDENT);
    }
  }

  // ======================================================================
  // SC-07.AC-1 · Verifier 复写 · 走 Consumer.onMessage 路径
  // ======================================================================

  @Test
  @DisplayName("[Verifier] SC-07.AC-1 happy_path.0 · Consumer.onMessage → 7 行（独立复写）")
  @CoversAC("SC-07.AC-1#happy_path.0")
  void verifier_sc07_ac1_consumer_path() {
    // Verifier 独立复写策略：绕 MQ listener 注册 · 直接 new Consumer 手调 onMessage
    WrongItemAnalyzedConsumer consumer =
        new WrongItemAnalyzedConsumer(service, new SimpleMeterRegistry());
    WrongItemAnalyzedEvent ev =
        new WrongItemAnalyzedEvent(
            V_ITEM_BASE, V_STUDENT, "math", Instant.parse("2026-04-24T10:00:00Z"));

    consumer.onMessage(ev);

    int rows =
        jdbc.queryForObject(
            "SELECT count(*) FROM review_plan WHERE wrong_item_id = ?",
            Integer.class,
            V_ITEM_BASE);
    assertThat(rows).as("consumer 消费 analyzed 事件后 plan 行数").isEqualTo(7);
  }

  @Test
  @DisplayName("[Verifier] SC-07.AC-1 boundary.0 · Consumer 重投幂等（独立复写）")
  @CoversAC("SC-07.AC-1#boundary.0")
  void verifier_sc07_ac1_consumer_idempotent() {
    WrongItemAnalyzedConsumer consumer =
        new WrongItemAnalyzedConsumer(service, new SimpleMeterRegistry());
    WrongItemAnalyzedEvent ev =
        new WrongItemAnalyzedEvent(
            V_ITEM_BASE + 1, V_STUDENT, "math", Instant.parse("2026-04-24T10:00:00Z"));

    consumer.onMessage(ev);
    consumer.onMessage(ev);
    consumer.onMessage(ev); // 3 次重投

    int rows =
        jdbc.queryForObject(
            "SELECT count(*) FROM review_plan WHERE wrong_item_id = ?",
            Integer.class,
            V_ITEM_BASE + 1);
    assertThat(rows).as("重投 3 次 · 仍 7 行").isEqualTo(7);
  }

  // ======================================================================
  // SC-08.AC-1 · Verifier 复写 · 走 MockMvc POST Controller 路径
  // ======================================================================

  @Test
  @DisplayName("[Verifier] SC-08.AC-1 happy_path.0 · MockMvc POST complete quality=5 → 200（独立复写）")
  @CoversAC("SC-08.AC-1#happy_path.0")
  void verifier_sc08_ac1_post_complete_http_path() throws Exception {
    // Builder 走 Service.complete 直接调 · Verifier 走 HTTP 端到端
    var plans =
        service.createSevenNodes(V_ITEM_BASE + 2, V_STUDENT, Instant.parse("2026-04-24T10:00:00Z"));
    Long t0Id = plans.get(0).getId();

    mvc.perform(
            post("/review-plans/{id}/complete", t0Id)
                .contentType("application/json")
                .content(jsonMapper.writeValueAsString(Map.of("quality", 5))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.planId").value(t0Id))
        .andExpect(jsonPath("$.data.mastered").value(false))
        .andExpect(jsonPath("$.data.easeFactorAfter").exists());
  }

  @Test
  @DisplayName("[Verifier] SC-08.AC-1 error_paths.1 · POST quality=6 → 400（独立复写）")
  @CoversAC("SC-08.AC-1#error_paths.1")
  void verifier_sc08_ac1_post_quality_invalid_http() throws Exception {
    var plans =
        service.createSevenNodes(V_ITEM_BASE + 3, V_STUDENT, Instant.parse("2026-04-24T10:00:00Z"));
    Long t0Id = plans.get(0).getId();

    mvc.perform(
            post("/review-plans/{id}/complete", t0Id)
                .contentType("application/json")
                .content(jsonMapper.writeValueAsString(Map.of("quality", 6))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value(40001));
  }

  @Test
  @DisplayName("[Verifier] SC-08.AC-1 error_paths.0 · POST 不存在 plan → 404（独立复写）")
  @CoversAC("SC-08.AC-1#error_paths.0")
  void verifier_sc08_ac1_post_plan_not_found_http() throws Exception {
    mvc.perform(
            post("/review-plans/{id}/complete", 99999999999L)
                .contentType("application/json")
                .content(jsonMapper.writeValueAsString(Map.of("quality", 5))))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(40401));
  }
}
