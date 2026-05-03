package com.longfeng.reviewplan.repo;

import com.longfeng.reviewplan.entity.PushTask;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * wb_push_task 仓库 · S6 BE-10.
 *
 * <p>findPending · Pageable limit · 扫 status=0 AND scheduled_at <= now()
 * updateStatusCas · CAS UPDATE version · 防并发双推
 */
public interface PushTaskRepository extends JpaRepository<PushTask, Long> {

  /**
   * PushTaskRelayJob 扫 PENDING · 按 scheduled_at 升序 · FOR UPDATE SKIP LOCKED.
   *
   * <p>使用 nativeQuery 以利用 DB 索引 idx_push_sched(status, scheduled_at).
   */
  @Query(
      value =
          "SELECT * FROM review.wb_push_task "
              + "WHERE status = 0 AND scheduled_at <= :now "
              + "ORDER BY scheduled_at ASC "
              + "FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  List<PushTask> findPending(@Param("now") OffsetDateTime now, Pageable pageable);

  /**
   * CAS UPDATE status · 防并发双推.
   *
   * @return 受影响行数（1 = 成功 · 0 = 被其他 executor 抢占）
   */
  @Modifying
  @Query(
      value =
          "UPDATE review.wb_push_task "
              + "SET status = :newStatus, version = version + 1, "
              + "    tried_times = tried_times + 1, last_error = :lastError "
              + "WHERE id = :id AND version = :expectedVersion",
      nativeQuery = true)
  int updateStatusCas(
      @Param("id") Long id,
      @Param("expectedVersion") int expectedVersion,
      @Param("newStatus") short newStatus,
      @Param("lastError") String lastError);

  /**
   * 检查 idempotency_key 是否已存在（幂等前置校验）.
   */
  boolean existsByIdempotencyKey(String idempotencyKey);
}
