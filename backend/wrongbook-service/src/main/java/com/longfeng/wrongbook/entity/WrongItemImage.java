package com.longfeng.wrongbook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** wrong_item_image (V1.0.013) — D8 drift: object_key + role (not ossKey/confirmedAt). */
@Entity
@Table(name = "wrong_item_image")
@EntityListeners(AuditingEntityListener.class)
public class WrongItemImage implements Serializable {

  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "wrong_item_id", nullable = false)
  private Long wrongItemId;

  @Column(name = "object_key", length = 512, nullable = false)
  private String objectKey;

  @Column(name = "role", length = 16, nullable = false)
  private String role;

  @Column(name = "width_px")
  private Integer widthPx;

  @Column(name = "height_px")
  private Integer heightPx;

  @Column(name = "byte_size")
  private Long byteSize;

  @Column(name = "content_type", length = 64)
  private String contentType;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getWrongItemId() {
    return wrongItemId;
  }

  public void setWrongItemId(Long wrongItemId) {
    this.wrongItemId = wrongItemId;
  }

  public String getObjectKey() {
    return objectKey;
  }

  public void setObjectKey(String objectKey) {
    this.objectKey = objectKey;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public Integer getWidthPx() {
    return widthPx;
  }

  public void setWidthPx(Integer widthPx) {
    this.widthPx = widthPx;
  }

  public Integer getHeightPx() {
    return heightPx;
  }

  public void setHeightPx(Integer heightPx) {
    this.heightPx = heightPx;
  }

  public Long getByteSize() {
    return byteSize;
  }

  public void setByteSize(Long byteSize) {
    this.byteSize = byteSize;
  }

  public String getContentType() {
    return contentType;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
