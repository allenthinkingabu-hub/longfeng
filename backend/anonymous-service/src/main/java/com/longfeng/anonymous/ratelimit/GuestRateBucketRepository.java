package com.longfeng.anonymous.ratelimit;

import com.longfeng.anonymous.entity.GuestRateBucket;
import com.longfeng.anonymous.entity.GuestRateBucket.RateBucketId;
import java.time.LocalDate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * GuestRateBucket Spring Data JPA repository · TDD §4.10 V1.0.071.
 *
 * <p>Used only by {@code RateLimitFallbackToDb} when Redis is unavailable.
 * C3 RED LINE: queries only {@code anon.guest_rate_bucket} — never {@code wb_*}.
 */
public interface GuestRateBucketRepository extends JpaRepository<GuestRateBucket, RateBucketId> {

  /**
   * Increment the count for the given device+ip+date combination using an upsert.
   * Returns the new count after increment.
   */
  @Modifying
  @Query(
      value =
          "INSERT INTO anon.guest_rate_bucket (device_fp, ip_hash, bucket_date, count) "
              + "VALUES (:deviceFp, :ipHash, :bucketDate, 1) "
              + "ON CONFLICT (device_fp, ip_hash, bucket_date) "
              + "DO UPDATE SET count = anon.guest_rate_bucket.count + 1",
      nativeQuery = true)
  void upsertIncrement(
      @Param("deviceFp") String deviceFp,
      @Param("ipHash") String ipHash,
      @Param("bucketDate") LocalDate bucketDate);

  /**
   * Read current count (0 if no row yet). Returns null if not found — caller treats null as 0.
   */
  @Query(
      value =
          "SELECT count FROM anon.guest_rate_bucket "
              + "WHERE device_fp = :deviceFp AND ip_hash = :ipHash AND bucket_date = :bucketDate",
      nativeQuery = true)
  Integer findCount(
      @Param("deviceFp") String deviceFp,
      @Param("ipHash") String ipHash,
      @Param("bucketDate") LocalDate bucketDate);

  /**
   * Clean up buckets older than {@code olderThan} days (run weekly to prevent table bloat).
   */
  @Modifying
  @Query(
      value =
          "DELETE FROM anon.guest_rate_bucket WHERE bucket_date < :cutoff",
      nativeQuery = true)
  int deleteOlderThan(@Param("cutoff") LocalDate cutoff);
}
