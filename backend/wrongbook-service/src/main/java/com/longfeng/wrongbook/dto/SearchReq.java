package com.longfeng.wrongbook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * Request body for POST /api/wb/questions/search (hybrid search).
 */
public record SearchReq(
    /** Free-text query for trigram search. */
    String query,
    /** Optional pre-computed embedding vector for semantic search. */
    @JsonProperty("query_vector") List<Float> queryVector,
    /** Optional student filter. */
    @JsonProperty("student_id") Long studentId,
    /** Optional subject filter. */
    String subject,
    /** Max results to return, default 20. */
    @JsonProperty("top_n") Integer topN) {}
