package com.longfeng.anonymous.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * ShareTokenAudit — immutable audit log for share token accesses · TDD §4.11 V1.0.073.
 *
 * <p>One row per access event. When the viewer subsequently registers as a student,
 * {@code upgradedStudentId} is back-filled by the caller.
 *
 * <p>C3 RED LINE: maps only to {@code anon.share_token_audit} — never {@code wb_*}.
 * C9: {@code viewed_at} uses {@link OffsetDateTime} (TIMESTAMPTZ).
 */
@Entity
@Table(name = "share_token_audit", schema = "anon")
@EntityListeners(AuditingEntityListener.class)
public class ShareTokenAudit implements Serializable {

  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  /** Soft FK → {@code anon.share_token.jti}. */
  @Column(name = "jti", length = 64, nullable = false)
  private String jti;

  /** Device fingerprint of the viewer (may be null for bare link access). */
  @Column(name = "viewer_device_fp", length = 128)
  private String viewerDeviceFp;

  /** HMAC-SHA256 of viewer's IP address (PII masked). */
  @Column(name = "viewer_ip_hash", length = 64)
  private String viewerIpHash;

  /** Back-filled after viewer completes registration → conversion funnel analytics. */
  @Column(name = "upgraded_student_id")
  private Long upgradedStudentId;

  @CreatedDate
  @Column(name = "viewed_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime viewedAt;

  // ── Getters / Setters ───────────────────────────────────────────────────
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getJti() { return jti; }
  public void setJti(String jti) { this.jti = jti; }

  public String getViewerDeviceFp() { return viewerDeviceFp; }
  public void setViewerDeviceFp(String viewerDeviceFp) { this.viewerDeviceFp = viewerDeviceFp; }

  public String getViewerIpHash() { return viewerIpHash; }
  public void setViewerIpHash(String viewerIpHash) { this.viewerIpHash = viewerIpHash; }

  public Long getUpgradedStudentId() { return upgradedStudentId; }
  public void setUpgradedStudentId(Long upgradedStudentId) { this.upgradedStudentId = upgradedStudentId; }

  public OffsetDateTime getViewedAt() { return viewedAt; }
}
