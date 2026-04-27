package com.longfeng.wrongbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.longfeng.wrongbook.event.WrongItemChangedEvent;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end IT covering V-S3-01..06 semantics via MockMvc + Testcontainers (pgvector/pg16 + redis
 * 7). RocketMQ is stubbed (TestMqConfig) — assertions check the captured event payload.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestMqConfig.class)
class WrongItemIT extends WrongbookIntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper om;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private List<TestMqConfig.CapturedMessage> captured;
  @Autowired private StringRedisTemplate redis;

  private static final long STUDENT_ID = 900000000000001L;

  @BeforeEach
  void seed() {
    captured.clear();
    // IT container lives across runs — clear idempotency cache so no stale rid→id lingers.
    redis.getConnectionFactory().getConnection().serverCommands().flushDb();
    jdbc.update("DELETE FROM wrong_item_tag");
    jdbc.update("DELETE FROM wrong_item_image");
    jdbc.update("DELETE FROM wrong_attempt");
    jdbc.update("DELETE FROM wrong_item_outbox");
    jdbc.update("DELETE FROM audit_log");
    jdbc.update("DELETE FROM review_plan");
    jdbc.update("DELETE FROM wrong_item_analysis");
    jdbc.update("DELETE FROM wrong_item");
    jdbc.update(
        "INSERT INTO user_account (id, username, role, status) VALUES (?, ?, 'STUDENT', 1) "
            + "ON CONFLICT (id) DO NOTHING",
        STUDENT_ID,
        "stu-it");
    jdbc.update(
        "INSERT INTO tag_taxonomy (id, code, display_name, subject, status) "
            + "VALUES (?, 'math.algebra.linear', '一元一次方程', 'math', 1) "
            + "ON CONFLICT (code) DO NOTHING",
        1001L);
  }

  private long createItem(String requestId, String stem) throws Exception {
    Map<String, Object> body =
        Map.of(
            "studentId",
            STUDENT_ID,
            "subject",
            "math",
            "gradeCode",
            "G7",
            "sourceType",
            1,
            "stemText",
            stem);
    MvcResult res =
        mvc.perform(
                post("/wrongbook/items")
                    .header("X-Request-Id", requestId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(om.writeValueAsString(body)))
            .andExpect(status().isCreated())
            .andReturn();
    var root = om.readTree(res.getResponse().getContentAsString());
    return root.path("data").path("id").asLong();
  }

  @Test
  @DisplayName("V-S3-01a · create → get round-trip with ApiResult envelope")
  void createAndGet() throws Exception {
    long id = createItem("rid-create", "2x + 3 = 7");
    mvc.perform(get("/wrongbook/items/" + id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(String.valueOf(id)))
        .andExpect(jsonPath("$.data.subject").value("math"))
        .andExpect(jsonPath("$.data.status").value("pending"));
  }

  @Test
  @DisplayName("V-S3-03 · idempotency — two POSTs with same X-Request-Id write one row")
  void idempotencySingleRequestId() throws Exception {
    long id1 = createItem("rid-idem", "x = 1");
    long id2 = createItem("rid-idem", "x = 1");
    assertThat(id1).isEqualTo(id2);
    Integer count =
        jdbc.queryForObject("SELECT count(*) FROM wrong_item WHERE id = ?", Integer.class, id1);
    assertThat(count).isEqualTo(1);
  }

  @Test
  @DisplayName("V-S3-04 · optimistic lock — second PATCH with stale version is 409")
  void optimisticLockConflict() throws Exception {
    long id = createItem("rid-lock", "a + b");
    Map<String, Object> body = Map.of("version", 0, "stemText", "A");
    mvc.perform(
            patch("/wrongbook/items/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(body)))
        .andExpect(status().isOk());
    Map<String, Object> stale = Map.of("version", 0, "stemText", "B");
    mvc.perform(
            patch("/wrongbook/items/" + id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(stale)))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("V-S3-05 · soft delete — GET returns 404, audit_log records action=delete")
  void softDelete() throws Exception {
    long id = createItem("rid-del", "delete me");
    mvc.perform(delete("/wrongbook/items/" + id).header("X-Request-Id", "rid-del-2"))
        .andExpect(status().isNoContent());
    mvc.perform(get("/wrongbook/items/" + id)).andExpect(status().isNotFound());
    Integer audits =
        jdbc.queryForObject(
            "SELECT count(*) FROM audit_log WHERE target_id = ? AND action = 'delete'",
            Integer.class,
            id);
    assertThat(audits).isGreaterThanOrEqualTo(1);
  }

  @Test
  @DisplayName("V-S3-06 · event publish — create emits WrongItemChangedEvent(created)")
  void eventOnCreate() throws Exception {
    long id = createItem("rid-evt", "send-event");
    assertThat(captured).isNotEmpty();
    var last = captured.get(captured.size() - 1);
    assertThat(last.topic()).isEqualTo(WrongItemChangedEvent.TOPIC);
    assertThat(last.payload()).isInstanceOf(WrongItemChangedEvent.class);
    var evt = (WrongItemChangedEvent) last.payload();
    assertThat(evt.itemId()).isEqualTo(id);
    assertThat(evt.action()).isEqualTo(WrongItemChangedEvent.ACTION_CREATED);
    assertThat(evt.version()).isEqualTo(0L);
  }

  @Test
  @DisplayName("V-S3-09 · embedding left NULL · insert does not fail")
  void embeddingIsNull() throws Exception {
    long id = createItem("rid-emb", "embedding null");
    // queryForObject requires a result row; queryForList returns empty when row is absent, so use
    // it to avoid EmptyResultDataAccessException when the isolation window hides the fresh row.
    var rows = jdbc.queryForList("SELECT embedding FROM wrong_item WHERE id = ?", id);
    assertThat(rows).hasSize(1);
    assertThat(rows.get(0).get("embedding")).isNull();
  }

  @Test
  @DisplayName("V-S3-10 · wrong_item_outbox table exists")
  void outboxTablePresent() {
    Integer tables =
        jdbc.queryForObject(
            "SELECT count(*) FROM information_schema.tables WHERE table_name = 'wrong_item_outbox'",
            Integer.class);
    assertThat(tables).isEqualTo(1);
  }

  @Test
  @DisplayName("page · list by subject returns created items")
  void pageBySubject() throws Exception {
    createItem("rid-page-1", "p1");
    createItem("rid-page-2", "p2");
    mvc.perform(get("/wrongbook/items").param("subject", "math").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.list.length()").value(org.hamcrest.Matchers.greaterThan(0)));
  }

  @Test
  @DisplayName("tag · bulk replace via PATCH (G-01)")
  void tagLifecycle() throws Exception {
    long id = createItem("rid-tag", "tag-test");
    Map<String, Object> replaceBody = Map.of("tags", List.of("math.algebra.linear"));
    mvc.perform(
            patch("/wrongbook/items/" + id + "/tags")
                .header("If-Match", 0)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(replaceBody)))
        .andExpect(status().isNoContent());
    // clear all tags
    mvc.perform(
            patch("/wrongbook/items/" + id + "/tags")
                .header("If-Match", 0)
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("tags", List.of()))))
        .andExpect(status().isNoContent());
  }

  @Test
  @DisplayName("image · confirm ORIGIN once")
  void imageConfirm() throws Exception {
    long id = createItem("rid-img", "img-test");
    Map<String, Object> body =
        Map.of("objectKey", "oss://bucket/key1", "role", "ORIGIN", "byteSize", 1024L);
    mvc.perform(
            post("/wrongbook/items/" + id + "/images")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.objectKey").value("oss://bucket/key1"));
  }

  @Test
  @DisplayName("difficulty · set level 4")
  void setDifficulty() throws Exception {
    long id = createItem("rid-diff", "diff-test");
    Map<String, Object> body = Map.of("level", 4);
    mvc.perform(
            post("/wrongbook/items/" + id + "/difficulty")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.difficulty").value(4));
  }

  @Test
  @DisplayName("attempt · create append-only + list")
  void attemptLifecycle() throws Exception {
    long id = createItem("rid-att", "att-test");
    Map<String, Object> body =
        Map.of(
            "studentId", STUDENT_ID, "answerText", "x=2", "isCorrect", false, "clientSource", "app");
    mvc.perform(
            post("/wrongbook/items/" + id + "/attempts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(body)))
        .andExpect(status().isCreated());
    mvc.perform(get("/wrongbook/items/" + id + "/attempts"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));
  }
}
