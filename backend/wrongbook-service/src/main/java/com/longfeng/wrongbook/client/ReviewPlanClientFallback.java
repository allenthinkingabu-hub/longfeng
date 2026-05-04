package com.longfeng.wrongbook.client;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Sentinel fallback for {@link ReviewPlanClient} · so P04 save never blocks on review-plan
 * availability.
 *
 * <p>Behaviour: build a deterministic 6-node SM-2 preview anchored at the current instant.
 * Real plan creation is deferred via outbox · see WrongItemProducer / wrong_item_outbox table
 * (S3-04 outbox pattern). A WARN log is always emitted so the prod fallback never goes silent.
 */
@Component
public class ReviewPlanClientFallback implements ReviewPlanClient {

  private static final Logger LOG = LoggerFactory.getLogger(ReviewPlanClientFallback.class);

  /** SM-2 default offsets (days) for T1..T6 — mirrors review-plan-service NODE_OFFSETS. */
  private static final long[] OFFSET_DAYS = {1, 2, 4, 7, 14, 30};

  @Override
  public CreatePlanResponse create(CreatePlanRequest req, String requestId) {
    LOG.warn(
        "review-plan-service unavailable · falling back to outbox-only preview · wrongItemId={} · requestId={}",
        req.wrongItemId(),
        requestId);
    Instant base = Instant.now();
    List<CreatePlanResponse.NodePreview> nodes =
        List.of(
            previewNode(req.wrongItemId(), 1, base.plus(Duration.ofDays(OFFSET_DAYS[0]))),
            previewNode(req.wrongItemId(), 2, base.plus(Duration.ofDays(OFFSET_DAYS[1]))),
            previewNode(req.wrongItemId(), 3, base.plus(Duration.ofDays(OFFSET_DAYS[2]))),
            previewNode(req.wrongItemId(), 4, base.plus(Duration.ofDays(OFFSET_DAYS[3]))),
            previewNode(req.wrongItemId(), 5, base.plus(Duration.ofDays(OFFSET_DAYS[4]))),
            previewNode(req.wrongItemId(), 6, base.plus(Duration.ofDays(OFFSET_DAYS[5]))));
    String pseudoPlanId = "outbox-" + req.wrongItemId();
    return new CreatePlanResponse(pseudoPlanId, nodes);
  }

  private static CreatePlanResponse.NodePreview previewNode(
      String wrongItemId, int level, Instant due) {
    return new CreatePlanResponse.NodePreview(
        "preview-" + wrongItemId + "-T" + level, "T" + level, due.toString());
  }
}
