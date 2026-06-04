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
@Table(name = "ai_correction_log")
public class AiCorrectionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "correction_type", length = 50)
    private String correctionType;

    @Column(name = "original_data", columnDefinition = "JSON")
    private String originalData;

    @Column(name = "corrected_data", columnDefinition = "JSON")
    private String correctedData;

    @Column(name = "correction_reason", length = 1000)
    private String correctionReason;

    @Column(name = "operator_id")
    private Long operatorId;

    @Column(name = "correction_time", nullable = false)
    private LocalDateTime correctionTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
