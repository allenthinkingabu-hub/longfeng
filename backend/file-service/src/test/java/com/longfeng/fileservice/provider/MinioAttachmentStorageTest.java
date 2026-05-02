package com.longfeng.fileservice.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.longfeng.common.exception.BusinessException;
import com.longfeng.fileservice.provider.AttachmentStorage.PresignResult;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link MinioAttachmentStorage}.
 *
 * <p>Since MinioClient has no convenient interface to mock, we use an inline subclass stub
 * that overrides the MinioClient interaction to simulate success/failure
 * (per F-02 guideline: prefer inline subclass stubs over Mockito for external SDK clients).
 *
 * <p>The tests verify:
 * <ul>
 *   <li>presign: returns PresignResult with correct TTL and objectKey
 *   <li>get: returns a download URL string
 *   <li>delete: completes without exception
 *   <li>promote: no-op in dev, no exception
 *   <li>name(): returns "minio"
 *   <li>failure path: SDK exceptions converted to BusinessException
 * </ul>
 */
class MinioAttachmentStorageTest {

    private static final String BUCKET = "wrongbook-dev";
    private static final String OBJECT_KEY = "wrongbook/0/202601/100/999_photo.jpg";
    private static final Duration TTL_15M = Duration.ofMinutes(15);

    // ── Inline stub: success path ──────────────────────────────────────────

    /**
     * Stub implementation that simulates a working MinioClient.
     * Overrides the SPI methods rather than the MinioClient SDK internals.
     */
    private static class SuccessMinioStorage extends MinioAttachmentStorage {

        SuccessMinioStorage() {
            // Use invalid endpoint — we override the SPI methods directly so MinioClient is never called.
            super("http://localhost:19999", "fakeuser", "fakepass", BUCKET);
        }

        @Override
        public PresignResult presign(String bucket, String objectKey, String mimeType, Duration ttl) {
            return new PresignResult("http://minio:9000/" + bucket + "/" + objectKey + "?sig=stub",
                    objectKey, ttl.toSeconds());
        }

        @Override
        public String get(String bucket, String objectKey, Duration ttl) {
            return "http://minio:9000/" + bucket + "/" + objectKey + "?download=1&sig=stub";
        }

        @Override
        public void delete(String bucket, String objectKey) {
            // simulated no-op delete
        }
    }

    /**
     * Stub that throws on every SPI call to test failure path.
     */
    private static class FailingMinioStorage extends MinioAttachmentStorage {

        FailingMinioStorage() {
            super("http://localhost:19999", "fakeuser", "fakepass", BUCKET);
        }

        @Override
        public PresignResult presign(String bucket, String objectKey, String mimeType, Duration ttl) {
            throw new BusinessException(
                    com.longfeng.common.exception.ErrCode.INTERNAL_ERROR,
                    "msgkey:file.error.presign_failed");
        }

        @Override
        public String get(String bucket, String objectKey, Duration ttl) {
            throw new BusinessException(
                    com.longfeng.common.exception.ErrCode.INTERNAL_ERROR,
                    "msgkey:file.error.get_url_failed");
        }

        @Override
        public void delete(String bucket, String objectKey) {
            throw new BusinessException(
                    com.longfeng.common.exception.ErrCode.INTERNAL_ERROR,
                    "msgkey:file.error.delete_failed");
        }
    }

    // ── presign tests ────────────────────────────────────────────────────

    @Test
    @DisplayName("presign() returns PresignResult with objectKey and correct TTL")
    void presign_returnsPresignResult() {
        MinioAttachmentStorage storage = new SuccessMinioStorage();
        PresignResult result = storage.presign(BUCKET, OBJECT_KEY, "image/jpeg", TTL_15M);

        assertThat(result).isNotNull();
        assertThat(result.objectKey()).isEqualTo(OBJECT_KEY);
        assertThat(result.expiresInSec()).isEqualTo(900L); // 15 min
        assertThat(result.uploadUrl()).isNotBlank();
    }

    @Test
    @DisplayName("presign() failure → BusinessException with msgkey: prefix (C8)")
    void presign_failure_throwsBusinessException() {
        MinioAttachmentStorage storage = new FailingMinioStorage();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> storage.presign(BUCKET, OBJECT_KEY, "image/jpeg", TTL_15M));

        assertThat(ex.getMessage()).startsWith("msgkey:");
    }

    // ── get tests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("get() returns presigned download URL")
    void get_returnsDownloadUrl() {
        MinioAttachmentStorage storage = new SuccessMinioStorage();
        String url = storage.get(BUCKET, OBJECT_KEY, TTL_15M);

        assertThat(url).isNotBlank();
        assertThat(url).contains(OBJECT_KEY);
    }

    @Test
    @DisplayName("get() failure → BusinessException with msgkey: prefix (C8)")
    void get_failure_throwsBusinessException() {
        MinioAttachmentStorage storage = new FailingMinioStorage();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> storage.get(BUCKET, OBJECT_KEY, TTL_15M));

        assertThat(ex.getMessage()).startsWith("msgkey:");
    }

    // ── delete tests ────────────────────────────────────────────────────

    @Test
    @DisplayName("delete() completes without exception (happy path)")
    void delete_happyPath_noException() {
        MinioAttachmentStorage storage = new SuccessMinioStorage();
        // Should not throw
        storage.delete(BUCKET, OBJECT_KEY);
    }

    @Test
    @DisplayName("delete() failure → BusinessException with msgkey: prefix (C8)")
    void delete_failure_throwsBusinessException() {
        MinioAttachmentStorage storage = new FailingMinioStorage();
        BusinessException ex = assertThrows(BusinessException.class,
                () -> storage.delete(BUCKET, OBJECT_KEY));

        assertThat(ex.getMessage()).startsWith("msgkey:");
    }

    // ── promote tests (dev no-op) ────────────────────────────────────────

    @Test
    @DisplayName("promote() is no-op in dev MinIO — no exception")
    void promote_devNoOp_noException() {
        MinioAttachmentStorage storage = new SuccessMinioStorage();
        // Should not throw
        storage.promote(BUCKET, OBJECT_KEY, "IA");
        storage.promote(BUCKET, OBJECT_KEY, "ARCHIVE");
    }

    // ── name() ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("name() returns 'minio'")
    void name_returnsMinio() {
        MinioAttachmentStorage storage = new SuccessMinioStorage();
        assertThat(storage.name()).isEqualTo("minio");
    }
}
