package com.longfeng.reviewplan.job;

import com.longfeng.reviewplan.entity.ReviewPlan;
import com.longfeng.reviewplan.feign.NotificationFeignClient;
import com.longfeng.reviewplan.feign.NotificationFeignClient.ReviewDueNotifyReq;
import com.longfeng.reviewplan.repo.ReviewPlanRepository;
import com.xxl.job.core.handler.annotation.XxlJob;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * XXL-Job · review-due-scan · §9.7 Step 7 + ADR 0015.
 *
 * <p>周期 5min · 扫 {@code status=0 AND next_due_at <= now()} · 批 500 · 乐观锁 CAS 派发.
 * 条件启用 {@code review.job.enabled=true} · IT/local 默认关（无 XXL-Job admin 可达）。
 */
@Component
@ConditionalOnProperty(value = "review.job.enabled", havingValue = "true", matchIfMissing = false)
public class ReviewDueJob {

  private static final Logger LOG = LoggerFactory.getLogger(ReviewDueJob.class);
  private static final int BATCH_SIZE = 500;

  private final ReviewPlanRepository planRepo;
  private final Optional<NotificationFeignClient> notificationClient;
  private final TransactionTemplate txTemplate;
  private final Counter scanCounter;
  private final Counter dispatchedCounter;
  private final Counter casFailCounter;

  @Autowired
  public ReviewDueJob(
      ReviewPlanRepository planRepo,
      Optional<NotificationFeignClient> notificationClient,
      PlatformTransactionManager txManager,
      MeterRegistry meterRegistry) {
    this.planRepo = planRepo;
    this.notificationClient = notificationClient;
    this.txTemplate = new TransactionTemplate(txManager);
    this.scanCounter = Counter.builder("review_due_scan_count").register(meterRegistry);
    this.dispatchedCounter =
        Counter.builder("review_due_event_publish_total").register(meterRegistry);
    this.casFailCounter =
        Counter.builder("review_due_cas_fail_total").register(meterRegistry);
  }

  @XxlJob("review-due-scan")
  public void scan() {
    execute();
  }

  /** 业务入口（IT 直接调此方法 · 不经 XXL-Job）. */
  public int execute() {
    List<ReviewPlan> batch = planRepo.findDueBatch(Instant.now(), BATCH_SIZE);
    scanCounter.increment();
    int dispatched = 0;
    for (ReviewPlan p : batch) {
      Integer rows =
          txTemplate.execute(
              status -> planRepo.compareAndUpdateDispatch(p.getId(), p.getDispatchVersion()));
      if (rows == null || rows != 1) {
        casFailCounter.increment();
        LOG.debug("cas fail planId={} · skip (抢占 · 其他 executor 已派发)", p.getId());
        continue;
      }
      notificationClient.ifPresent(
          c -> {
            try {
              c.notifyReviewDue(
                  new ReviewDueNotifyReq(
                      p.getId(), p.getStudentId(), p.getWrongItemId(),
                      p.getNodeIndex() == null ? 0 : p.getNodeIndex().intValue(),
                      p.getNextDueAt()));
            } catch (Exception e) {
              LOG.warn("notification feign fail planId={} · event 留 outbox relay 兜底", p.getId(), e);
            }
          });
      dispatched++;
      dispatchedCounter.increment();
    }
    if (dispatched > 0) {
      LOG.info("review-due-scan · batch={} · dispatched={}", batch.size(), dispatched);
    }
    return dispatched;
  }
}
