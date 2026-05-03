package com.longfeng.reviewplan.service;

import com.longfeng.reviewplan.entity.PushTask;
import com.longfeng.reviewplan.repo.PushTaskRepository;
import com.longfeng.reviewplan.support.SnowflakeIdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 推送任务编排器 · S6 BE-10 · D-MultiPush / D-Push-Idem / C6.
 *
 * <p>按 review.due 事件入 wb_push_task · 含 idempotency_key MD5 计算。
 * 幂等：MD5(node_id + ':' + scheduled_at::text) → UNIQUE 约束 ON CONFLICT DO NOTHING。
 */
@Service
public class PushTaskOrchestrator {

  private static final Logger LOG = LoggerFactory.getLogger(PushTaskOrchestrator.class);

  /** 默认推送渠道串（D-MultiPush 优先级）. */
  public static final String DEFAULT_CHANNELS = "WX_MP,APP,EMAIL,SMS";

  /** 默认 tenant_id · 单租户 MVP. */
  private static final long DEFAULT_TENANT_ID = 1L;

  private final PushTaskRepository pushTaskRepo;
  private final DndService dndService;
  private final SnowflakeIdGenerator idGenerator;

  public PushTaskOrchestrator(
      PushTaskRepository pushTaskRepo,
      DndService dndService,
      SnowflakeIdGenerator idGenerator) {
    this.pushTaskRepo = pushTaskRepo;
    this.dndService = dndService;
    this.idGenerator = idGenerator;
  }

  /**
   * 为 review node 入队推送任务 · 幂等.
   *
   * @param nodeId      wb_review_node.id
   * @param studentId   学生 ID
   * @param scheduledAt 原定推送时刻（ready_at 时刻）
   * @param timezone    学生时区（student.preference.timezone）
   * @return 入队的 PushTask（已持久化）；幂等重复时返回 null（ON CONFLICT DO NOTHING）
   */
  @Transactional
  public PushTask enqueue(Long nodeId, Long studentId, Instant scheduledAt, String timezone) {
    // D-DND: 计算实际投递时间（静音时段延迟到次日 08:00）
    Instant actualDelivery = dndService.nextDeliveryTime(scheduledAt, timezone);

    // C6: idempotency_key = MD5(node_id + ':' + scheduled_at::text)
    String idemKey = computeIdempotencyKey(nodeId, scheduledAt);

    // 幂等前置检查（双重保险：先查再插，DB UNIQUE 约束终极兜底）
    if (pushTaskRepo.existsByIdempotencyKey(idemKey)) {
      LOG.debug("push_task already exists · nodeId={} idemKey={} · skip", nodeId, idemKey);
      return null;
    }

    PushTask task = new PushTask();
    task.setId(idGenerator.nextId());
    task.setNodeId(nodeId);
    task.setStudentId(studentId);
    task.setChannels(DEFAULT_CHANNELS);
    task.setScheduledAt(OffsetDateTime.ofInstant(actualDelivery, ZoneOffset.UTC));
    task.setStatus(PushTask.STATUS_PENDING);
    task.setIdempotencyKey(idemKey);
    task.setTenantId(DEFAULT_TENANT_ID);
    task.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));

    try {
      PushTask saved = pushTaskRepo.save(task);
      if (!actualDelivery.equals(scheduledAt)) {
        LOG.info(
            "push_task enqueued · nodeId={} · DND deferred {} → {}",
            nodeId, scheduledAt, actualDelivery);
      } else {
        LOG.debug("push_task enqueued · nodeId={} · scheduledAt={}", nodeId, scheduledAt);
      }
      return saved;
    } catch (DataIntegrityViolationException ex) {
      // DB UNIQUE 约束兜底：并发双写时只有一个成功
      LOG.debug(
          "push_task conflict on DB unique · nodeId={} idemKey={} · idempotent skip",
          nodeId, idemKey);
      return null;
    }
  }

  /**
   * C6: MD5(node_id + ':' + scheduled_at::text).
   *
   * <p>scheduled_at::text 格式对应 PostgreSQL TIMESTAMPTZ ISO-8601 文本形式；
   * 本实现用 Instant.toString()（UTC · ISO-8601）确保与 DB 语义一致。
   */
  public static String computeIdempotencyKey(Long nodeId, Instant scheduledAt) {
    String raw = nodeId + ":" + scheduledAt.toString();
    try {
      MessageDigest md = MessageDigest.getInstance("MD5");
      byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      // MD5 is guaranteed by JDK spec — this should never happen
      throw new IllegalStateException("MD5 not available", e);
    }
  }
}
