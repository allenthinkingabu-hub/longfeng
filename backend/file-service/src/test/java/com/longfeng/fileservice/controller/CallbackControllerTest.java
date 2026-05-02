package com.longfeng.fileservice.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import com.longfeng.fileservice.entity.WbFile;
import com.longfeng.fileservice.repo.WbFileRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Unit tests for {@link CallbackController}.
 *
 * <p>Uses inline Mockito stubs + MockHttpServletRequest (no @SpringBootTest).
 *
 * <p>Covers:
 * <ul>
 *   <li>No signature headers (dev/minio mode) → 200 + file marked UPLOADED
 *   <li>Invalid/malformed signature headers → BusinessException OSS_CALLBACK_SIGN_FAILED (403)
 *   <li>objectKey not found in repo → still returns 200 (warn + no-op)
 * </ul>
 *
 * <p>Full Aliyun RSA-SHA1 signature verification is tested via the OSS E2E IT;
 * here we test the structural logic.
 */
@ExtendWith(MockitoExtension.class)
class CallbackControllerTest {

    @Mock WbFileRepository fileRepo;

    private CallbackController controller;

    @BeforeEach
    void setUp() {
        controller = new CallbackController(fileRepo);
    }

    // ── No signature headers (dev/MinIO mode) ─────────────────────────────

    @Test
    @DisplayName("callback with no auth headers (dev mode) · marks file UPLOADED and returns 200")
    void callback_noAuthHeaders_devMode_marksUploaded() {
        // Given
        String objectKey = "wrongbook/0/202601/100/999_photo.jpg";
        WbFile pendingFile = makePendingFile(objectKey);
        when(fileRepo.findByObjectKey(objectKey)).thenReturn(Optional.of(pendingFile));
        when(fileRepo.save(any(WbFile.class))).thenAnswer(inv -> inv.getArgument(0));

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/files/callback");
        request.setQueryString("objectKey=" + objectKey + "&size=4500000&mimeType=image%2Fjpeg");

        // When
        var response = controller.callback(null, null, objectKey, 4_500_000L, "image/jpeg", request);

        // Then
        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(0);

        ArgumentCaptor<WbFile> captor = ArgumentCaptor.forClass(WbFile.class);
        verify(fileRepo).save(captor.capture());
        WbFile saved = captor.getValue();
        assertThat(saved.getStatus()).isEqualTo(WbFile.STATUS_UPLOADED);
        assertThat(saved.getUploadedAt()).isNotNull();
        assertThat(saved.getBytes()).isEqualTo(4_500_000L);
        assertThat(saved.getMimeType()).isEqualTo("image/jpeg");
    }

    @Test
    @DisplayName("callback with no auth headers and objectKey not found · returns 200 no-op")
    void callback_noAuthHeaders_fileNotFound_returnsOkNoOp() {
        when(fileRepo.findByObjectKey(any())).thenReturn(Optional.empty());

        MockHttpServletRequest request = new MockHttpServletRequest();
        var response = controller.callback(null, null, "wrongbook/0/202601/0/notfound.jpg", null, null, request);

        assertThat(response).isNotNull();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(0);
        verify(fileRepo, never()).save(any());
    }

    @Test
    @DisplayName("callback with null objectKey · returns 200 no-op (no file lookup)")
    void callback_nullObjectKey_returnsOkNoOp() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        var response = controller.callback(null, null, null, null, null, request);

        assertThat(response).isNotNull();
        assertThat(response.getBody().code()).isEqualTo(0);
        verify(fileRepo, never()).findByObjectKey(any());
    }

    // ── Invalid signature (wrong auth header format) ──────────────────────

    @Test
    @DisplayName("callback with invalid base64 pub-key-url · throws BusinessException OSS_CALLBACK_SIGN_FAILED")
    void callback_invalidPubKeyUrl_throwsSignFailed() {
        // Provide garbage base64 that decodes to a non-allowed URL host
        // Base64 of "https://evil.example.com/key.pem"
        String evilUrlBase64 = java.util.Base64.getEncoder()
                .encodeToString("https://evil.example.com/key.pem".getBytes());
        String fakeAuthBase64 = java.util.Base64.getEncoder()
                .encodeToString("fake-signature".getBytes());

        MockHttpServletRequest request = new MockHttpServletRequest();

        BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> controller.callback(evilUrlBase64, fakeAuthBase64,
                        "wrongbook/0/202601/0/photo.jpg", null, null, request));

        assertThat(ex.errCode()).isEqualTo(ErrCode.OSS_CALLBACK_SIGN_FAILED);
        assertThat(ex.getMessage()).startsWith("msgkey:");
    }

    @Test
    @DisplayName("callback with malformed base64 auth header · throws BusinessException OSS_CALLBACK_SIGN_FAILED")
    void callback_malformedBase64Auth_throwsSignFailed() {
        // pub-key-url is a valid URL host but auth base64 is not a real RSA signature
        String pubKeyBase64 = java.util.Base64.getEncoder()
                .encodeToString("https://gosspublic.alicdn.com/callback-signature-key".getBytes());
        // auth is valid base64 but not a real RSA signature — verify() will fail
        String badAuthBase64 = java.util.Base64.getEncoder()
                .encodeToString("not-a-valid-rsa-signature".getBytes());

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/files/callback");

        // The controller will try to download the key from gosspublic.alicdn.com — this will
        // fail with a network error in test, which causes verifyOssSignature() to return false,
        // which triggers OSS_CALLBACK_SIGN_FAILED.
        BusinessException ex = org.junit.jupiter.api.Assertions.assertThrows(
                BusinessException.class,
                () -> controller.callback(pubKeyBase64, badAuthBase64,
                        "wrongbook/0/202601/0/photo.jpg", null, null, request));

        assertThat(ex.errCode()).isEqualTo(ErrCode.OSS_CALLBACK_SIGN_FAILED);
        assertThat(ex.getMessage()).startsWith("msgkey:");
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private WbFile makePendingFile(String objectKey) {
        WbFile f = new WbFile();
        f.setId(1L);
        f.setStudentId(100L);
        f.setTenantId(0L);
        f.setObjectKey(objectKey);
        f.setStatus(WbFile.STATUS_PENDING);
        return f;
    }
}
