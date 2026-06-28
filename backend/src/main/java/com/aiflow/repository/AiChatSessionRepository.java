package com.aiflow.repository;

import com.aiflow.model.AiChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * AI聊天会话Repository：按用户查询会话列表。
 */
@Repository
public interface AiChatSessionRepository extends JpaRepository<AiChatSession, Long> {

    List<AiChatSession> findByUserIdAndDeletedOrderByUpdatedAtDesc(Long userId, Integer deleted);

    Optional<AiChatSession> findByIdAndDeleted(Long id, Integer deleted);
}
