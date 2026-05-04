package com.longfeng.anonymous.guest;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.longfeng.anonymous.entity.GuestSession;
import com.longfeng.anonymous.ratelimit.GuestRateLimiter;
import com.longfeng.anonymous.session.GuestSessionService;
import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import com.longfeng.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GuestController slice IT · plan §S7 BUG-LF-09 fix.
 *
 * <p>Verifies:
 * <ul>
 *   <li>{@code GET /quota} returns camelCase {@code { quotaRemaining, quotaResetAt }}.
 *   <li>{@code POST /analyze} accepts snake_case body and returns snake_case response.
 *   <li>{@code POST /analyze} returns 429 + {@code QUOTA_EXHAUSTED} when limiter throws.
 *   <li>{@code POST /analyze} returns 400 on validation failure (missing device_fp).
 * </ul>
 */
@WebMvcTest(controllers = GuestController.class)
@Import(GlobalExceptionHandler.class)
class GuestControllerTest {

  @Autowired private MockMvc mvc;

  @MockBean private GuestRateLimiter rateLimiter;
  @MockBean private GuestSessionService sessionService;

  @Test
  void quota_noFingerprint_returnsOptimisticOne() throws Exception {
    mvc.perform(get("/api/guest/quota"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quotaRemaining").value(1))
        .andExpect(jsonPath("$.quotaResetAt").exists());
  }

  @Test
  void quota_withFingerprintHeader_callsPeek() throws Exception {
    when(rateLimiter.peekRemainingFp(eq("fp-abc"))).thenReturn(0);
    mvc.perform(get("/api/guest/quota").header("X-Device-Fp", "fp-abc"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quotaRemaining").value(0))
        .andExpect(jsonPath("$.quotaResetAt").exists());
  }

  @Test
  void analyze_happyPath_returnsSnakeCaseTaskId() throws Exception {
    GuestSession session = new GuestSession();
    session.setId(123_456_789L);
    when(sessionService.create(anyString(), anyString(), any(), anyString(), anyString()))
        .thenReturn(session);

    String body = """
        { "device_fp": "fp-xyz", "subject": "math", "image_url": "https://oss/x.jpg" }
        """;
    mvc.perform(post("/api/guest/analyze")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.guest_session_id").value("123456789"))
        .andExpect(jsonPath("$.task_id").exists())
        .andExpect(jsonPath("$.status").value("ANALYZING"));
  }

  @Test
  void analyze_quotaExhausted_returns429WithErrorTag() throws Exception {
    doThrow(new BusinessException(ErrCode.GUEST_QUOTA_EXHAUSTED, "msgkey:anon.guest.quota_exhausted"))
        .when(rateLimiter).checkAndConsume(anyString(), anyString());

    String body = """
        { "device_fp": "fp-xyz", "subject": "math", "image_url": "https://oss/x.jpg" }
        """;
    mvc.perform(post("/api/guest/analyze")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.error").value("QUOTA_EXHAUSTED"))
        .andExpect(jsonPath("$.reset_at").exists());
  }

  @Test
  void analyze_missingDeviceFp_returns400() throws Exception {
    String body = """
        { "subject": "math", "image_url": "https://oss/x.jpg" }
        """;
    mvc.perform(post("/api/guest/analyze")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest());
  }
}
