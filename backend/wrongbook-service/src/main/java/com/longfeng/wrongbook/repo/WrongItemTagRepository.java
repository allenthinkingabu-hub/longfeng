package com.longfeng.wrongbook.repo;

import com.longfeng.wrongbook.entity.WrongItemTag;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WrongItemTagRepository extends JpaRepository<WrongItemTag, Long> {
  List<WrongItemTag> findByWrongItemIdOrderByIdAsc(Long wrongItemId);

  Optional<WrongItemTag> findByWrongItemIdAndTagCode(Long wrongItemId, String tagCode);

  @Modifying
  @Query("delete from WrongItemTag t where t.wrongItemId = :wrongItemId and t.tagCode = :tagCode")
  int deleteByWrongItemIdAndTagCode(
      @Param("wrongItemId") Long wrongItemId, @Param("tagCode") String tagCode);
}
