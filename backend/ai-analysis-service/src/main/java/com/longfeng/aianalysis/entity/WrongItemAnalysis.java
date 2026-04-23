package com.longfeng.aianalysis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** wrong_item_analysis (V1.0.012 · S1 DDL) — status 0=success 1=fallback 9=pending (Q1-R1). */
@Entity
@Table(name = "wrong_item_analysis")
@EntityListeners(AuditingEntityListener.class)
public class WrongItemAnalysis implements Serializable {

  public static final short STATUS_SUCCESS = 0;
  public static final short STATUS_FALLBACK = 1;
  public static final short STATUS_PENDING = 9;

  @Id
  @Column(name = "id")
  private Long id;

  @Column(name = "wrong_item_id", nullable = false)
  private Long wrongItemId;

  @Column(name = "version", nullable = false)
  private Integer version;

  @Column(name = "model_provider", length = 32, nullable = false)
  private String modelProvider;

  @Column(name = "model_name", length = 64, nullable = false)
  private String modelName;

  @Column(name = "input_tokens")
  private Integer inputTokens;

  @Column(name = "output_tokens")
  private Integer outputTokens;

  @Column(name = "cost_cents")
  private Integer costCents;

  @Column(name = "stem_text")
  private String stemText;

  @Column(name = "student_answer")
  private String studentAnswer;

  @Column(name = "correct_answer")
  private String correctAnswer;

  @Column(name = "error_type", length = 32)
  private String errorType;

  @Column(name = "error_reason")
  private String errorReason;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "solution_steps", columnDefinition = "jsonb")
  private String solutionSteps;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "knowledge_points", columnDefinition = "jsonb")
  private String knowledgePoints;

  @Column(name = "difficulty")
  private Short difficulty;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "raw_json", columnDefinition = "jsonb", nullable = false)
  private String rawJson;

  @Column(name = "status", nullable = false)
  private Short status;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }
  public Long getWrongItemId() { return wrongItemId; }
  public void setWrongItemId(Long wrongItemId) { this.wrongItemId = wrongItemId; }
  public Integer getVersion() { return version; }
  public void setVersion(Integer version) { this.version = version; }
  public String getModelProvider() { return modelProvider; }
  public void setModelProvider(String p) { this.modelProvider = p; }
  public String getModelName() { return modelName; }
  public void setModelName(String n) { this.modelName = n; }
  public Integer getInputTokens() { return inputTokens; }
  public void setInputTokens(Integer t) { this.inputTokens = t; }
  public Integer getOutputTokens() { return outputTokens; }
  public void setOutputTokens(Integer t) { this.outputTokens = t; }
  public Integer getCostCents() { return costCents; }
  public void setCostCents(Integer c) { this.costCents = c; }
  public String getStemText() { return stemText; }
  public void setStemText(String s) { this.stemText = s; }
  public String getStudentAnswer() { return studentAnswer; }
  public void setStudentAnswer(String s) { this.studentAnswer = s; }
  public String getCorrectAnswer() { return correctAnswer; }
  public void setCorrectAnswer(String s) { this.correctAnswer = s; }
  public String getErrorType() { return errorType; }
  public void setErrorType(String s) { this.errorType = s; }
  public String getErrorReason() { return errorReason; }
  public void setErrorReason(String s) { this.errorReason = s; }
  public String getSolutionSteps() { return solutionSteps; }
  public void setSolutionSteps(String s) { this.solutionSteps = s; }
  public String getKnowledgePoints() { return knowledgePoints; }
  public void setKnowledgePoints(String s) { this.knowledgePoints = s; }
  public Short getDifficulty() { return difficulty; }
  public void setDifficulty(Short d) { this.difficulty = d; }
  public String getRawJson() { return rawJson; }
  public void setRawJson(String r) { this.rawJson = r; }
  public Short getStatus() { return status; }
  public void setStatus(Short s) { this.status = s; }
  public Instant getFinishedAt() { return finishedAt; }
  public void setFinishedAt(Instant f) { this.finishedAt = f; }
  public Instant getCreatedAt() { return createdAt; }
}
