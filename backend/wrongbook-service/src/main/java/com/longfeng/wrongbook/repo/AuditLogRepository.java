package com.longfeng.wrongbook.repo;

import com.longfeng.wrongbook.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {}
