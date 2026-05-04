package com.longfeng.wrongbook.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * POST /api/wb/questions/{qid}/save body · FE actually sends {@code { qid }} (and FE puts qid in
 * the URL too · so body is largely a confirmation echo).
 */
@Schema(description = "P04 Save body")
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SaveQuestionReq(String qid) {}
