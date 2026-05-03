package com.longfeng.wrongbook.mq;

import static org.assertj.core.api.Assertions.assertThat;

import com.longfeng.wrongbook.event.WrongItemChangedEvent;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit test verifying RocketMQ topic naming convention.
 * S7 Issue 6: topic must match {@code ^[%|a-zA-Z0-9_-]+$} (no dots).
 */
class WrongItemProducerTopicTest {

  private static final Pattern ROCKETMQ_TOPIC_PATTERN = Pattern.compile("^[%|a-zA-Z0-9_\\-]+$");

  @Test
  @DisplayName("S7 Issue 6 · WrongItemChangedEvent.TOPIC matches RocketMQ naming rule (no dots)")
  void topicNameHasNoDots() {
    String topic = WrongItemChangedEvent.TOPIC;
    assertThat(topic).doesNotContain(".");
    assertThat(ROCKETMQ_TOPIC_PATTERN.matcher(topic).matches())
        .as("topic '%s' must match ^[%%|a-zA-Z0-9_-]+$", topic)
        .isTrue();
  }

  @Test
  @DisplayName("S7 Issue 6 · topic is wrongbook_item_changed (underscore)")
  void topicValue() {
    assertThat(WrongItemChangedEvent.TOPIC).isEqualTo("wrongbook_item_changed");
  }
}
