package com.aiflow.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话会话表 (ai_chat_session)
 * 记录用户与 AI 的一次对话会话，包含会话标题、使用的模型及消息统计。
 */
@Data
@Entity
@Table(name = "ai_chat_session")
public class AiChatSession {

    /** 会话主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 所属用户 ID */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** 会话标题 */
    @Column(name = "title", nullable = false, length = 128)
    private String title;

    /** 使用的 AI 模型名称 */
    @Column(name = "model", nullable = false, length = 64)
    private String model;

    /** 会话中的消息总数 */
    @Column(name = "message_count", nullable = false)
    private Integer messageCount;

    /** 最后一条消息的时间 */
    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    /** 创建时间（不可更新） */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：1-已删除，0-正常 */
    @Column(name = "deleted", nullable = false, columnDefinition = "TINYINT")
    private Integer deleted;

    /** JPA 持久化前自动设置创建时间和默认值 */
    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (deleted == null) deleted = 0;
        if (messageCount == null) messageCount = 0;
    }

    /** JPA 更新前自动刷新更新时间 */
    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
