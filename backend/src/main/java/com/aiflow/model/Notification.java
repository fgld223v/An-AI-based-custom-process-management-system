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
 * 通知表 (notification)
 * 存储系统发送给用户的消息通知，支持多种通知类型及已读/未读状态追踪。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "notification")
public class Notification {

    /** 通知主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 接收通知的用户 ID */
    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

    /** 通知类型：task_remind(任务提醒) / timeout_warning(超时预警) / approval_result(审批结果) / process_completed(流程完成) / system_notice(系统公告) */
    @Column(name = "type", nullable = false, columnDefinition = "ENUM('task_remind','timeout_warning','approval_result','process_completed','system_notice')")
    private String type;

    /** 通知标题 */
    @Column(name = "title", nullable = false)
    private String title;

    /** 通知正文内容 */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** 关联目标的类型（如 instance / task） */
    @Column(name = "target_type")
    private String targetType;

    /** 关联目标的 ID */
    @Column(name = "target_id")
    private Long targetId;

    /** 点击通知后跳转的 URL */
    @Column(name = "target_url", length = 512)
    private String targetUrl;

    /** 已读状态：true-已读，false-未读 */
    @Column(name = "read_status", nullable = false, columnDefinition = "TINYINT")
    private Boolean isRead;

    /** 阅读时间 */
    @Column(name = "read_at")
    private LocalDateTime readAt;

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
