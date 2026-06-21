package com.aiflow.model;

import com.aiflow.enums.TemplateSourceType;
import com.aiflow.enums.TemplateSourceTypeConverter;
import com.aiflow.enums.ProcessResourceType;
import com.aiflow.enums.ProcessResourceTypeConverter;
import com.aiflow.enums.TemplateStatus;
import com.aiflow.enums.TemplateStatusConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
@Table(name = "process_template")
public class ProcessTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "template_code")
    private String templateCode;

    @Column(name = "template_name")
    private String templateName;

    @Column(name = "biz_type_id")
    private Long bizTypeId;

    @Column(name = "form_id")
    private Long formId;

    @Column(name = "version")
    private Integer version;

    @Convert(converter = TemplateStatusConverter.class)
    @Column(name = "status", columnDefinition = "ENUM('draft','reviewing','published','disabled')")
    private TemplateStatus status;

    @Convert(converter = TemplateSourceTypeConverter.class)
    @Column(name = "source_type", columnDefinition = "ENUM('ai_generated','manual','market_copy','fragment_combo')")
    private TemplateSourceType sourceType;

    @Convert(converter = ProcessResourceTypeConverter.class)
    @Column(name = "resource_type", columnDefinition = "ENUM('system_template','business_process')")
    private ProcessResourceType resourceType;

    @Column(name = "bpmn_xml", columnDefinition = "LONGTEXT")
    private String bpmnXml;

    @Column(name = "node_config", columnDefinition = "JSON")
    private String nodeConfig;

    @Column(name = "form_bind_config", columnDefinition = "JSON")
    private String formBindConfig;

    @Column(name = "flowable_deployment_id")
    private String flowableDeploymentId;

    @Column(name = "flowable_process_definition_id")
    private String flowableProcessDefinitionId;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted", columnDefinition = "TINYINT")
    private Integer deleted;
}
