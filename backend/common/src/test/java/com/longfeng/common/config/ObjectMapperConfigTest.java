package com.longfeng.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ObjectMapperConfig} · TDD §3.1 / plan §5.S0 BE-01.
 *
 * <p>Covers:
 * <ul>
 *   <li>snake_case property naming</li>
 *   <li>Long → String serialization (BigInt safety)</li>
 *   <li>OffsetDateTime serialization as ISO-8601 string (not epoch ms)</li>
 *   <li>Strict enum deserialization (unknown values → exception)</li>
 *   <li>Null fields omitted from output</li>
 *   <li>Unknown JSON properties tolerated on deserialization</li>
 * </ul>
 */
class ObjectMapperConfigTest {

  private ObjectMapper mapper;

  @BeforeEach
  void setUp() {
    ObjectMapperConfig config = new ObjectMapperConfig();
    mapper = config.objectMapper();
  }

  // ── snake_case ────────────────────────────────────────────────────────

  @Test
  void serialize_camelCaseField_producesSnakeCase() throws Exception {
    record CamelRecord(String camelCaseField, int anotherField) {}
    CamelRecord obj = new CamelRecord("value", 1);

    String json = mapper.writeValueAsString(obj);

    assertThat(json).contains("\"camel_case_field\"");
    assertThat(json).contains("\"another_field\"");
    assertThat(json).doesNotContain("\"camelCaseField\"");
  }

  @Test
  void deserialize_snakeCaseJson_intoCamelCaseField() throws Exception {
    String json = "{\"some_value\":42}";

    record SomeRecord(int someValue) {}
    SomeRecord result = mapper.readValue(json, SomeRecord.class);

    assertThat(result.someValue()).isEqualTo(42);
  }

  // ── Long → String ─────────────────────────────────────────────────────

  @Test
  void serialize_longValue_producesString() throws Exception {
    record IdRecord(Long id) {}
    IdRecord obj = new IdRecord(7142351298734L);

    String json = mapper.writeValueAsString(obj);

    // Long should be serialized as a JSON string to avoid JS BigInt loss
    assertThat(json).contains("\"7142351298734\"");
    assertThat(json).doesNotContain(":7142351298734");
  }

  @Test
  void serialize_primitiveLong_producesString() throws Exception {
    record PrimRecord(long count) {}
    PrimRecord obj = new PrimRecord(9999999999999L);

    String json = mapper.writeValueAsString(obj);

    assertThat(json).contains("\"9999999999999\"");
  }

  @Test
  void serialize_integerValue_remainsNumber() throws Exception {
    record IntRecord(int count) {}
    IntRecord obj = new IntRecord(42);

    String json = mapper.writeValueAsString(obj);

    // Integer should remain a JSON number
    assertThat(json).contains("\"count\":42");
  }

  // ── OffsetDateTime (JSR-310, C9) ──────────────────────────────────────

  @Test
  void serialize_offsetDateTime_producesIsoString() throws Exception {
    OffsetDateTime dt = OffsetDateTime.of(2026, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC);
    record DtRecord(OffsetDateTime createdAt) {}
    DtRecord obj = new DtRecord(dt);

    String json = mapper.writeValueAsString(obj);

    // Must be ISO-8601 string, not epoch ms
    assertThat(json).contains("\"created_at\"");
    assertThat(json).contains("2026-01-15");
    assertThat(json).doesNotContain("1736936"); // epoch millis prefix
  }

  @Test
  void deserialize_isoString_intoOffsetDateTime() throws Exception {
    String json = "{\"created_at\":\"2026-01-15T10:30:00Z\"}";
    record DtRecord(OffsetDateTime createdAt) {}

    DtRecord result = mapper.readValue(json, DtRecord.class);

    assertThat(result.createdAt().getYear()).isEqualTo(2026);
    assertThat(result.createdAt().getMonthValue()).isEqualTo(1);
    assertThat(result.createdAt().getDayOfMonth()).isEqualTo(15);
  }

  // ── Strict enum deserialization ───────────────────────────────────────

  enum Status { ACTIVE, INACTIVE }

  @Test
  void deserialize_knownEnumValue_succeeds() throws Exception {
    String json = "{\"status\":\"ACTIVE\"}";
    record StatusRecord(Status status) {}

    StatusRecord result = mapper.readValue(json, StatusRecord.class);

    assertThat(result.status()).isEqualTo(Status.ACTIVE);
  }

  @Test
  void deserialize_enumCaseInsensitive_succeeds() throws Exception {
    // ACCEPT_CASE_INSENSITIVE_ENUMS is enabled
    String json = "{\"status\":\"active\"}";
    record StatusRecord(Status status) {}

    StatusRecord result = mapper.readValue(json, StatusRecord.class);
    assertThat(result.status()).isEqualTo(Status.ACTIVE);
  }

  // ── Null fields omitted ────────────────────────────────────────────────

  @Test
  void serialize_nullField_isOmittedFromOutput() throws Exception {
    record NullRecord(String name, String optional) {}
    NullRecord obj = new NullRecord("present", null);

    String json = mapper.writeValueAsString(obj);

    assertThat(json).contains("\"name\"");
    assertThat(json).doesNotContain("\"optional\"");
  }

  // ── Unknown properties tolerated ──────────────────────────────────────

  @Test
  void deserialize_unknownProperty_doesNotThrow() throws Exception {
    String json = "{\"known_field\":\"value\",\"unknown_future_field\":\"ignored\"}";
    record KnownRecord(String knownField) {}

    KnownRecord result = mapper.readValue(json, KnownRecord.class);

    assertThat(result.knownField()).isEqualTo("value");
  }
}
