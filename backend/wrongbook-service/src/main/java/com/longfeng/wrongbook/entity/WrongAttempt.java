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

/** wrong_attempt (V1.0.022) — append-only aggregate root (Q4-R1 drift resolution). */
@Entity
@Table(name = "wrong_attempt")
@EntityListeners(AuditingEntityListener.class)
public class WrongAttempt implements Serializable {

  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "wrong_item_id", nullable = false)
  private Long wrongItemId;

  @Column(name = "student_id", nullable = false)
  private Long studentId;

  @Column(name = "answer_text")
  private String answerText;

  @Column(name = "is_correct", nullable = false)
  private Boolean isCorrect;

  @Column(name = "duration_sec")
  private Short durationSec;

  @Column(name = "client_source", length = 16)
  private String clientSource;

  @Column(name = "submitted_at", nullable = false)
  private Instant submittedAt;

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

  public Long getStudentId() {
    return studentId;
  }

  public void setStudentId(Long studentId) {
    this.studentId = studentId;
  }

  public String getAnswerText() {
    return answerText;
  }

  public void setAnswerText(String answerText) {
    this.answerText = answerText;
  }

  public Boolean getCorrect() {
    return isCorrect;
  }

  public void setCorrect(Boolean correct) {
    this.isCorrect = correct;
  }

  public Short getDurationSec() {
    return durationSec;
  }

  public void setDurationSec(Short durationSec) {
    this.durationSec = durationSec;
  }

  public String getClientSource() {
    return clientSource;
  }

  public void setClientSource(String clientSource) {
    this.clientSource = clientSource;
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public void setSubmittedAt(Instant submittedAt) {
    this.submittedAt = submittedAt;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
