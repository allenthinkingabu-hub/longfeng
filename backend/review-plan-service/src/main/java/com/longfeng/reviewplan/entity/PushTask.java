package com.longfeng.reviewplan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 推送任务 JPA Entity · 对应 V1.0.061 wb_push_task.
 *
 * <p>C6: idempotency_key = MD5(node_id + ':' + scheduled_at::text) · UNIQUE 约束 DB 已建.
 * C9: scheduled_at / created_at 均 TIMESTAMPTZ → OffsetDateTime.
 * version 乐观锁 · XXL-Job CAS 抢占防并发双推.
 */
@Entity
@Table(name = "wb_push_task", schema = "review")
public class PushTask implements Serializable {

  /** 状态：0 等待 / 1 处理中 / 2 成功 / 3 部分成功 / 9 失败 */
  public static final short STATUS_PENDING = 0;
  public static final short STATUS_PROCESSING = 1;
  public static final short STATUS_SUCCESS = 2;
  public static final short STATUS_PARTIAL = 3;
  public static final short STATUS_DEAD = 9;

  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "node_id", nullable = false)
  private Long nodeId;

  @Column(name = "student_id", nullable = false)
  private Long studentId;

  /** 推送通道逗号分隔 · WX_MP,APP,EMAIL,SMS. */
  @Column(name = "channels", length = 64, nullable = false)
  private String channels;

  /** C9: TIMESTAMPTZ → OffsetDateTime · 预定推送时间（DND 后可能顺延）. */
  @Column(name = "scheduled_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime scheduledAt;

  @Column(name = "status", nullable = false)
  private short status = STATUS_PENDING;

  @Column(name = "tried_times", nullable = false)
  private short triedTimes = 0;

  @Column(name = "last_error")
  private String lastError;

  /**
   * C6: MD5(node_id + ':' + scheduled_at::text) · UNIQUE 约束防重复投递.
   * 计算由 {@link com.longfeng.reviewplan.service.PushTaskOrchestrator} 完成.
   */
  @Column(name = "idempotency_key", length = 64, nullable = false)
  private String idempotencyKey;

  @Version
  @Column(name = "version", nullable = false)
  private int version = 0;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime createdAt;

  // ---- getters / setters ----

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public Long getNodeId() { return nodeId; }
  public void setNodeId(Long nodeId) { this.nodeId = nodeId; }

  public Long getStudentId() { return studentId; }
  public void setStudentId(Long studentId) { this.studentId = studentId; }

  public String getChannels() { return channels; }
  public void setChannels(String channels) { this.channels = channels; }

  public OffsetDateTime getScheduledAt() { return scheduledAt; }
  public void setScheduledAt(OffsetDateTime scheduledAt) { this.scheduledAt = scheduledAt; }

  public short getStatus() { return status; }
  public void setStatus(short status) { this.status = status; }

  public short getTriedTimes() { return triedTimes; }
  public void setTriedTimes(short triedTimes) { this.triedTimes = triedTimes; }

  public String getLastError() { return lastError; }
  public void setLastError(String lastError) { this.lastError = lastError; }

  public String getIdempotencyKey() { return idempotencyKey; }
  public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

  public int getVersion() { return version; }
  public void setVersion(int version) { this.version = version; }

  public Long getTenantId() { return tenantId; }
  public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
