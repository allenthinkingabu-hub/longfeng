package com.longfeng.aianalysis.controller;

import com.longfeng.aianalysis.service.AnalysisStreamHub;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * D-AI-Cancel 端点（TDD §0.9 D-AI-Cancel + §12.2.3 + plan §5.S3）。
 *
 * <p>语义："学生 tap 取消分析" → 前端关 EventSource → POST /api/ai/cancel/{taskId}
 * → {@link AnalysisStreamHub#dispose} 取消上游 Disposable + emit CANCELLED chunk + complete sink。
 *
 * <p><strong>已经付费给供应商的 token 不退</strong> —— 取消是用户的"我不要了"语义，不是"未发生"；
 * DB 不留死状态（{@code wb_question.status=CANCELLED} 由 wrongbook-service 写）。
 *
 * <p><b>WT4 · 2026-05-04</b>：响应改为 {@code {"status":"CANCELLED"}} 200 OK · 跟 FE
 * {@code useEventSource.ts} cancel handler + MSW handler 对齐（之前 204 No Content）。
 * 幂等：未知 taskId 仍返 {@code {"status":"CANCELLED"}} · 不暴露内部 disposed 状态。
 */
@RestController
@RequestMapping("/api/ai")
public class AiCancelController {

  private static final Logger LOG = LoggerFactory.getLogger(AiCancelController.class);

  private final AnalysisStreamHub streamHub;

  public AiCancelController(AnalysisStreamHub streamHub) {
    this.streamHub = streamHub;
  }

  @PostMapping("/cancel/{taskId}")
  public ResponseEntity<Map<String, String>> cancel(@PathVariable String taskId) {
    boolean disposed = streamHub.cancel(taskId);
    LOG.info("D-AI-Cancel · taskId={} disposed={}", taskId, disposed);
    return ResponseEntity.ok(Map.of("status", "CANCELLED"));
  }
}
