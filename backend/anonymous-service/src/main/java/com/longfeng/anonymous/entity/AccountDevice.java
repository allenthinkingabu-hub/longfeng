package com.longfeng.anonymous.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * AccountDevice entity · TDD §4.13 · V1.0.076.
 *
 * <p>Soft-bind between a student account and a device fingerprint.
 * D-Guest-Device: claim validates device_fp consistency.
 * C9: all timestamps are TIMESTAMPTZ / {@link OffsetDateTime}.
 * C3 RED LINE: no reference to any {@code wb_*} table.
 */
@Entity
@Table(name = "account_device", schema = "anon")
@EntityListeners(AuditingEntityListener.class)
public class AccountDevice implements Serializable {

  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "student_id", nullable = false)
  private Long studentId;

  @Column(name = "tenant_id", nullable = false)
  private Long tenantId = 0L;

  @Column(name = "device_fp", length = 128, nullable = false)
  private String deviceFp;

  @Column(name = "platform", length = 16)
  private String platform;

  @CreatedDate
  @Column(name = "first_seen_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime firstSeenAt;

  @LastModifiedDate
  @Column(name = "last_seen_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime lastSeenAt;

  @Column(name = "login_count", nullable = false)
  private Integer loginCount = 1;

  // ── Getters / Setters ───────────────────────────────────────────────────
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public Long getStudentId() { return studentId; }
  public void setStudentId(Long studentId) { this.studentId = studentId; }

  public Long getTenantId() { return tenantId; }
  public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

  public String getDeviceFp() { return deviceFp; }
  public void setDeviceFp(String deviceFp) { this.deviceFp = deviceFp; }

  public String getPlatform() { return platform; }
  public void setPlatform(String platform) { this.platform = platform; }

  public OffsetDateTime getFirstSeenAt() { return firstSeenAt; }

  public OffsetDateTime getLastSeenAt() { return lastSeenAt; }

  public Integer getLoginCount() { return loginCount; }
  public void setLoginCount(Integer loginCount) { this.loginCount = loginCount; }
}
