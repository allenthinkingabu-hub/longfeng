package com.longfeng.wrongbook.repo;

import com.longfeng.wrongbook.entity.WrongItem;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WrongItemRepository
    extends JpaRepository<WrongItem, Long>, WrongItemQueryRepository {

  /** @SQLRestriction already filters deleted_at IS NULL. */
  Optional<WrongItem> findById(Long id);
}
