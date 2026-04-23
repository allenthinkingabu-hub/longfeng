package com.longfeng.reviewplan.exception;

/** plan 不存在 · 404 PLAN_NOT_FOUND · SC-08.AC-1 error_paths.0. */
public class PlanNotFoundException extends RuntimeException {
  private final Long planId;

  public PlanNotFoundException(Long planId) {
    super("review plan not found: id=" + planId);
    this.planId = planId;
  }

  public Long planId() {
    return planId;
  }
}
