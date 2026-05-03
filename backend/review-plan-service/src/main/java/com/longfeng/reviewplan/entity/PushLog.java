package com.longfeng.reviewplan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;

/**
 * 推送日志 JPA Entity · 对应 V1.0.062 wb_push_log.
 *
 * <p>每通道每次尝试一行 · 用于故障追查 + 成功率统计.
 * C9: delivered_at / created_at 均 TIMESTAMPTZ → OffsetDateTime.
 */
@Entity
@Table(name = "wb_push_log", schema = "review")
public class PushLog implements Serializable {

  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "task_id", nullable = false)
  private Long taskId;

  /** WX_MP / APP / EMAIL / SMS. */
  @Column(name = "channel", length = 16, nullable = false)
  private String channel;

  /** 第三方平台请求 ID · 微信 msgid / APNs uuid 等. */
  @Column(name = "request_id", length = 64)
  private String requestId;

  @Column(name = "success", nullable = false)
  private boolean success;

  @Column(name = "error_code", length = 32)
  private String errorCode;

  @Column(name = "error_msg")
  private String errorMsg;

  /** C9: 实际投递确认时间. */
  @Column(name = "delivered_at", columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime deliveredAt;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId;

  @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime createdAt;

  // ---- getters / setters ----

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public Long getTaskId() { return taskId; }
  public void setTaskId(Long taskId) { this.taskId = taskId; }

  public String getChannel() { return channel; }
  public void setChannel(String channel) { this.channel = channel; }

  public String getRequestId() { return requestId; }
  public void setRequestId(String requestId) { this.requestId = requestId; }

  public boolean isSuccess() { return success; }
  public void setSuccess(boolean success) { this.success = success; }

  public String getErrorCode() { return errorCode; }
  public void setErrorCode(String errorCode) { this.errorCode = errorCode; }

  public String getErrorMsg() { return errorMsg; }
  public void setErrorMsg(String errorMsg) { this.errorMsg = errorMsg; }

  public OffsetDateTime getDeliveredAt() { return deliveredAt; }
  public void setDeliveredAt(OffsetDateTime deliveredAt) { this.deliveredAt = deliveredAt; }

  public Long getTenantId() { return tenantId; }
  public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
