package com.longfeng.fileservice.controller;

import com.longfeng.common.dto.ApiResult;
import com.longfeng.fileservice.config.StorageProperties;
import com.longfeng.fileservice.dto.CompleteResp;
import com.longfeng.fileservice.dto.DownloadResp;
import com.longfeng.fileservice.dto.PresignReq;
import com.longfeng.fileservice.dto.PresignResp;
import com.longfeng.fileservice.entity.FileAsset;
import com.longfeng.fileservice.service.SignatureService;
import com.longfeng.fileservice.service.SignatureService.PresignResponse;
import com.longfeng.fileservice.service.UploadService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** file-service REST 入口 · §10.6 产出物 UploadController · 3 endpoints. */
@RestController
public class UploadController {

  private static final String OWNER_HEADER = "X-User-Id";

  private final SignatureService signatureService;
  private final UploadService uploadService;
  private final StorageProperties props;

  public UploadController(
      SignatureService signatureService, UploadService uploadService, StorageProperties props) {
    this.signatureService = signatureService;
    this.uploadService = uploadService;
    this.props = props;
  }

  /** POST /files/presign · SC-11.AC-1. */
  @PostMapping("/files/presign")
  public ApiResult<PresignResp> presign(
      @Valid @RequestBody PresignReq req,
      @RequestHeader(value = OWNER_HEADER, defaultValue = "0") Long ownerId) {
    PresignResponse r =
        signatureService.presignUpload(ownerId, req.filename(), req.mime(), req.size());
    return ApiResult.ok(new PresignResp(r.uploadUrl(), r.fileKey(), r.expiresAt(), r.ttlSeconds()));
  }

  /** POST /files/complete/{fileKey} · SC-11.AC-2. */
  @PostMapping("/files/complete/{fileKey}")
  public ApiResult<CompleteResp> complete(@PathVariable("fileKey") String fileKey) {
    FileAsset a = uploadService.complete(fileKey);
    return ApiResult.ok(
        new CompleteResp(a.getObjectKey(), a.getStatus(), a.getVariantThumbKey(), a.getVariantMediumKey()));
  }

  /** GET /files/download/{fileKey}?variant=thumb|medium|original · SC-11.AC-3. */
  @GetMapping("/files/download/{fileKey}")
  public ApiResult<DownloadResp> download(
      @PathVariable("fileKey") String fileKey,
      @RequestParam(value = "variant", required = false) String variant) {
    String url = signatureService.presignDownload(fileKey, variant);
    return ApiResult.ok(new DownloadResp(url, variant == null ? "medium" : variant, props.presignTtlSeconds()));
  }
}
