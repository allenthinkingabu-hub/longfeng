package com.longfeng.wrongbook.dto;

import java.time.Instant;
import java.util.List;

public record WrongItemVO(
    Long id,
    Long studentId,
    String subject,
    String gradeCode,
    Short sourceType,
    String originImageKey,
    String processedImageKey,
    String ocrText,
    String stemText,
    Short status,
    Short mastery,
    Short difficulty,
    Long version,
    Instant createdAt,
    Instant updatedAt,
    List<WrongItemTagVO> tags,
    List<WrongItemImageVO> images) {}
