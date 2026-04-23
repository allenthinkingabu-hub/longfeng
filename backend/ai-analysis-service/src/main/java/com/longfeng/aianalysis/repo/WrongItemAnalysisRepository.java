package com.longfeng.aianalysis.repo;

import com.longfeng.aianalysis.entity.WrongItemAnalysis;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WrongItemAnalysisRepository extends JpaRepository<WrongItemAnalysis, Long> {

  Optional<WrongItemAnalysis> findByWrongItemIdAndVersion(Long wrongItemId, Integer version);

  Optional<WrongItemAnalysis> findTopByWrongItemIdOrderByVersionDesc(Long wrongItemId);
}
