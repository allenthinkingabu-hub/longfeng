package com.longfeng.wrongbook.mq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.longfeng.wrongbook.entity.WrongItemOutbox;
import com.longfeng.wrongbook.event.WrongItemChangedEvent;
import com.longfeng.wrongbook.repo.WrongItemOutboxRepository;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Emits WrongItemChangedEvent to topic {@code wrongbook.item.changed}. Primary path: RocketMQ
 * syncSend. Fallback (ADR 0002): insert into wrong_item_outbox; scheduler (S10) republishes.
 */
@Component
public class WrongItemProducer {

  private static final Logger LOG = LoggerFactory.getLogger(WrongItemProducer.class);

  private final RocketMQTemplate template;
  private final WrongItemOutboxRepository outboxRepo;
  private final ObjectMapper objectMapper;

  public WrongItemProducer(
      @Autowired(required = false) RocketMQTemplate template,
      WrongItemOutboxRepository outboxRepo,
      ObjectMapper objectMapper) {
    this.template = template;
    this.outboxRepo = outboxRepo;
    this.objectMapper = objectMapper;
  }

  public void publish(WrongItemChangedEvent evt) {
    try {
      if (template == null) {
        LOG.info("RocketMQTemplate absent · routing {} to outbox fallback", evt);
        writeOutbox(evt);
        return;
      }
      template.syncSend(WrongItemChangedEvent.TOPIC, MessageBuilder.withPayload(evt).build());
    } catch (Exception ex) {
      LOG.warn("RocketMQ syncSend failed for {} · falling back to outbox: {}", evt, ex.getMessage());
      writeOutbox(evt);
    }
  }

  private void writeOutbox(WrongItemChangedEvent evt) {
    WrongItemOutbox row = new WrongItemOutbox();
    row.setAggregateId(evt.itemId());
    row.setEventType(evt.action());
    row.setStatus("PENDING");
    try {
      row.setPayload(objectMapper.writeValueAsString(evt));
    } catch (JsonProcessingException jpe) {
      row.setPayload("{\"itemId\":" + evt.itemId() + ",\"action\":\"" + evt.action() + "\"}");
    }
    outboxRepo.save(row);
  }
}
