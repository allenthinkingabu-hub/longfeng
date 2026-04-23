package com.longfeng.wrongbook.repo;

import com.longfeng.wrongbook.entity.WrongItem;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.data.repository.query.Param;

public interface WrongItemRepository
    extends JpaRepository<WrongItem, Long>, QuerydslPredicateExecutor<WrongItem> {

  /** @SQLRestriction already filters deleted_at IS NULL. */
  Optional<WrongItem> findById(Long id);

  @Query(
      "select w from WrongItem w "
          + "where (:subject is null or w.subject = :subject) "
          + "and (:status is null or w.status = :status) "
          + "and (:studentId is null or w.studentId = :studentId) "
          + "and (:cursor is null or w.id < :cursor) "
          + "order by w.id desc")
  List<WrongItem> page(
      @Param("subject") String subject,
      @Param("status") Short status,
      @Param("studentId") Long studentId,
      @Param("cursor") Long cursor,
      Limit limit);
}
