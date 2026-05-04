package com.longfeng.anonymous.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.OffsetDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * AnalyticsEvent · simple fire-and-forget event log · plan §S7 BUG-LF-09 fix.
 *
 * <p>Captures FE landing/guest funnel events (anon_landing_view, anon_guest_capture_shoot,
 * anon_guest_quota_exhausted, etc.) for product analytics. Async write only — never on the
 * request thread.
 *
 * <p>C9: created_at uses TIMESTAMPTZ. Schema: {@code anon}.
 */
@Entity
@Table(name = "analytics_event", schema = "anon")
public class AnalyticsEvent implements Serializable {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id", nullable = false)
  private Long id;

  @Column(name = "event_name", length = 64, nullable = false)
  private String eventName;

  @Column(name = "device_fp", length = 128)
  private String deviceFp;

  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "payload", columnDefinition = "jsonb")
  private String payloadJson;

  @Column(name = "created_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
  private OffsetDateTime createdAt;

  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getEventName() { return eventName; }
  public void setEventName(String eventName) { this.eventName = eventName; }

  public String getDeviceFp() { return deviceFp; }
  public void setDeviceFp(String deviceFp) { this.deviceFp = deviceFp; }

  public String getPayloadJson() { return payloadJson; }
  public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }

  public OffsetDateTime getCreatedAt() { return createdAt; }
  public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
