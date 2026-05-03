package com.longfeng.reviewplan.controller;

import com.longfeng.common.dto.ApiResult;
import com.longfeng.reviewplan.dto.BatchResetByIdsReq;
import com.longfeng.reviewplan.dto.BatchResetByIdsResp;
import com.longfeng.reviewplan.dto.CompleteReviewReq;
import com.longfeng.reviewplan.dto.CompleteReviewResp;
import com.longfeng.reviewplan.dto.DayViewResp;
import com.longfeng.reviewplan.dto.ListReviewPlanResp;
import com.longfeng.reviewplan.dto.ReviewPlanDto;
import com.longfeng.reviewplan.dto.ReviewStatsResp;
import com.longfeng.reviewplan.entity.ReviewPlan;
import com.longfeng.reviewplan.feign.CalendarFeignClient;
import com.longfeng.reviewplan.feign.CalendarFeignClientFallback;
import com.longfeng.reviewplan.service.ReviewPlanService;
import com.longfeng.reviewplan.service.ReviewStatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 复习计划 REST 入口 · 5 端点 · SC-07/08/09/10. */
@RestController
@Tag(name = "review-plans", description = "复习计划管理")
public class ReviewPlanController {

  private static final String USER_ID_HEADER = "X-User-Id";
  private static final String TIMEZONE_HEADER = "X-User-Timezone";
  private static final String DEFAULT_TZ = "Asia/Shanghai";
  private static final String ADMIN_HEADER = "X-Admin";

  private final ReviewPlanService service;
  private final ReviewStatsService statsService;
  private final CalendarFeignClient calendarFeign;

  @Autowired
  public ReviewPlanController(
      ReviewPlanService service,
      ReviewStatsService statsService,
      CalendarFeignClient calendarFeign) {
    this.service = service;
    this.statsService = statsService;
    this.calendarFeign = calendarFeign;
  }

  /** GET /review-plans?date= · 日视图 · SC-07 + SC-10 calendar 节点. */
  @Operation(summary = "日视图 — 当日 due 节点 + calendar_node（SC-07 + SC-10）")
  @ApiResponse(responseCode = "200", description = "日视图")
  @ApiResponse(
      responseCode = "503",
      description = "calendar-platform core-service 不可用且 cache 过期")
  @GetMapping("/review-plans")
  public ApiResult<DayViewResp> dayView(
      @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
      @RequestParam(value = "subject", required = false) String subject,
      @RequestHeader(value = USER_ID_HEADER, defaultValue = "0") Long userId,
      @RequestHeader(value = TIMEZONE_HEADER, required = false) String timezone) {

    ZoneId zone = resolveZone(timezone);
    Instant start = date.atStartOfDay(zone).toInstant();
    Instant end = date.plusDays(1).atStartOfDay(zone).toInstant();

    List<ReviewPlan> plans = service.getDayPlans(userId, start, end);
    List<ReviewPlanDto> items =
        plans.stream().map(ReviewPlanDto::from).collect(Collectors.toList());

    // SC-10 · CalendarFeignClient + Sentinel + Caffeine fallback (fallback returns empty on miss)
    List<Map<String, Object>> calendarNodes;
    String source;
    try {
      List<Map<String, Object>> nodes = calendarFeign.getNodes(date);
      calendarNodes = nodes != null ? nodes : Collections.emptyList();
      source = calendarNodes.isEmpty() ? "cache:miss" : "fresh";
    } catch (Exception e) {
      calendarNodes = Collections.emptyList();
      source = "unavailable";
    }

    return ApiResult.ok(new DayViewResp(items, calendarNodes, source));
  }

