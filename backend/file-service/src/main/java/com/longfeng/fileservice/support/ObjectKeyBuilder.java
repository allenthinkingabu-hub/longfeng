package com.longfeng.fileservice.support;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * D-OSS-Key path strategy implementation.
 *
 * <p>Format: {@code wrongbook/{tenantId}/{yyyyMM}/{studentId}/{snowflakeId}_{sanitizedFilename}}.
 *
 * <p>Constraints (TDD §0.9 D-OSS-Key):
 * <ul>
 *   <li>No student email in path — only numeric IDs.
 *   <li>Segment order: tenantId first to avoid single-tenant hot spots.
 *   <li>Monthly partition for cold-tier archiving.
 *   <li>Filename is sanitized (non-alphanumeric except dot/dash/underscore replaced with '_').
 * </ul>
 */
@Component
public class ObjectKeyBuilder {

    private static final DateTimeFormatter YYYYMM = DateTimeFormatter.ofPattern("yyyyMM");

    /** Only alphanumerics, '.', '-', '_' are kept; everything else replaced with '_'. */
    private static final Pattern UNSAFE_CHARS = Pattern.compile("[^a-zA-Z0-9._-]");

    /** Maximum length for sanitized filename component. */
    private static final int MAX_FILENAME_LEN = 128;

    /**
     * Build the D-OSS-Key object path.
     *
     * @param tenantId      tenant numeric ID
     * @param studentId     student numeric ID (not email — C5 / D-OSS-Key)
     * @param snowflakeId   unique file ID (Snowflake)
     * @param originalName  original filename (will be sanitized)
     * @param now           current time for yyyyMM partition
     * @return              object key string
     */
    public String build(long tenantId, long studentId, long snowflakeId,
                        String originalName, OffsetDateTime now) {
        String month = now.format(YYYYMM);
        String sanitized = sanitize(originalName);
        return String.format("wrongbook/%d/%s/%d/%d_%s", tenantId, month, studentId, snowflakeId, sanitized);
    }

    /**
     * Sanitize a filename so it is safe to embed in an OSS object key.
     * Strips directory separators, replaces unsafe chars with '_', and truncates.
     *
     * @param name original filename (may be null)
     * @return sanitized filename, never null, never empty (fallbacks to "file")
     */
    public String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        // Strip any directory parts
        String basename = name.replace('\\', '/');
        int slash = basename.lastIndexOf('/');
        if (slash >= 0) {
            basename = basename.substring(slash + 1);
        }
        if (basename.isBlank()) {
            return "file";
        }
        // Replace unsafe characters
        String safe = UNSAFE_CHARS.matcher(basename).replaceAll("_");
        // Truncate
        if (safe.length() > MAX_FILENAME_LEN) {
            safe = safe.substring(0, MAX_FILENAME_LEN);
        }
        return safe.isBlank() ? "file" : safe;
    }
}
