package com.longfeng.wrongbook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * GET /api/wb/questions/{qid} response envelope · FE destructures as {@code data.question} +
 * {@code data.plannedNodes}.
 */
@Schema(description = "P04 question detail aggregate response")
public record QuestionDetailResp(
    QuestionDetailDto question,
    @JsonProperty("plannedNodes") List<PlannedNodeDto> plannedNodes) {}
