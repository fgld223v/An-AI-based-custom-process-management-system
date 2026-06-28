package com.aiflow.repository;

import com.aiflow.model.AiChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * AI聊天消息Repository：按会话查询聊天消息，支持滑动窗口。
 */
@Repository
public interface AiChatMessageRepository extends JpaRepository<AiChatMessage, Long> {

    List<AiChatMessage> findBySessionIdAndDeletedOrderByCreatedAtAsc(Long sessionId, Integer deleted);

    /** For sliding window: last N messages (newest first) */
    List<AiChatMessage> findBySessionIdAndDeletedOrderByCreatedAtDesc(Long sessionId, Integer deleted, Pageable pageable);

    long countBySessionIdAndDeleted(Long sessionId, Integer deleted);
}
