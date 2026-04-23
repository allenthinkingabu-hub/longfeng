package com.longfeng.wrongbook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * WrongItem aggregate root — maps wrong_item (V1.0.010) + version (V1.0.020) + difficulty
 * (V1.0.021). Soft delete via deleted_at TIMESTAMPTZ (S1 A5 / D4 drift). Status semantics see
 * {@link com.longfeng.wrongbook.domain.WrongItemStatus}. Embedding column is NOT mapped here —
 * S4 writes it natively after consuming wrongbook.item.changed.
 */
@Entity
@Table(name = "wrong_item")
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(sql = "UPDATE wrong_item SET deleted_at = now() WHERE id = ? AND version = ?")
@SQLRestriction("deleted_at IS NULL")
public class WrongItem implements Serializable {

  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "student_id", nullable = false)
  private Long studentId;

  @Column(name = "subject", length = 16, nullable = false)
  private String subject;

  @Column(name = "grade_code", length = 16)
  private String gradeCode;

  @Column(name = "source_type", nullable = false)
  private Short sourceType;

  @Column(name = "origin_image_key", length = 512)
  private String originImageKey;

  @Column(name = "processed_image_key", length = 512)
  private String processedImageKey;

  @Column(name = "ocr_text")
  private String ocrText;

  @Column(name = "stem_text")
  private String stemText;

  @Column(name = "status", nullable = false)
  private Short status;

  @Column(name = "mastery", nullable = false)
  private Short mastery;

  @Column(name = "difficulty")
  private Short difficulty;

  @Column(name = "mastered_at")
  private Instant masteredAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getStudentId() {
    return studentId;
  }

  public void setStudentId(Long studentId) {
    this.studentId = studentId;
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject;
  }

  public String getGradeCode() {
    return gradeCode;
  }

  public void setGradeCode(String gradeCode) {
    this.gradeCode = gradeCode;
  }

  public Short getSourceType() {
    return sourceType;
  }

  public void setSourceType(Short sourceType) {
    this.sourceType = sourceType;
  }

  public String getOriginImageKey() {
    return originImageKey;
  }

  public void setOriginImageKey(String originImageKey) {
    this.originImageKey = originImageKey;
  }

  public String getProcessedImageKey() {
    return processedImageKey;
  }

  public void setProcessedImageKey(String processedImageKey) {
    this.processedImageKey = processedImageKey;
  }

  public String getOcrText() {
    return ocrText;
  }

  public void setOcrText(String ocrText) {
    this.ocrText = ocrText;
  }

  public String getStemText() {
    return stemText;
  }

  public void setStemText(String stemText) {
    this.stemText = stemText;
  }

  public Short getStatus() {
    return status;
  }

  public void setStatus(Short status) {
    this.status = status;
  }

  public Short getMastery() {
    return mastery;
  }

  public void setMastery(Short mastery) {
    this.mastery = mastery;
  }

  public Short getDifficulty() {
    return difficulty;
  }

  public void setDifficulty(Short difficulty) {
    this.difficulty = difficulty;
  }

  public Instant getMasteredAt() {
    return masteredAt;
  }

  public void setMasteredAt(Instant masteredAt) {
    this.masteredAt = masteredAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public Instant getDeletedAt() {
    return deletedAt;
  }

  public Long getVersion() {
    return version;
  }
}
