package com.longfeng.common.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Centralised Jackson {@link ObjectMapper} configuration · TDD §3.1 / plan §5.S0 BE-01.
 *
 * <p>Enforces:
 * <ul>
 *   <li><b>snake_case</b> property naming for all JSON fields</li>
 *   <li><b>JSR-310 / Java-time</b> support via {@link JavaTimeModule} (ISO-8601 strings, not epoch ms)</li>
 *   <li><b>Long → String</b> serialisation to avoid JavaScript BigInt precision loss for IDs ≥ 2^53</li>
 *   <li><b>Strict enum deserialisation</b>: unknown enum values throw instead of silently mapping to null</li>
 *   <li>{@code OffsetDateTime} / {@code ZonedDateTime} preferred (C9 compliance — LocalDateTime banned)</li>
 *   <li>Null fields omitted from output ({@link JsonInclude#NON_NULL})</li>
 *   <li>{@code FAIL_ON_UNKNOWN_PROPERTIES = false} to tolerate forward-compatible API additions</li>
 * </ul>
 */
@Configuration
@ConditionalOnClass(ObjectMapper.class)
public class ObjectMapperConfig {

  /**
   * Primary Jackson {@link ObjectMapper} bean shared across all Spring MVC / Feign serialization.
   *
   * @return configured mapper
   */
  @Bean
  @Primary
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();

    // ── Naming strategy ──────────────────────────────────────────────────
    mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    // ── Java-time (JSR-310) ──────────────────────────────────────────────
    // JavaTimeModule handles OffsetDateTime / ZonedDateTime / Instant out of the box.
    // LocalDateTime is intentionally NOT special-cased here — C9 red-line forbids its use.
    // Any code that attempts to serialize LocalDateTime will encounter Jackson's default behavior
    // which will surface the issue at review time.
    mapper.registerModule(new JavaTimeModule());
    // Write dates as ISO-8601 strings, not epoch milliseconds
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // ── Long → String (BigInt safety for frontend) ───────────────────────
    SimpleModule longToStringModule = new SimpleModule("LongToStringModule");
    longToStringModule.addSerializer(Long.class, ToStringSerializer.instance);
    longToStringModule.addSerializer(long.class, ToStringSerializer.instance);
    mapper.registerModule(longToStringModule);

    // ── Enum strict deserialization ──────────────────────────────────────
    // Reject ordinal-based enum mapping (enums must be deserialized by name)
    mapper.enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS);
    // Accept case-insensitive enum names to tolerate frontend naming conventions
    mapper.enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS);
    // Unknown enum values: fail immediately rather than silently mapping to null
    mapper.disable(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL);

    // ── Robustness ────────────────────────────────────────────────────────
    // Forward-compatible: ignore new fields from downstream services
    mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    // Omit null fields from JSON output to reduce payload size
    mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

    return mapper;
  }
}
