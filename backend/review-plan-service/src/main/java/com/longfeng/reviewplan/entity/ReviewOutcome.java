package com.longfeng.reviewplan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** 复习结果审计 · V1.0.053 · 每次 POST complete 产 1 行 · SC-08.AC-1。 */
@Entity
@Table(name = "review_outcome")
@EntityListeners(AuditingEntityListener.class)
public class ReviewOutcome implements Serializable {

  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "plan_id", nullable = false)
  private Long planId;

  @Column(name = "wrong_item_id", nullable = false)
  private Long wrongItemId;

  @Column(name = "user_id", nullable = false)
  private Long userId;

  @Column(name = "quality", nullable = false)
  private Short quality;

  @Column(name = "ease_factor_before", nullable = false, precision = 4, scale = 2)
  private BigDecimal easeFactorBefore;

  @Column(name = "ease_factor_after", nullable = false, precision = 4, scale = 2)
  private BigDecimal easeFactorAfter;

  @Column(name = "interval_days_before", nullable = false)
  private Integer intervalDaysBefore;

  @Column(name = "interval_days_after", nullable = false)
  private Integer intervalDaysAfter;

  @Column(name = "completed_at", nullable = false)
  private Instant completedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getPlanId() { return planId; }
  public void setPlanId(Long planId) { this.planId = planId; }
  public Long getWrongItemId() { return wrongItemId; }
  public void setWrongItemId(Long wrongItemId) { this.wrongItemId = wrongItemId; }
  public Long getUserId() { return userId; }
  public void setUserId(Long userId) { this.userId = userId; }
  public Short getQuality() { return quality; }
  public void setQuality(Short quality) { this.quality = quality; }
  public BigDecimal getEaseFactorBefore() { return easeFactorBefore; }
  public void setEaseFactorBefore(BigDecimal easeFactorBefore) { this.easeFactorBefore = easeFactorBefore; }
  public BigDecimal getEaseFactorAfter() { return easeFactorAfter; }
  public void setEaseFactorAfter(BigDecimal easeFactorAfter) { this.easeFactorAfter = easeFactorAfter; }
  public Integer getIntervalDaysBefore() { return intervalDaysBefore; }
  public void setIntervalDaysBefore(Integer intervalDaysBefore) { this.intervalDaysBefore = intervalDaysBefore; }
  public Integer getIntervalDaysAfter() { return intervalDaysAfter; }
  public void setIntervalDaysAfter(Integer intervalDaysAfter) { this.intervalDaysAfter = intervalDaysAfter; }
  public Instant getCompletedAt() { return completedAt; }
  public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
}
