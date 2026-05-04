package com.longfeng.wrongbook.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.longfeng.wrongbook.TestMqConfig;
import com.longfeng.wrongbook.WrongbookIntegrationTestBase;
import com.longfeng.wrongbook.client.AnalysisDetailClient;
import com.longfeng.wrongbook.client.AnalysisDetailClient.AnalysisDetailResponse;
import com.longfeng.wrongbook.client.ReviewPlanClient;
import com.longfeng.wrongbook.client.ReviewPlanClient.CreatePlanResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end IT for {@link QuestionDetailController} (P04 Result page) covering:
 *
 * <ul>
 *   <li>GET /api/wb/questions/{qid} happy path · returns aggregate with snake_case payload
 *   <li>GET /api/wb/questions/{qid} 404 · wrong_item missing
 *   <li>POST /api/wb/questions/{qid}/save happy path · plan_id non-null + 6 nodes
 *   <li>POST save · review-plan-service down · falls back to outbox preview (still 200)
 * </ul>
 *
 * <p>Both Feign clients are mocked via {@link MockBean} — this IT does not depend on a running
 * ai-analysis-service or review-plan-service.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestMqConfig.class)
class QuestionDetailControllerIT extends WrongbookIntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper om;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private StringRedisTemplate redis;

  @MockBean private AnalysisDetailClient analysisDetailClient;
  @MockBean private ReviewPlanClient reviewPlanClient;

  private static final long STUDENT_ID = 900000000000031L;

  @BeforeEach
  void seed() {
    // IT redis lives across runs · clear idempotency cache so no stale rid→id leaks across tests
    redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    jdbc.update("DELETE FROM wrong_item_tag");
    jdbc.update("DELETE FROM wrong_item_image");
    jdbc.update("DELETE FROM wrong_attempt");
    jdbc.update("DELETE FROM wrong_item_outbox");
    jdbc.update("DELETE FROM audit_log");
    // review_outcome FK on review_plan · must wipe outcome first
    jdbc.update("DELETE FROM review_outcome");
    jdbc.update("DELETE FROM review_plan");
    jdbc.update("DELETE FROM wrong_item_analysis");
    jdbc.update("DELETE FROM wrong_item");
    jdbc.update(
        "INSERT INTO user_account (id, username, role, status) VALUES (?, ?, 'STUDENT', 1) "
            + "ON CONFLICT (id) DO NOTHING",
        STUDENT_ID,
        "stu-p04-it");
    jdbc.update(
        "INSERT INTO tag_taxonomy (id, code, display_name, subject, status) "
            + "VALUES (?, 'math.algebra.linear', '一元一次方程', 'math', 1) "
            + "ON CONFLICT (code) DO NOTHING",
        1031L);
  }

  /** Create a wrong_item via existing endpoint to honour the snowflake-id contract. */
  private long createItem(String requestId, String stem) throws Exception {
    // common/ObjectMapperConfig enforces snake_case · request body MUST use snake_case keys.
    // Use raw JSON (not Map.of) to keep precise control over scalar types (Long / Short).
    String body =
        String.format(
            "{\"student_id\":%d,\"subject\":\"math\",\"grade_code\":\"G7\","
                + "\"source_type\":1,\"stem_text\":%s,\"difficulty\":3}",
            STUDENT_ID,
            om.writeValueAsString(stem));
    MvcResult res =
        mvc.perform(
                post("/wrongbook/items")
                    .header("X-Request-Id", requestId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    // common/ObjectMapperConfig serialises Long → String to avoid JS precision loss.
    return Long.parseLong(
        om.readTree(res.getResponse().getContentAsString()).path("data").path("id").asText());
  }

  @Test
  @DisplayName("GET /api/wb/questions/{qid} · happy path · returns aggregate with snake_case keys")
  void getDetailHappy() throws Exception {
    long id = createItem("rid-p04-get", "f(x) = x² − 4x + 3");
    // submit one attempt so my_answer is populated from real DB (snake_case enforced)
    // client_source MUST match regex app|web|mp|admin (per CreateAttemptReq.@Pattern)
    Map<String, Object> attempt =
        Map.of(
            "student_id", STUDENT_ID,
            "answer_text", "B. (-2, -1)",
            "is_correct", false,
            "client_source", "web");
    mvc.perform(
            post("/wrongbook/items/" + id + "/attempts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(attempt)))
        .andExpect(status().isCreated());

    // ai-analysis-service returns a complete analysis
    Mockito.when(analysisDetailClient.latest(id))
        .thenReturn(
            new AnalysisDetailResponse(
                String.valueOf(id),
                String.valueOf(id),
                1,
                "dashscope",
                "qwen-vl-max",
                "success",
                "你把顶点式的 h 与 k 读反了 · 顶点应为 (h, k) = (2, -1)。",
                "CONCEPT",
                List.of("math.algebra.linear"),
                List.of(
                    Map.of("idx", 1, "title", "对 f(x) 配方", "formula", "f(x) = (x-2)² - 1"),
                    Map.of("idx", 2, "title", "读出顶点 (h, k) = (2, -1)")),
                "2026-05-04T07:00:00Z"));

    mvc.perform(get("/api/wb/questions/" + id))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        // FE destructures top-level data.question · so question MUST sit at root
        .andExpect(jsonPath("$.question.id").value(String.valueOf(id)))
        .andExpect(jsonPath("$.question.subject").value("math"))
        .andExpect(jsonPath("$.question.stem").exists())
        .andExpect(jsonPath("$.question.my_answer").value("B. (-2, -1)"))
        .andExpect(jsonPath("$.question.correct_answer").exists())
        .andExpect(jsonPath("$.question.reason_markdown").exists())
        .andExpect(jsonPath("$.question.knowledge_points").isArray())
        .andExpect(jsonPath("$.question.model_info.name").value("qwen-vl-max"))
        .andExpect(jsonPath("$.question.confidence").exists())
        .andExpect(jsonPath("$.question.difficulty").value(3))
        // plannedNodes mirrors FE PlannedNode[] · must have 6 entries T1..T6
        .andExpect(jsonPath("$.plannedNodes.length()").value(6))
        .andExpect(jsonPath("$.plannedNodes[0].t_level").value("T1"))
        .andExpect(jsonPath("$.plannedNodes[0].status").value("preview"))
        .andExpect(jsonPath("$.plannedNodes[5].t_level").value("T6"));
  }

  @Test
  @DisplayName("GET /api/wb/questions/{qid} · 404 when wrong_item missing")
  void getDetailNotFound() throws Exception {
    mvc.perform(get("/api/wb/questions/9999999999999999")).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("POST /api/wb/questions/{qid}/save · happy path · plan_id non-null + 6 nodes")
  void saveHappy() throws Exception {
    long id = createItem("rid-p04-save", "y = x² · 求最值");
    String planId = "plan-9000001";
    Mockito.when(reviewPlanClient.create(any(), any()))
        .thenReturn(
            new CreatePlanResponse(
                planId,
                List.of(
                    new CreatePlanResponse.NodePreview("nid-1", "T1", "2026-05-05T07:00:00Z"),
                    new CreatePlanResponse.NodePreview("nid-2", "T2", "2026-05-06T07:00:00Z"),
                    new CreatePlanResponse.NodePreview("nid-3", "T3", "2026-05-08T07:00:00Z"),
                    new CreatePlanResponse.NodePreview("nid-4", "T4", "2026-05-11T07:00:00Z"),
                    new CreatePlanResponse.NodePreview("nid-5", "T5", "2026-05-18T07:00:00Z"),
                    new CreatePlanResponse.NodePreview("nid-6", "T6", "2026-06-03T07:00:00Z"))));

    mvc.perform(
            post("/api/wb/questions/" + id + "/save")
                .header("X-Request-Id", "rid-p04-save-trigger")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qid\":\"" + id + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.qid").value(String.valueOf(id)))
        .andExpect(jsonPath("$.plan_id").value(planId))
        .andExpect(jsonPath("$.nodes.length()").value(6))
        .andExpect(jsonPath("$.nodes[0].nid").value("nid-1"))
        .andExpect(jsonPath("$.nodes[0].t_level").value("T1"))
        .andExpect(jsonPath("$.nodes[0].due_at").exists())
        .andExpect(jsonPath("$.nodes[5].t_level").value("T6"));
  }

  @Test
  @DisplayName("POST save · review-plan-service throws · falls back to outbox preview · still 200")
  void saveFallback() throws Exception {
    long id = createItem("rid-p04-save-fb", "fallback test");
    Mockito.when(reviewPlanClient.create(any(), any()))
        .thenThrow(new RuntimeException("review-plan-service connection refused"));

    mvc.perform(
            post("/api/wb/questions/" + id + "/save")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"qid\":\"" + id + "\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.qid").value(String.valueOf(id)))
        .andExpect(jsonPath("$.plan_id").value("outbox-" + id))
        .andExpect(jsonPath("$.nodes.length()").value(6));
  }
}
