package com.longfeng.reviewplan.repo;

import com.longfeng.reviewplan.entity.ReviewOutcome;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewOutcomeRepository extends JpaRepository<ReviewOutcome, Long> {

  /** Q-G mastered 触发判定 · 查本 wrong_item 最近 N 条 outcome · 判连续 3 次 ease≥2.8. */
  List<ReviewOutcome> findByWrongItemIdOrderByCompletedAtDesc(Long wrongItemId, Pageable pageable);

  /**
   * SC-09.AC-1 · 按用户 timezone 日切 · 聚合 correctRate/masteredCount/reviewCount.
   *
   * <p>返回 Object[]: [0]=date (java.sql.Date) · [1]=correctRate (BigDecimal · null if 0 reviews)
   * · [2]=masteredCount (Long) · [3]=reviewCount (Long).
   *
   * <p>correctRate = count(quality≥3) / count(*) · masteredCount = 当日触发 mastered 的错题 distinct
   * 数（通过 review_plan 的 deleted_at 同日落入日期）.
   */
  @Query(
      value =
          "SELECT "
              + "  date(ro.completed_at AT TIME ZONE CAST(:tz AS TEXT)) AS d, "
              + "  CAST(count(*) FILTER (WHERE ro.quality >= 3) AS numeric) / NULLIF(count(*), 0) AS correct_rate, "
              + "  count(DISTINCT rp.wrong_item_id) FILTER ("
              + "    WHERE rp.status = 1 AND rp.deleted_at IS NOT NULL "
              + "    AND date(rp.deleted_at AT TIME ZONE CAST(:tz AS TEXT))"
              + "      = date(ro.completed_at AT TIME ZONE CAST(:tz AS TEXT))"
              + "  ) AS mastered_count, "
              + "  count(*) AS review_count "
              + "FROM review_outcome ro "
              + "JOIN review_plan rp ON ro.plan_id = rp.id "
              + "WHERE ro.user_id = :userId "
              + "  AND ro.completed_at >= :start "
              + "  AND ro.completed_at < :endExclusive "
              + "  AND (CAST(:subject AS TEXT) IS NULL OR EXISTS ("
              + "      SELECT 1 FROM wrong_item wi "
              + "      WHERE wi.id = ro.wrong_item_id AND wi.subject = CAST(:subject AS TEXT)"
              + "    )) "
              + "GROUP BY d "
              + "ORDER BY d",
      nativeQuery = true)
  List<Object[]> aggregateDailyStats(
      @Param("userId") Long userId,
      @Param("start") Instant start,
      @Param("endExclusive") Instant endExclusive,
      @Param("subject") String subject,
      @Param("tz") String timezone);

  /**
   * SC-09.AC-1 · Top N 薄弱项 · 按 forget_rate 倒序 · 忽略样本过小的科目.
   *
   * <p>返回 Object[]: [0]=subject · [1]=totalReview (Long) · [2]=forgetCount (Long) ·
   * [3]=forgetRate (BigDecimal).
   */
  @Query(
      value =
          "SELECT "
              + "  wi.subject, "
              + "  count(*) AS total_review, "
              + "  count(*) FILTER (WHERE ro.quality < 3) AS forget_count, "
              + "  CAST(count(*) FILTER (WHERE ro.quality < 3) AS numeric) / NULLIF(count(*), 0) AS forget_rate "
              + "FROM review_outcome ro "
              + "JOIN wrong_item wi ON wi.id = ro.wrong_item_id AND wi.deleted_at IS NULL "
              + "WHERE ro.user_id = :userId "
              + "  AND ro.completed_at >= :start "
              + "  AND ro.completed_at < :endExclusive "
              + "GROUP BY wi.subject "
              + "HAVING count(*) >= :minSample "
              + "ORDER BY forget_rate DESC NULLS LAST, total_review DESC "
              + "LIMIT :topN",
      nativeQuery = true)
  List<Object[]> topWeakSubjects(
      @Param("userId") Long userId,
      @Param("start") Instant start,
      @Param("endExclusive") Instant endExclusive,
      @Param("minSample") Integer minSample,
      @Param("topN") Integer topN);
}
