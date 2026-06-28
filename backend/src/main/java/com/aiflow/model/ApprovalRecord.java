package com.aiflow.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 审批记录表 (approval_record)
 * 记录流程中每一次审批操作，包括审批动作、意见、附件及操作时间。
 * 同一 task_id + action 组合唯一，防止重复审批。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "approval_record", uniqueConstraints =
        @UniqueConstraint(name = "uk_approval_record_task_action", columnNames = {"task_id", "action"}))
public class ApprovalRecord {

    /** 审批记录主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 关联的流程实例 ID */
    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    /** Flowable 任务 ID */
    @Column(name = "task_id", length = 128)
    private String taskId;

    /** 审批节点 key */
    @Column(name = "node_key", nullable = false, length = 128)
    private String nodeKey;

    /** 审批人用户 ID */
    @Column(name = "approver_id")
    private Long approverId;

    /** 审批动作：approve(通过) / reject(驳回) / supplement(补充材料) / delegate(委派) / transfer(转办) */
    @Column(name = "action", nullable = false,
            columnDefinition = "ENUM('approve','reject','supplement','delegate','transfer')")
    private String action;

    /** 审批意见文本 */
    @Column(name = "comment_text", columnDefinition = "TEXT")
    private String commentText;

    /** 附件列表 JSON */
    @Column(name = "attachment_list", columnDefinition = "JSON")
    private String attachmentList;

    /** 审批操作时间 */
    @Column(name = "operated_at", nullable = false)
    private LocalDateTime operatedAt;

    /** 创建时间 */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
