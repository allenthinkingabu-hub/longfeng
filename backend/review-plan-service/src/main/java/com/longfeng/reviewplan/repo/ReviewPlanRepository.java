package com.longfeng.reviewplan.repo;

import com.longfeng.reviewplan.entity.ReviewPlan;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewPlanRepository extends JpaRepository<ReviewPlan, Long> {

  /** Consumer 幂等前置检查 · SC-07.AC-1 boundary.0. */
  boolean existsByWrongItemId(Long wrongItemId);

  /** 查 wrong_item 全 7 行（mastered 触发时聚合用）· 按 node_index asc 返. */
  List<ReviewPlan> findByWrongItemIdOrderByNodeIndexAsc(Long wrongItemId);

  /** complete 单节点 · 加行锁读当前 ease/interval. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT p FROM ReviewPlan p WHERE p.id = :id")
  Optional<ReviewPlan> findByIdForUpdate(@Param("id") Long id);

  /** XXL-Job 扫 due · 批量 · limit. */
  @Query(
      "SELECT p FROM ReviewPlan p WHERE p.status = 0 AND p.nextDueAt <= :now "
          + "ORDER BY p.nextDueAt ASC")
  List<ReviewPlan> findDueBatch(@Param("now") Instant now, Pageable pageable);

  /** 触发 mastered · 一次性 UPDATE 全 7 行 · Q-G 聚合根原子性. */
  @Modifying
  @Query(
      "UPDATE ReviewPlan p SET p.status = 1, p.deletedAt = :now "
          + "WHERE p.wrongItemId = :wrongItemId AND p.status = 0")
  int markAllMasteredByWrongItemId(
      @Param("wrongItemId") Long wrongItemId, @Param("now") Instant now);
}
