package com.longfeng.wrongbook.entity;

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

/** audit_log (V1.0.050) — append-only cross-cutting audit. Same-transaction write per Q7-R2. */
@Entity
@Table(name = "audit_log")
@EntityListeners(AuditingEntityListener.class)
public class AuditLog implements Serializable {

  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "actor_type", length = 16, nullable = false)
  private String actorType;

  @Column(name = "actor_id")
  private Long actorId;

  @Column(name = "action", length = 64, nullable = false)
  private String action;

  @Column(name = "target_type", length = 32)
  private String targetType;

  @Column(name = "target_id")
  private Long targetId;

  @Column(name = "ip_hash", length = 64)
  private String ipHash;

  @Column(name = "ua_sha", length = 64)
  private String uaSha;

  @Column(name = "request_id", length = 64)
  private String requestId;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb")
  private String payload;

  @CreatedDate
  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getActorType() {
    return actorType;
  }

  public void setActorType(String actorType) {
    this.actorType = actorType;
  }

  public Long getActorId() {
    return actorId;
  }

  public void setActorId(Long actorId) {
    this.actorId = actorId;
  }

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getTargetType() {
    return targetType;
  }

  public void setTargetType(String targetType) {
    this.targetType = targetType;
  }

  public Long getTargetId() {
    return targetId;
  }

  public void setTargetId(Long targetId) {
    this.targetId = targetId;
  }

  public String getIpHash() {
    return ipHash;
  }

  public void setIpHash(String ipHash) {
    this.ipHash = ipHash;
  }

  public String getUaSha() {
    return uaSha;
  }

  public void setUaSha(String uaSha) {
    this.uaSha = uaSha;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public String getPayload() {
    return payload;
  }

  public void setPayload(String payload) {
    this.payload = payload;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
