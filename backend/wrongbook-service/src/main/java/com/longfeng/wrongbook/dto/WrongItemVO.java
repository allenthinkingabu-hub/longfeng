package com.longfeng.wrongbook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;
import java.util.List;

/**
 * S7 contract fixes:
 * <ul>
 *   <li>Issue 2: {@code mastery} is now 0-100 (mapped from internal 0-2 by service layer)
 *   <li>Issue 3: {@code origin_image_key} → {@code image_url}
 *   <li>Issue 4: {@code tags} simplified to {@code string[]} (tagCode only)
 * </ul>
 */
public record WrongItemVO(
    @JsonSerialize(using = ToStringSerializer.class) Long id,
    @JsonProperty("student_id") Long studentId,
    String subject,
    @JsonProperty("grade_code") String gradeCode,
    @JsonProperty("source_type") Short sourceType,
    @JsonProperty("image_url") String imageUrl,
    @JsonProperty("processed_image_key") String processedImageKey,
    @JsonProperty("ocr_text") String ocrText,
    @JsonProperty("stem_text") String stemText,
    String status,
    int mastery,
    Short difficulty,
    Long version,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt,
    List<String> tags,
    List<WrongItemImageVO> images) {}
