package com.longfeng.anonymous.landing;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.longfeng.anonymous.ratelimit.LandingRateLimiter;
import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import com.longfeng.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * LandingController slice IT · plan §S7 BUG-LF-09 fix.
 *
 * <p>Verifies:
 * <ul>
 *   <li>{@code GET /samples} returns 3 hard-coded cards with the FE camelCase schema.
 *   <li>{@code GET /kpi} returns the camelCase KPI snapshot.
 *   <li>Rate-limit overflow throws BusinessException → mapped to HTTP 429 by GlobalExceptionHandler.
 * </ul>
 */
@WebMvcTest(controllers = LandingController.class)
@Import(GlobalExceptionHandler.class)
class LandingControllerTest {

  @Autowired private MockMvc mvc;

  @MockBean private LandingRateLimiter rateLimiter;

  @Test
  void samples_happyPath_returnsThreeCardsCamelCase() throws Exception {
    mvc.perform(get("/api/landing/samples").param("bucket", "default"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.bucket").value("default"))
        .andExpect(jsonPath("$.samples", org.hamcrest.Matchers.hasSize(3)))
        .andExpect(jsonPath("$.samples[0].id").value("sample-1"))
        .andExpect(jsonPath("$.samples[0].subject").value("math"))
        .andExpect(jsonPath("$.samples[0].stemPreview").exists())
        .andExpect(jsonPath("$.samples[0].errorReason").exists())
        .andExpect(jsonPath("$.samples[0].kpLabel").exists())
        .andExpect(jsonPath("$.samples[0].tagLabel").exists())
        .andExpect(jsonPath("$.samples[0].aiAnalysisMock.reason").exists())
        .andExpect(jsonPath("$.samples[0].aiAnalysisMock.stepsCount").isNumber())
        .andExpect(jsonPath("$.samples[0].aiAnalysisMock.hint").exists());
  }

  @Test
  void kpi_happyPath_returnsCamelCaseFields() throws Exception {
    mvc.perform(get("/api/landing/kpi"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.totalQuestionsAnalyzed").isNumber())
        .andExpect(jsonPath("$.retention7d").isNumber())
        .andExpect(jsonPath("$.headline").exists());
  }

  @Test
  void samples_rateLimited_returns429() throws Exception {
    doThrow(new BusinessException(ErrCode.LANDING_RATE_LIMIT, "msgkey:anon.landing.rate_limit"))
        .when(rateLimiter).checkAndConsume(anyString());

    mvc.perform(get("/api/landing/samples"))
        .andExpect(status().isTooManyRequests())
        .andExpect(jsonPath("$.code").value(ErrCode.LANDING_RATE_LIMIT.code()));
  }
}