  /**
   * BE-13 · GET /review-plans/list · cursor 翻页 list.
   *
   * <p>spec: {@code userId} (required) · {@code status} opt (active|mastered) · {@code cursor} opt
   * (numeric id) · {@code limit} default 20.
   *
   * <p>排序: created_at DESC + id DESC（cursor stable）.
   */
  @Operation(summary = "list cursor 翻页（BE-13 · 按 user + status filter）")
  @ApiResponse(responseCode = "200", description = "list 成功")
  @GetMapping("/review-plans/list")
  public ApiResult<ListReviewPlanResp> listByCursor(
      @RequestParam("user_id") Long userIdParam,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "cursor", required = false) String cursor,
      @RequestParam(value = "limit", required = false, defaultValue = "20") int limit) {
    int statusOpt = parseStatusOpt(status);
    Long cursorId = parseCursor(cursor);
    List<ReviewPlan> rows = service.listByCursor(userIdParam, statusOpt, cursorId, limit);
    List<ReviewPlanDto> items = rows.stream().map(ReviewPlanDto::from).collect(Collectors.toList());
    String nextCursor =
        rows.size() < limit || rows.isEmpty()
            ? null
            : String.valueOf(rows.get(rows.size() - 1).getId());
    return ApiResult.ok(new ListReviewPlanResp(items, nextCursor));
  }

  /** GET /review-plans/{id} · 单节点详情. */
  @Operation(summary = "单节点详情")
  @ApiResponse(responseCode = "200", description = "节点详情")
  @ApiResponse(responseCode = "404", description = "不存在或已 mastered")
  @GetMapping("/review-plans/{id}")
  public ApiResult<ReviewPlanDto> getById(
      @Parameter(description = "节点 ID (Snowflake)") @PathVariable Long id) {
    ReviewPlan plan = service.getById(id);
    return ApiResult.ok(ReviewPlanDto.from(plan));
  }

  /** POST /review-plans/{id}/complete · 复习主循环 · SC-08.AC-1. */
  @Operation(summary = "完成复习 · SM-2 + 乐观锁 + Outbox（SC-08.AC-1）")
  @ApiResponse(responseCode = "200", description = "complete 成功")
  @ApiResponse(responseCode = "400", description = "INVALID_QUALITY")
  @ApiResponse(responseCode = "404", description = "PLAN_NOT_FOUND")
  @ApiResponse(responseCode = "409", description = "乐观锁冲突")
  @ApiResponse(responseCode = "410", description = "PLAN_MASTERED")
  @PostMapping("/review-plans/{id}/complete")
  public ApiResult<CompleteReviewResp> complete(
      @Parameter(description = "节点 ID (Snowflake)") @PathVariable Long id,
      @Valid @RequestBody CompleteReviewReq req) {
    ReviewPlanService.CompleteResult r = service.complete(id, req.quality());
    return ApiResult.ok(
        new CompleteReviewResp(
            String.valueOf(r.planId()),
            r.nextReviewAt().toString(),
            r.easeFactorAfter(),
            r.mastered()));
  }

  /** POST /review-plans/batch-reset · admin · 学期初清空 · ROLE_ADMIN. */
  @Operation(summary = "学期初清空 · admin only（需 X-Admin: true header）")
  @ApiResponse(responseCode = "200", description = "已接受")
  @ApiResponse(responseCode = "403", description = "FORBIDDEN")
  @PostMapping("/review-plans/batch-reset")
  public ApiResult<Void> batchReset(
      @RequestHeader(value = ADMIN_HEADER, required = false) String admin,
      @RequestHeader(value = USER_ID_HEADER, defaultValue = "0") Long userId) {
    if (!"true".equalsIgnoreCase(admin)) {
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN, "X-Admin header required");
    }
    // 学期初清空：软删该学生所有 active plan（仅影响当前用户）
    service.batchReset(userId);
    return ApiResult.ok(null);
  }

  /**
   * BE-13 · POST /review-plans/batch-reset-by-ids · 按 plan_ids 批量重置 · 返 reset_count.
   *
   * <p>与 {@link #batchReset} 区分：旧端点是 admin 学期初清空整个学生，新端点是按指定 plan_ids 重置.
   */
  @Operation(summary = "按 plan_ids 批量重置（BE-13）")
  @ApiResponse(responseCode = "200", description = "batch-reset-by-ids 成功")
  @ApiResponse(responseCode = "400", description = "plan_ids 为空")
  @PostMapping("/review-plans/batch-reset-by-ids")
  public ApiResult<BatchResetByIdsResp> batchResetByIds(@Valid @RequestBody BatchResetByIdsReq req) {
    List<Long> ids =
        req.planIds().stream()
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .map(Long::parseLong)
            .collect(Collectors.toList());
    int count = service.batchResetByIds(ids);
    return ApiResult.ok(new BatchResetByIdsResp(count));
  }

  /** GET /review-stats · 学情聚合 · SC-09.AC-1. */
  @Operation(summary = "学情聚合 — 日视图趋势 + Top N 薄弱项（SC-09.AC-1）")
  @ApiResponse(responseCode = "200", description = "学情聚合成功")
  @ApiResponse(responseCode = "400", description = "INVALID_RANGE")
  @GetMapping("/review-stats")
  public ApiResult<ReviewStatsResp> reviewStats(
      @RequestParam("range") String range,
      @RequestParam(value = "subject", required = false) String subject,
      @RequestHeader(value = USER_ID_HEADER, defaultValue = "0") Long userId,
      @RequestHeader(value = TIMEZONE_HEADER, required = false) String timezone) {
    return ApiResult.ok(statsService.aggregate(userId, range, subject, timezone));
  }

  private ZoneId resolveZone(String timezone) {
    if (timezone == null || timezone.isBlank()) return ZoneId.of(DEFAULT_TZ);
    try {
      return ZoneId.of(timezone);
    } catch (Exception e) {
      return ZoneId.of(DEFAULT_TZ);
    }
  }

  /** -1 = 不过滤 / 0 = active / 1 = mastered（与 ReviewPlan.STATUS_* 对齐）. */
  private static int parseStatusOpt(String status) {
    if (status == null || status.isBlank()) return -1;
    String s = status.trim().toLowerCase();
    if ("active".equals(s) || "pending".equals(s)) return ReviewPlan.STATUS_ACTIVE;
    if ("mastered".equals(s) || "completed".equals(s)) return ReviewPlan.STATUS_MASTERED;
    if ("cancelled".equals(s) || "skipped".equals(s)) return ReviewPlan.STATUS_CANCELLED;
    return -1;
  }

  /** cursor=null → 首页 (返 null) · 否则解析为 Long id（非数字 → 当作 null 兜底）. */
  private static Long parseCursor(String cursor) {
    if (cursor == null || cursor.isBlank()) return null;
    try {
      return Long.parseLong(cursor.trim());
    } catch (NumberFormatException e) {
      return null;
    }
  }
}
