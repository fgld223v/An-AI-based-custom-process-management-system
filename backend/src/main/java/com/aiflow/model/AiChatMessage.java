package com.aiflow.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话消息表 (ai_chat_message)
 * 存储 AI 对话会话中的每一条消息，包括用户提问和 AI 回复。
 */
@Data
@Entity
@Table(name = "ai_chat_message")
public class AiChatMessage {

    /** 消息主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 所属会话 ID，关联 ai_chat_session 表 */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 消息角色：user(用户) / assistant(AI助手) / system(系统) */
    @Column(name = "role", nullable = false, length = 16)
    private String role;

    /** 消息正文内容 */
    @Column(name = "content", nullable = false, columnDefinition = "LONGTEXT")
    private String content;

    /** Token 消耗数（用于统计和计费） */
    @Column(name = "token_count")
    private Integer tokenCount;

    /** 创建时间（不可更新） */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 逻辑删除标记：1-已删除，0-正常 */
    @Column(name = "deleted", nullable = false, columnDefinition = "TINYINT")
    private Integer deleted;

    /** JPA 持久化前自动设置创建时间和默认值 */
    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (deleted == null) deleted = 0;
    }
}
