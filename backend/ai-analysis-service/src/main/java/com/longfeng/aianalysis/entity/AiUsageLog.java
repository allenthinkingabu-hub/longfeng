package com.longfeng.aianalysis.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** ai_usage_log (V1.0.023 · S4 新增) — append-only per-call trace. */
@Entity
@Table(name = "ai_usage_log")
@EntityListeners(AuditingEntityListener.class)
public class AiUsageLog implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @Column(name = "user_id")
  private Long userId;

  @Column(name = "item_id")
  private Long itemId;

  @Column(name = "provider", length = 16, nullable = false)
  private String provider;

  @Column(name = "model", length = 64, nullable = false)
  private String model;

  @Column(name = "api_type", length = 16, nullable = false)
  private String apiType;

  @Column(name = "tokens_in")
  private Integer tokensIn;

  @Column(name = "tokens_out")
  private Integer tokensOut;

  @Column(name = "cost_cents")
  private Integer costCents;

  @Column(name = "latency_ms")
  private Integer latencyMs;

  @Column(name = "status", nullable = false)
  private Short status;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public Long getId() { return id; }
  public Long getUserId() { return userId; }
  public void setUserId(Long u) { this.userId = u; }
  public Long getItemId() { return itemId; }
  public void setItemId(Long i) { this.itemId = i; }
  public String getProvider() { return provider; }
  public void setProvider(String p) { this.provider = p; }
  public String getModel() { return model; }
  public void setModel(String m) { this.model = m; }
  public String getApiType() { return apiType; }
  public void setApiType(String t) { this.apiType = t; }
  public Integer getTokensIn() { return tokensIn; }
  public void setTokensIn(Integer t) { this.tokensIn = t; }
  public Integer getTokensOut() { return tokensOut; }
  public void setTokensOut(Integer t) { this.tokensOut = t; }
  public Integer getCostCents() { return costCents; }
  public void setCostCents(Integer c) { this.costCents = c; }
  public Integer getLatencyMs() { return latencyMs; }
  public void setLatencyMs(Integer l) { this.latencyMs = l; }
  public Short getStatus() { return status; }
  public void setStatus(Short s) { this.status = s; }
  public Instant getCreatedAt() { return createdAt; }
}
