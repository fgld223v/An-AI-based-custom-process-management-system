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
@Table(name = "form_submission")
public class FormSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "process_instance_id")
    private Long processInstanceId;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "node_key")
    private String nodeKey;

    @Column(name = "node_name")
    private String nodeName;

    @Column(name = "business_type")
    private String businessType;

    @Column(name = "form_id")
    private Long formId;

    @Column(name = "form_data_json", columnDefinition = "LONGTEXT")
    private String formDataJson;

    @Column(name = "status", length = 32)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createTime;

    @Column(name = "updated_at")
    private LocalDateTime updateTime;

    @Column(name = "deleted", columnDefinition = "TINYINT")
    private Integer deleted;
}
