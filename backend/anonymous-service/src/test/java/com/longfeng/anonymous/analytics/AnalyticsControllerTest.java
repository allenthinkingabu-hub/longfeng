package com.longfeng.anonymous.analytics;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.longfeng.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * AnalyticsController slice IT · plan §S7 BUG-LF-09 fix.
 *
 * <p>Verifies:
 * <ul>
 *   <li>Happy path returns 204 No Content + delegates to {@link AnalyticsEventService#recordAsync}.
 *   <li>Missing {@code event} field still returns 204 (fire-and-forget contract — never break FE).
 * </ul>
 */
@WebMvcTest(controllers = AnalyticsController.class)
@Import(GlobalExceptionHandler.class)
class AnalyticsControllerTest {

  @Autowired private MockMvc mvc;

  @MockBean private AnalyticsEventService service;

  @Test
  void event_happyPath_returns204AndRecords() throws Exception {
    String body = """
        { "event": "anon_landing_view", "device_fp": "fp-xyz",
          "cta_position": "hero", "subject": "math" }
        """;
    mvc.perform(post("/api/analytics/event")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isNoContent());

    verify(service).recordAsync(eq("anon_landing_view"), eq("fp-xyz"), any());
  }

  @Test
  void event_missingEventName_stillReturns204AndDoesNotRecord() throws Exception {
    String body = """
        { "device_fp": "fp-xyz" }
        """;
    mvc.perform(post("/api/analytics/event")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isNoContent());

    verifyNoInteractions(service);
  }
}
