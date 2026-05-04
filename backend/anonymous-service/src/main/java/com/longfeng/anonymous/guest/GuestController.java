package com.longfeng.anonymous.guest;

import com.longfeng.anonymous.entity.GuestSession;
import com.longfeng.anonymous.ratelimit.GuestRateLimiter;
import com.longfeng.anonymous.session.GuestSessionService;
import com.longfeng.anonymous.support.ClientIpResolver;
import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * GuestController · {@code /api/guest/*} · plan §S7 BUG-LF-09 fix.
 *
 * <p>Two endpoints serve the anonymous guest analyze flow:
 * <ul>
 *   <li>{@code GET /quota} — read-only daily quota peek (no consume)
 *   <li>{@code POST /analyze} — consume one quota slot + create a guest session
 * </ul>
 *
 * <p>Path / response shape contract — see
 * {@code frontend/apps/h5/src/__mocks__/handlers/guest.ts}:
 * <ul>
 *   <li>Quota response is camelCase: {@code { quotaRemaining, quotaResetAt }}.
 *   <li>Analyze request body is snake_case: {@code { device_fp, subject, image_url }}.
 *   <li>Analyze response is snake_case: {@code { guest_session_id, task_id, status }}.
 * </ul>
 *
 * <p>Note: the FE does NOT send {@code X-Device-Fp} on {@code GET /quota} — this controller
 * accepts an optional header (some clients do send it) and falls back to the request body /
 * IP-based bucket. The FE always passes {@code device_fp} in the analyze body.
 *
 * <p>TODO(WT4): once {@code ai-analysis-service} exposes
 * {@code POST /api/ai/analyze-by-url}, replace the mock {@link UUID#randomUUID()} task id
 * with a real Feign call. Until then we return a UUID and let the FE poll later.
 */
@RestController
@RequestMapping("/api/guest")
public class GuestController {

  private static final Logger log = LoggerFactory.getLogger(GuestController.class);

  private static final ZoneId CST = ZoneId.of("Asia/Shanghai");

  private final GuestRateLimiter rateLimiter;
  private final GuestSessionService sessionService;

  public GuestController(GuestRateLimiter rateLimiter, GuestSessionService sessionService) {
    this.rateLimiter = rateLimiter;
    this.sessionService = sessionService;
  }

  @GetMapping("/quota")
  public GuestQuotaResponse quota(
      @RequestHeader(name = "X-Device-Fp", required = false) String deviceFpHeader,
      HttpServletRequest req) {
    // FE typically does not send X-Device-Fp on GET /quota — degrade to "full quota" peek.
    // When the header IS present we look up the actual remaining count.
    int remaining = (deviceFpHeader != null && !deviceFpHeader.isBlank())
        ? rateLimiter.peekRemainingFp(deviceFpHeader)
        : 1; // optimistic default — actual decrement happens on /analyze
    String resetAt = nextDailyResetIso();
    log.debug("guest-quota peek fp={} remaining={}", deviceFpHeader, remaining);
    return new GuestQuotaResponse(remaining, resetAt);
  }

  @PostMapping("/analyze")
  public ResponseEntity<?> analyze(
      @Valid @RequestBody GuestAnalyzeRequest body,
      HttpServletRequest req) {
    String rawIp = ClientIpResolver.resolve(req);
    String ua = req.getHeader("User-Agent");

    // 1. Quota check — throws 429 BusinessException(GUEST_QUOTA_EXHAUSTED) on overflow
    try {
      rateLimiter.checkAndConsume(body.deviceFp(), rawIp);
    } catch (BusinessException ex) {
      if (ex.errCode() == ErrCode.GUEST_QUOTA_EXHAUSTED) {
        log.info("guest-analyze QUOTA_EXHAUSTED fp={}", body.deviceFp());
        return ResponseEntity
            .status(429)
            .body(new GuestQuotaExhaustedResponse("QUOTA_EXHAUSTED", nextDailyResetIso()));
      }
      throw ex;
    }

    // 2. Create guest session row (status=CREATED, 24h TTL)
    GuestSession session = sessionService.create(
        body.deviceFp(),
        com.longfeng.anonymous.ratelimit.GuestRateLimiter.hmacIp(rawIp),
        ua,
        "landing", // entry_source — FE always lands here in the BUG-LF-09 funnel
        "default"); // experiment_bucket — single-bucket until A/B framework lands

    // 3. TODO(WT4): kick off real AI analysis via Feign to ai-analysis-service.
    //    POST /api/ai/analyze-by-url { image_url, subject, callback_session_id }
    //    For now we return a mock task id — FE polls but tolerates ANALYZING forever in mock-b.
    String taskId = UUID.randomUUID().toString();
    log.info("guest-analyze created session={} task={} subject={}",
        session.getId(), taskId, body.subject());

    return ResponseEntity.ok(new GuestAnalyzeResponse(
        String.valueOf(session.getId()),
        taskId,
        "ANALYZING"));
  }

  /** Returns the next daily reset (00:00 next day Asia/Shanghai) as ISO-8601 with offset. */
  private static String nextDailyResetIso() {
    LocalDate tomorrow = LocalDate.now(CST).plusDays(1);
    OffsetDateTime resetAt = tomorrow.atStartOfDay(CST).toOffsetDateTime();
    return resetAt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
  }
}
