package com.aiflow.model;

import com.aiflow.enums.AiAdviceType;
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
@Table(name = "ai_advice_record")
public class AiAdviceRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "node_id")
    private Long nodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "advice_type", nullable = false, length = 20)
    private AiAdviceType adviceType;

    @Column(name = "advice_content", length = 2000)
    private String adviceContent;

    @Column(name = "confidence")
    private Double confidence;

    @Column(name = "data_source", length = 500)
    private String dataSource;

    @Column(name = "used")
    private Integer used;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
