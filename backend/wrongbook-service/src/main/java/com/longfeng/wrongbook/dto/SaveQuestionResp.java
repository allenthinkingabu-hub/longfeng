package com.longfeng.wrongbook.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/**
 * POST /api/wb/questions/{qid}/save response · FE expects {@code { qid, plan_id, nodes: [{ nid,
 * t_level, due_at }] }}.
 */
@Schema(description = "P04 Save · 返回保存后的复习计划 + 6 个节点")
public record SaveQuestionResp(
    String qid,
    @JsonProperty("plan_id") String planId,
    List<PlanNode> nodes) {

  @Schema(description = "复习节点 · nid / t_level / due_at")
  public record PlanNode(
      String nid,
      @JsonProperty("t_level") String tLevel,
      @JsonProperty("due_at") String dueAt) {}
}
