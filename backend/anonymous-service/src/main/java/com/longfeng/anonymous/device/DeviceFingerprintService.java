package com.longfeng.anonymous.device;

import com.longfeng.anonymous.entity.AccountDevice;
import com.longfeng.anonymous.support.SnowflakeIdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * DeviceFingerprintService · TDD §3.1 device/ · plan §5.S2 BE-05.
 *
 * <p>Computes a stable device fingerprint from the five browser-side signal sources as defined in
 * TDD §0.9 D-Guest-Device:
 * <ol>
 *   <li>Canvas fingerprint hash</li>
 *   <li>WebGL renderer hash</li>
 *   <li>AudioContext hash</li>
 *   <li>User-Agent string</li>
 *   <li>Accept-Language header</li>
 * </ol>
 *
 * <p>The server-side role is to validate / re-hash the composite that arrives from the front end
 * and to maintain the {@code anon.account_device} soft-binding table.
 *
 * <p>C3 RED LINE: never touches any {@code wb_*} table.
 */
@Service
@Transactional
public class DeviceFingerprintService {

  private static final Logger log = LoggerFactory.getLogger(DeviceFingerprintService.class);

  private final AccountDeviceRepository deviceRepo;
  private final SnowflakeIdGenerator idGen;

  public DeviceFingerprintService(AccountDeviceRepository deviceRepo, SnowflakeIdGenerator idGen) {
    this.deviceRepo = deviceRepo;
    this.idGen = idGen;
  }

  // ── Fingerprint computation ───────────────────────────────────────────────

  /**
   * Computes a SHA-256 hash over the five signal sources.
   *
   * <p>Sources may be null / empty if the browser does not support the API; the method still
   * produces a consistent hash by treating null as an empty string.
   *
   * @param canvasHash    SHA-256 hex of pixel data from canvas rendering
   * @param webGlHash     SHA-256 hex of WebGL renderer + vendor strings
   * @param audioHash     SHA-256 hex of AudioContext sample buffer
   * @param userAgent     raw User-Agent header value
   * @param acceptLanguage Accept-Language header value
   * @return 64-char hex SHA-256 digest of the concatenated signals
   */
  public String computeFingerprint(
      String canvasHash,
      String webGlHash,
      String audioHash,
      String userAgent,
      String acceptLanguage) {
    String composite =
        safe(canvasHash) + "|"
            + safe(webGlHash) + "|"
            + safe(audioHash) + "|"
            + safe(userAgent) + "|"
            + safe(acceptLanguage);
    return sha256Hex(composite);
  }

  // ── Drift detection ───────────────────────────────────────────────────────

  /**
   * Checks whether the given fingerprint matches any fingerprint previously associated with
   * {@code studentId} in {@code anon.account_device}.
   *
   * @param studentId the authenticated student
   * @param deviceFp  fingerprint to validate
   * @return true if the fingerprint is known / bound to this student; false otherwise (drift)
   */
  @Transactional(readOnly = true)
  public boolean isKnownDevice(Long studentId, String deviceFp) {
    return deviceRepo.findByStudentIdAndDeviceFp(studentId, deviceFp).isPresent();
  }

  // ── Soft-binding ──────────────────────────────────────────────────────────

  /**
   * Upserts the device binding: creates a new record on first login, or updates {@code last_seen_at}
   * and increments {@code login_count} on subsequent logins.
   *
   * @param studentId authenticated student
   * @param deviceFp  composite fingerprint hash
   * @param platform  H5 / MINIP / IOS / ANDROID
   * @param tenantId  tenant identifier
   */
  public void upsertBinding(Long studentId, String deviceFp, String platform, Long tenantId) {
    int updated = deviceRepo.updateLastSeen(studentId, deviceFp);
    if (updated == 0) {
      // First time this student uses this device — insert new binding
      AccountDevice device = new AccountDevice();
      device.setId(idGen.nextId());
      device.setStudentId(studentId);
      device.setDeviceFp(deviceFp);
      device.setPlatform(platform);
      device.setTenantId(tenantId != null ? tenantId : 0L);
      device.setLoginCount(1);
      deviceRepo.save(device);
      log.info("device-binding created studentId={} fp={} platform={}", studentId, deviceFp, platform);
    } else {
      log.debug("device-binding updated studentId={} fp={}", studentId, deviceFp);
    }
  }

  // ── Multi-account ambiguity ───────────────────────────────────────────────

  /**
   * Returns all student IDs that have ever used {@code deviceFp}.
   * If more than one student is returned, the fingerprint is shared (family device, drift, etc.)
   * and the caller (FingerprintMatchPolicy) should apply the disambiguation policy.
   *
   * @param deviceFp fingerprint to query
   * @return list of student IDs (empty if fingerprint is unknown)
   */
  @Transactional(readOnly = true)
  public java.util.List<Long> studentsForFingerprint(String deviceFp) {
    return deviceRepo.findByDeviceFp(deviceFp)
        .stream()
        .map(AccountDevice::getStudentId)
        .distinct()
        .toList();
  }

  /**
   * Looks up the single AccountDevice binding for a student + fingerprint pair.
   *
   * @param studentId student identifier
   * @param deviceFp  fingerprint
   * @return optional binding record
   */
  @Transactional(readOnly = true)
  public Optional<AccountDevice> findBinding(Long studentId, String deviceFp) {
    return deviceRepo.findByStudentIdAndDeviceFp(studentId, deviceFp);
  }

  // ── Internal helpers ──────────────────────────────────────────────────────

  private static String safe(String s) {
    return s == null ? "" : s;
  }

  static String sha256Hex(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    }
  }
}
