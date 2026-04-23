package com.longfeng.wrongbook.repo;

import com.longfeng.wrongbook.entity.WrongItemImage;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WrongItemImageRepository extends JpaRepository<WrongItemImage, Long> {
  List<WrongItemImage> findByWrongItemIdOrderByIdAsc(Long wrongItemId);

  Optional<WrongItemImage> findByWrongItemIdAndRole(Long wrongItemId, String role);
}
