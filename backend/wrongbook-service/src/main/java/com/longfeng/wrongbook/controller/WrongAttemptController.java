package com.longfeng.wrongbook.controller;

import com.longfeng.common.dto.ApiResult;
import com.longfeng.wrongbook.dto.CreateAttemptReq;
import com.longfeng.wrongbook.dto.WrongAttemptVO;
import com.longfeng.wrongbook.service.WrongAttemptService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/wrongbook/items/{id}/attempts")
public class WrongAttemptController {

  private final WrongAttemptService service;

  public WrongAttemptController(WrongAttemptService service) {
    this.service = service;
  }

  @PostMapping
  public ResponseEntity<ApiResult<WrongAttemptVO>> create(
      @PathVariable Long id, @Valid @RequestBody CreateAttemptReq req) {
    WrongAttemptVO vo = service.create(id, req);
    return ResponseEntity.status(201).body(ApiResult.ok(vo));
  }

  @GetMapping
  public ApiResult<List<WrongAttemptVO>> list(
      @PathVariable Long id, @RequestParam(defaultValue = "20") int size) {
    return ApiResult.ok(service.list(id, size));
  }
}
