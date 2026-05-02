package com.longfeng.anonymous.device;

import com.longfeng.anonymous.entity.AccountDevice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * AccountDevice Spring Data JPA repository · TDD §4.13 V1.0.076.
 *
 * <p>Supports soft device-fingerprint binding: a student may have multiple devices, and a device
 * fingerprint may be associated with multiple students (drift scenario — handled by
 * {@code FingerprintMatchPolicy}).
 *
 * <p>C3 RED LINE: queries only {@code anon.account_device} — never {@code wb_*}.
 */
public interface AccountDeviceRepository extends JpaRepository<AccountDevice, Long> {

  /** Look up all devices for a given student (used in D-Guest-Device claim validation). */
  List<AccountDevice> findByStudentId(Long studentId);

  /** Reverse lookup: all students that have used this fingerprint (drift / multi-account). */
  @Query(
      value = "SELECT * FROM anon.account_device WHERE device_fp = :deviceFp",
      nativeQuery = true)
  List<AccountDevice> findByDeviceFp(@Param("deviceFp") String deviceFp);

  /** Exact match: one student ↔ one device fingerprint. */
  Optional<AccountDevice> findByStudentIdAndDeviceFp(Long studentId, String deviceFp);

  /**
   * Upsert: update last_seen_at + login_count if already bound; used to avoid a SELECT-then-INSERT
   * pattern. Returns updated rows (1 = existing updated, 0 = needs INSERT).
   */
  @Modifying
  @Query(
      value =
          "UPDATE anon.account_device "
              + "SET last_seen_at = now(), login_count = login_count + 1 "
              + "WHERE student_id = :studentId AND device_fp = :deviceFp",
      nativeQuery = true)
  int updateLastSeen(
      @Param("studentId") Long studentId, @Param("deviceFp") String deviceFp);
}
