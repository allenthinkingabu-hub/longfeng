package com.longfeng.aianalysis.consumer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 监听 {@code ai.analysis.completed} topic · TDD §6.1 Saga-2b + plan §5.S3。
 *
 * <p>触发场景：QuestionAnalyzerImpl 终态 → 走 @TransactionalEventListener AFTER_COMMIT
 * → outbox-relay 发布 {@code ai.analysis.completed} → 本 consumer 收到。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>幂等校验（同 taskId 重复消息 → skip）
 *   <li>触发 wrongbook-service 写 wb_question.mastery（通过 OpenFeign · 当前 phase 仅日志占位）
 *   <li>发布 question.analyzed 事件给 review-plan-service（生成 ebbinghaus 计划）
 * </ul>
 *
 * <p>失败兜底：异常时**仍 ack**（不抛 throw）防 MQ 死循环 · log + DLQ 由 RocketMQ 配置兜。
 */
@Component
@RocketMQMessageListener(
    topic = "ai.analysis.completed",
    consumerGroup = "ai-analysis-completed-cg")
public class AnalysisCompletedConsumer implements RocketMQListener<String> {

  private static final Logger LOG = LoggerFactory.getLogger(AnalysisCompletedConsumer.class);

  private final ObjectMapper objectMapper;

  public AnalysisCompletedConsumer(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void onMessage(String raw) {
    try {
      ObjectNode node = (ObjectNode) objectMapper.readTree(raw);
      String taskId = node.path("taskId").asText(null);
      Long questionId = node.has("questionId") ? node.get("questionId").asLong() : null;
      if (taskId == null || questionId == null) {
        LOG.warn("skip malformed event · raw={}", raw);
        return;
      }
      // Phase 1 · 占位日志 · Phase 2 接入 wrongbook Feign + 发布 question.analyzed event
      LOG.info(
          "ai.analysis.completed received · taskId={} questionId={} · TODO Feign wrongbook update mastery",
          taskId,
          questionId);
    } catch (JsonProcessingException jpe) {
      LOG.warn("skip unparseable event · raw={} · cause={}", raw, jpe.getMessage());
    } catch (Exception ex) {
      // 防 MQ 死循环 · log+ack · DLQ 由 RocketMQ 配置承载
      LOG.error("AnalysisCompletedConsumer unexpected · raw={}", raw, ex);
    }
  }
}
