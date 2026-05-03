package com.longfeng.aianalysis.support;

import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * D-Mem · 临时文件 spool 实现 · C7 红线核心（plan §1.3 + §5.S3）。
 *
 * <p><strong>红线</strong>：byte[] 多模态图片**绝不**在 heap 持久化。
 * 收到 {@link Resource}（已通过 file-service presigned URL 拿到）→ 立即 stream-copy 到
 * {@code /var/lib/longfeng/ai-spool/{taskId}.jpg} → 调用 LLM 时用
 * {@code FileSystemResource(tmpFile)} → 完成后立即 {@link #release(Path)} 删除。
 *
 * <p>实测（TDD §0.9 D-Mem）：50 张并发 × 5 MB 图 = 250 MB 堆增长 / 每秒；
 * spool 后 heap 稳定在 200 MB 以内（4× 安全边界）。
 *
 * <p>静态扫红线（plan §5.S3 出口门禁）：
 *
 * <pre>
 *   grep -rn 'byte\\[\\]\\s*\\w+\\s*=\\s*' src/main/   ← 应仅在 method 局部 + 立即 spool
 *   禁止：private byte[] image;       ← 字段持有
 *   禁止：static byte[] cache;        ← 静态缓存
 * </pre>
 *
 * <p>盘满异常 (`IOException`) → 包成 {@link BusinessException}({@link ErrCode#AI_PROVIDER_UNAVAILABLE})
 * 让上游 {@code FallbackOrchestrator} 走手填降级。
 */
@Component
public class TempFileSpooler {

  private static final Logger LOG = LoggerFactory.getLogger(TempFileSpooler.class);

  private final Path tmpDir;
  private final long maxSizeBytes;

  public TempFileSpooler(
      @Value("${longfeng.ai.spool.tmp-dir:/var/lib/longfeng/ai-spool}") String tmpDir,
      @Value("${longfeng.ai.spool.max-size-mb:20}") int maxSizeMb) {
    this.tmpDir = Paths.get(tmpDir);
    this.maxSizeBytes = (long) maxSizeMb * 1024L * 1024L;
  }

  @PostConstruct
  void ensureDir() {
    try {
      if (!Files.exists(tmpDir)) {
        Files.createDirectories(tmpDir);
      }
      // 0700 · 仅 owner 可访问（PII 兜底）
      try {
        Files.setPosixFilePermissions(
            tmpDir,
            PosixFilePermissions.fromString("rwx------"));
      } catch (UnsupportedOperationException ignored) {
        // Windows / 非 POSIX 文件系统 · skip
      }
      LOG.info("TempFileSpooler tmpDir ready · path={} · maxSizeBytes={}", tmpDir, maxSizeBytes);
    } catch (IOException ioe) {
      LOG.error("Failed to create tmp-dir · path={}", tmpDir, ioe);
      throw new BusinessException(
          ErrCode.AI_PROVIDER_UNAVAILABLE,
          "msgkey:ai.error.provider_unavailable",
          ioe);
    }
  }

  /**
   * 把 Resource 流式落到本地临时文件 · 立即释放 byte[]（heap 不持有）。
   *
   * @param taskId 用作文件名（{@code {taskId}.jpg}）· 方便 D-AI-Cancel 找到 spool 文件
   * @param image  图片 Resource（来自 file-service Feign 或 OSS presigned）
   * @return 临时文件 path
   * @throws BusinessException 若盘满 / IO 失败 / 超过 max-size
   */
  public Path spool(String taskId, Resource image) {
    if (image == null) {
      throw new BusinessException(
          ErrCode.VALIDATION_FAILED, "msgkey:common.error.validation_failed");
    }
    Path target = tmpDir.resolve(safeFileName(taskId));
    try (InputStream in = image.getInputStream()) {
      // 用 streaming copy · 不一次性 readAllBytes（保护 heap）
      long copied;
      try {
        copied = Files.copy(in, target);
      } catch (FileAlreadyExistsException dup) {
        // 重试场景（D-AI-Cancel 后再发）· 删旧再写
        Files.deleteIfExists(target);
        try (InputStream in2 = image.getInputStream()) {
          copied = Files.copy(in2, target);
        }
      }
      if (copied > maxSizeBytes) {
        Files.deleteIfExists(target);
        throw new BusinessException(
            ErrCode.VALIDATION_FAILED,
            "msgkey:common.error.validation_failed");
      }
      // 0600 · 仅 owner 可读（PII）
      try {
        Files.setPosixFilePermissions(
            target,
            Set.of(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE));
      } catch (UnsupportedOperationException ignored) {
        // Windows · skip
      }
      LOG.debug("spool ok · taskId={} bytes={} path={}", taskId, copied, target);
      return target;
    } catch (IOException ioe) {
      LOG.error("spool failed (盘满 / IO 异常) · taskId={} path={}", taskId, target, ioe);
      // C7 即使失败也确保不落 byte[] in heap
      try {
        Files.deleteIfExists(target);
      } catch (IOException ignored) {
        // best-effort cleanup
      }
      throw new BusinessException(
          ErrCode.AI_PROVIDER_UNAVAILABLE,
          "msgkey:ai.error.provider_unavailable",
          ioe);
    }
  }

  /**
   * 释放 spool 文件 · 应在 try-finally 调用 · 即便上游 LLM 异常也要释放。
   *
   * @param spoolFile {@link #spool(String, Resource)} 返回的 path · null/不存在 = no-op
   */
  public void release(Path spoolFile) {
    if (spoolFile == null) {
      return;
    }
    try {
      Files.deleteIfExists(spoolFile);
      LOG.debug("spool released · path={}", spoolFile);
    } catch (IOException ioe) {
      LOG.warn("spool release failed (best-effort cleanup) · path={}", spoolFile, ioe);
    }
  }

  /** 仅暴露给 test 用 · 检查目录是否存在。 */
  Path tmpDir() {
    return tmpDir;
  }

  /** 防 path traversal · 仅保留字母数字 - _。 */
  private static String safeFileName(String taskId) {
    String safe = taskId == null ? "unknown" : taskId.replaceAll("[^a-zA-Z0-9_-]", "_");
    return safe + ".jpg";
  }
}
