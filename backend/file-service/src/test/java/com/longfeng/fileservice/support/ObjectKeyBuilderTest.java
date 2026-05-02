package com.longfeng.fileservice.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ObjectKeyBuilder}.
 *
 * <p>Covers:
 * <ul>
 *   <li>D-OSS-Key path format correctness
 *   <li>Filename sanitization (unsafe chars, path traversal, length truncation)
 *   <li>No student email in path
 *   <li>Monthly partitioning
 * </ul>
 */
class ObjectKeyBuilderTest {

    private final ObjectKeyBuilder builder = new ObjectKeyBuilder();

    private static final OffsetDateTime JAN_2026 =
            OffsetDateTime.of(2026, 1, 15, 10, 30, 0, 0, ZoneOffset.UTC);

    // ── D-OSS-Key path format ──────────────────────────────────────────────

    @Test
    @DisplayName("build() produces correct D-OSS-Key format: wrongbook/{tenantId}/{yyyyMM}/{studentId}/{snowflakeId}_{filename}")
    void build_correctPathFormat() {
        String key = builder.build(42L, 1001L, 99999L, "math.jpg", JAN_2026);
        // Format: wrongbook/{tenantId}/{yyyyMM}/{studentId}/{snowflakeId}_{sanitizedFilename}
        assertThat(key).isEqualTo("wrongbook/42/202601/1001/99999_math.jpg");
    }

    @Test
    @DisplayName("build() uses yyyyMM from provided timestamp")
    void build_monthlyPartition() {
        OffsetDateTime dec2025 = OffsetDateTime.of(2025, 12, 1, 0, 0, 0, 0, ZoneOffset.UTC);
        String key = builder.build(0L, 500L, 12345L, "photo.png", dec2025);
        assertThat(key).contains("/202512/");
    }

    @Test
    @DisplayName("build() path does not contain student email (only numeric student ID)")
    void build_noStudentEmailInPath() {
        // Simulate a case where a caller might mistakenly pass email-like string as filename
        // The path segments for tenantId and studentId are always numeric — not emails.
        String key = builder.build(1L, 777L, 8888L, "question.jpg", JAN_2026);
        // Verify the path structure: no @ sign in the path segments
        String[] segments = key.split("/");
        // segments: [wrongbook, tenantId, yyyyMM, studentId, filenamePart]
        assertThat(segments).hasSizeGreaterThanOrEqualTo(5);
        assertThat(segments[1]).doesNotContain("@"); // tenantId is numeric
        assertThat(segments[3]).doesNotContain("@"); // studentId is numeric
    }

    // ── Filename sanitization ──────────────────────────────────────────────

    @Test
    @DisplayName("sanitize() replaces spaces and special chars with underscore")
    void sanitize_replacesUnsafeChars() {
        String result = builder.sanitize("my problem set (2026).jpg");
        assertThat(result).doesNotContain(" ", "(", ")");
        assertThat(result).endsWith(".jpg");
    }

    @Test
    @DisplayName("sanitize() strips directory traversal (../ prefix)")
    void sanitize_stripsDirectoryTraversal() {
        String result = builder.sanitize("../../etc/passwd");
        // Should strip leading path segments, result is just the basename
        assertThat(result).isNotEmpty();
        assertThat(result).doesNotContain("/");
        assertThat(result).doesNotContain("..");
    }

    @Test
    @DisplayName("sanitize() handles null input gracefully")
    void sanitize_nullInput() {
        String result = builder.sanitize(null);
        assertThat(result).isEqualTo("file");
    }

    @Test
    @DisplayName("sanitize() handles blank input gracefully")
    void sanitize_blankInput() {
        String result = builder.sanitize("   ");
        assertThat(result).isEqualTo("file");
    }

    @Test
    @DisplayName("sanitize() truncates filenames longer than 128 characters")
    void sanitize_truncatesLongFilename() {
        String longName = "a".repeat(200) + ".jpg";
        String result = builder.sanitize(longName);
        assertThat(result).hasSizeLessThanOrEqualTo(128);
    }

    @Test
    @DisplayName("sanitize() preserves alphanumerics, dot, dash, underscore")
    void sanitize_preservesSafeChars() {
        String result = builder.sanitize("math-problem_01.jpg");
        assertThat(result).isEqualTo("math-problem_01.jpg");
    }

    @Test
    @DisplayName("sanitize() strips Windows backslash paths")
    void sanitize_stripsWindowsPath() {
        String result = builder.sanitize("C:\\Users\\student\\photo.png");
        assertThat(result).doesNotContain("C:", "Users", "student");
        assertThat(result).isEqualTo("photo.png");
    }

    @Test
    @DisplayName("build() with sanitized filename containing spaces")
    void build_sanitizedFilenameInPath() {
        String key = builder.build(1L, 100L, 55555L, "my exam photo.jpg", JAN_2026);
        // Space should be replaced with underscore
        assertThat(key).contains("my_exam_photo.jpg");
        assertThat(key).startsWith("wrongbook/1/202601/100/55555_");
    }
}
