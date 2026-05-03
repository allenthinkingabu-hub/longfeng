package com.longfeng.reviewplan.feign;

import com.longfeng.reviewplan.repo.PushLogRepository;
import com.longfeng.reviewplan.repo.PushTaskRepository;
import com.longfeng.reviewplan.support.SnowflakeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * S6 notification-service Feign Fallback · C10 · Sentinel 熔断降级.
 *
 * <p>任意渠道调用失败时返回 success=false 的 {@link NotificationFeignClient.SendResp}；
 * 调用方 {@link com.longfeng.reviewplan.job.PushTaskRelayJob} 负责写 push_log status=DEAD。
 * Fallback 本身不写库（避免 Feign 线程池内 DB 调用死锁）——由 Job 在自己的事务写。
 */
@Component
public class NotificationFeignClientFallback implements NotificationFeignClient {

  private static final Logger LOG = LoggerFactory.getLogger(NotificationFeignClientFallback.class);

  private static final SendResp DEAD =
      new SendResp(false, null, "SENTINEL_FALLBACK", "notification-service circuit open");

  // Injected lazily (optional) — if absent in IT stubs the fallback still works
  @Autowired(required = false)
  private PushLogRepository pushLogRepository;

  @Autowired(required = false)
  private PushTaskRepository pushTaskRepository;

  @Autowired(required = false)
  private SnowflakeIdGenerator idGenerator;

  @Override
  public SendResp sendWxMp(SendReq req) {
    LOG.warn("notification-service WX_MP fallback · msgkey={}", req.msgkey());
    return DEAD;
  }

  @Override
  public SendResp sendApp(SendReq req) {
    LOG.warn("notification-service APP fallback · msgkey={}", req.msgkey());
    return DEAD;
  }

  @Override
  public SendResp sendEmail(SendReq req) {
    LOG.warn("notification-service EMAIL fallback · msgkey={}", req.msgkey());
    return DEAD;
  }

  @Override
  public SendResp sendSms(SendReq req) {
    LOG.warn("notification-service SMS fallback · msgkey={}", req.msgkey());
    return DEAD;
  }
}
