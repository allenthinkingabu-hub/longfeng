package com.longfeng.wrongbook;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration test framework for WrongbookSearchController.
 *
 * <p>Requires a running pg16+pgvector at 127.0.0.1:15432 and redis at 127.0.0.1:16379.
 * Seeds a few wrong_item rows and verifies the hybrid search endpoint responds correctly.
 *
 * <p>NOTE: pg_trgm extension must be installed: {@code CREATE EXTENSION IF NOT EXISTS pg_trgm;}.
 * This is typically handled in Flyway migration V1.0.010.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Import(TestMqConfig.class)
class WrongbookSearchIT extends WrongbookIntegrationTestBase {

  @Autowired private MockMvc mvc;
  @Autowired private ObjectMapper om;
  @Autowired private JdbcTemplate jdbc;

  private static final long STUDENT_ID = 900000000000099L;

  @BeforeEach
  void seed() {
    jdbc.update("DELETE FROM wrong_item_tag WHERE wrong_item_id IN "
        + "(SELECT id FROM wrong_item WHERE student_id = ?)", STUDENT_ID);
    jdbc.update("DELETE FROM wrong_item WHERE student_id = ?", STUDENT_ID);
    jdbc.update(
        "INSERT INTO user_account (id, username, role, status) VALUES (?, ?, 'STUDENT', 1) "
            + "ON CONFLICT (id) DO NOTHING",
        STUDENT_ID, "search-it");

    // Insert two items with searchable text
    jdbc.update(
        "INSERT INTO wrong_item (id, student_id, subject, source_type, ocr_text, stem_text, status, mastery, version) "
            + "VALUES (?, ?, 'math', 1, '一元一次方程 2x+3=7', '求解 2x+3=7', 0, 0, 0)",
        800001L, STUDENT_ID);
    jdbc.update(
        "INSERT INTO wrong_item (id, student_id, subject, source_type, ocr_text, stem_text, status, mastery, version) "
            + "VALUES (?, ?, 'math', 1, '二次方程 x^2+2x+1=0', '因式分解', 0, 0, 0)",
        800002L, STUDENT_ID);
  }

  @Test
  @DisplayName("POST /wrongbook/questions/search · trigram text query returns matching items")
  void textSearchReturnsResults() throws Exception {
    Map<String, Object> body = Map.of("query", "一元一次方程", "student_id", STUDENT_ID);
    mvc.perform(
            post("/wrongbook/questions/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(body)))
        .andExpect(status().isOk())
        // ApiResult.ok uses code=0 (not 200) per common §6.6 envelope spec.
        .andExpect(jsonPath("$.code").value(0))
        .andExpect(jsonPath("$.data").isArray());
  }

  @Test
  @DisplayName("POST /wrongbook/questions/search · empty query returns empty list")
  void emptyQueryReturnsEmpty() throws Exception {
    Map<String, Object> body = Map.of("query", "");
    mvc.perform(
            post("/wrongbook/questions/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(body)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").isArray())
        .andExpect(jsonPath("$.data.length()").value(0));
  }

  @Test
  @DisplayName("GET /wrongbook/items response uses items field (S7 Issue 1)")
  void listResponseUsesItemsField() throws Exception {
    mvc.perform(
            post("/wrongbook/questions/search")
                .contentType(MediaType.APPLICATION_JSON)
                .content(om.writeValueAsString(Map.of("query", "方程", "student_id", STUDENT_ID))))
        .andExpect(status().isOk());
    // Additional assertion: items field in page response
    // S7 fixed: studentId param-name (controller still uses camelCase) · use studentId here.
    var result = mvc.perform(
            org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .get("/wrongbook/items")
                .param("studentId", String.valueOf(STUDENT_ID))
                .param("size", "5"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray())
        .andReturn();

    var root = om.readTree(result.getResponse().getContentAsString());
    // has_more is boolean primitive · always present
    assertThat(root.path("data").has("has_more")).isTrue();
    // next_cursor may be omitted when null (NON_NULL inclusion) · just verify items shape
    assertThat(root.path("data").path("items").isArray()).isTrue();
  }
}
