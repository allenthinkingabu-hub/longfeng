package com.longfeng.fileservice.exception;

import com.longfeng.common.dto.ApiResult;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** file-service 错误码映射 · 落地计划 §10.8 V-S6-XX 对应 HTTP 状态. */
@RestControllerAdvice(basePackages = "com.longfeng.fileservice")
public class FileServiceExceptionHandler {

  @ExceptionHandler(MimeNotAllowedException.class)
  public ResponseEntity<ApiResult<Void>> mime(MimeNotAllowedException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResult.fail(40001, "MIME_NOT_ALLOWED: " + e.mime()));
  }

  @ExceptionHandler(OversizeException.class)
  public ResponseEntity<ApiResult<Void>> oversize(OversizeException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResult.fail(40002, "FILE_TOO_LARGE: " + e.size() + " > " + e.max()));
  }

  @ExceptionHandler(FileNotFoundException.class)
  public ResponseEntity<ApiResult<Void>> notFound(FileNotFoundException e) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(ApiResult.fail(40401, "FILE_NOT_FOUND: " + e.fileKey()));
  }

  @ExceptionHandler(VirusDetectedException.class)
  public ResponseEntity<ApiResult<Void>> virus(VirusDetectedException e) {
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
        .body(ApiResult.fail(42201, "VIRUS_DETECTED: " + e.fileKey()));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResult<Void>> validation(MethodArgumentNotValidException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResult.fail(40000, "INVALID_ARG: " + e.getBindingResult().getAllErrors()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ApiResult<Void>> illegalArg(IllegalArgumentException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(ApiResult.fail(40003, "INVALID_ARG: " + e.getMessage()));
  }
}
