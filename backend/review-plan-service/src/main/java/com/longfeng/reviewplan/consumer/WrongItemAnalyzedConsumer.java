package com.longfeng.reviewplan.consumer;

import com.longfeng.reviewplan.service.ReviewPlanService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Consumer · 消费 S4 wrongbook.item.analyzed → 幂等 INSERT 7 行 review_plan · SC-07.AC-1.
 *
 * <p>条件启用 {@code review.mq.enabled=true} · 默认关（无 name-server 时可跳过 boot 校验）。 IT 测试用
 * {@code MockRocketMQTemplate} 直接触发 {@link #onMessage}。
 */
@Component
@ConditionalOnProperty(value = "review.mq.enabled", havingValue = "true", matchIfMissing = false)
@RocketMQMessageListener(
    topic = "wrongbook.item.analyzed",
    consumerGroup = "review-plan-cg")
public class WrongItemAnalyzedConsumer implements RocketMQListener<WrongItemAnalyzedEvent> {

  private static final Logger LOG = LoggerFactory.getLogger(WrongItemAnalyzedConsumer.class);

  private final ReviewPlanService service;
  private final Counter successCounter;
  private final Counter duplicateCounter;
  private final Counter orphanCounter;

  public WrongItemAnalyzedConsumer(ReviewPlanService service, MeterRegistry meterRegistry) {
    this.service = service;
    this.successCounter =
        Counter.builder("review_plan_create_total").tag("outcome", "success").register(meterRegistry);
    this.duplicateCounter =
        Counter.builder("review_plan_create_total").tag("outcome", "duplicate").register(meterRegistry);
    this.orphanCounter =
        Counter.builder("review_plan_create_orphan_total").register(meterRegistry);
  }

  @Override
  public void onMessage(WrongItemAnalyzedEvent ev) {
    if (ev == null || ev.itemId() == null || ev.userId() == null || ev.analyzedAt() == null) {
      LOG.warn("invalid event · skip · {}", ev);
      orphanCounter.increment();
      return;
    }
    var created =
        service.createSevenNodes(ev.itemId(), ev.userId(), ev.analyzedAt());
    if (created.isEmpty()) {
      duplicateCounter.increment();
      LOG.debug("duplicate analyzed event for wrong_item_id={} · skip", ev.itemId());
    } else {
      successCounter.increment(created.size());
      LOG.info("created {} review_plan rows for wrong_item_id={}", created.size(), ev.itemId());
    }
  }
}
