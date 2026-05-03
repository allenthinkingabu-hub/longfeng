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
 * ShareToken aggregate entity · TDD §4.11 V1.0.072.
 *
 * <p>Represents a short-lived HS256 share link (≤ 7 days). The JWT is signed with
 * {@code longfeng.jwt.share.secret} and carries {@code jti} as the unique correlation ID.
 *
 * <p>Status machine (TDD §4.11):
 * <pre>
 *   ACTIVE(1) → EXPIRED(2)   (TTL elapsed · ShareTokenExpiryJob)
 *   ACTIVE(1) → REVOKED(3)   (sharer explicit revocation)
 *   ACTIVE(1) → EXHAUSTED(4) (usage_count ≥ usage_limit)
 * </pre>
 *
 * <p>C3 RED LINE: maps only to {@code anon.share_token} — never {@code wb_*}.
 * C9: all timestamps use {@link OffsetDateTime} (TIMESTAMPTZ in PG).
 */
@Entity
@Table(name = "share_token", schema = "anon")
@EntityListeners(AuditingEntityListener.class)
public class ShareToken implements Serializable {

  // ── Status constants ────────────────────────────────────────────────────
  public static final short STATUS_ACTIVE    = 1;
  public static final short STATUS_EXPIRED   = 2;
  public static final short STATUS_REVOKED   = 3;
  public static final short STATUS_EXHAUSTED = 4;

  // ── Share type constants ────────────────────────────────────────────────
  public static final String TYPE_EXAM_DAY    = "EXAM_DAY";
  public static final String TYPE_QUESTION    = "QUESTION";
  public static final String TYPE_REVIEW_NODE = "REVIEW_NODE";

  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  /** JWT ID — embedded in HS256 JWT; used as Bloom revocation key. */
  @Column(name = "jti", length = 64, nullable = false, unique = true)
  private String jti;

  /** The student who created this share link. */
  @Column(name = "sharer_student_id", nullable = false)
  private Long sharerStudentId;

  /** EXAM_DAY / QUESTION / REVIEW_NODE */
  @Column(name = "share_type", length = 16, nullable = false)
  private String shareType;

  /** 'question:{qid}' / 'review_node:{nid}' / 'exam:{eid}' */
  @Column(name = "relation_id", length = 128, nullable = false)
  private String relationId;

  /** Whether the receiver is allowed to claim (copy) the shared question. */
  @Column(name = "allow_claim", nullable = false)
  private Boolean allowClaim = Boolean.FALSE;

  /** Soft usage cap; status → EXHAUSTED when usage_count reaches this. */
  @Column(name = "usage_limit", nullable = false)
  private Integer usageLimit = 1000;

  /** Number of times this token has been successfully accessed. */
  @Column(name = "usage_count", nullable = false)
  private Integer usageCount = 0;

  @Column(name = "status", nullable = false)
  private Short status = STATUS_ACTIVE;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime createdAt;

  /** Token expiry — must be ≤ created_at + 7 days (enforced by ShareTokenService). */
  @Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime expiresAt;

  // ── Getters / Setters ───────────────────────────────────────────────────
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getJti() { return jti; }
  public void setJti(String jti) { this.jti = jti; }

  public Long getSharerStudentId() { return sharerStudentId; }
  public void setSharerStudentId(Long sharerStudentId) { this.sharerStudentId = sharerStudentId; }

  public String getShareType() { return shareType; }
  public void setShareType(String shareType) { this.shareType = shareType; }

  public String getRelationId() { return relationId; }
  public void setRelationId(String relationId) { this.relationId = relationId; }

  public Boolean getAllowClaim() { return allowClaim; }
  public void setAllowClaim(Boolean allowClaim) { this.allowClaim = allowClaim; }

  public Integer getUsageLimit() { return usageLimit; }
  public void setUsageLimit(Integer usageLimit) { this.usageLimit = usageLimit; }

  public Integer getUsageCount() { return usageCount; }
  public void setUsageCount(Integer usageCount) { this.usageCount = usageCount; }

  public Short getStatus() { return status; }
  public void setStatus(Short status) { this.status = status; }

  public OffsetDateTime getCreatedAt() { return createdAt; }

  public OffsetDateTime getExpiresAt() { return expiresAt; }
  public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }
}
