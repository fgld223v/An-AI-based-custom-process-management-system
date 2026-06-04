package com.aiflow.repository;

import com.aiflow.model.BottleneckPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BottleneckPredictionRepository extends JpaRepository<BottleneckPrediction, Long> {
}
