package com.longfeng.reviewplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.longfeng.common.test.CoversAC;
import com.longfeng.reviewplan.IntegrationTestBase;
import com.longfeng.reviewplan.entity.ReviewPlan;
import java.time.Instant;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * ForgotResetIT · quality=0（FORGOT）SM-2 reset 路径验收.
 *
 * <p>覆盖 TDD §14.3 + D-Q-Flow 决策：
 * <ul>
 *   <li>quality=0 → ease reset 到 2.5 · interval=1d（Q-C）</li>
 *   <li>连续遗忘 3 次 → 不触发 mastered（mastered 仅由 ease≥2.8 触发）</li>
 *   <li>forget 次数累加进 total_forget 字段</li>
 *   <li>consecutive_good_count reset 到 0（quality < 3）</li>
 *   <li>outbox completed 事件含 quality=0 信息</li>
 *   <li>POST /review-plans/batch-reset · admin scope 校验 + 软删该学生所有 active plan</li>
 * </ul>
 *
 * <p>ForgotReset ≠ CancelNodes · 当前 SM-2 实现是 ease+interval reset，不取消 T4-T6。
 * 若 PRD §3.2 D-Q-Flow "FORGOT → 取消未来节点 + 从 now 重排 T0..T6" 功能后续实现，
 * 本 IT 需扩展 CANCELLED 状态校验（TODO: be-10 阶段按需扩展）。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ForgotResetIT extends IntegrationTestBase {

  @Autowired private ReviewPlanService planService;
  @Autowired private DataSource dataSource;
  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper jsonMapper;

  private JdbcTemplate jdbc;

  private static final long FORGOT_STUDENT = 9000400L;
  private static final long FORGOT_ITEM_BASE = 9000040001L;
  private static final long FORGOT_ITEM_END = 9000040010L;

  @BeforeEach
  void seed() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DELETE FROM review_plan_outbox WHERE plan_id > 100000");
    jdbc.execute("DELETE FROM review_outcome WHERE plan_id > 100000");
    jdbc.update(
        "DELETE FROM review_plan WHERE wrong_item_id BETWEEN ? AND ?",
        FORGOT_ITEM_BASE, FORGOT_ITEM_END);
    jdbc.update(
        "DELETE FROM wrong_item WHERE id BETWEEN ? AND ?", FORGOT_ITEM_BASE, FORGOT_ITEM_END);
    jdbc.update("DELETE FROM user_account WHERE id = ?", FORGOT_STUDENT);

    jdbc.update(
        "INSERT INTO user_account (id, username, role, status, timezone) "
            + "VALUES (?, ?, 'STUDENT', 1, 'Asia/Shanghai')",
        FORGOT_STUDENT, "forgot-it-user");

    for (long id = FORGOT_ITEM_BASE; id <= FORGOT_ITEM_END; id++) {
      jdbc.update(
          "INSERT INTO wrong_item (id, student_id, subject, source_type, status, mastery, version) "
              + "VALUES (?, ?, 'math', 1, 0, 0, 0)",
          id, FORGOT_STUDENT);
    }
  }

  // ======================================================================
  // FORGOT-T1 · quality=0 → ease reset 2.5 · interval=1d (Q-C)
  // ======================================================================

  @Test
  @DisplayName("FORGOT-T1 · quality=0 → ease reset 到 2.5 · consecutive_good_count=0 (Q-C)")
  @CoversAC("SC-07.AC-2#forgot_reset.0")
  void forgot_quality_0_resets_ease_to_init() throws Exception {
    long itemId = FORGOT_ITEM_BASE;
    List<ReviewPlan> plans = planService.createSevenNodes(itemId, FORGOT_STUDENT,
        Instant.parse("2026-05-02T10:00:00Z"));
    Long t1Id = plans.get(1).getId(); // T1 · 1d 节点

    // 先做一次 quality=5 提高 ease
    planService.complete(t1Id, 5);

    // 再做 quality=0 · 应 reset ease 到 2.5
    ReviewPlanService.CompleteResult result = planService.complete(t1Id, 0);

    assertThat(result.easeFactorAfter())
        .as("quality=0 ease reset 到 easeInit=2.5")
        .isEqualByComparingTo("2.5");
    assertThat(result.mastered()).isFalse();

    // DB 校验 consecutive_good_count = 0
    Integer consecutiveGood = jdbc.queryForObject(
        "SELECT consecutive_good_count FROM review_plan WHERE id = ?",
        Integer.class, t1Id);
    assertThat(consecutiveGood).as("quality=0 后 consecutive_good_count reset 到 0").isEqualTo(0);
  }

  // ======================================================================
  // FORGOT-T2 · total_forget 累加
  // ======================================================================

  @Test
  @DisplayName("FORGOT-T2 · 连续 quality=0 3 次 → total_forget=3 · 不触发 mastered")
  @CoversAC("SC-07.AC-2#forgot_accumulate.0")
  void forgot_three_times_accumulates_forget_count() {
    long itemId = FORGOT_ITEM_BASE + 1;
    List<ReviewPlan> plans = planService.createSevenNodes(itemId, FORGOT_STUDENT,
        Instant.parse("2026-05-02T11:00:00Z"));
    Long t0Id = plans.get(0).getId();

    planService.complete(t0Id, 0);
    planService.complete(t0Id, 0);
    ReviewPlanService.CompleteResult r3 = planService.complete(t0Id, 0);

    assertThat(r3.mastered()).as("连续遗忘不触发 mastered").isFalse();

    Integer totalForget = jdbc.queryForObject(
        "SELECT total_forget FROM review_plan WHERE id = ?",
        Integer.class, t0Id);
    assertThat(totalForget).as("total_forget 累加到 3").isEqualTo(3);

    Integer totalReview = jdbc.queryForObject(
        "SELECT total_review FROM review_plan WHERE id = ?",
        Integer.class, t0Id);
    assertThat(totalReview).as("total_review 累加到 3").isEqualTo(3);
  }

  // ======================================================================
  // FORGOT-T3 · quality=2 (中间值 < 3) → 也算 forget · ease reset
  // ======================================================================

  @Test
  @DisplayName("FORGOT-T3 · quality=2 (< 3) → 算 forget · ease reset 到 2.5")
  @CoversAC("SC-07.AC-2#forgot_boundary.0")
  void forgot_quality_2_is_below_threshold() {
    long itemId = FORGOT_ITEM_BASE + 2;
    List<ReviewPlan> plans = planService.createSevenNodes(itemId, FORGOT_STUDENT,
        Instant.parse("2026-05-02T12:00:00Z"));
    Long t0Id = plans.get(0).getId();

    ReviewPlanService.CompleteResult result = planService.complete(t0Id, 2);

    assertThat(result.easeFactorAfter())
        .as("quality=2 < 3 → ease reset 到 2.5")
        .isEqualByComparingTo("2.5");

    Integer totalForget = jdbc.queryForObject(
        "SELECT total_forget FROM review_plan WHERE id = ?",
        Integer.class, t0Id);
    assertThat(totalForget).as("quality=2 计入 total_forget").isEqualTo(1);
  }

  // ======================================================================
  // FORGOT-T4 · outbox completed 事件含 quality 信息
  // ======================================================================

  @Test
  @DisplayName("FORGOT-T4 · quality=0 complete → outbox event payload 含 quality=0")
  @CoversAC("SC-08.AC-1#forgot_outbox.0")
  void forgot_outbox_event_contains_quality() throws Exception {
    long itemId = FORGOT_ITEM_BASE + 3;
    List<ReviewPlan> plans = planService.createSevenNodes(itemId, FORGOT_STUDENT,
        Instant.parse("2026-05-02T13:00:00Z"));
    Long t0Id = plans.get(0).getId();

    mvc.perform(
            post("/review-plans/{id}/complete", t0Id)
                .contentType("application/json")
                .content(jsonMapper.writeValueAsString(Map.of("quality", 0))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.mastered").value(false));

    // outbox payload 中包含 quality 信息
    String payload = jdbc.queryForObject(
        "SELECT payload FROM review_plan_outbox WHERE plan_id = ? AND event_type = 'completed'",
        String.class, t0Id);
    assertThat(payload).as("outbox payload 含 quality=0").contains("\"quality\"");
  }

  // ======================================================================
  // BATCH-RESET-T1 · POST /review-plans/batch-reset · admin header
  // ======================================================================

  @Test
  @DisplayName("BATCH-RESET-T1 · POST /review-plans/batch-reset · X-Admin=true → 200 · 软删所有 active plan")
  @CoversAC("SC-09.AC-1#batch_reset_admin.0")
  void batch_reset_with_admin_header_soft_deletes_all_active() throws Exception {
    long itemId = FORGOT_ITEM_BASE + 4;
    planService.createSevenNodes(itemId, FORGOT_STUDENT,
        Instant.parse("2026-05-02T14:00:00Z"));

    // 确认 7 行 active
    int activeBefore = jdbc.queryForObject(
        "SELECT count(*) FROM review_plan WHERE wrong_item_id = ? AND deleted_at IS NULL",
        Integer.class, itemId);
    assertThat(activeBefore).isEqualTo(7);

    mvc.perform(
            post("/review-plans/batch-reset")
                .header("X-Admin", "true")
                .header("X-User-Id", FORGOT_STUDENT))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    // 7 行 soft-delete
    int activeAfter = jdbc.queryForObject(
        "SELECT count(*) FROM review_plan WHERE wrong_item_id = ? AND deleted_at IS NULL",
        Integer.class, itemId);
    assertThat(activeAfter).as("batch-reset 后 0 行 active").isEqualTo(0);

    int deletedCount = jdbc.queryForObject(
        "SELECT count(*) FROM review_plan WHERE wrong_item_id = ? AND deleted_at IS NOT NULL",
        Integer.class, itemId);
    assertThat(deletedCount).as("batch-reset 后 7 行 soft-deleted").isEqualTo(7);
  }

  // ======================================================================
  // BATCH-RESET-T2 · 无 admin header → 403 FORBIDDEN
  // ======================================================================

  @Test
  @DisplayName("BATCH-RESET-T2 · POST /review-plans/batch-reset · 无 X-Admin header → 403")
  @CoversAC("SC-09.AC-1#batch_reset_forbidden.0")
  void batch_reset_without_admin_header_returns_403() throws Exception {
    mvc.perform(
            post("/review-plans/batch-reset")
                .header("X-User-Id", FORGOT_STUDENT))
        .andExpect(status().isForbidden());
  }

  // ======================================================================
  // BATCH-RESET-T3 · X-Admin=false → 403
  // ======================================================================

  @Test
  @DisplayName("BATCH-RESET-T3 · POST /review-plans/batch-reset · X-Admin=false → 403")
  @CoversAC("SC-09.AC-1#batch_reset_forbidden.1")
  void batch_reset_with_false_admin_header_returns_403() throws Exception {
    mvc.perform(
            post("/review-plans/batch-reset")
                .header("X-Admin", "false")
                .header("X-User-Id", FORGOT_STUDENT))
        .andExpect(status().isForbidden());
  }

  // ======================================================================
  // BATCH-RESET-T4 · 空账户 batch-reset → 返回 200 (0 行 soft-delete · 不报错)
  // ======================================================================

  @Test
  @DisplayName("BATCH-RESET-T4 · 无 active plan 的学生 batch-reset → 200 (0 行影响 · 无报错)")
  @CoversAC("SC-09.AC-1#batch_reset_empty.0")
  void batch_reset_on_empty_student_returns_ok() throws Exception {
    // 用不同 student ID · 没有任何 plan
    long emptyStudent = 9000499L;
    jdbc.update("DELETE FROM user_account WHERE id = ?", emptyStudent);
    jdbc.update(
        "INSERT INTO user_account (id, username, role, status, timezone) "
            + "VALUES (?, 'empty-reset-user', 'STUDENT', 1, 'Asia/Shanghai')",
        emptyStudent);

    mvc.perform(
            post("/review-plans/batch-reset")
                .header("X-Admin", "true")
                .header("X-User-Id", emptyStudent))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0));

    jdbc.update("DELETE FROM user_account WHERE id = ?", emptyStudent);
  }
}
