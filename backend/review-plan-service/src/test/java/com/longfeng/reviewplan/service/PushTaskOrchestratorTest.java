package com.longfeng.reviewplan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.longfeng.reviewplan.entity.PushTask;
import com.longfeng.reviewplan.repo.PushTaskRepository;
import com.longfeng.reviewplan.support.SnowflakeIdGenerator;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * PushTaskOrchestratorTest · S6 BE-10 · 单元测试.
 *
 * <p>覆盖：idempotency_key MD5 唯一 · 多次入队幂等 · DND 延迟.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PushTaskOrchestrator · 推送任务编排")
class PushTaskOrchestratorTest {

  @Mock
  private PushTaskRepository pushTaskRepo;

  private final DndService dndService = new DndService();
  private final SnowflakeIdGenerator idGenerator = new SnowflakeIdGenerator(1);

  private PushTaskOrchestrator orchestrator;

  @BeforeEach
  void setUp() {
    orchestrator = new PushTaskOrchestrator(pushTaskRepo, dndService, idGenerator);
    // 默认：key 不存在 → 允许入队
    when(pushTaskRepo.existsByIdempotencyKey(anyString())).thenReturn(false);
    when(pushTaskRepo.save(any(PushTask.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  // ── C6 idempotency_key MD5 ─────────────────────────────────────

  @Test
  @DisplayName("C6: 相同 nodeId + scheduledAt 生成相同 MD5 idempotency_key")
  void idemKey_sameInput_sameMd5() {
    Long nodeId = 12345L;
    Instant ts = Instant.parse("2026-05-01T15:00:00Z");
    String key1 = PushTaskOrchestrator.computeIdempotencyKey(nodeId, ts);
    String key2 = PushTaskOrchestrator.computeIdempotencyKey(nodeId, ts);
    assertThat(key1).isEqualTo(key2);
    assertThat(key1).hasSize(32); // MD5 hex = 32 chars
  }

  @Test
  @DisplayName("C6: 不同 nodeId 生成不同 MD5")
  void idemKey_diffNodeId_diffMd5() {
    Instant ts = Instant.parse("2026-05-01T15:00:00Z");
    String key1 = PushTaskOrchestrator.computeIdempotencyKey(100L, ts);
    String key2 = PushTaskOrchestrator.computeIdempotencyKey(101L, ts);
    assertThat(key1).isNotEqualTo(key2);
  }

  @Test
  @DisplayName("C6: 不同 scheduledAt 生成不同 MD5")
  void idemKey_diffScheduledAt_diffMd5() {
    Long nodeId = 999L;
    Instant ts1 = Instant.parse("2026-05-01T15:00:00Z");
    Instant ts2 = Instant.parse("2026-05-01T16:00:00Z");
    String key1 = PushTaskOrchestrator.computeIdempotencyKey(nodeId, ts1);
    String key2 = PushTaskOrchestrator.computeIdempotencyKey(nodeId, ts2);
    assertThat(key1).isNotEqualTo(key2);
  }

  // ── 幂等性 ──────────────────────────────────────────────────────

  @Test
  @DisplayName("幂等: key 已存在时第二次 enqueue 返回 null · 不重复 save")
  void enqueue_idempotent_returnNullOnDuplicate() {
    Long nodeId = 42L;
    Instant ts = Instant.parse("2026-05-01T15:00:00Z");
    String idemKey = PushTaskOrchestrator.computeIdempotencyKey(nodeId, ts);

    // 第一次：不存在 → 正常入队
    when(pushTaskRepo.existsByIdempotencyKey(idemKey)).thenReturn(false);
    PushTask first = orchestrator.enqueue(nodeId, 1L, ts, "Asia/Shanghai");
    assertThat(first).isNotNull();

    // 第二次：已存在 → 幂等跳过
    when(pushTaskRepo.existsByIdempotencyKey(idemKey)).thenReturn(true);
    PushTask second = orchestrator.enqueue(nodeId, 1L, ts, "Asia/Shanghai");
    assertThat(second).isNull();

    // save 只调用一次
    verify(pushTaskRepo, times(1)).save(any(PushTask.class));
  }

  @Test
  @DisplayName("幂等: 多次调用 · key 相同 · 只入队一次")
  void enqueue_multipleCallsSameKey_onlyOneSave() {
    Long nodeId = 77L;
    Instant ts = Instant.parse("2026-06-01T10:00:00Z");
    String idemKey = PushTaskOrchestrator.computeIdempotencyKey(nodeId, ts);

    when(pushTaskRepo.existsByIdempotencyKey(idemKey))
        .thenReturn(false)   // 第1次
        .thenReturn(true)    // 第2次
        .thenReturn(true);   // 第3次

    orchestrator.enqueue(nodeId, 2L, ts, "Asia/Shanghai");
    orchestrator.enqueue(nodeId, 2L, ts, "Asia/Shanghai");
    orchestrator.enqueue(nodeId, 2L, ts, "Asia/Shanghai");

    verify(pushTaskRepo, times(1)).save(any(PushTask.class));
  }

  // ── DND 延迟 ───────────────────────────────────────────────────

  @Test
  @DisplayName("DND: 上海 23:30 入队 → scheduledAt 顺延到次日 08:00")
  void enqueue_dndDeferred_scheduledAtNextDay() {
    Long nodeId = 55L;
    Instant inDnd = ZonedDateTime.of(2026, 5, 1, 23, 30, 0, 0,
        ZoneId.of("Asia/Shanghai")).toInstant();

    PushTask task = orchestrator.enqueue(nodeId, 3L, inDnd, "Asia/Shanghai");
    assertThat(task).isNotNull();

    Instant expected =
        ZonedDateTime.of(2026, 5, 2, 8, 0, 0, 0, ZoneId.of("Asia/Shanghai")).toInstant();
    assertThat(task.getScheduledAt().toInstant()).isEqualTo(expected);
  }

  @Test
  @DisplayName("非 DND 时段: scheduledAt 不变")
  void enqueue_notDnd_scheduledAtUnchanged() {
    Long nodeId = 66L;
    Instant notDnd = ZonedDateTime.of(2026, 5, 1, 15, 0, 0, 0,
        ZoneId.of("Asia/Shanghai")).toInstant();

    PushTask task = orchestrator.enqueue(nodeId, 4L, notDnd, "Asia/Shanghai");
    assertThat(task).isNotNull();
    assertThat(task.getScheduledAt().toInstant()).isEqualTo(notDnd);
  }

  // ── channels + C8 msgkey ───────────────────────────────────────

  @Test
  @DisplayName("入队 PushTask channels = WX_MP,APP,EMAIL,SMS (D-MultiPush)")
  void enqueue_defaultChannels() {
    Long nodeId = 88L;
    Instant ts = Instant.parse("2026-05-01T10:00:00Z");

    PushTask task = orchestrator.enqueue(nodeId, 5L, ts, "Asia/Shanghai");
    assertThat(task).isNotNull();
    assertThat(task.getChannels()).isEqualTo(PushTaskOrchestrator.DEFAULT_CHANNELS);
  }

  @Test
  @DisplayName("idempotency_key 写入 PushTask 且 = MD5(nodeId:scheduledAt)")
  void enqueue_idemKeySetCorrectly() {
    Long nodeId = 99L;
    Instant ts = Instant.parse("2026-05-01T10:00:00Z");
    String expectedKey = PushTaskOrchestrator.computeIdempotencyKey(nodeId, ts);

    PushTask task = orchestrator.enqueue(nodeId, 6L, ts, "Asia/Shanghai");
    assertThat(task).isNotNull();
    assertThat(task.getIdempotencyKey()).isEqualTo(expectedKey);
  }
}
