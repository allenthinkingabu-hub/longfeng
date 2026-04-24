package com.longfeng.reviewplan.repo;

import com.longfeng.reviewplan.entity.ReviewPlan;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

  /** XXL-Job 扫 due · 批量 · limit · 走 nativeQuery 规避 JPQL short 字面量的 type-infer 问题. */
  @Query(
      value =
          "SELECT * FROM review_plan WHERE status = 0 AND deleted_at IS NULL "
              + "AND next_due_at <= :now ORDER BY next_due_at ASC LIMIT :limit",
      nativeQuery = true)
  List<ReviewPlan> findDueBatch(@Param("now") Instant now, @Param("limit") int limit);

  /** 触发 mastered · 一次性 UPDATE 全 7 行 · Q-G 聚合根原子性. */
  @Modifying
  @Query(
      "UPDATE ReviewPlan p SET p.status = 1, p.deletedAt = :now "
          + "WHERE p.wrongItemId = :wrongItemId AND p.status = 0")
  int markAllMasteredByWrongItemId(
      @Param("wrongItemId") Long wrongItemId, @Param("now") Instant now);

  /** XXL-Job CAS 派发 · UPDATE dispatch_version WHERE id AND expected · rowsAffected=1 成功. */
  @Modifying
  @Query(
      value =
          "UPDATE review_plan SET dispatch_version = dispatch_version + 1, "
              + "updated_at = now() WHERE id = :id AND dispatch_version = :expected "
              + "AND deleted_at IS NULL",
      nativeQuery = true)
  int compareAndUpdateDispatch(
      @Param("id") Long id, @Param("expected") Long expectedVersion);
}
