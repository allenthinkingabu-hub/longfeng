package com.longfeng.anonymous.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.OffsetDateTime;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * ObserverSession — read-only observer JWT session · TDD §4.12 V1.0.075.
 *
 * <p>Created when a parent or teacher exchanges an {@link ObserverInvite}. The resulting HS256 JWT
 * carries {@code scope=READ} and {@code jti} for revocation tracking.
 *
 * <p>Status machine (D-State · CAS on {@code version}):
 * <pre>
 *   ACTIVE(1) → EXPIRED(2)             (expires_at reached · ObserverSessionGcJob)
 *   ACTIVE(1) → REVOKED_BY_STUDENT(3)  (student revokes · ObserverSessionService.revokeByStudent)
 * </pre>
 *
 * <p>D-Observer-TTL: PARENT 30d / TEACHER 90d (set at exchange time from {@code invite.role}).
 * D-Observer-Revoke: on revocation, write {@code obs:revoked:{jti}} to Redis SETEX so the
 * gateway ObserverFilter can block within ≤ 1 s.
 *
 * <p>C4 RED LINE: {@code scope} is always {@code "READ"} — the service layer must never produce
 * an ObserverSession with scope != READ.
 *
 * <p>C3 RED LINE: maps only to {@code anon.observer_session} — never {@code wb_*}.
 * C9: all timestamps use {@link OffsetDateTime} (TIMESTAMPTZ in PG).
 */
@Entity
@Table(name = "observer_session", schema = "anon")
@EntityListeners(AuditingEntityListener.class)
public class ObserverSession implements Serializable {

  // ── Status constants ────────────────────────────────────────────────────
  public static final short STATUS_ACTIVE             = 1;
  public static final short STATUS_EXPIRED            = 2;
  public static final short STATUS_REVOKED_BY_STUDENT = 3;

  /**
   * Scope constant enforced by C4.
   * ObserverSessionService must embed this literal in every JWT it issues.
   */
  public static final String SCOPE_READ = "READ";

  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  /** JWT ID — unique; used as the Redis revocation key suffix ({@code obs:revoked:{jti}}). */
  @Column(name = "jti", length = 64, nullable = false, unique = true)
  private String jti;

  /** The student being observed. */
  @Column(name = "student_id", nullable = false)
  private Long studentId;

  /** PARENT / TEACHER (from the exchanged ObserverInvite). */
  @Column(name = "role", length = 16, nullable = false)
  private String role;

  /** Observer's device fingerprint (optional — may be null for web sessions). */
  @Column(name = "device_fp", length = 128)
  private String deviceFp;

  @Column(name = "status", nullable = false)
  private Short status = STATUS_ACTIVE;

  /**
   * CAS version — all state transitions must read current version and pass it to
   * {@link com.longfeng.anonymous.observer.ObserverSessionRepository#casRevoke}.
   * S1 caveat: included to prevent concurrent revoke/expire race.
   */
  @Version
  @Column(name = "version", nullable = false)
  private Integer version = 0;

  @CreatedDate
  @Column(name = "issued_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime issuedAt;

  /** Updated on each successful observer API call (sliding activity tracking). */
  @Column(name = "last_seen_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime lastSeenAt;

  /** D-Observer-TTL: PARENT → issued_at + 30d; TEACHER → issued_at + 90d. */
  @Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime expiresAt;

  /** Set when status → REVOKED_BY_STUDENT. */
  @Column(name = "revoked_by_student_at", columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime revokedByStudentAt;

  // ── Getters / Setters ───────────────────────────────────────────────────
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getJti() { return jti; }
  public void setJti(String jti) { this.jti = jti; }

  public Long getStudentId() { return studentId; }
  public void setStudentId(Long studentId) { this.studentId = studentId; }

  public String getRole() { return role; }
  public void setRole(String role) { this.role = role; }

  public String getDeviceFp() { return deviceFp; }
  public void setDeviceFp(String deviceFp) { this.deviceFp = deviceFp; }

  public Short getStatus() { return status; }
  public void setStatus(Short status) { this.status = status; }

  public Integer getVersion() { return version; }
  public void setVersion(Integer version) { this.version = version; }

  public OffsetDateTime getIssuedAt() { return issuedAt; }

  public OffsetDateTime getLastSeenAt() { return lastSeenAt; }
  public void setLastSeenAt(OffsetDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }

  public OffsetDateTime getExpiresAt() { return expiresAt; }
  public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }

  public OffsetDateTime getRevokedByStudentAt() { return revokedByStudentAt; }
  public void setRevokedByStudentAt(OffsetDateTime revokedByStudentAt) {
    this.revokedByStudentAt = revokedByStudentAt;
  }
}
