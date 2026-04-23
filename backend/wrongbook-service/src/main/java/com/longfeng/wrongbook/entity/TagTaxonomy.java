package com.longfeng.wrongbook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** tag_taxonomy (V1.0.014) — read-only static data. */
@Entity
@Table(name = "tag_taxonomy")
@EntityListeners(AuditingEntityListener.class)
public class TagTaxonomy implements Serializable {

  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "code", length = 64, nullable = false, unique = true)
  private String code;

  @Column(name = "display_name", length = 128, nullable = false)
  private String displayName;

  @Column(name = "parent_code", length = 64)
  private String parentCode;

  @Column(name = "subject", length = 16)
  private String subject;

  @Column(name = "bloom_level")
  private Short bloomLevel;

  @Column(name = "status", nullable = false)
  private Short status;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getCode() {
    return code;
  }

  public void setCode(String code) {
    this.code = code;
  }

  public String getDisplayName() {
    return displayName;
  }

  public void setDisplayName(String displayName) {
    this.displayName = displayName;
  }

  public String getParentCode() {
    return parentCode;
  }

  public void setParentCode(String parentCode) {
    this.parentCode = parentCode;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public Short getBloomLevel() {
    return bloomLevel;
  }

  public void setBloomLevel(Short bloomLevel) {
    this.bloomLevel = bloomLevel;
  }

  public Short getStatus() {
    return status;
  }

  public void setStatus(Short status) {
    this.status = status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }
}
