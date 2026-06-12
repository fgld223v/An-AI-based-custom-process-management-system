package com.aiflow.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "process_instance")
public class ProcessInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_code", nullable = false, unique = true)
    private String instanceCode;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "form_id")
    private Long formId;

    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    @Column(name = "biz_type_id")
    private Long bizTypeId;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "form_data", columnDefinition = "LONGTEXT")
    private String formData;

    @Column(name = "current_node_key")
    private String currentNodeKey;

    @Column(name = "current_node_name")
    private String currentNodeName;

    @Column(name = "current_business_type")
    private String currentBusinessType;

    @Column(name = "flowable_process_instance_id")
    private String flowableProcessInstanceId;

    @Column(name = "flowable_definition_id")
    private String flowableDefinitionId;

    @Column(name = "flowable_deployment_id")
    private String flowableDeploymentId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted", nullable = false, columnDefinition = "TINYINT")
    private Integer deleted;
}