package com.longfeng.wrongbook.client;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client for review-plan-service · creates the SM-2 plan + T1..T6 calendar nodes the moment
 * a wrong item is saved from P04.
 *
 * <p>review-plan-service does not yet expose a single "create plan + return nodes" endpoint
 * (the existing controller is centred on day-view / cursor-list / complete · see
 * {@code ReviewPlanController}). This client targets the new endpoint
 * {@code POST /review-plans} which review-plan-service must add (see follow-ups in the WT3
 * report). Until that endpoint exists, the fallback returns a deterministic preview so save still
 * succeeds (outbox semantics — main flow not blocked by review-plan availability).
 */
@FeignClient(
    name = "review-plan-create",
    url = "${feign.client.review-plan.url:http://localhost:9882}",
    fallback = ReviewPlanClientFallback.class)
public interface ReviewPlanClient {

  /** POST /review-plans · body {@link CreatePlanRequest} · 200 with {@link CreatePlanResponse}. */
  @PostMapping("/review-plans")
  CreatePlanResponse create(
      @RequestBody CreatePlanRequest req,
      @RequestHeader(value = "X-Request-Id", required = false) String requestId);

  /** Body sent to review-plan-service · binds wrong_item to a student so SM-2 can compute due. */
  record CreatePlanRequest(
      @JsonProperty("wrong_item_id") String wrongItemId,
      @JsonProperty("student_id") String studentId,
      String subject) {}

  /** Response · plan_id + 6 nodes (T1..T6) with iso due timestamps. */
  record CreatePlanResponse(
      @JsonProperty("plan_id") String planId,
      List<NodePreview> nodes) {

    public record NodePreview(
        @JsonProperty("nid") String nid,
        @JsonProperty("t_level") String tLevel,
        @JsonProperty("due_at") String dueAt) {}
  }
}
