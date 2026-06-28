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

/**
 * 流程模板表 (process_template)
 * 存储流程定义的核心数据，包含 BPMN 流程图、节点配置、表单绑定及 Flowable 引擎关联信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "process_template")
public class ProcessTemplate {

    /** 模板主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 模板编码，用于唯一标识和版本管理 */
    @Column(name = "template_code")
    private String templateCode;

    /** 模板名称 */
    @Column(name = "template_name")
    private String templateName;

    /** 所属业务类型 ID */
    @Column(name = "biz_type_id")
    private Long bizTypeId;

    /** 关联的表单定义 ID */
    @Column(name = "form_id")
    private Long formId;

    /** 模板版本号 */
    @Column(name = "version")
    private Integer version;

    /** 模板状态：draft(草稿) / reviewing(审核中) / published(已发布) / disabled(已停用) */
    @Convert(converter = TemplateStatusConverter.class)
    @Column(name = "status", columnDefinition = "ENUM('draft','reviewing','published','disabled')")
    private TemplateStatus status;

    /** 模板来源：ai_generated(AI生成) / manual(手动创建) / market_copy(市场复制) / fragment_combo(片段组合) */
    @Convert(converter = TemplateSourceTypeConverter.class)
    @Column(name = "source_type", columnDefinition = "ENUM('ai_generated','manual','market_copy','fragment_combo')")
    private TemplateSourceType sourceType;

    /** 资源类型：system_template(系统模板) / business_process(业务流程) */
    @Convert(converter = ProcessResourceTypeConverter.class)
    @Column(name = "resource_type", columnDefinition = "ENUM('system_template','business_process')")
    private ProcessResourceType resourceType;

    /** BPMN 2.0 XML 流程定义内容 */
    @Column(name = "bpmn_xml", columnDefinition = "LONGTEXT")
    private String bpmnXml;

    /** 节点配置 JSON，定义各节点的审批人、条件等 */
    @Column(name = "node_config", columnDefinition = "JSON")
    private String nodeConfig;

    /** 表单绑定配置 JSON，定义节点与表单的关联关系 */
    @Column(name = "form_bind_config", columnDefinition = "JSON")
    private String formBindConfig;

    /** Flowable 引擎中的部署 ID */
    @Column(name = "flowable_deployment_id")
    private String flowableDeploymentId;

    /** Flowable 引擎中的流程定义 ID */
    @Column(name = "flowable_process_definition_id")
    private String flowableProcessDefinitionId;

    /** 创建者用户 ID */
    @Column(name = "created_by")
    private Long createdBy;

    /** 发布时间 */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：1-已删除，0-正常 */
    @Column(name = "deleted", columnDefinition = "TINYINT")
    private Integer deleted;
}
