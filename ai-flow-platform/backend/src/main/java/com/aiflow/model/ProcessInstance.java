package com.aiflow.model;

import com.aiflow.enums.InstanceStatus;
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
@Table(name = "process_instance")
public class ProcessInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_code", unique = true, nullable = false, length = 100)
    private String instanceCode;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private InstanceStatus status;

    @Column(name = "biz_type_id")
    private Long bizTypeId;

    @Column(name = "submitter_id", nullable = false)
    private Long submitterId;

    @Column(name = "form_data", columnDefinition = "JSON")
    private String formData;

    @Column(name = "flow_context", columnDefinition = "JSON")
    private String flowContext;

    @Column(name = "submit_time", nullable = false)
    private LocalDateTime submitTime;

    @Column(name = "complete_time")
    private LocalDateTime completeTime;

    @Column(name = "total_duration")
    private Long totalDuration;

    @Column(name = "current_node_id")
    private Long currentNodeId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
