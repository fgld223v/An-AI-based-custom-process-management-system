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
@Table(name = "form_submission")
public class FormSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "process_instance_id", nullable = false)
    private Long processInstanceId;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "node_key", nullable = false)
    private String nodeKey;

    @Column(name = "node_name")
    private String nodeName;

    @Column(name = "business_type")
    private String businessType;

    @Column(name = "form_id", nullable = false)
    private Long formId;

    @Column(name = "form_data_json", columnDefinition = "LONGTEXT")
    private String formDataJson;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted", nullable = false, columnDefinition = "TINYINT")
    private Integer deleted;
}