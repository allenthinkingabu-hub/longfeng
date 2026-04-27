package com.longfeng.wrongbook.repo;

import com.longfeng.wrongbook.entity.TagTaxonomy;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagTaxonomyRepository extends JpaRepository<TagTaxonomy, Long> {
  Optional<TagTaxonomy> findByCode(String code);

  List<TagTaxonomy> findByStatus(Short status);

  List<TagTaxonomy> findBySubjectAndStatus(String subject, Short status);
}
