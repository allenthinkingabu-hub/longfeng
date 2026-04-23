package com.longfeng.reviewplan.exception;

/** plan 已 mastered 软删 · 再 POST complete → 410 Gone · SC-08.AC-1 error_paths.2. */
public class PlanMasteredException extends RuntimeException {
  private final Long planId;

  public PlanMasteredException(Long planId) {
    super("review plan already mastered: id=" + planId);
    this.planId = planId;
  }

  public Long planId() {
    return planId;
  }
}
