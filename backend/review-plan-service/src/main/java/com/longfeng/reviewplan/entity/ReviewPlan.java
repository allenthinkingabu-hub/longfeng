package com.longfeng.reviewplan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * ReviewPlan aggregate root · S1 V1.0.016 + V1.0.055 schema · Q-B/F 决策 "7 行 node_index 0..6".
 *
 * <p>每条 wrong_item 对应 7 行（node_index 0..6 · 偏移 [2h, 1d, 2d, 4d, 7d, 14d, 30d]）· 每行独立持有
 * ease_factor/interval_index · complete 只 UPDATE 当前节点行（Q-F · 不级联后续）。
 */
@Entity
@Table(name = "review_plan")
@EntityListeners(AuditingEntityListener.class)
@SQLDelete(
    sql = "UPDATE review_plan SET deleted_at = now() WHERE id = ? AND dispatch_version = ?")
@SQLRestriction("deleted_at IS NULL")
public class ReviewPlan implements Serializable {

  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "wrong_item_id", nullable = false)
  private Long wrongItemId;

  @Column(name = "student_id", nullable = false)
  private Long studentId;

  @Column(name = "node_index", nullable = false)
  private Short nodeIndex;

  @Column(name = "strategy_code", length = 32, nullable = false)
  private String strategyCode = "EBBINGHAUS_SM2";

  @Column(name = "start_at", nullable = false)
  private Instant startAt;

  @Column(name = "current_level", nullable = false)
  private Short currentLevel;

  @Column(name = "interval_index", nullable = false)
  private Short intervalIndex;

  @Column(name = "ease_factor", nullable = false, precision = 5, scale = 3)
  private BigDecimal easeFactor;

  @Column(name = "total_review", nullable = false)
  private Integer totalReview = 0;

  @Column(name = "total_forget", nullable = false)
  private Integer totalForget = 0;

  @Column(name = "mastery_score", nullable = false, precision = 5, scale = 2)
  private BigDecimal masteryScore = BigDecimal.ZERO;

  @Column(name = "next_due_at")
  private Instant nextDueAt;

  @Column(name = "status", nullable = false)
  private Short status = 0;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "consecutive_good_count", nullable = false)
  private Short consecutiveGoodCount = 0;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @LastModifiedDate
  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "deleted_at")
  private Instant deletedAt;

  @Version
  @Column(name = "dispatch_version", nullable = false)
  private Long dispatchVersion = 0L;

  // ----- status 语义 -----
  public static final short STATUS_ACTIVE = 0;
  public static final short STATUS_MASTERED = 1;
  public static final short STATUS_CANCELLED = 9;

  public boolean isMastered() {
    return status != null && status == STATUS_MASTERED;
  }

  // ----- getters / setters -----
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getWrongItemId() { return wrongItemId; }
  public void setWrongItemId(Long wrongItemId) { this.wrongItemId = wrongItemId; }
  public Long getStudentId() { return studentId; }
  public void setStudentId(Long studentId) { this.studentId = studentId; }
  public Short getNodeIndex() { return nodeIndex; }
  public void setNodeIndex(Short nodeIndex) { this.nodeIndex = nodeIndex; }
  public String getStrategyCode() { return strategyCode; }
  public void setStrategyCode(String strategyCode) { this.strategyCode = strategyCode; }
  public Instant getStartAt() { return startAt; }
  public void setStartAt(Instant startAt) { this.startAt = startAt; }
  public Short getCurrentLevel() { return currentLevel; }
  public void setCurrentLevel(Short currentLevel) { this.currentLevel = currentLevel; }
  public Short getIntervalIndex() { return intervalIndex; }
  public void setIntervalIndex(Short intervalIndex) { this.intervalIndex = intervalIndex; }
  public BigDecimal getEaseFactor() { return easeFactor; }
  public void setEaseFactor(BigDecimal easeFactor) { this.easeFactor = easeFactor; }
  public Integer getTotalReview() { return totalReview; }
  public void setTotalReview(Integer totalReview) { this.totalReview = totalReview; }
  public Integer getTotalForget() { return totalForget; }
  public void setTotalForget(Integer totalForget) { this.totalForget = totalForget; }
  public BigDecimal getMasteryScore() { return masteryScore; }
  public void setMasteryScore(BigDecimal masteryScore) { this.masteryScore = masteryScore; }
  public Instant getNextDueAt() { return nextDueAt; }
  public void setNextDueAt(Instant nextDueAt) { this.nextDueAt = nextDueAt; }
  public Short getStatus() { return status; }
  public void setStatus(Short status) { this.status = status; }
  public Instant getCompletedAt() { return completedAt; }
  public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }
  public Short getConsecutiveGoodCount() { return consecutiveGoodCount; }
  public void setConsecutiveGoodCount(Short consecutiveGoodCount) { this.consecutiveGoodCount = consecutiveGoodCount; }
  public Instant getCreatedAt() { return createdAt; }
  public Instant getUpdatedAt() { return updatedAt; }
  public Instant getDeletedAt() { return deletedAt; }
  public void setDeletedAt(Instant deletedAt) { this.deletedAt = deletedAt; }
  public Long getDispatchVersion() { return dispatchVersion; }
  public void setDispatchVersion(Long dispatchVersion) { this.dispatchVersion = dispatchVersion; }
}
