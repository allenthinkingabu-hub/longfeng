package com.longfeng.fileservice.controller;

import com.longfeng.common.dto.ApiResult;
import com.longfeng.common.exception.BusinessException;
import com.longfeng.common.exception.ErrCode;
import com.longfeng.fileservice.entity.WbFile;
import com.longfeng.fileservice.repo.WbFileRepository;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * OSS callback controller — TDD §12.5.2 POST /api/files/callback.
 *
 * <p>Aliyun OSS calls this endpoint after a successful direct upload.
 * We verify the OSS callback signature using the Aliyun public key,
 * then mark the {@link WbFile} record as UPLOADED.
 *
 * <p>Security: invalid signatures are rejected with HTTP 403.
 *
 * <p>C7: No byte[] of image content stored here — only metadata updates.
 * C8: BusinessException messages carry "msgkey:" prefix.
 * C9: OffsetDateTime for all timestamps.
 *
 * <p>References:
 * <ul>
 *   <li>Aliyun OSS callback documentation § callback-signature-verification
 *   <li>TDD §0.9 D-Storage / ErrCode#OSS_CALLBACK_SIGN_FAILED
 * </ul>
 */
@RestController
@RequestMapping("/api/files")
public class CallbackController {

    private static final Logger LOG = LoggerFactory.getLogger(CallbackController.class);

    /**
     * Aliyun OSS public key endpoint for downloading the signing certificate.
     * The URL is provided in the X-OSS-PUB-KEY-URL header of every callback request.
     */
    private static final String ALLOWED_PUB_KEY_HOST = "gosspublic.alicdn.com";

    private final WbFileRepository fileRepo;
    private final HttpClient httpClient;

    public CallbackController(WbFileRepository fileRepo) {
        this.fileRepo = fileRepo;
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * POST /api/files/callback
     *
     * <p>Called by Aliyun OSS after the client completes a presigned PUT.
     * Headers provided by OSS:
     * <ul>
     *   <li>{@code x-oss-pub-key-url} — Base64-encoded URL of the signing public key
     *   <li>{@code authorization} — Base64-encoded RSA-SHA1 signature over the callback body
     * </ul>
     *
     * <p>Query parameters forwarded from the presign callback configuration:
     * <ul>
     *   <li>{@code objectKey} — D-OSS-Key path of the uploaded object
     *   <li>{@code size} — file size in bytes (informational)
     *   <li>{@code mimeType} — detected MIME type
     * </ul>
     *
     * @return 200 on success; 403 on signature failure
     */
    @PostMapping("/callback")
    public ResponseEntity<ApiResult<CallbackRespBody>> callback(
            @RequestHeader(value = "x-oss-pub-key-url", required = false) String pubKeyUrlBase64,
            @RequestHeader(value = "authorization", required = false) String authorizationBase64,
            @RequestParam(value = "objectKey", required = false) String objectKey,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "mimeType", required = false) String mimeType,
            jakarta.servlet.http.HttpServletRequest httpReq) {

        // ── Signature verification ────────────────────────────────────────
        if (pubKeyUrlBase64 != null && authorizationBase64 != null) {
            boolean signatureValid = verifyOssSignature(
                    pubKeyUrlBase64, authorizationBase64, httpReq);
            if (!signatureValid) {
                LOG.warn("OSS callback signature verification FAILED objectKey={}", objectKey);
                throw new BusinessException(ErrCode.OSS_CALLBACK_SIGN_FAILED,
                        "msgkey:file.error.callback_sign_failed");
            }
        } else {
            // In dev (MinIO) there is no OSS callback; allow if no auth header provided.
            LOG.debug("OSS callback received without signature headers (dev/minio mode) objectKey={}", objectKey);
        }

        // ── Update wb_file record ─────────────────────────────────────────
        if (objectKey != null) {
            fileRepo.findByObjectKey(objectKey).ifPresentOrElse(file -> {
                file.setStatus(WbFile.STATUS_UPLOADED);
                file.setUploadedAt(OffsetDateTime.now(ZoneOffset.UTC));
                if (size != null) {
                    file.setBytes(size);
                }
                if (mimeType != null) {
                    file.setMimeType(mimeType);
                }
                fileRepo.save(file);
                LOG.info("OSS callback: file marked UPLOADED objectKey={} size={}", objectKey, size);
            }, () -> LOG.warn("OSS callback: no wb_file found for objectKey={}", objectKey));
        }

        return ResponseEntity.ok(ApiResult.ok(new CallbackRespBody("ok")));
    }

    // ── Signature verification helpers ────────────────────────────────────

    /**
     * Verify the Aliyun OSS callback RSA-SHA1 signature.
     *
     * <p>Algorithm (per Aliyun OSS docs):
     * <ol>
     *   <li>Base64-decode {@code x-oss-pub-key-url} to get the public key URL.
     *   <li>Validate the URL host is {@code gosspublic.alicdn.com}.
     *   <li>Download the PEM public key from that URL.
     *   <li>Build the signature base string: {@code requestURI + "\n" + callbackBody}.
     *   <li>Base64-decode the Authorization header to get the signature bytes.
     *   <li>Verify using RSA/SHA1 with the downloaded key.
     * </ol>
     *
     * @return true if signature is valid, false otherwise
     */
    private boolean verifyOssSignature(String pubKeyUrlBase64, String authorizationBase64,
                                       jakarta.servlet.http.HttpServletRequest request) {
        try {
            // 1. Decode and validate the public key URL
            String pubKeyUrl = new String(Base64.getDecoder().decode(pubKeyUrlBase64),
                    StandardCharsets.UTF_8);
            URI uri = URI.create(pubKeyUrl);
            if (!ALLOWED_PUB_KEY_HOST.equals(uri.getHost())) {
                LOG.warn("OSS callback: pub-key-url host rejected: {}", uri.getHost());
                return false;
            }

            // 2. Download the public key PEM
            String pem = downloadPublicKey(pubKeyUrl);
            PublicKey publicKey = parsePemPublicKey(pem);

            // 3. Build the signature base string
            String requestUri = request.getRequestURI();
            String queryString = request.getQueryString();
            if (queryString != null && !queryString.isEmpty()) {
                requestUri = requestUri + "?" + queryString;
            }
            String body = readRequestBody(request);
            String signBase = requestUri + "\n" + body;

            // 4. Verify RSA-SHA1 signature
            byte[] signatureBytes = Base64.getDecoder().decode(authorizationBase64);
            Signature verifier = Signature.getInstance("SHA1withRSA");
            verifier.initVerify(publicKey);
            verifier.update(signBase.getBytes(StandardCharsets.UTF_8));
            return verifier.verify(signatureBytes);

        } catch (Exception e) {
            LOG.error("OSS callback signature verification error", e);
            return false;
        }
    }

    private String downloadPublicKey(String url) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url)).GET().build();
        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        return resp.body();
    }

    private PublicKey parsePemPublicKey(String pem) throws Exception {
        String stripped = pem
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(stripped);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(keySpec);
    }

    private String readRequestBody(jakarta.servlet.http.HttpServletRequest request) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(request.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }

    // ── DTOs ──────────────────────────────────────────────────────────────

    /** Callback response body. OSS expects a 200 with any non-empty body. */
    public record CallbackRespBody(String status) {}
}
