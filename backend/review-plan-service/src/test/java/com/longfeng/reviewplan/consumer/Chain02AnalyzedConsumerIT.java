package com.longfeng.reviewplan.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import com.longfeng.common.test.CoversAC;
import com.longfeng.reviewplan.IntegrationTestBase;
import com.longfeng.reviewplan.service.ReviewPlanService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;

/**
 * s5.5 chain-02 · analysis 完成事件 → review-plan 创建 7 节点 · DB 断言 count+7.
 *
 * <p>对照主文档 §10.5 chain-02 · 覆盖 SC-07.AC-2 跨服务联调（S4 → S5）· consumer 手动实例化 ·
 * 绕过 @RocketMQMessageListener 的 broker 探活 · 直接喂事件到 onMessage().
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Chain02AnalyzedConsumerIT extends IntegrationTestBase {

  @Autowired private ReviewPlanService service;
  @Autowired private DataSource dataSource;

  private WrongItemAnalyzedConsumer consumer;

  private JdbcTemplate jdbc;

  private static final long STUDENT_ID = 9000055L;
  private static final long WRONG_ITEM_ID = 9000000055L;

  @BeforeEach
  void cleanAndSeed() {
    jdbc = new JdbcTemplate(dataSource);
    if (consumer == null) {
      MeterRegistry meterRegistry = new SimpleMeterRegistry();
      consumer = new WrongItemAnalyzedConsumer(service, meterRegistry);
    }
    jdbc.update("DELETE FROM review_plan_outbox WHERE plan_id > 100000");
    jdbc.update("DELETE FROM review_outcome WHERE plan_id > 100000");
    jdbc.update("DELETE FROM review_plan WHERE wrong_item_id = ?", WRONG_ITEM_ID);
    jdbc.update("DELETE FROM wrong_item WHERE id = ?", WRONG_ITEM_ID);
    jdbc.update("DELETE FROM user_account WHERE id = ?", STUDENT_ID);

    jdbc.update(
        "INSERT INTO user_account (id, username, role, status, timezone) "
            + "VALUES (?, 's55-chain02-user', 'STUDENT', 1, 'Asia/Shanghai')",
        STUDENT_ID);
    jdbc.update(
        "INSERT INTO wrong_item (id, student_id, subject, source_type, status, mastery, version) "
            + "VALUES (?, ?, 'math', 1, 0, 0, 0)",
        WRONG_ITEM_ID,
        STUDENT_ID);
  }

  @Test
  @DisplayName("s5.5 chain-02 · WrongItemAnalyzedEvent 触发 review_plan 7 行（count+7）")
  @CoversAC("SC-07.AC-2#chain_02.0")
  void chain02_analyzed_event_creates_seven_review_plan_rows() {
    Instant analyzedAt = Instant.parse("2026-04-24T08:00:00Z");
    Instant start = Instant.now();

    consumer.onMessage(new WrongItemAnalyzedEvent(WRONG_ITEM_ID, STUDENT_ID, "math", analyzedAt));

    Duration elapsed = Duration.between(start, Instant.now());

    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM review_plan WHERE wrong_item_id = ?",
            Integer.class,
            WRONG_ITEM_ID);
    assertThat(count).as("chain-02 · event → 7 节点插入").isEqualTo(7);
    assertThat(elapsed.toMillis()).as("chain-02 · 事件处理 < 1s").isLessThan(1000L);
  }

  @Test
  @DisplayName("s5.5 chain-02 idempotent · 重复投递事件 · 节点仍 7 行（不重复创建）")
  @CoversAC("SC-07.AC-2#chain_02.idempotent")
  void chain02_duplicate_event_stays_seven_rows() {
    Instant analyzedAt = Instant.parse("2026-04-24T08:00:00Z");
    WrongItemAnalyzedEvent ev =
        new WrongItemAnalyzedEvent(WRONG_ITEM_ID, STUDENT_ID, "math", analyzedAt);

    consumer.onMessage(ev);
    consumer.onMessage(ev); // 重投

    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM review_plan WHERE wrong_item_id = ?",
            Integer.class,
            WRONG_ITEM_ID);
    assertThat(count).as("chain-02 · 幂等 · 重投不增").isEqualTo(7);
  }

  @Test
  @DisplayName("s5.5 chain-02 invalid · null item/user/analyzedAt 不炸 · 行数 0")
  @CoversAC("SC-07.AC-2#chain_02.invalid_event")
  void chain02_invalid_event_no_rows() {
    consumer.onMessage(new WrongItemAnalyzedEvent(null, STUDENT_ID, "math", Instant.now()));
    consumer.onMessage(new WrongItemAnalyzedEvent(WRONG_ITEM_ID, null, "math", Instant.now()));
    consumer.onMessage(new WrongItemAnalyzedEvent(WRONG_ITEM_ID, STUDENT_ID, "math", null));

    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM review_plan WHERE wrong_item_id = ?",
            Integer.class,
            WRONG_ITEM_ID);
    assertThat(count).as("chain-02 · 非法 event · 跳过而非崩溃").isEqualTo(0);
  }
}
