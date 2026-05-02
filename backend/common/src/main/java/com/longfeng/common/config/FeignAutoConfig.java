package com.longfeng.common.config;

import com.longfeng.common.context.TenantContext;
import feign.RequestInterceptor;
import feign.Request;
import feign.Retryer;
import feign.codec.ErrorDecoder;
import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Global Feign client auto-configuration · TDD §0.9 D-Calendar / §3.1 / C10 / plan §5.S0 BE-01.
 *
 * <p>Registered globally for all {@code @FeignClient} beans in the application context.
 * Controls:
 * <ul>
 *   <li><b>Header propagation</b>: {@code X-Trace-Id} from {@link TenantContext} is forwarded
 *       to downstream services so distributed traces correlate across hops.</li>
 *   <li><b>Timeout</b>: connect 2 s / read 5 s — balanced for internal service calls.</li>
 *   <li><b>Retry</b>: {@link Retryer#NEVER_RETRY} by default to let Sentinel handle fallback.
 *       Individual Feign clients may override with their own {@code Retryer} bean.</li>
 *   <li><b>Error decoding</b>: 4xx responses propagate as {@link BusinessException} so Sentinel
 *       fallback chains receive a typed exception instead of a raw Feign
 *       {@link feign.FeignException}.</li>
 *   <li><b>Sentinel hook</b>: Sentinel integration is activated via the
 *       {@code feign.sentinel.enabled=true} property in each service's {@code application.yml}.
 *       No explicit bean wiring needed here; this config provides the baseline behaviour that
 *       Sentinel wraps.</li>
 * </ul>
 *
 * <p>Conditionally active only when {@code feign.Client} is on the classpath, so services that do
 * not declare an OpenFeign dependency are unaffected.
 */
@Configuration
@ConditionalOnClass(name = "feign.Client")
public class FeignAutoConfig {

  private static final Logger log = LoggerFactory.getLogger(FeignAutoConfig.class);

  /** Connect timeout for outgoing Feign calls (milliseconds). */
  private static final long CONNECT_TIMEOUT_MS = 2_000L;

  /** Read timeout for outgoing Feign calls (milliseconds). */
  private static final long READ_TIMEOUT_MS = 5_000L;

  /**
   * Global request interceptor: propagates {@code X-Trace-Id} from {@link TenantContext} to
   * all outgoing Feign requests, enabling distributed tracing across service boundaries.
   *
   * @return the Feign request interceptor
   */
  @Bean
  public RequestInterceptor traceIdFeignInterceptor() {
    return template -> {
      String traceId = TenantContext.traceId();
      if (traceId != null && !traceId.isBlank()) {
        template.header("X-Trace-Id", traceId);
        log.trace("Feign propagating X-Trace-Id={} to url={}", traceId, template.url());
      }
    };
  }

  /**
   * Global Feign request options: connect 2 s / read 5 s.
   *
   * @return Feign request options
   */
  @Bean
  public Request.Options feignRequestOptions() {
    return new Request.Options(
        Duration.ofMillis(CONNECT_TIMEOUT_MS), Duration.ofMillis(READ_TIMEOUT_MS), true);
  }

  /**
   * Never-retry policy — Sentinel circuit breaker handles fallback instead of blind retries.
   * Override in specific service configurations if retry is appropriate (e.g. idempotent GETs).
   *
   * @return Feign retryer that never retries
   */
  @Bean
  public Retryer feignRetryer() {
    return Retryer.NEVER_RETRY;
  }

  /**
   * Error decoder that converts Feign HTTP error responses into typed {@link BusinessException}
   * instances so Sentinel {@code fallbackFactory} receives structured exceptions.
   *
   * @return Feign error decoder
   */
  @Bean
  public ErrorDecoder feignErrorDecoder() {
    return (methodKey, response) -> {
      int status = response.status();
      log.warn("Feign error status={} method={}", status, methodKey);
      ErrCode errCode = mapHttpStatusToErrCode(status);
      // Message must start with msgkey: prefix (C8). The feign method key is logged, not embedded
      // in the message, to preserve i18n key integrity.
      return new BusinessException(errCode, errCode.defaultMsgkey());
    };
  }

  /**
   * Maps an HTTP status code to the closest {@link ErrCode} for error decoder use.
   *
   * @param httpStatus HTTP status code
   * @return best-matching ErrCode
   */
  private static ErrCode mapHttpStatusToErrCode(int httpStatus) {
    return switch (httpStatus) {
      case 400 -> ErrCode.VALIDATION_FAILED;
      case 401 -> ErrCode.UNAUTHORIZED;
      case 403 -> ErrCode.OBSERVER_FORBIDDEN_WRITE;
      case 404 -> ErrCode.NOT_FOUND;
      case 409 -> ErrCode.GUEST_ALREADY_CLAIMED;
      case 410 -> ErrCode.TOKEN_EXPIRED;
      case 429 -> ErrCode.AI_RATE_LIMIT;
      default -> ErrCode.INTERNAL_ERROR;
    };
  }
}
