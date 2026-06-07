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

    @Column(name = "instance_code")
    private String instanceCode;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "form_id")
    private Long formId;

    @Column(name = "applicant_id")
    private Long applicantId;

    @Column(name = "biz_type_id")
    private Long bizTypeId;

    @Column(name = "title")
    private String title;

    @Column(name = "status", columnDefinition = "ENUM('running','pending_modify','completed','rejected','cancelled')")
    private String status;

    @Column(name = "form_data", columnDefinition = "JSON")
    private String formData;

    @Column(name = "current_node_key")
    private String currentNodeKey;

    @Column(name = "flowable_process_instance_id")
    private String flowableProcessInstanceId;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted", columnDefinition = "TINYINT")
    private Integer deleted;
}
