package com.longfeng.wrongbook.dto;

import java.time.Instant;

public record WrongAttemptVO(
    Long id,
    Long wrongItemId,
    Long studentId,
    String answerText,
    Boolean isCorrect,
    Short durationSec,
    String clientSource,
    Instant submittedAt) {}
