package com.longfeng.wrongbook;

import java.util.ArrayList;
import java.util.List;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.Message;

/**
 * Captures every outbound syncSend in-process. Avoids a real broker and sidesteps the Mockito
 * inline-mock / JDK 25 incompatibility on the RocketMQ template class.
 */
@TestConfiguration
public class TestMqConfig {

  @Bean
  public List<CapturedMessage> capturedMessages() {
    return new ArrayList<>();
  }

  @Bean
  @Primary
  public RocketMQTemplate rocketMQTemplate(List<CapturedMessage> capture) {
    return new RecordingRocketMQTemplate(capture);
  }

  public record CapturedMessage(String topic, Object payload) {}

  /** Lightweight subclass — overrides only the overloads S3 producer invokes. */
  public static class RecordingRocketMQTemplate extends RocketMQTemplate {
    private final List<CapturedMessage> capture;

    public RecordingRocketMQTemplate(List<CapturedMessage> capture) {
      this.capture = capture;
    }

    @Override
    public SendResult syncSend(String destination, Message<?> message) {
      capture.add(new CapturedMessage(destination, message.getPayload()));
      return new SendResult();
    }

    @Override
    public SendResult syncSend(String destination, Object payload) {
      capture.add(new CapturedMessage(destination, payload));
      return new SendResult();
    }

    @Override
    public void afterPropertiesSet() {
      // skip — the real producer lifecycle is not wanted in tests.
    }

    @Override
    public void destroy() {
      // skip — no resources held.
    }
  }
}
