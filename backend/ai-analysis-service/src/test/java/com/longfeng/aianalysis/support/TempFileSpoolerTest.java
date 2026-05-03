package com.longfeng.aianalysis.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

/**
 * Unit tests for {@link TempFileSpooler} · plan §5.S3 出口门禁 C7 红线。
 *
 * <p>覆盖：spool 文件落盘 / release 删除 / 盘满异常 / max-size 超限 / safe filename。
 */
class TempFileSpoolerTest {

  @TempDir Path tmpDir;
  TempFileSpooler spooler;

  @BeforeEach
  void setUp() {
    spooler = new TempFileSpooler(tmpDir.toString(), /* maxSizeMb */ 1);
    spooler.ensureDir();
  }

  @Test
  void spool_writesFileToDisk() throws IOException {
    Resource image = new ByteArrayResource("hello-image-bytes".getBytes());
    Path target = spooler.spool("task-001", image);

    assertThat(target).exists();
    assertThat(Files.readString(target)).isEqualTo("hello-image-bytes");
    assertThat(target.getFileName().toString()).isEqualTo("task-001.jpg");
  }

  @Test
  void release_deletesFile() throws IOException {
    Resource image = new ByteArrayResource("data".getBytes());
    Path target = spooler.spool("task-002", image);
    assertThat(target).exists();

    spooler.release(target);
    assertThat(target).doesNotExist();
  }

  @Test
  void release_nullIsNoOp() {
    spooler.release(null);
    // 没有抛异常即通过
  }

  @Test
  void spool_throwsBusinessException_whenOverMaxSize() {
    byte[] big = new byte[2 * 1024 * 1024]; // 2 MB > 1 MB 上限
    Resource image = new ByteArrayResource(big);
    assertThatThrownBy(() -> spooler.spool("task-big", image))
        .isInstanceOf(BusinessException.class)
        .matches(
            ex -> ((BusinessException) ex).errCode() == ErrCode.VALIDATION_FAILED,
            "errCode VALIDATION_FAILED for size limit");
  }

  @Test
  void spool_safeFileName_stripsDangerousChars() throws IOException {
    Resource image = new ByteArrayResource("x".getBytes());
    Path target = spooler.spool("../../etc/passwd", image);
    // path traversal 必须被 sanitize
    assertThat(target.getFileName().toString())
        .doesNotContain("/")
        .doesNotContain("..")
        .endsWith(".jpg");
  }

  @Test
  void spool_throwsAiProviderUnavailable_whenIoException() {
    // 用一个 always-throw 的 Resource 模拟"盘满 / IO 异常"
    Resource brokenImage =
        new Resource() {
          @Override public InputStream getInputStream() throws IOException {
            throw new IOException("disk full / mock");
          }
          @Override public boolean exists() { return true; }
          @Override public boolean isReadable() { return true; }
          @Override public boolean isOpen() { return false; }
          @Override public boolean isFile() { return false; }
          @Override public java.net.URL getURL() throws IOException { throw new IOException(); }
          @Override public java.net.URI getURI() throws IOException { throw new IOException(); }
          @Override public java.io.File getFile() throws IOException { throw new IOException(); }
          @Override public java.nio.channels.ReadableByteChannel readableChannel() throws IOException {
            throw new IOException();
          }
          @Override public long contentLength() throws IOException { return 0; }
          @Override public long lastModified() throws IOException { return 0; }
          @Override public Resource createRelative(String relativePath) throws IOException {
            throw new IOException();
          }
          @Override public String getFilename() { return "broken"; }
          @Override public String getDescription() { return "broken-resource"; }
        };
    assertThatThrownBy(() -> spooler.spool("task-broken", brokenImage))
        .isInstanceOf(BusinessException.class)
        .matches(
            ex -> ((BusinessException) ex).errCode() == ErrCode.AI_PROVIDER_UNAVAILABLE,
            "errCode AI_PROVIDER_UNAVAILABLE for IO");
  }

  @Test
  void spool_overwriteExisting_works() throws IOException {
    Resource v1 = new ByteArrayResource("v1".getBytes());
    Resource v2 = new ByteArrayResource("v2-content".getBytes());
    Path first = spooler.spool("dup-task", v1);
    Path second = spooler.spool("dup-task", v2);
    assertThat(first).isEqualTo(second);
    assertThat(Files.readString(second)).isEqualTo("v2-content");
  }

  @Test
  void spool_nullImage_throwsValidation() {
    assertThatThrownBy(() -> spooler.spool("task-null", null))
        .isInstanceOf(BusinessException.class)
        .matches(
            ex -> ((BusinessException) ex).errCode() == ErrCode.VALIDATION_FAILED,
            "errCode VALIDATION_FAILED for null image");
  }
}
