package com.longfeng.fileservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** file_asset · V1.0.056 · S6 图片/文件元数据聚合根. */
@Entity
@Table(name = "file_asset")
@EntityListeners(AuditingEntityListener.class)
@SQLRestriction("deleted_at IS NULL")
public class FileAsset implements Serializable {

  public static final String STATUS_PENDING = "PENDING";
  public static final String STATUS_READY = "READY";
  public static final String STATUS_QUARANTINED = "QUARANTINED";
  public static final String STATUS_ARCHIVED = "ARCHIVED";

  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "owner_id", nullable = false)
  private Long ownerId;

  @Column(name = "bucket", nullable = false, length = 64)
  private String bucket;

  @Column(name = "object_key", nullable = false, length = 512)
  private String objectKey;

  @Column(name = "mime", nullable = false, length = 64)
  private String mime;

  @Column(name = "size_bytes", nullable = false)
  private Long sizeBytes;

  @Column(name = "checksum_sha256", length = 64)
  private String checksumSha256;

  @Column(name = "status", nullable = false, length = 16)
  private String status = STATUS_PENDING;

  @Column(name = "uploaded_at")
  private Instant uploadedAt;

  @Column(name = "variant_thumb_key", length = 512)
  private String variantThumbKey;

  @Column(name = "variant_medium_key", length = 512)
  private String variantMediumKey;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getOwnerId() { return ownerId; }
  public void setOwnerId(Long ownerId) { this.ownerId = ownerId; }
  public String getBucket() { return bucket; }
  public void setBucket(String bucket) { this.bucket = bucket; }
  public String getObjectKey() { return objectKey; }
  public void setObjectKey(String objectKey) { this.objectKey = objectKey; }
  public String getMime() { return mime; }
  public void setMime(String mime) { this.mime = mime; }
  public Long getSizeBytes() { return sizeBytes; }
  public void setSizeBytes(Long sizeBytes) { this.sizeBytes = sizeBytes; }
  public String getChecksumSha256() { return checksumSha256; }
  public void setChecksumSha256(String checksumSha256) { this.checksumSha256 = checksumSha256; }
  public String getStatus() { return status; }
  public void setStatus(String status) { this.status = status; }
  public Instant getUploadedAt() { return uploadedAt; }
  public void setUploadedAt(Instant uploadedAt) { this.uploadedAt = uploadedAt; }
  public String getVariantThumbKey() { return variantThumbKey; }
  public void setVariantThumbKey(String variantThumbKey) { this.variantThumbKey = variantThumbKey; }
  public String getVariantMediumKey() { return variantMediumKey; }
  public void setVariantMediumKey(String variantMediumKey) { this.variantMediumKey = variantMediumKey; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public Instant getDeletedAt() { return deletedAt; }
  public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
}
