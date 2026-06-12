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
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "process_instance")
public class ProcessInstance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "instance_code")
    private String instanceCode;

    @Column(name = "title")
    private String instanceTitle;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "form_id")
    private Long formId;

    @Column(name = "applicant_id")
    private Long applicantId;

    @Column(name = "biz_type_id")
    private Long bizTypeId;

    @Column(name = "current_node_key")
    private String currentNodeKey;

    @Column(name = "current_node_name")
    private String currentNodeName;

    @Column(name = "current_business_type")
    private String currentBusinessType;

    @Column(name = "form_data", columnDefinition = "LONGTEXT")
    private String formDataJson;

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

    @Column(name = "created_at")
    private LocalDateTime createTime;

    @Column(name = "updated_at")
    private LocalDateTime updateTime;

    @Column(name = "deleted", columnDefinition = "TINYINT")
    private Integer deleted;
}
