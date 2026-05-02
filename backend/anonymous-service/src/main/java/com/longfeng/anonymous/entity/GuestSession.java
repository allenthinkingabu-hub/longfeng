package com.longfeng.anonymous.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * GuestSession aggregate root · TDD §4.10 · V1.0.070.
 *
 * <p>24-h TTL anonymous session tracking a single guest analysis attempt.
 * Status machine (C2 / D-State): all transitions via CAS UPDATE on {@code version}.
 *
 * <p>C3 RED LINE: this entity maps ONLY to {@code anon.guest_session} — never to any
 * {@code wb_*} table.
 *
 * <p>C9: all timestamps use {@link OffsetDateTime} (TIMESTAMPTZ in PG · UTC stored).
 */
@Entity
@Table(name = "guest_session", schema = "anon")
@EntityListeners(AuditingEntityListener.class)
public class GuestSession implements Serializable {

  // ── Status constants ────────────────────────────────────────────────────
  public static final short STATUS_CREATED      = 0;
  public static final short STATUS_ANALYZING    = 1;
  public static final short STATUS_RESULT_READY = 2;
  public static final short STATUS_FAILED       = 3;
  public static final short STATUS_CLAIMED      = 4;
  public static final short STATUS_EXPIRED      = 9;

  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "device_fp", length = 128, nullable = false)
  private String deviceFp;

  @Column(name = "ip_hash", length = 64)
  private String ipHash;

  @Column(name = "ua", length = 256)
  private String ua;

  @Column(name = "entry_source", length = 32)
  private String entrySource;

  @Column(name = "experiment_bucket", length = 32)
  private String experimentBucket;

  @Column(name = "image_tmp_url", length = 512)
  private String imageTmpUrl;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "analysis_result_json", columnDefinition = "jsonb")
  private String analysisResultJson;

  @Column(name = "consent_at", columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime consentAt;

  @Column(name = "consent_type")
  private Short consentType;

  @Column(name = "status", nullable = false)
  private Short status = STATUS_CREATED;

  /** CAS version field — all state transitions must read + CAS on this. D-State / C2. */
  @Version
  @Column(name = "version", nullable = false)
  private Integer version = 0;

  @Column(name = "claimed_by_student_id")
  private Long claimedByStudentId;

  @Column(name = "claimed_question_id")
  private Long claimedQuestionId;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime createdAt;

  @Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime expiresAt;

  @Column(name = "claimed_at", columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime claimedAt;

  // ── Getters / Setters ───────────────────────────────────────────────────
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getDeviceFp() { return deviceFp; }
  public void setDeviceFp(String deviceFp) { this.deviceFp = deviceFp; }

  public String getIpHash() { return ipHash; }
  public void setIpHash(String ipHash) { this.ipHash = ipHash; }

  public String getUa() { return ua; }
  public void setUa(String ua) { this.ua = ua; }

  public String getEntrySource() { return entrySource; }
  public void setEntrySource(String entrySource) { this.entrySource = entrySource; }

  public String getExperimentBucket() { return experimentBucket; }
  public void setExperimentBucket(String experimentBucket) { this.experimentBucket = experimentBucket; }

  public String getImageTmpUrl() { return imageTmpUrl; }
  public void setImageTmpUrl(String imageTmpUrl) { this.imageTmpUrl = imageTmpUrl; }

  public String getAnalysisResultJson() { return analysisResultJson; }
  public void setAnalysisResultJson(String analysisResultJson) { this.analysisResultJson = analysisResultJson; }

  public OffsetDateTime getConsentAt() { return consentAt; }
  public void setConsentAt(OffsetDateTime consentAt) { this.consentAt = consentAt; }

  public Short getConsentType() { return consentType; }
  public void setConsentType(Short consentType) { this.consentType = consentType; }

  public Short getStatus() { return status; }
  public void setStatus(Short status) { this.status = status; }

  public Integer getVersion() { return version; }
  public void setVersion(Integer version) { this.version = version; }

  public Long getClaimedByStudentId() { return claimedByStudentId; }
  public void setClaimedByStudentId(Long claimedByStudentId) { this.claimedByStudentId = claimedByStudentId; }

  public Long getClaimedQuestionId() { return claimedQuestionId; }
  public void setClaimedQuestionId(Long claimedQuestionId) { this.claimedQuestionId = claimedQuestionId; }

  public OffsetDateTime getCreatedAt() { return createdAt; }

  public OffsetDateTime getExpiresAt() { return expiresAt; }
  public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }

  public OffsetDateTime getClaimedAt() { return claimedAt; }
  public void setClaimedAt(OffsetDateTime claimedAt) { this.claimedAt = claimedAt; }
}
