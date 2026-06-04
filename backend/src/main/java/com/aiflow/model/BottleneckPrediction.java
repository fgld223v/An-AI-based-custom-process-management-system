package com.aiflow.model;

import com.aiflow.enums.PredictionLevel;
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
@Table(name = "bottleneck_prediction")
public class BottleneckPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "node_id")
    private Long nodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "prediction_level", nullable = false, length = 30)
    private PredictionLevel predictionLevel;

    @Column(name = "predicted_delay")
    private Long predictedDelay;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "recommendation", length = 1000)
    private String recommendation;

    @Column(name = "prediction_time", nullable = false)
    private LocalDateTime predictionTime;

    @Column(name = "triggered")
    private Integer triggered;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
