package com.longfeng.wrongbook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import java.time.Instant;
import java.util.List;

public record WrongItemVO(
    @JsonSerialize(using = ToStringSerializer.class) Long id,
    @JsonProperty("student_id") Long studentId,
    String subject,
    @JsonProperty("grade_code") String gradeCode,
    @JsonProperty("source_type") Short sourceType,
    @JsonProperty("origin_image_key") String originImageKey,
    @JsonProperty("processed_image_key") String processedImageKey,
    @JsonProperty("ocr_text") String ocrText,
    @JsonProperty("stem_text") String stemText,
    String status,
    Short mastery,
    Short difficulty,
    Long version,
    @JsonProperty("created_at") Instant createdAt,
    @JsonProperty("updated_at") Instant updatedAt,
    List<WrongItemTagVO> tags,
    List<WrongItemImageVO> images) {}
