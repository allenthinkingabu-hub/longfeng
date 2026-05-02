package com.longfeng.anonymous.device;

import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * FingerprintMatchPolicy · TDD §3.1 device/ · plan §5.S2 BE-05.
 *
 * <p>Implements the multi-account ambiguity and device-mismatch resolution policy described in
 * TDD §0.9 D-Guest-Device:
 * <ul>
 *   <li>Single student match → pass</li>
 *   <li>Multi-student match → degrade to P00 login (cannot auto-claim)</li>
 *   <li>No match for expected student → {@code DEVICE_MISMATCH} (403)</li>
 * </ul>
 *
 * <p>C3 RED LINE: no {@code wb_*} tables referenced.
 */
@Component
public class FingerprintMatchPolicy {

  private static final Logger log = LoggerFactory.getLogger(FingerprintMatchPolicy.class);

  private final DeviceFingerprintService fpService;

  public FingerprintMatchPolicy(DeviceFingerprintService fpService) {
    this.fpService = fpService;
  }

  /**
   * Validates that {@code deviceFp} is consistent with the claiming student.
   *
   * <p>Logic:
   * <ol>
   *   <li>If the fingerprint is unknown to the system → first-time device, allow claim
   *       (new binding created later).</li>
   *   <li>If the fingerprint is known to exactly one student AND that student matches
   *       {@code studentId} → allow.</li>
   *   <li>If the fingerprint is known to multiple students (family device) → degrade to P00
   *       (throw {@link AmbiguousDeviceException}).</li>
   *   <li>If the fingerprint belongs only to a different student → {@code DEVICE_MISMATCH}.</li>
   * </ol>
   *
   * @param studentId the student attempting the claim
   * @param deviceFp  fingerprint extracted from the request
   * @throws BusinessException DEVICE_MISMATCH (403) when device is definitively bound to another student
   * @throws AmbiguousDeviceException when multiple students share the fingerprint — caller should
   *         redirect to P00 login
   */
  public void validate(Long studentId, String deviceFp) {
    List<Long> owners = fpService.studentsForFingerprint(deviceFp);

    if (owners.isEmpty()) {
      // Unknown device — first login, allow claim
      log.debug("device-fp {} unknown — first-time device for student {}", deviceFp, studentId);
      return;
    }

    if (owners.size() > 1) {
      // Multi-account ambiguity — degrade to P00
      log.warn("device-fp {} shared by {} students — ambiguous, degrade to P00", deviceFp, owners.size());
      throw new AmbiguousDeviceException(deviceFp, owners.size());
    }

    // Exactly one owner
    Long owner = owners.get(0);
    if (!owner.equals(studentId)) {
      log.warn("device-fp {} belongs to student {} but claim attempted by {} — DEVICE_MISMATCH",
          deviceFp, owner, studentId);
      throw new BusinessException(ErrCode.DEVICE_MISMATCH, "msgkey:anon.error.device_mismatch");
    }
    log.debug("device-fp {} validated for student {}", deviceFp, studentId);
  }

  /**
   * Thrown when a device fingerprint is associated with multiple student accounts.
   * The caller (e.g. GuestController) should redirect the user to P00 login.
   */
  public static class AmbiguousDeviceException extends RuntimeException {

    private final String deviceFp;
    private final int ownerCount;

    public AmbiguousDeviceException(String deviceFp, int ownerCount) {
      super("Device fingerprint " + deviceFp + " is shared by " + ownerCount + " accounts");
      this.deviceFp = deviceFp;
      this.ownerCount = ownerCount;
    }

    public String getDeviceFp() { return deviceFp; }
    public int getOwnerCount() { return ownerCount; }
  }
}
