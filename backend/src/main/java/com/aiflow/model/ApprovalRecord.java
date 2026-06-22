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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "approval_record", uniqueConstraints =
        @UniqueConstraint(name = "uk_approval_record_task_action", columnNames = {"task_id", "action"}))
public class ApprovalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "task_id", length = 128)
    private String taskId;

    @Column(name = "node_key", nullable = false, length = 128)
    private String nodeKey;

    @Column(name = "approver_id")
    private Long approverId;

    @Column(name = "action", nullable = false,
            columnDefinition = "ENUM('approve','reject','supplement','delegate','transfer')")
    private String action;

    @Column(name = "comment_text", columnDefinition = "TEXT")
    private String commentText;

    @Column(name = "attachment_list", columnDefinition = "JSON")
    private String attachmentList;

    @Column(name = "operated_at", nullable = false)
    private LocalDateTime operatedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
