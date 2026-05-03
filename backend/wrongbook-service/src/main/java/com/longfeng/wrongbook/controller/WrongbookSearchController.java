package com.longfeng.wrongbook.controller;

import com.longfeng.common.dto.ApiResult;
import com.longfeng.wrongbook.dto.SearchReq;
import com.longfeng.wrongbook.dto.WrongItemVO;
import com.longfeng.wrongbook.service.WrongbookSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Hybrid search endpoint for wrongbook items.
 *
 * <p>POST /api/wb/questions/search — RRF fusion of pgvector semantic search + pg_trgm keyword search.
 * Gateway routes /api/wb/** → wrongbook-service (plan §3 routing table).
 */
@RestController
@RequestMapping("/wrongbook/questions/search")
@Tag(name = "wrongbook-search", description = "Hybrid search · pgvector + pg_trgm · RRF ranking")
public class WrongbookSearchController {

  private final WrongbookSearchService searchService;

  public WrongbookSearchController(WrongbookSearchService searchService) {
    this.searchService = searchService;
  }

  @Operation(summary = "Hybrid search (pgvector + pg_trgm, RRF fusion)")
  @PostMapping
  public ApiResult<List<WrongItemVO>> search(@RequestBody SearchReq req) {
    int topN = req.topN() != null ? req.topN() : 20;
    List<WrongItemVO> results = searchService.hybridSearch(
        req.query(),
        req.queryVector(),
        req.studentId(),
        req.subject(),
        topN);
    return ApiResult.ok(results);
  }
}
