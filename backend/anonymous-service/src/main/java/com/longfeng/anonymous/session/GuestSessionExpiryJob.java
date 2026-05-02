package com.longfeng.anonymous.session;

import com.xxl.job.core.handler.annotation.XxlJob;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * GuestSessionExpiryJob · XXL-Job · plan §5.S2 BE-05.
 *
 * <p>Scans {@code anon.guest_session} for rows with {@code expires_at < now()} via the BRIN index
 * on {@code expires_at} (D-Guest-Storage — BRIN efficient on monotonically-inserted data).
 * Runs every 30 minutes; enabled only when {@code anon.job.enabled=true}.
 *
 * <p>Batch-expire in a single UPDATE (native query) for efficiency.
 * C3 RED LINE: only modifies {@code anon.guest_session} — never {@code wb_*}.
 */
@Component
@ConditionalOnProperty(value = "anon.job.enabled", havingValue = "true", matchIfMissing = false)
public class GuestSessionExpiryJob {

  private static final Logger log = LoggerFactory.getLogger(GuestSessionExpiryJob.class);

  private final GuestSessionRepository repo;

  public GuestSessionExpiryJob(GuestSessionRepository repo) {
    this.repo = repo;
  }

  /** XXL-Job entry point · handler name "guest-session-expiry". */
  @XxlJob("guest-session-expiry")
  public void xxlExecute() {
    execute();
  }

  /**
   * Business entry point — called directly by IT tests (no XXL-Job context needed).
   *
   * @return number of sessions marked EXPIRED
   */
  @Transactional
  public int execute() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    int expired = repo.expireBefore(now);
    if (expired > 0) {
      log.info("guest-session-expiry: marked {} sessions EXPIRED (before={})", expired, now);
    } else {
      log.debug("guest-session-expiry: no sessions to expire at {}", now);
    }
    return expired;
  }
}
