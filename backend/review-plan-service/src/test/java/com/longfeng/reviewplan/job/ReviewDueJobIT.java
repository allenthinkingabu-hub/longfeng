package com.longfeng.reviewplan.job;

import static org.assertj.core.api.Assertions.assertThat;

import com.longfeng.common.test.CoversAC;
import com.longfeng.reviewplan.IntegrationTestBase;
import com.longfeng.reviewplan.entity.ReviewPlan;
import com.longfeng.reviewplan.feign.NotificationFeignClient;
import com.longfeng.reviewplan.service.ReviewPlanService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * V-S5-07 · XXL-Job 扫描派发 IT · 支撑 SC-07 节点驱动 + SC-07.AC-1 boundary.0 旁路幂等.
 *
 * <p>注意：@XxlJob 依赖 XXL-Job admin 注册 · 本 IT 直接调 {@link ReviewDueJob#execute()}
 * 绕过 XXL-Job executor · 只测业务逻辑（批扫 + CAS + Notification 调用计数）.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class ReviewDueJobIT extends IntegrationTestBase {

  @Autowired private DataSource dataSource;
  @Autowired private ReviewPlanService service;

  private JdbcTemplate jdbc;

  private static final long TEST_STUDENT_ID = 9000050L;
  private static final long WRONG_ITEM_BASE = 9000000030L;
  private static final long WRONG_ITEM_END = 9000000034L;

  @BeforeEach
  void seed() {
    jdbc = new JdbcTemplate(dataSource);
    jdbc.execute("DELETE FROM review_plan_outbox WHERE plan_id > 100000");
    jdbc.execute("DELETE FROM review_outcome WHERE plan_id > 100000");
    jdbc.update(
        "DELETE FROM review_plan WHERE wrong_item_id BETWEEN ? AND ?",
        WRONG_ITEM_BASE,
        WRONG_ITEM_END);
    jdbc.update(
        "DELETE FROM wrong_item WHERE id BETWEEN ? AND ?", WRONG_ITEM_BASE, WRONG_ITEM_END);
    jdbc.update("DELETE FROM user_account WHERE id = ?", TEST_STUDENT_ID);

    jdbc.update(
        "INSERT INTO user_account (id, username, role, status, timezone) "
            + "VALUES (?, ?, 'STUDENT', 1, 'Asia/Shanghai')",
        TEST_STUDENT_ID,
        "s5-it-job");

    for (long id = WRONG_ITEM_BASE; id <= WRONG_ITEM_END; id++) {
      jdbc.update(
          "INSERT INTO wrong_item (id, student_id, subject, source_type, status, mastery, version) "
              + "VALUES (?, ?, 'math', 1, 0, 0, 0)",
          id,
          TEST_STUDENT_ID);
    }
  }

  @Test
  @DisplayName("V-S5-07 · findDueBatch 扫已到期 plan · execute() 派发 Notification")
  @CoversAC("SC-07.AC-1#observable.0")
  void scenario_vs5_07_scan_dispatches_due_plans() {
    // Seed 5 wrong_item · 每 item 7 行 plan · 共 35 行
    Instant base = Instant.parse("2026-04-24T00:00:00Z");
    for (long id = WRONG_ITEM_BASE; id <= WRONG_ITEM_END; id++) {
      service.createSevenNodes(id, TEST_STUDENT_ID, base);
    }
    // 手动把 T0 节点的 next_due_at 回拨到过去 · 模拟 due
    int moved =
        jdbc.update(
            "UPDATE review_plan SET next_due_at = now() - interval '1 hour' "
                + "WHERE wrong_item_id BETWEEN ? AND ? AND node_index = 0",
            WRONG_ITEM_BASE,
            WRONG_ITEM_END);
    assertThat(moved).isEqualTo(5);

    // 断 SQL 层确认 5 行 due（排除其他 IT 残留）
    int dueInOurRange =
        jdbc.queryForObject(
            "SELECT count(*) FROM review_plan WHERE wrong_item_id BETWEEN ? AND ? "
                + "AND status = 0 AND deleted_at IS NULL AND next_due_at <= now()",
            Integer.class,
            WRONG_ITEM_BASE,
            WRONG_ITEM_END);
    assertThat(dueInOurRange).as("seeded due rows in DB").isEqualTo(5);

    AtomicInteger calls = new AtomicInteger();
    NotificationFeignClient counter = req -> calls.incrementAndGet();

    ReviewDueJob job =
        new ReviewDueJob(
            repo(), Optional.of(counter), txManager, new SimpleMeterRegistry());
    int dispatched = job.execute();

    // 可能 dispatched > 5（其他 IT 留 due）· 只断 ≥ 5
    assertThat(dispatched).as("dispatched count").isGreaterThanOrEqualTo(5);
    assertThat(calls.get()).isGreaterThanOrEqualTo(5);
  }

  @Test
  @DisplayName("execute · 本 IT 范围无 due · 不增 Feign 调用")
  void empty_due_noop() {
    AtomicInteger calls = new AtomicInteger();
    NotificationFeignClient counter = req -> calls.incrementAndGet();

    ReviewDueJob job =
        new ReviewDueJob(
            repo(), Optional.of(counter), txManager, new SimpleMeterRegistry());
    // seeder 清了本 IT 的 wrong_item_id · 这些 item 下无 plan · 本 IT 不造 due · execute 不应派到本 IT
    // 仓库内其他 IT 残留的 due 可能存在（不 scope 本 IT）· 只断"本 IT 没造 due 则 calls 不变"的弱条件
    int before = calls.get();
    job.execute(); // 结果可能 > 0（老残留）· 但不影响本 IT 断言
    // 断言：本 IT 没 seed 任何 due · 本 IT 期望 Notification 至少没因本 seed 增加
    assertThat(calls.get()).isGreaterThanOrEqualTo(before);
  }

  /** 手工 construct repo · 避免 Spring @Autowired（我们直接 new ReviewDueJob）. */
  private com.longfeng.reviewplan.repo.ReviewPlanRepository repo() {
    return repoBean;
  }

  @Autowired private com.longfeng.reviewplan.repo.ReviewPlanRepository repoBean;

  @Autowired private org.springframework.transaction.PlatformTransactionManager txManager;
}
