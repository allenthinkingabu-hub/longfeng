package com.longfeng.anonymous.analytics;

import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AnalyticsController · {@code POST /api/analytics/event} · plan §S7 BUG-LF-09 fix.
 *
 * <p>Single fire-and-forget endpoint that takes any JSON object containing an {@code event}
 * field plus arbitrary payload (subject / cta_position / quota_remaining / success / etc).
 * Always returns 204 No Content within ~1ms — the actual DB insert is async via
 * {@link AnalyticsEventService#recordAsync}.
 *
 * <p>Body schema (snake_case keys, matching FE):
 * <pre>{@code
 *   { "event": "anon_landing_view", "device_fp": "abc123",
 *     "cta_position": "hero", "subject": "math", "success": true, ... }
 * }</pre>
 *
 * <p>The {@code event} field is required; everything else (including {@code device_fp}) is
 * optional — landing-view fires before the device fp is ready.
 */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

  private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);

  private final AnalyticsEventService service;

  public AnalyticsController(AnalyticsEventService service) {
    this.service = service;
  }

  @PostMapping("/event")
  public ResponseEntity<Void> event(@RequestBody Map<String, Object> body) {
    String eventName = body == null ? null : objAsString(body.get("event"));
    if (eventName == null || eventName.isBlank()) {
      // Bad request — but we still want fire-and-forget contract; reply 204 to keep the FE
      // cheerful and just drop. Log it so we can spot misuse in dashboards.
      log.warn("analytics-event missing event-name body={}", body);
      return ResponseEntity.noContent().build();
    }
    String deviceFp = body == null ? null : objAsString(body.get("device_fp"));
    Map<String, Object> payload = new HashMap<>(body == null ? Map.of() : body);
    payload.remove("event");
    payload.remove("device_fp");
    service.recordAsync(eventName, deviceFp, payload);
    return ResponseEntity.noContent().build();
  }

  private static String objAsString(Object o) {
    return o == null ? null : o.toString();
  }
}
