package com.longfeng.wrongbook.repo;

import com.longfeng.wrongbook.entity.TagTaxonomy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagTaxonomyRepository extends JpaRepository<TagTaxonomy, Long> {
  Optional<TagTaxonomy> findByCode(String code);
}
