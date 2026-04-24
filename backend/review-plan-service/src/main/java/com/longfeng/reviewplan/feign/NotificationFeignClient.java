package com.longfeng.reviewplan.feign;

import java.time.Instant;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * S6 notification-service Feign · SC-07 节点 due 派发（由 ReviewDueJob 调用）.
 *
 * <p>fallback 走 Sentinel default · 失败不阻塞 Job（event 仍在 outbox · Relay 后续重发）.
 */
@FeignClient(
    name = "notification-service",
    url = "${notification.url:http://localhost:18090}")
public interface NotificationFeignClient {

  @PostMapping("/notifications/review-due")
  void notifyReviewDue(@RequestBody ReviewDueNotifyReq req);

  /** POST /notifications/review-due payload. */
  record ReviewDueNotifyReq(
      Long planId, Long userId, Long wrongItemId, Integer nodeIndex, Instant dueAt) {}
}
