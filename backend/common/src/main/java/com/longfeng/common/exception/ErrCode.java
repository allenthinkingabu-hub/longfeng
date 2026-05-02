package com.longfeng.common.exception;

/**
 * Canonical 22-entry error-code registry · TDD §B (附录 B) / plan §5.S0 BE-01.
 *
 * <p>Code layout: {@code HTTP_STATUS * 100 + business_sub_code}.
 * All entries map to a default {@code msgkey:} used when a {@link BusinessException} is thrown
 * without an explicit message. Override the msgkey by passing it to the constructor.
 *
 * <p>Every case here must be handled in {@link GlobalExceptionHandler}.
 */
public enum ErrCode {

  // ── 400 Bad Request ────────────────────────────────────────────────────
  /** Request parameter validation failed (MethodArgumentNotValid / ConstraintViolation). */
  VALIDATION_FAILED(40001, 400, "msgkey:common.error.validation_failed"),

  /** Model ID not in catalog, or model unavailable in current region. */
  MODEL_NOT_IN_CATALOG(40306, 400, "msgkey:ai.model.not_in_catalog"),

  // ── 401 Unauthorized ───────────────────────────────────────────────────
  /** Not logged in / JWT expired. */
  UNAUTHORIZED(40101, 401, "msgkey:common.error.unauthorized"),

  // ── 403 Forbidden ──────────────────────────────────────────────────────
  /** Device fingerprint mismatch during guest session validation. */
  DEVICE_MISMATCH(40301, 403, "msgkey:anon.error.device_mismatch"),

  /** Anonymous / share scope attempting a write operation. */
  ANONYMOUS_WRITE_FORBIDDEN(40302, 403, "msgkey:anon.error.write_forbidden"),

  /** Observer scope attempting a write operation (read-only enforcement). */
  OBSERVER_FORBIDDEN_WRITE(40303, 403, "msgkey:anon.observer.write_forbidden"),

  /** Observer token has been revoked by the owning student. */
  OBSERVER_REVOKED(40304, 403, "msgkey:anon.observer.revoked"),

  /** NORMAL tier user attempted to set a VIP-only AI model. */
  NON_VIP_MODEL_DENIED(40305, 403, "msgkey:ai.model.vip_only_required"),

  // ── 404 Not Found ──────────────────────────────────────────────────────
  /** Requested resource does not exist. */
  NOT_FOUND(40401, 404, "msgkey:common.error.not_found"),

  // ── 409 Conflict ───────────────────────────────────────────────────────
  /** Guest session has already been claimed; idempotent — returns original qid. */
  GUEST_ALREADY_CLAIMED(40901, 409, "msgkey:anon.guest.already_claimed"),

  // ── 410 Gone ───────────────────────────────────────────────────────────
  /** Guest session has expired (TTL elapsed). */
  GUEST_SESSION_EXPIRED(41001, 410, "msgkey:anon.guest.session_expired"),

  /** Share or observer invite token has expired. */
  TOKEN_EXPIRED(41002, 410, "msgkey:anon.token.expired"),

  // ── 429 Too Many Requests ──────────────────────────────────────────────
  /** AI analysis rate limit exceeded (per-user or per-tenant). */
  AI_RATE_LIMIT(42901, 429, "msgkey:ai.error.rate_limit"),

  /** Guest daily analysis quota exhausted. */
  GUEST_QUOTA_EXHAUSTED(42902, 429, "msgkey:anon.guest.quota_exhausted"),

  /** Landing page rate limit exceeded (30 req/min/IP). */
  LANDING_RATE_LIMIT(42903, 429, "msgkey:anon.landing.rate_limit"),

  // ── 500 Internal Server Error ──────────────────────────────────────────
  /** AI provider (OpenAI / Qianwen / Zhipu / local-vllm) unavailable. */
  AI_PROVIDER_UNAVAILABLE(50010, 500, "msgkey:ai.error.provider_unavailable"),

  /** AI structured-output JSON could not be parsed into target schema. */
  AI_JSON_PARSE_FAILED(50011, 500, "msgkey:ai.error.json_parse_failed"),

  /** Prompt injection pattern detected by SafeGuardAdvisor. */
  PROMPT_INJECTION_DETECTED(50012, 500, "msgkey:ai.error.prompt_injection"),

  /** OSS callback signature verification failed. */
  OSS_CALLBACK_SIGN_FAILED(50020, 500, "msgkey:file.error.callback_sign_failed"),

  /** Ebbinghaus review node has passed its deadline without review. */
  EBBINGHAUS_NODE_EXPIRED(50030, 500, "msgkey:review.error.node_expired"),

  /** Ebbinghaus node state-machine received an illegal transition. */
  NODE_INVALID_TRANSITION(50031, 500, "msgkey:wb.error.node.invalid_transition"),

  /** Generic internal server error (fallback). */
  INTERNAL_ERROR(50000, 500, "msgkey:common.error.internal");

  private final int code;
  private final int httpStatus;
  private final String defaultMsgkey;

  ErrCode(int code, int httpStatus, String defaultMsgkey) {
    this.code = code;
    this.httpStatus = httpStatus;
    this.defaultMsgkey = defaultMsgkey;
  }

  /** Numeric business code for the JSON {@code code} field of {@link com.longfeng.common.dto.ApiResult}. */
  public int code() {
    return code;
  }

  /** HTTP status code associated with this error. */
  public int httpStatus() {
    return httpStatus;
  }

  /**
   * Default i18n message key (always starts with {@code "msgkey:"}).
   * Clients can override by passing a custom msgkey to
   * {@link BusinessException#BusinessException(ErrCode, String)}.
   */
  public String defaultMsgkey() {
    return defaultMsgkey;
  }
}
