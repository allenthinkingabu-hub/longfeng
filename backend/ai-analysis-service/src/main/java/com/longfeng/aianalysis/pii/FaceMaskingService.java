package com.longfeng.aianalysis.pii;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 人脸打码服务 · TDD §16.4 PII（保护未成年）。
 *
 * <p>使命：在送 LLM 前对错题图中的人脸（如学生自拍 / 同学合影背景）做 mask；
 * 避免把未成年人图像送给第三方供应商造成 GDPR / 未成年人保护合规风险。
 *
 * <p>Phase 1 stub 实现 · 真实方案（OpenCV + Haar Cascade · 或第三方人脸 API）由 P1 接入。
 * 测试 / dev 环境不动图 · prod 配 {@code longfeng.ai.pii.face-mask=true} 才启用。
 */
@Component
public class FaceMaskingService {

  private static final Logger LOG = LoggerFactory.getLogger(FaceMaskingService.class);

  private final boolean enabled;

  public FaceMaskingService(
      @Value("${longfeng.ai.pii.face-mask:false}") boolean enabled) {
    this.enabled = enabled;
  }

  /**
   * 对图片人脸做 mask · 原地修改 · 返回处理后的临时文件 path（可能仍是输入 path）。
   *
   * <p>Phase 1：no-op · 直接返回 input。
   *
   * @param imageFile 已 spool 的临时文件
   * @return 处理后的临时文件 path（Phase 1 == imageFile）
   */
  public Path mask(Path imageFile) {
    if (!enabled) {
      return imageFile;
    }
    LOG.debug("Face masking · file={}", imageFile);
    // Phase 1 占位 · Phase 2 接 OpenCV / 第三方
    return imageFile;
  }
}
