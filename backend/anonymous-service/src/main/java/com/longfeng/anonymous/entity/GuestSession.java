package com.longfeng.anonymous.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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
 *
 * <p><strong>Auditing strategy (WT6 fix · 2026-05-04)</strong>:
 * Spring Data JPA Auditing's {@code @CreatedDate} only supports
 * {@code LocalDateTime / Instant / Date / Long} — NOT {@link OffsetDateTime}. To keep the C9
 * TIMESTAMPTZ-only mandate, this entity drops {@code @CreatedDate / @LastModifiedDate} and
 * relies on the DB defaults ({@code created_at DEFAULT now()} / {@code updated_at DEFAULT now()})
 * by marking those columns {@code insertable=false, updatable=false} so Hibernate never tries to
 * write them. The {@code expires_at} column has no DB default, so the service layer MUST set it
 * explicitly before {@code save()}.
 *
 * <p><strong>Legacy column compat (WT6 fix · 2026-05-04)</strong>:
 * The DB carries both {@code device_fp_hash CHAR(64) NOT NULL} (V1.0.030 legacy schema) AND
 * {@code device_fp VARCHAR(128)} (V1.0.070 anonymous-service schema). The service layer must set
 * BOTH on insert: {@code deviceFp} = raw 5-source composite hash, {@code deviceFpHash} =
 * SHA-256(deviceFp) hex (64 chars · satisfies {@code CHECK char_length(device_fp_hash)=64}).
 */
@Entity
@Table(name = "guest_session", schema = "anon")
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

  /**
   * V1.0.070 schema · 5-source composite fingerprint (Canvas+WebGL+AudioContext+UA+Accept-Lang).
   * Nullable in DB (added by WT6 ALTER) — service still sets it for completeness.
   */
  @Column(name = "device_fp", length = 128)
  private String deviceFp;

  /**
   * V1.0.030 legacy schema · CHAR(64) NOT NULL · CHECK char_length=64.
   * Service must populate this (typically SHA-256(deviceFp) hex). Without it, INSERT fails.
   */
  @Column(name = "device_fp_hash", length = 64, nullable = false)
  private String deviceFpHash;

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

  /**
   * DB default {@code now()} owns this column · entity NEVER writes it (insertable=false).
   * Read-back populates the field after save() for the service layer to log / use.
   */
  @Column(name = "created_at", insertable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime createdAt;

  /**
   * DB default {@code now()} owns this column · entity NEVER writes it (insertable=false).
   * State-transition CAS UPDATEs touch this via {@code SET updated_at = now()} when needed.
   */
  @Column(name = "updated_at", insertable = false, updatable = false, columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime updatedAt;

  /** Service MUST set this before save() · DB has no default · NOT NULL. */
  @Column(name = "expires_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime expiresAt;

  @Column(name = "claimed_at", columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime claimedAt;

  // ── Getters / Setters ───────────────────────────────────────────────────
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getDeviceFp() { return deviceFp; }
  public void setDeviceFp(String deviceFp) { this.deviceFp = deviceFp; }

  public String getDeviceFpHash() { return deviceFpHash; }
  public void setDeviceFpHash(String deviceFpHash) { this.deviceFpHash = deviceFpHash; }

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

  public OffsetDateTime getUpdatedAt() { return updatedAt; }

  public OffsetDateTime getExpiresAt() { return expiresAt; }
  public void setExpiresAt(OffsetDateTime expiresAt) { this.expiresAt = expiresAt; }

  public OffsetDateTime getClaimedAt() { return claimedAt; }
  public void setClaimedAt(OffsetDateTime claimedAt) { this.claimedAt = claimedAt; }
}
