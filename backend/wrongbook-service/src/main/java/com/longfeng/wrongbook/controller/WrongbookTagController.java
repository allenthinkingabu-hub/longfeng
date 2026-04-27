package com.longfeng.wrongbook.controller;

import com.longfeng.common.dto.ApiResult;
import com.longfeng.wrongbook.entity.TagTaxonomy;
import com.longfeng.wrongbook.service.WrongItemService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** G-02: exposes GET /wrongbook/tags for active tag taxonomy lookup. */
@RestController
@RequestMapping("/wrongbook")
public class WrongbookTagController {

  private final WrongItemService service;

  public WrongbookTagController(WrongItemService service) {
    this.service = service;
  }

  @Operation(summary = "获取活跃标签列表")
  @GetMapping("/tags")
  public ApiResult<List<Map<String, Object>>> getTags(
      @RequestParam(required = false) String subject) {
    List<TagTaxonomy> tags = service.getActiveTags(subject);
    List<Map<String, Object>> result =
        tags.stream()
            .map(
                t ->
                    Map.<String, Object>of(
                        "code", t.getCode(),
                        "display_name", t.getDisplayName(),
                        "subject", t.getSubject() != null ? t.getSubject() : ""))
            .collect(Collectors.toList());
    return ApiResult.ok(result);
  }
}
