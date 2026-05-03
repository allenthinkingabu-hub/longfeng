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
 * ObserverInvite — short-lived observer invitation code · TDD §4.12 V1.0.074.
 *
 * <p>A student generates an 8-character base32 invite code (trimmed to 6 chars stored in CHAR(6))
 * that expires after 24 hours. A parent or teacher exchanges it via
 * {@code POST /api/observer/exchange} to create an {@link ObserverSession}.
 *
 * <p>Status machine:
 * <pre>
 *   PENDING(1) → EXCHANGED(2)  (invite_code consumed · ObserverInviteService.exchange)
 *   PENDING(1) → EXPIRED(3)    (24h TTL · ObserverSessionGcJob)
 *   PENDING(1) → REVOKED(4)    (student explicit revocation)
 * </pre>
 *
 * <p>D-Observer-TTL: when status transitions to EXCHANGED, the resulting {@link ObserverSession}
 * TTL is: PARENT → +30 days · TEACHER → +90 days.
 *
 * <p>C3 RED LINE: maps only to {@code anon.observer_invite} — never {@code wb_*}.
 * C9: all timestamps use {@link OffsetDateTime} (TIMESTAMPTZ in PG).
 */
@Entity
@Table(name = "observer_invite", schema = "anon")
@EntityListeners(AuditingEntityListener.class)
public class ObserverInvite implements Serializable {

  // ── Status constants ────────────────────────────────────────────────────
  public static final short STATUS_PENDING    = 1;
  public static final short STATUS_EXCHANGED  = 2;
  public static final short STATUS_EXPIRED    = 3;
  public static final short STATUS_REVOKED    = 4;

  // ── Role constants ──────────────────────────────────────────────────────
  public static final String ROLE_PARENT  = "PARENT";
  public static final String ROLE_TEACHER = "TEACHER";

  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  /**
   * 6-character uppercase alphanumeric invite code.
   * Generated as first 6 chars of a base32-encoded UUID (uppercase A-Z2-7, only letters+digits).
   */
  @Column(name = "invite_code", length = 6, nullable = false, unique = true)
  private String inviteCode;

  /** The student who initiated the invite. */
  @Column(name = "student_id", nullable = false)
  private Long studentId;

  /** PARENT / TEACHER — drives session TTL on exchange. */
  @Column(name = "role", length = 16, nullable = false)
  private String role;

  @Column(name = "status", nullable = false)
  private Short status = STATUS_PENDING;

  /** Created_at + 24h; enforced by ObserverInviteService and GcJob. */
  @Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime expiresAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime createdAt;

  // ── Getters / Setters ───────────────────────────────────────────────────
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getInviteCode() { return inviteCode; }
  public void setInviteCode(String inviteCode) { this.inviteCode = inviteCode; }

  public Long getStudentId() { return studentId; }
  public void setStudentId(Long studentId) { this.studentId = studentId; }

  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }

  public Short getStatus() { return status; }
  public void setStatus(Short status) { this.status = status; }

  public OffsetDateTime getExpiresAt() { return expiresAt; }
  public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }

  public OffsetDateTime getCreatedAt() { return createdAt; }
}
