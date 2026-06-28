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

/**
 * 表单提交记录表 (form_submission)
 * 记录流程每个节点上用户提交的表单数据，按节点级别进行存储。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "form_submission")
public class FormSubmission {

    /** 提交记录主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的流程实例 ID */
    @Column(name = "process_instance_id", nullable = false)
    private Long processInstanceId;

    /** 关联的流程模板 ID */
    @Column(name = "template_id", nullable = false)
    private Long templateId;

    /** 提交时的节点 key */
    @Column(name = "node_key", nullable = false)
    private String nodeKey;

    /** 提交时的节点名称 */
    @Column(name = "node_name")
    private String nodeName;

    /** 节点对应的业务类型 */
    @Column(name = "business_type")
    private String businessType;

    /** 关联的表单定义 ID */
    @Column(name = "form_id", nullable = false)
    private Long formId;

    /** 用户填写的表单数据 JSON */
    @Column(name = "form_data_json", columnDefinition = "LONGTEXT")
    private String formDataJson;

    /** 提交状态（如：submitted / draft 等） */
    @Column(name = "status", nullable = false)
    private String status;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：1-已删除，0-正常 */
    @Column(name = "deleted", nullable = false, columnDefinition = "TINYINT")
    private Integer deleted;
}