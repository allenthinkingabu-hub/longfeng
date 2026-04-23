package com.longfeng.wrongbook.controller;

import com.longfeng.common.dto.ApiResult;
import com.longfeng.wrongbook.dto.AddTagReq;
import com.longfeng.wrongbook.dto.ConfirmImageReq;
import com.longfeng.wrongbook.dto.CreateWrongItemReq;
import com.longfeng.wrongbook.dto.SetDifficultyReq;
import com.longfeng.wrongbook.dto.UpdateWrongItemReq;
import com.longfeng.wrongbook.dto.WrongItemImageVO;
import com.longfeng.wrongbook.dto.WrongItemPageVO;
import com.longfeng.wrongbook.dto.WrongItemVO;
import com.longfeng.wrongbook.service.WrongItemService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wrongbook/items")
public class WrongItemController {

  private final WrongItemService service;

  public WrongItemController(WrongItemService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ApiResult<WrongItemVO>> create(
      @Valid @RequestBody CreateWrongItemReq req,
      @RequestHeader(name = "X-Request-Id", required = false) String requestId) {
    WrongItemVO vo = service.create(req, requestId);
    return ResponseEntity.created(URI.create("/wrongbook/items/" + vo.id()))
        .header("X-Request-Id", requestId == null ? "" : requestId)
        .body(ApiResult.ok(vo));
  }

  @GetMapping("/{id}")
  public ResponseEntity<ApiResult<WrongItemVO>> get(@PathVariable Long id) {
    return service
        .findById(id)
        .map(vo -> ResponseEntity.ok(ApiResult.ok(vo)))
        .orElseThrow(() -> new WrongItemService.NotFoundException("wrong_item not found: " + id));
  }

  @GetMapping
  public ApiResult<WrongItemPageVO> page(
      @RequestParam(required = false) String subject,
      @RequestParam(required = false) Short status,
      @RequestParam(required = false) Long studentId,
      @RequestParam(required = false) Long cursor,
      @RequestParam(defaultValue = "20") int size) {
    return ApiResult.ok(service.page(subject, status, studentId, cursor, size));
  }

  @PatchMapping("/{id}")
  public ApiResult<WrongItemVO> update(
      @PathVariable Long id,
      @Valid @RequestBody UpdateWrongItemReq req,
      @RequestHeader(name = "X-Request-Id", required = false) String requestId) {
    return ApiResult.ok(service.update(id, req, requestId));
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @PathVariable Long id,
      @RequestHeader(name = "X-Request-Id", required = false) String requestId) {
    service.delete(id, requestId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/tags")
  public ApiResult<Void> addTag(
      @PathVariable Long id,
      @Valid @RequestBody AddTagReq req,
      @RequestHeader(name = "X-Request-Id", required = false) String requestId) {
    service.addTag(id, req, requestId);
    return ApiResult.ok(null);
  }

  @DeleteMapping("/{id}/tags/{tagCode}")
  public ResponseEntity<Void> removeTag(
      @PathVariable Long id,
      @PathVariable String tagCode,
      @RequestHeader(name = "X-Request-Id", required = false) String requestId) {
    service.removeTag(id, tagCode, requestId);
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/{id}/images")
  public ApiResult<WrongItemImageVO> confirmImage(
      @PathVariable Long id,
      @Valid @RequestBody ConfirmImageReq req,
      @RequestHeader(name = "X-Request-Id", required = false) String requestId) {
    return ApiResult.ok(service.confirmImage(id, req, requestId));
  }

  @PostMapping("/{id}/difficulty")
  public ApiResult<WrongItemVO> setDifficulty(
      @PathVariable Long id,
      @Valid @RequestBody SetDifficultyReq req,
      @RequestHeader(name = "X-Request-Id", required = false) String requestId) {
    return ApiResult.ok(service.setDifficulty(id, req, requestId));
  }
}
