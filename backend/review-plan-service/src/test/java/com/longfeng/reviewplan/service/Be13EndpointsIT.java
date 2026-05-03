package com.longfeng.reviewplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
 * BE-13 IT · 覆盖 S5 caveat 补丁的 3 端点 + DTO 4 字段.
 *
 * <p>覆盖：
 *
 * <ul>
 *   <li>GET /review-plans/list cursor 翻页（首页 / next_cursor / status 过滤 / 末页 null）
 *   <li>GET /review-plans/{id} 200 单节点 + DTO 含 user_id / next_due_at / mastery / interval
 *   <li>GET /review-plans/{id} 404
 *   <li>POST /review-plans/batch-reset-by-ids 返 reset_count
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Be13EndpointsIT extends IntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private DataSource dataSource;
  @Autowired private ObjectMapper jsonMapper;
  @Autowired private ReviewPlanService planService;

  private JdbcTemplate jdbc;

  private static final long BE13_STUDENT = 9000700L;
  private static final long BE13_ITEM_BASE = 9000070001L;
  private static final long BE13_ITEM_END = 9000070010L;

  @BeforeEach
  void seed() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DELETE FROM review_plan_outbox WHERE plan_id > 100000");
    jdbc.execute("DELETE FROM review_outcome WHERE plan_id > 100000");
    jdbc.update(
        "DELETE FROM review_plan WHERE wrong_item_id BETWEEN ? AND ?",
        BE13_ITEM_BASE, BE13_ITEM_END);
    jdbc.update(
        "DELETE FROM wrong_item WHERE id BETWEEN ? AND ?", BE13_ITEM_BASE, BE13_ITEM_END);
    jdbc.update("DELETE FROM user_account WHERE id = ?", BE13_STUDENT);

    jdbc.update(
        "INSERT INTO user_account (id, username, role, status, timezone) "
            + "VALUES (?, ?, 'STUDENT', 1, 'Asia/Shanghai')",
        BE13_STUDENT, "be13-it-user");

    for (long id = BE13_ITEM_BASE; id <= BE13_ITEM_END; id++) {
      jdbc.update(
          "INSERT INTO wrong_item (id, student_id, subject, source_type, status, mastery, version) "
              + "VALUES (?, ?, 'math', 1, 0, 0, 0)",
          id, BE13_STUDENT);
    }
  }

  // ======================================================================
  // BE-13-T1 · GET /review-plans/list 首页 + DTO 字段断言
  // ======================================================================

  @Test
  @DisplayName("BE-13-T1 · GET /review-plans/list?user_id= · 首页返 ≤limit 行 · DTO 含 4 字段")
  void list_first_page_returns_items_with_dto_fields() throws Exception {
    // seed 2 wrong_item × 7 = 14 plan
    planService.createSevenNodes(BE13_ITEM_BASE, BE13_STUDENT,
        Instant.parse("2026-05-01T10:00:00Z"));
    planService.createSevenNodes(BE13_ITEM_BASE + 1, BE13_STUDENT,
        Instant.parse("2026-05-01T11:00:00Z"));

    mvc.perform(
            get("/review-plans/list")
                .param("user_id", String.valueOf(BE13_STUDENT))
                .param("limit", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.items").isArray())
        .andExpect(jsonPath("$.data.items[0].user_id").value(String.valueOf(BE13_STUDENT)))
        .andExpect(jsonPath("$.data.items[0].next_due_at").exists())
        .andExpect(jsonPath("$.data.items[0].mastery").exists())
        .andExpect(jsonPath("$.data.items[0].mastery_label").value("NEW"))
        .andExpect(jsonPath("$.data.items[0].interval").exists())
        .andExpect(jsonPath("$.data.items[0].interval_days").exists())
        .andExpect(jsonPath("$.data.next_cursor").exists());
  }

  // ======================================================================
  // BE-13-T2 · GET /review-plans/list cursor 翻页 · 末页 next_cursor=null
  // ======================================================================

  @Test
  @DisplayName("BE-13-T2 · cursor 翻页 · 翻到末页 next_cursor=null")
  void list_cursor_pagination_terminates() throws Exception {
    planService.createSevenNodes(BE13_ITEM_BASE + 2, BE13_STUDENT,
        Instant.parse("2026-05-01T12:00:00Z"));
    // 7 行 · limit=3 · 期 page1=3 (next_cursor != null) · page2=3 (next_cursor != null) · page3=1 (next_cursor=null)

    String cursor = null;
    int totalSeen = 0;
    int pages = 0;
    while (pages < 5) {
      pages++;
      var req = get("/review-plans/list")
          .param("user_id", String.valueOf(BE13_STUDENT))
          .param("limit", "3");
      if (cursor != null) req = req.param("cursor", cursor);
      var resp = mvc.perform(req).andExpect(status().isOk()).andReturn();
      String body = resp.getResponse().getContentAsString();
      Map<?, ?> data = (Map<?, ?>) jsonMapper.readValue(body, Map.class).get("data");
      List<?> items = (List<?>) data.get("items");
      totalSeen += items.size();
      Object nc = data.get("next_cursor");
      if (nc == null) break;
      cursor = String.valueOf(nc);
    }
    assertThat(totalSeen).as("cursor 应翻完 7 行").isEqualTo(7);
    assertThat(pages).as("3+3+1 = 3 页").isLessThanOrEqualTo(3);
  }

  // ======================================================================
  // BE-13-T3 · GET /review-plans/list?status=mastered 过滤
  // ======================================================================

  @Test
  @DisplayName("BE-13-T3 · status=mastered 过滤 · seed 全 active 时返 0 行")
  void list_with_status_filter_mastered_returns_empty() throws Exception {
    planService.createSevenNodes(BE13_ITEM_BASE + 3, BE13_STUDENT,
        Instant.parse("2026-05-01T13:00:00Z"));

    mvc.perform(
            get("/review-plans/list")
                .param("user_id", String.valueOf(BE13_STUDENT))
                .param("status", "mastered"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items.length()").value(0))
        .andExpect(jsonPath("$.data.next_cursor").doesNotExist());
  }

  // ======================================================================
  // BE-13-T4 · GET /review-plans/{id} 200 + 404
  // ======================================================================

  @Test
  @DisplayName("BE-13-T4 · GET /review-plans/{id} · 存在 200 · DTO 含 user_id/next_due_at/mastery/interval")
  void get_by_id_200_with_dto_fields() throws Exception {
    List<ReviewPlan> plans = planService.createSevenNodes(BE13_ITEM_BASE + 4, BE13_STUDENT,
        Instant.parse("2026-05-01T14:00:00Z"));
    Long t0Id = plans.get(0).getId();

    mvc.perform(get("/review-plans/{id}", t0Id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(String.valueOf(t0Id)))
        .andExpect(jsonPath("$.data.user_id").value(String.valueOf(BE13_STUDENT)))
        .andExpect(jsonPath("$.data.next_due_at").exists())
        .andExpect(jsonPath("$.data.mastery").value(0))
        .andExpect(jsonPath("$.data.mastery_label").value("NEW"))
        .andExpect(jsonPath("$.data.interval").exists())
        .andExpect(jsonPath("$.data.interval_days").exists());
  }

  @Test
  @DisplayName("BE-13-T5 · GET /review-plans/{id} · 不存在 → 404")
  void get_by_id_404_when_missing() throws Exception {
    mvc.perform(get("/review-plans/{id}", 99999999L))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(40401));
  }

  // ======================================================================
  // BE-13-T6 · POST /review-plans/batch-reset-by-ids 返 reset_count
  // ======================================================================

  @Test
  @DisplayName("BE-13-T6 · POST /batch-reset-by-ids · {plan_ids:[..]} → reset_count = 软删行数")
  void batch_reset_by_ids_returns_count() throws Exception {
    List<ReviewPlan> plans = planService.createSevenNodes(BE13_ITEM_BASE + 5, BE13_STUDENT,
        Instant.parse("2026-05-01T15:00:00Z"));
    String id1 = String.valueOf(plans.get(0).getId());
    String id2 = String.valueOf(plans.get(1).getId());
    String idMissing = "99999999";

    String body = jsonMapper.writeValueAsString(
        Map.of("plan_ids", List.of(id1, id2, idMissing)));

    mvc.perform(
            post("/review-plans/batch-reset-by-ids")
                .contentType("application/json")
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data.reset_count").value(2));

    // DB 确认 2 行 soft-deleted
    int deleted = jdbc.queryForObject(
        "SELECT count(*) FROM review_plan WHERE id IN (?, ?) AND deleted_at IS NOT NULL",
        Integer.class, Long.parseLong(id1), Long.parseLong(id2));
    assertThat(deleted).isEqualTo(2);
  }
}
