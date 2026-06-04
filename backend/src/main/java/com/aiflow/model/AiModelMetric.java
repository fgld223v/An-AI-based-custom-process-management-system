package com.aiflow.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "ai_model_metric")
public class AiModelMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_name", nullable = false, length = 100)
    private String modelName;

    @Column(name = "metric_type", nullable = false, length = 50)
    private String metricType;

    @Column(name = "metric_value")
    private Double metricValue;

    @Column(name = "sample_count")
    private Integer sampleCount;

    @Column(name = "period", length = 20)
    private String period;

    @Column(name = "record_time", nullable = false)
    private LocalDateTime recordTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
