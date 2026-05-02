package com.longfeng.common.exception;

import java.util.Objects;

/**
 * Base business exception · TDD §3.1 / §13.3 (msgkey protocol) / C8 red-line.
 *
 * <p><strong>C8 enforcement</strong>: every instance <em>must</em> carry a message that begins
 * with the literal prefix {@code "msgkey:"}. Attempting to construct without this prefix throws an
 * {@link IllegalArgumentException} immediately, ensuring the constraint is enforced at build time
 * rather than at review time.
 *
 * <p>Example usage:
 * <pre>{@code
 *   throw new BusinessException(ErrCode.NODE_INVALID_TRANSITION,
 *       "msgkey:wb.error.node.invalid_transition");
 * }</pre>
 *
 * <p>Services must sub-class or throw this class; never throw
 * {@link RuntimeException} / {@link java.lang.Exception} directly for business errors.
 */
public class BusinessException extends RuntimeException {

  private static final long serialVersionUID = 1L;

  /** The required prefix enforced by C8. */
  public static final String MSGKEY_PREFIX = "msgkey:";

  private final ErrCode errCode;

  /**
   * Construct with a specific error code, using its {@link ErrCode#defaultMsgkey()} as the message.
   *
   * @param errCode non-null error code
   */
  public BusinessException(ErrCode errCode) {
    this(errCode, errCode.defaultMsgkey());
  }

  /**
   * Construct with a specific error code and an explicit msgkey message.
   *
   * @param errCode non-null error code
   * @param msgkey  message that <em>must</em> start with {@code "msgkey:"} (C8)
   * @throws IllegalArgumentException if msgkey is null or does not start with {@code "msgkey:"}
   */
  public BusinessException(ErrCode errCode, String msgkey) {
    super(requireMsgkey(msgkey));
    this.errCode = Objects.requireNonNull(errCode, "errCode must not be null");
  }

  /**
   * Construct with a specific error code, a msgkey message, and a root cause.
   *
   * @param errCode non-null error code
   * @param msgkey  message that <em>must</em> start with {@code "msgkey:"} (C8)
   * @param cause   root cause
   * @throws IllegalArgumentException if msgkey is null or does not start with {@code "msgkey:"}
   */
  public BusinessException(ErrCode errCode, String msgkey, Throwable cause) {
    super(requireMsgkey(msgkey), cause);
    this.errCode = Objects.requireNonNull(errCode, "errCode must not be null");
  }

  /**
   * Returns the associated {@link ErrCode}.
   *
   * @return never null
   */
  public ErrCode errCode() {
    return errCode;
  }

  /**
   * Convenience — HTTP status code for the response.
   *
   * @return HTTP status integer
   */
  public int httpStatus() {
    return errCode.httpStatus();
  }

  /**
   * Convenience — numeric business code for the JSON {@code code} field.
   *
   * @return business code integer
   */
  public int code() {
    return errCode.code();
  }

  /**
   * Validates that the given message starts with {@value #MSGKEY_PREFIX}.
   *
   * @param msgkey the message to validate
   * @return the message unchanged if valid
   * @throws IllegalArgumentException if the message is null or does not start with the required prefix
   */
  private static String requireMsgkey(String msgkey) {
    if (msgkey == null || !msgkey.startsWith(MSGKEY_PREFIX)) {
      throw new IllegalArgumentException(
          "BusinessException message must start with \"" + MSGKEY_PREFIX + "\" (C8 red-line). "
              + "Received: " + msgkey);
    }
    return msgkey;
  }
}
