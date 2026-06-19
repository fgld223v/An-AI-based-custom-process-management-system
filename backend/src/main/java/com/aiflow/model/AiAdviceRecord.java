package com.aiflow.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ai_advice_record")
public class AiAdviceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "task_id")
    private Long taskId;

    @Column(name = "node_key")
    private String nodeKey;

    @Column(name = "advice_type", nullable = false,
            columnDefinition = "ENUM('pass','verify','reject','risk')")
    private String adviceType;  // pass / verify / reject / risk

    @Column(name = "advice_content", columnDefinition = "LONGTEXT")
    private String adviceContent;

    @Column(name = "risk_points", columnDefinition = "JSON")
    private String riskPoints;

    @Column(name = "confidence", columnDefinition = "DECIMAL(5,4)")
    private Double confidence;

    @Column(name = "model_name")
    private String modelName;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
