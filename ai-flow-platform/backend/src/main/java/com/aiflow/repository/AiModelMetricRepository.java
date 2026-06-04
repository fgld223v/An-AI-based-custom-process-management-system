package com.aiflow.repository;

import com.aiflow.model.AiModelMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AiModelMetricRepository extends JpaRepository<AiModelMetric, Long> {
}
