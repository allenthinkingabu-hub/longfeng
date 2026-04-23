package com.longfeng.reviewplan.repo;

import com.longfeng.reviewplan.entity.ReviewOutcome;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewOutcomeRepository extends JpaRepository<ReviewOutcome, Long> {

  /** Q-G mastered 触发判定 · 查本 wrong_item 最近 N 条 outcome · 判连续 3 次 ease≥2.8. */
  List<ReviewOutcome> findByWrongItemIdOrderByCompletedAtDesc(Long wrongItemId, Pageable pageable);
}
