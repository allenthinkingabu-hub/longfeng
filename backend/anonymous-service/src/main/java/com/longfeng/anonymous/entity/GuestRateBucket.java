package com.longfeng.anonymous.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDate;

/**
 * GuestRateBucket entity · TDD §4.10 guest_rate_bucket · V1.0.071.
 *
 * <p>Fallback persistence when Redis is unavailable (RateLimitFallbackToDb).
 * PK is composite: (device_fp, ip_hash, bucket_date) — maps to {@code anon.guest_rate_bucket}.
 * C3 RED LINE: no reference to any {@code wb_*} table.
 */
@Entity
@Table(name = "guest_rate_bucket", schema = "anon")
public class GuestRateBucket implements Serializable {

  /** Composite primary key. */
  @Embeddable
  public static class RateBucketId implements Serializable {

    @Column(name = "device_fp", length = 128, nullable = false)
    private String deviceFp;

    @Column(name = "ip_hash", length = 64, nullable = false)
    private String ipHash;

    @Column(name = "bucket_date", nullable = false)
    private LocalDate bucketDate;

    public RateBucketId() {}

    public RateBucketId(String deviceFp, String ipHash, LocalDate bucketDate) {
      this.deviceFp = deviceFp;
      this.ipHash = ipHash;
      this.bucketDate = bucketDate;
    }

    public String getDeviceFp() { return deviceFp; }
    public void setDeviceFp(String deviceFp) { this.deviceFp = deviceFp; }

    public String getIpHash() { return ipHash; }
    public void setIpHash(String ipHash) { this.ipHash = ipHash; }

    public LocalDate getBucketDate() { return bucketDate; }
    public void setBucketDate(LocalDate bucketDate) { this.bucketDate = bucketDate; }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof RateBucketId that)) return false;
      return java.util.Objects.equals(deviceFp, that.deviceFp)
          && java.util.Objects.equals(ipHash, that.ipHash)
          && java.util.Objects.equals(bucketDate, that.bucketDate);
    }

    @Override
    public int hashCode() {
      return java.util.Objects.hash(deviceFp, ipHash, bucketDate);
    }
  }

  @EmbeddedId
  private RateBucketId id;

  @Column(name = "count", nullable = false)
  private Integer count = 0;

  public GuestRateBucket() {}

  public GuestRateBucket(String deviceFp, String ipHash, LocalDate bucketDate, int count) {
    this.id = new RateBucketId(deviceFp, ipHash, bucketDate);
    this.count = count;
  }

  // ── Getters / Setters ───────────────────────────────────────────────────
  public RateBucketId getId() { return id; }
  public void setId(RateBucketId id) { this.id = id; }

  public Integer getCount() { return count; }
  public void setCount(Integer count) { this.count = count; }
}
