package com.longfeng.aianalysis.service;

import com.longfeng.aianalysis.llm.AnalysisResult;
import com.longfeng.common.dto.AnalysisChunk;
import org.springframework.core.io.Resource;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * AI 错题分析 · 业务接口（plan §5.S3 service/ 子域）。
 *
 * <p>暴露同步 + 流式两种调用方式 · 实现类 {@code QuestionAnalyzerImpl} 用 Spring AI ChatClient
 * + entity(AnalysisResult.class) 结构化输出。
 *
 * <p>"业务代码不感知供应商差异"——本接口不出现 provider 名 / API key / model name。
 *
 * <p>"图片用 Resource 不用 byte[]"——C7 红线：byte[] 不在 heap 持久化（参 TempFileSpooler）。
 */
public interface QuestionAnalyzer {

  /**
   * 同步分析 · 8s 超时（D-AI timeout-sync）· 适合 polling fallback 场景（{@code GET /api/ai/result/{taskId}}）。
   *
   * @param taskId  任务 ID（用于 spool 文件命名 + 日志关联）
   * @param image   图片 Resource（来自 file-service · 已 NSFW + face-mask 过滤）
   * @param subject 学科枚举提示（MATH / CHINESE / ...）
   * @return Mono of AnalysisResult · 失败走 FallbackOrchestrator 三段降级
   */
  Mono<AnalysisResult> analyze(String taskId, Resource image, String subject);

  /**
   * 流式分析 · 15s 超时（D-AI timeout-stream）· 同步推送 4 stage chunk 到 {@link AnalysisStreamHub}。
   *
   * <p>D-AI-Stream chunk 4 stage：OCR → ANALYSIS → STEPS → DONE。每个 stage 一条 chunk
   * 进 sink；前端按 stage 切换 P03 4 步流水线动画。
   *
   * @param taskId  任务 ID
   * @param image   图片 Resource
   * @param subject 学科
   * @return Flux of {@link AnalysisChunk}（同时被 SSE 端点 + WS Handler 消费 · TDD §8.1 单源）
   */
  Flux<AnalysisChunk> streamAnalyze(String taskId, Resource image, String subject);
}
