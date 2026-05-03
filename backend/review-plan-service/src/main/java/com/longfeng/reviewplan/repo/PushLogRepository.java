package com.longfeng.reviewplan.repo;

import com.longfeng.reviewplan.entity.PushLog;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * wb_push_log 仓库 · S6 BE-10.
 *
 * <p>用于故障追查 + 成功率统计。每通道每次尝试一行。
 */
public interface PushLogRepository extends JpaRepository<PushLog, Long> {

  /** 查某任务的所有推送日志 · 按创建时间倒序（最新失败优先）. */
  List<PushLog> findByTaskIdOrderByCreatedAtDesc(Long taskId);

  /** 查某任务是否有成功送达的日志. */
  boolean existsByTaskIdAndSuccessTrue(Long taskId);
}
