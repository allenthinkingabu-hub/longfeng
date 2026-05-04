package com.longfeng.anonymous.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longfeng.anonymous.entity.AnalyticsEvent;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AnalyticsEventService · async fire-and-forget event sink · plan §S7 BUG-LF-09 fix.
 *
 * <p>Persists landing/guest funnel events without blocking the request thread.
 * Failures are logged but never thrown — analytics must NEVER break the user flow.
 */
@Service
public class AnalyticsEventService {

  private static final Logger log = LoggerFactory.getLogger(AnalyticsEventService.class);

  private final AnalyticsEventRepository repo;
  private final ObjectMapper objectMapper;

  public AnalyticsEventService(AnalyticsEventRepository repo, ObjectMapper objectMapper) {
    this.repo = repo;
    this.objectMapper = objectMapper;
  }

  /**
   * Async insert. Caller returns 204 immediately; this method runs on the analytics executor.
   *
   * @param eventName  event name (e.g. {@code anon_landing_view})
   * @param deviceFp   device fingerprint (may be null — landing-view fires before fp ready)
   * @param payload    raw event payload (any extra fields the FE sends)
   */
  @Async("analyticsExecutor")
  @Transactional
  public void recordAsync(String eventName, String deviceFp, Map<String, Object> payload) {
    try {
      AnalyticsEvent event = new AnalyticsEvent();
      event.setEventName(eventName);
      event.setDeviceFp(deviceFp);
      event.setPayloadJson(payload == null ? null : objectMapper.writeValueAsString(payload));
      event.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
      repo.save(event);
    } catch (JsonProcessingException ex) {
      log.warn("analytics-event payload serialize failed event={} err={}", eventName, ex.getMessage());
    } catch (Exception ex) {
      // Never propagate — analytics are best-effort
      log.warn("analytics-event persist failed event={} err={}", eventName, ex.getMessage());
    }
  }
}
