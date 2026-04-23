package com.longfeng.wrongbook.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/** wrong_item_tag (V1.0.011) — many-to-many join · D7 drift: tag_code VARCHAR (not tag_id). */
@Entity
@Table(name = "wrong_item_tag")
@EntityListeners(AuditingEntityListener.class)
public class WrongItemTag implements Serializable {

  @Id
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "wrong_item_id", nullable = false)
  private Long wrongItemId;

  @Column(name = "tag_code", length = 64, nullable = false)
  private String tagCode;

  @Column(name = "weight", nullable = false, precision = 4, scale = 3)
  private BigDecimal weight;

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

  public String getTagCode() {
    return tagCode;
  }

  public void setTagCode(String tagCode) {
    this.tagCode = tagCode;
  }

  public BigDecimal getWeight() {
    return weight;
  }

  public void setWeight(BigDecimal weight) {
    this.weight = weight;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
