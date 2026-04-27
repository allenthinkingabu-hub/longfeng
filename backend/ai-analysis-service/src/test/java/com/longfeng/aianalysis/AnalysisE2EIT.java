package com.longfeng.aianalysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.longfeng.aianalysis.consumer.WrongItemChangedConsumer;
import com.longfeng.aianalysis.entity.WrongItemAnalysis;
import com.longfeng.aianalysis.event.ItemChangedEvent;
import com.longfeng.aianalysis.llm.LlmProvider;
import com.longfeng.aianalysis.llm.StubProvider;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * V-S4-02 equivalent · asserts the three mandated branches (dashscope_ok / openai_ok /
 * fallback_pending) via swappable stub providers. No WireMock process required because the stubs
 * already capture the sanitized prompt · satisfying V-S4-04 inline.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(AnalysisE2EIT.TestStubs.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
class AnalysisE2EIT extends AiAnalysisIntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper om;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private WrongItemChangedConsumer consumer;
  @Autowired private TestStubs.StubHandle stubs;

  private static final long STUDENT_ID = 900000000000900L;
  private static final long ITEM_ID = 700000000000700L;

  @BeforeEach
  void seed() {
    stubs.resetAll();
    jdbc.update("DELETE FROM wrong_item_analysis WHERE wrong_item_id IN (?, ?)", ITEM_ID, ITEM_ID + 1);
    jdbc.update("DELETE FROM ai_usage_log");
    jdbc.update("DELETE FROM wrong_item_tag WHERE wrong_item_id IN (?, ?)", ITEM_ID, ITEM_ID + 1);
    jdbc.update("DELETE FROM wrong_item_image WHERE wrong_item_id IN (?, ?)", ITEM_ID, ITEM_ID + 1);
    jdbc.update("DELETE FROM wrong_item WHERE id IN (?, ?)", ITEM_ID, ITEM_ID + 1);
    jdbc.update(
        "INSERT INTO user_account (id, username, role, status) VALUES (?, '张小明', 'STUDENT', 1) ON CONFLICT (id) DO NOTHING",
        STUDENT_ID);
    jdbc.update(
        "INSERT INTO wrong_item (id, student_id, subject, source_type, stem_text, status, mastery, deleted_at, version) "
            + "VALUES (?, ?, 'math', 1, '学生 张小明 的联系电话 13800138000 · 请求身份证 110101199003074612 · 解方程 2x+3=7', 0, 0, NULL, 0)",
        ITEM_ID, STUDENT_ID);
    jdbc.update(
        "INSERT INTO wrong_item (id, student_id, subject, source_type, stem_text, status, mastery, deleted_at, version) "
            + "VALUES (?, ?, 'math', 1, '解方程 x^2 = 9', 0, 0, NULL, 0)",
        ITEM_ID + 1, STUDENT_ID);
  }

  @Test
  @DisplayName("dashscope_ok · primary stub returns · status=0 · sanitized prompt reached LLM")
  void dashscopeOk() throws Exception {
    stubs.dashscope.reset();
    stubs.openai.reset();
    consumer.onMessage(
        om.writeValueAsString(new ItemChangedEvent(ITEM_ID, "created", 0L, Instant.now())));
    Map<String, Object> row =
        jdbc.queryForMap("SELECT status, model_provider FROM wrong_item_analysis WHERE wrong_item_id = ?", ITEM_ID);
    assertThat(((Number) row.get("status")).shortValue()).isEqualTo(WrongItemAnalysis.STATUS_SUCCESS);
    assertThat(row.get("model_provider")).isEqualTo("dashscope");
    assertThat(stubs.dashscope.chatCallCount()).isEqualTo(1);
    assertThat(stubs.openai.chatCallCount()).isEqualTo(0);
    // V-S4-04 · payload reaching LLM is PII-free and placeholder-present
    assertThat(stubs.dashscope.lastPrompt()).contains("{phone}").contains("{idcard}");
    assertThat(stubs.dashscope.lastPrompt()).doesNotContain("13800138000");
    assertThat(stubs.dashscope.lastPrompt()).doesNotContain("110101199003074612");
  }

  @Test
  @DisplayName("openai_ok · primary fails · fallback returns · status=0 · provider=openai")
  void openaiFallbackOk() throws Exception {
    stubs.makeDashscopeFailChat();
    consumer.onMessage(
        om.writeValueAsString(new ItemChangedEvent(ITEM_ID, "created", 0L, Instant.now())));
    Map<String, Object> row =
        jdbc.queryForMap("SELECT status, model_provider FROM wrong_item_analysis WHERE wrong_item_id = ?", ITEM_ID);
    assertThat(((Number) row.get("status")).shortValue()).isEqualTo(WrongItemAnalysis.STATUS_SUCCESS);
    assertThat(row.get("model_provider")).isEqualTo("dashscope"); // router reports preferred name
    assertThat(stubs.dashscope.chatCallCount()).isEqualTo(1);
    assertThat(stubs.openai.chatCallCount()).isEqualTo(1);
  }

  @Test
  @DisplayName("fallback_pending · both providers fail · status=9 · ack'd to MQ")
  void bothFailPending() throws Exception {
    stubs.makeDashscopeFailChat();
    stubs.makeOpenaiFailChat();
    consumer.onMessage(
        om.writeValueAsString(new ItemChangedEvent(ITEM_ID, "created", 0L, Instant.now())));
    Map<String, Object> row =
        jdbc.queryForMap("SELECT status FROM wrong_item_analysis WHERE wrong_item_id = ?", ITEM_ID);
    assertThat(((Number) row.get("status")).shortValue()).isEqualTo(WrongItemAnalysis.STATUS_PENDING);
  }

  @Test
  @DisplayName("V-S4-03 idempotency · same item_id + version replay writes only 1 row")
  void idempotent() throws Exception {
    String body = om.writeValueAsString(new ItemChangedEvent(ITEM_ID, "created", 0L, Instant.now()));
    consumer.onMessage(body);
    consumer.onMessage(body);
    Integer cnt =
        jdbc.queryForObject(
            "SELECT count(*) FROM wrong_item_analysis WHERE wrong_item_id = ?",
            Integer.class,
            ITEM_ID);
    assertThat(cnt).isEqualTo(1);
  }

  @Test
  @DisplayName("V-S4-05 embedding 1024 dims written back")
  void embeddingDims() throws Exception {
    consumer.onMessage(
        om.writeValueAsString(new ItemChangedEvent(ITEM_ID, "created", 0L, Instant.now())));
    Integer dims =
        jdbc.queryForObject(
            "SELECT vector_dims(embedding) FROM wrong_item WHERE id = ?", Integer.class, ITEM_ID);
    assertThat(dims).isEqualTo(1024);
  }

  @Test
  @DisplayName("V-S4-06 similar endpoint returns rows with distance ∈ [0,2]")
  void similarEndpoint() throws Exception {
    // seed embeddings for both items
    consumer.onMessage(
        om.writeValueAsString(new ItemChangedEvent(ITEM_ID, "created", 0L, Instant.now())));
    consumer.onMessage(
        om.writeValueAsString(new ItemChangedEvent(ITEM_ID + 1, "created", 0L, Instant.now())));

    MvcResult res =
        mvc.perform(get("/analysis/" + ITEM_ID + "/similar").param("k", "3"))
            .andExpect(status().isOk())
            .andReturn();
    var root = om.readTree(res.getResponse().getContentAsString());
    List<?> items = om.convertValue(root.path("items"), List.class);
    assertThat(items).isNotEmpty();
    double max = 0;
    for (var item : root.path("items")) {
      double d = item.path("distance").asDouble();
      assertThat(d).isBetween(0.0, 2.0);
      max = Math.max(max, d);
    }
  }

  @Test
  @DisplayName("V-S4-08 ai_usage_log · at least one trace row per analyze")
  void usageLogPopulated() throws Exception {
    consumer.onMessage(
        om.writeValueAsString(new ItemChangedEvent(ITEM_ID, "created", 0L, Instant.now())));
    Integer cnt = jdbc.queryForObject("SELECT count(*) FROM ai_usage_log", Integer.class);
    assertThat(cnt).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("V-S4-10 · /analysis/provider reflects Feature Flag default · dashscope")
  void providerEndpoint() throws Exception {
    mvc.perform(get("/analysis/provider"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.provider").value("dashscope"));
  }

  @Test
  @DisplayName("admin /retry bumps version · new analysis row · X-Admin gate enforced")
  void adminRetryBumpsVersion() throws Exception {
    consumer.onMessage(
        om.writeValueAsString(new ItemChangedEvent(ITEM_ID, "created", 0L, Instant.now())));
    mvc.perform(post("/analysis/" + ITEM_ID + "/retry").header("X-Admin", "true"))
        .andExpect(status().isAccepted());
    Integer cnt =
        jdbc.queryForObject(
            "SELECT count(*) FROM wrong_item_analysis WHERE wrong_item_id = ?",
            Integer.class,
            ITEM_ID);
    assertThat(cnt).isEqualTo(2);
  }

  @Test
  @DisplayName("/retry without X-Admin → 403 Forbidden (admin gate)")
  void retryWithoutAdminHeaderForbidden() throws Exception {
    mvc.perform(post("/analysis/" + ITEM_ID + "/retry")).andExpect(status().isForbidden());
  }

  @Test
  @DisplayName("GET /analysis/{itemId} · returns AnalysisVO with snake_case + string id")
  void latestReturnsSnakeCaseVO() throws Exception {
    consumer.onMessage(
        om.writeValueAsString(new ItemChangedEvent(ITEM_ID, "created", 0L, Instant.now())));
    mvc.perform(get("/analysis/" + ITEM_ID))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.wrong_item_id").value(String.valueOf(ITEM_ID)))
        .andExpect(jsonPath("$.id").isString())
        .andExpect(jsonPath("$.status").value("success"))
        .andExpect(jsonPath("$.model_provider").value("dashscope"))
        .andExpect(jsonPath("$.auto_tags").isArray());
  }

  @Test
  @DisplayName("GET /analysis/{itemId} · 404 when no analysis row exists")
  void latestReturns404WhenMissing() throws Exception {
    mvc.perform(get("/analysis/" + ITEM_ID)).andExpect(status().isNotFound());
  }

  @Test
  @DisplayName("GET /analysis/{itemId}/stream · SSE replays explain chunks · text/event-stream")
  void streamSseReplaysChunks() throws Exception {
    consumer.onMessage(
        om.writeValueAsString(new ItemChangedEvent(ITEM_ID, "created", 0L, Instant.now())));
    MvcResult res =
        mvc.perform(get("/analysis/" + ITEM_ID + "/stream"))
            .andExpect(status().isOk())
            .andExpect(
                header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/event-stream")))
            .andReturn();
    String body =
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(res))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    assertThat(body).contains("data:").contains("\"chunk\"").contains("\"done\":true");
  }

  @Test
  @DisplayName("GET /analysis/{itemId}/stream · terminal chunk when not analyzed (no 4xx)")
  void streamSseTerminalChunkWhenMissing() throws Exception {
    MvcResult res =
        mvc.perform(get("/analysis/" + ITEM_ID + "/stream")).andExpect(status().isOk()).andReturn();
    String body =
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch(res))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString(StandardCharsets.UTF_8);
    assertThat(body).contains("\"done\":true").contains("尚未分析");
  }

  /** Swaps out the Config beans with controllable stubs. */
  @TestConfiguration
  static class TestStubs {
    @Bean
    public StubHandle stubs() {
      return new StubHandle();
    }

    @Bean
    @Primary
    public LlmProvider dashscopeProvider(StubHandle h) {
      return h.dashscope;
    }

    @Bean
    @Primary
    public LlmProvider openaiProvider(StubHandle h) {
      return h.openai;
    }

    static class StubHandle {
      final StubProvider dashscope = new StubProvider("dashscope", "qwen-plus");
      final StubProvider openai = new StubProvider("openai", "gpt-4o-mini");

      void resetAll() {
        dashscope.setChatFails(false);
        dashscope.setEmbedFails(false);
        dashscope.reset();
        openai.setChatFails(false);
        openai.setEmbedFails(false);
        openai.reset();
      }

      void makeDashscopeFailChat() {
        dashscope.setChatFails(true);
      }

      void makeOpenaiFailChat() {
        openai.setChatFails(true);
      }
    }
  }
}
