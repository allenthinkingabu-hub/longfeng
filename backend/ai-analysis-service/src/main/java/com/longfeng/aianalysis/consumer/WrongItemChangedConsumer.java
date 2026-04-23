package com.longfeng.aianalysis.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longfeng.aianalysis.event.ItemChangedEvent;
import com.longfeng.aianalysis.service.AnalysisService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Subscribes to S3 topic wrongbook.item.changed · invokes AnalysisService per event. */
@Component
@RocketMQMessageListener(
    topic = "wrongbook.item.changed",
    consumerGroup = "ai-analysis-cg")
public class WrongItemChangedConsumer implements RocketMQListener<String> {

  private static final Logger LOG = LoggerFactory.getLogger(WrongItemChangedConsumer.class);

  private final AnalysisService analysis;
  private final ObjectMapper om;

  public WrongItemChangedConsumer(AnalysisService analysis, ObjectMapper om) {
    this.analysis = analysis;
    this.om = om;
  }

  @Override
  public void onMessage(String raw) {
    ItemChangedEvent evt = parse(raw);
    if (evt == null) {
      LOG.warn("skip unparseable event: {}", raw);
      return;
    }
    if (!"created".equals(evt.action()) && !"updated".equals(evt.action())) {
      LOG.debug("ignore action={} itemId={}", evt.action(), evt.itemId());
      return;
    }
    try {
      int version = analysis.analyze(evt.itemId(), false);
      LOG.info("analyzed itemId={} version={} action={}", evt.itemId(), version, evt.action());
    } catch (Exception e) {
      // The service handles provider failure by writing status=9; if we still fault here,
      // log and ack so the queue is not blocked (arch §3.2 降级路径).
      LOG.error("analyze itemId={} unexpected", evt.itemId(), e);
    }
  }

  private ItemChangedEvent parse(String raw) {
    try {
      return om.readValue(raw, ItemChangedEvent.class);
    } catch (JsonProcessingException e) {
      return null;
    }
  }
}
