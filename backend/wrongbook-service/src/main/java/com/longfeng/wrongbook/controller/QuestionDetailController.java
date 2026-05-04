package com.longfeng.wrongbook.controller;

import com.longfeng.wrongbook.dto.QuestionDetailResp;
import com.longfeng.wrongbook.dto.SaveQuestionReq;
import com.longfeng.wrongbook.dto.SaveQuestionResp;
import com.longfeng.wrongbook.service.QuestionAggregateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * P04 Result page · question detail aggregate · serves the FE
 * {@code frontend/apps/h5/src/pages/Result/index.tsx} page.
 *
 * <p>Important · the response shape is the FE-expected aggregate {@link QuestionDetailResp}
 * (a plain JSON {@code { question, plannedNodes }}) — NOT the {@code ApiResult} envelope used by
 * {@link WrongItemController}. The FE destructures top-level {@code data.question} +
 * {@code data.plannedNodes}, so wrapping in {@code ApiResult} would push everything one level
 * deeper and break P04 rendering.
 *
 * <p>Path is intentionally {@code /api/wb/questions} (matches the FE fetch path 1:1) — distinct
 * from the legacy {@code /wrongbook/items} controller.
 */
@RestController
@RequestMapping("/api/wb/questions")
@Tag(name = "p04-questions", description = "P04 Result · 题目详情聚合 + save 触发 SM-2")
public class QuestionDetailController {

  private final QuestionAggregateService aggregate;

  public QuestionDetailController(QuestionAggregateService aggregate) {
    this.aggregate = aggregate;
  }

  /** GET /api/wb/questions/{qid} · 返 {@link QuestionDetailResp} 聚合. */
  @Operation(summary = "P04 · 题目详情聚合 (question + plannedNodes)")
  @ApiResponse(responseCode = "200", description = "聚合详情")
  @ApiResponse(responseCode = "404", description = "wrong_item 不存在")
  @GetMapping("/{qid}")
  public QuestionDetailResp get(@PathVariable("qid") String qid) {
    return aggregate.getDetail(qid);
  }

  /**
   * POST /api/wb/questions/{qid}/save · 创建 SM-2 plan + 6 nodes · 返 {@link SaveQuestionResp}.
   *
   * <p>FE 实际发送 body {@code { qid }} (虽然 qid 也在 URL 里 · 见 Result/index.tsx L154) ·
   * body 在这里仅作 echo 校验 · path 优先.
   */
  @Operation(summary = "P04 · 保存到错题本并触发艾宾浩斯 6 节点")
  @ApiResponse(responseCode = "200", description = "save 成功 · 返 plan_id + 6 nodes")
  @ApiResponse(responseCode = "404", description = "wrong_item 不存在")
  @PostMapping("/{qid}/save")
  public SaveQuestionResp save(
      @PathVariable("qid") String qid,
      @RequestBody(required = false) SaveQuestionReq req,
      @RequestHeader(name = "X-Request-Id", required = false) String requestId) {
    // path takes precedence · body qid is just an echo (FE sends both)
    return aggregate.save(qid, requestId);
  }
}
