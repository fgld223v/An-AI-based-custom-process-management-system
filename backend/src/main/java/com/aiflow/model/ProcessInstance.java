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
 * 流程实例表 (process_instance)
 * 记录每一次流程发起后的运行实例，追踪当前审批节点、状态及关联的 Flowable 引擎信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "process_instance")
public class ProcessInstance {

    /** 实例主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 实例编码，全局唯一，用于对外展示和检索 */
    @Column(name = "instance_code", nullable = false, unique = true)
    private String instanceCode;

    /** 关联的流程模板 ID */
    @Column(name = "template_id", nullable = false)
    private Long templateId;

    /** 关联的表单定义 ID */
    @Column(name = "form_id")
    private Long formId;

    /** 申请人用户 ID */
    @Column(name = "applicant_id", nullable = false)
    private Long applicantId;

    /** 业务类型 ID */
    @Column(name = "biz_type_id")
    private Long bizTypeId;

    /** 流程实例标题 / 事由 */
    @Column(name = "title", nullable = false)
    private String title;

    /** 实例状态（如：审批中、已完成、已拒绝等） */
    @Column(name = "status", nullable = false)
    private String status;

    /** 表单填写数据 JSON */
    @Column(name = "form_data", columnDefinition = "LONGTEXT")
    private String formData;

    /** 当前审批节点 key */
    @Column(name = "current_node_key")
    private String currentNodeKey;

    /** 当前审批节点名称 */
    @Column(name = "current_node_name")
    private String currentNodeName;

    /** 当前节点对应的业务类型 */
    @Column(name = "current_business_type")
    private String currentBusinessType;

    /** Flowable 引擎中的流程实例 ID */
    @Column(name = "flowable_process_instance_id")
    private String flowableProcessInstanceId;

    /** Flowable 引擎中的流程定义 ID */
    @Column(name = "flowable_definition_id")
    private String flowableDefinitionId;

    /** Flowable 引擎中的部署 ID */
    @Column(name = "flowable_deployment_id")
    private String flowableDeploymentId;

    /** 流程启动时间 */
    @Column(name = "started_at")
    private LocalDateTime startedAt;

    /** 流程结束时间 */
    @Column(name = "ended_at")
    private LocalDateTime endedAt;

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