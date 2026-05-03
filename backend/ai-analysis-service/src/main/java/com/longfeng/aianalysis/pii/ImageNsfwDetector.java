package com.longfeng.aianalysis.pii;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 多模态 NSFW 前置过滤 · TDD §16.4 PII + §10.2 longfeng.ai.pii.nsfw-block-threshold。
 *
 * <p>策略（Phase 1 简化实现）：
 *
 * <ul>
 *   <li>调用本地启发式 / 第三方 NSFW API 拿 score ∈ [0,1]
 *   <li>score ≥ {@code nsfw-block-threshold}（默认 0.85）→ 阻断 · 返回 false（不送 LLM）
 *   <li>未配置 / 调不通 → fail-open（兜底放行）+ WARN 日志
 * </ul>
 *
 * <p>Phase 1 提供 stub 实现 · 真实 NSFW 服务（如阿里云内容安全）由 ops 在 staging 注入；
 * 测试场景下永远返回 0.0（safe）。
 */
@Component
public class ImageNsfwDetector {

  private static final Logger LOG = LoggerFactory.getLogger(ImageNsfwDetector.class);

  private final double blockThreshold;
  private final boolean enabled;

  public ImageNsfwDetector(
      @Value("${longfeng.ai.pii.nsfw-block-threshold:0.85}") double blockThreshold,
      @Value("${longfeng.ai.pii.nsfw-enabled:false}") boolean enabled) {
    this.blockThreshold = blockThreshold;
    this.enabled = enabled;
  }

  /**
   * 返回是否安全（true=可以送 LLM）。
   *
   * <p>Phase 1 stub：不调外部 API · 全部返 true（safe）。Phase 2 接入阿里云内容安全 API。
   *
   * @param imageFile 图片临时文件（已 spool · 不在 heap 持有 byte[]）
   * @return true=可继续 · false=NSFW 拦截
   */
  public boolean isSafe(Path imageFile) {
    if (!enabled) {
      return true;
    }
    double score = scoreImage(imageFile);
    if (score >= blockThreshold) {
      LOG.warn("NSFW detected · file={} score={} threshold={}", imageFile, score, blockThreshold);
      return false;
    }
    return true;
  }

  /**
   * 评分钩子 · Phase 2 替换为真实实现。
   *
   * @return 0.0（safe）..1.0（unsafe）
   */
  protected double scoreImage(Path imageFile) {
    return 0.0;
  }
}
