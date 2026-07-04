package com.aiflow.service;

import com.aiflow.common.BusinessException;
import com.aiflow.config.AiConfig;
import com.aiflow.dto.AiChatMessageResponse;
import com.aiflow.dto.AiChatRequest;
import com.aiflow.dto.AiChatSessionResponse;
import com.aiflow.model.AiChatMessage;
import com.aiflow.model.AiChatSession;
import com.aiflow.repository.AiChatMessageRepository;
import com.aiflow.repository.AiChatSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AI 对话服务单元测试 — 测试会话 CRUD、所有权校验、流式对话前置校验。
 */
class AiChatServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private AiChatSessionRepository sessionRepo;
    private AiChatMessageRepository messageRepo;
    private WebClient webClient;
    private AiConfig aiConfig;
    private AiChatService service;

    @BeforeEach
    void setUp() {
        sessionRepo = mock(AiChatSessionRepository.class);
        messageRepo = mock(AiChatMessageRepository.class);
        webClient = mock(WebClient.class);
        aiConfig = new AiConfig();
        aiConfig.setModel("deepseek-chat");
        aiConfig.setTimeoutSeconds(60);
        aiConfig.setMaxContextMessages(20);
        service = new AiChatService(sessionRepo, messageRepo, webClient, aiConfig, objectMapper);
    }

    // ================================================================
    // 会话创建
    // ================================================================

    @Test
    void createsSessionWithGivenTitle() {
        when(sessionRepo.save(any(AiChatSession.class))).thenAnswer(invocation -> {
            AiChatSession s = invocation.getArgument(0);
            s.setId(1L);
            s.setCreatedAt(LocalDateTime.now());
            return s;
        });

        AiChatSessionResponse result = service.createSession(10L, "流程设计咨询");

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getTitle()).isEqualTo("流程设计咨询");
        assertThat(result.getModel()).isEqualTo("deepseek-chat");
        assertThat(result.getMessageCount()).isZero();
    }

    @Test
    void createsSessionWithDefaultTitleWhenEmpty() {
        when(sessionRepo.save(any(AiChatSession.class))).thenAnswer(invocation -> {
            AiChatSession s = invocation.getArgument(0);
            s.setId(1L);
            s.setCreatedAt(LocalDateTime.now());
            return s;
        });

        AiChatSessionResponse result = service.createSession(10L, null);

        assertThat(result.getTitle()).isEqualTo("新对话");
    }

    @Test
    void createsSessionWithDefaultTitleWhenBlank() {
        when(sessionRepo.save(any(AiChatSession.class))).thenAnswer(invocation -> {
            AiChatSession s = invocation.getArgument(0);
            s.setId(1L);
            s.setCreatedAt(LocalDateTime.now());
            return s;
        });

        AiChatSessionResponse result = service.createSession(10L, "   ");

        assertThat(result.getTitle()).isEqualTo("新对话");
    }

    // ================================================================
    // 会话列表
    // ================================================================

    @Test
    void listsSessionsForUserOrderedByUpdateTime() {
        when(sessionRepo.findByUserIdAndDeletedOrderByUpdatedAtDesc(10L, 0))
                .thenReturn(List.of(session(2L, 10L, "咨询2", 3, LocalDateTime.now()),
                        session(1L, 10L, "咨询1", 5, LocalDateTime.now().minusHours(2))));

        List<AiChatSessionResponse> result = service.listSessions(10L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AiChatSessionResponse::getTitle)
                .containsExactly("咨询2", "咨询1");
    }

    @Test
    void listsEmptyWhenUserHasNoSessions() {
        when(sessionRepo.findByUserIdAndDeletedOrderByUpdatedAtDesc(10L, 0)).thenReturn(List.of());

        List<AiChatSessionResponse> result = service.listSessions(10L);

        assertThat(result).isEmpty();
    }

    // ================================================================
    // 会话删除
    // ================================================================

    @Test
    void deletesSessionAndSoftDeletesAllMessages() {
        AiChatSession session = session(1L, 10L, "测试对话", 3, LocalDateTime.now());
        AiChatMessage msg1 = message(1L, 1L, "user", "你好");
        AiChatMessage msg2 = message(2L, 1L, "assistant", "你好！");
        when(sessionRepo.findByIdAndDeleted(1L, 0)).thenReturn(Optional.of(session));
        when(messageRepo.findBySessionIdAndDeletedOrderByCreatedAtAsc(1L, 0))
                .thenReturn(List.of(msg1, msg2));

        service.deleteSession(1L, 10L);

        assertThat(session.getDeleted()).isEqualTo(1);
        assertThat(msg1.getDeleted()).isEqualTo(1);
        assertThat(msg2.getDeleted()).isEqualTo(1);
        verify(sessionRepo).save(session);
        verify(messageRepo).saveAll(List.of(msg1, msg2));
    }

    @Test
    void deleteSessionRequiresOwnership() {
        when(sessionRepo.findByIdAndDeleted(1L, 0)).thenReturn(Optional.of(
                session(1L, 20L, "别人的对话", 3, LocalDateTime.now())));

        assertThatThrownBy(() -> service.deleteSession(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问");
        verify(sessionRepo, never()).save(any());
    }

    @Test
    void deleteSessionThrowsWhenSessionNotFound() {
        when(sessionRepo.findByIdAndDeleted(1L, 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteSession(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("对话会话不存在");
    }

    // ================================================================
    // 消息查询
    // ================================================================

    @Test
    void getsMessagesForSessionOwner() {
        when(sessionRepo.findByIdAndDeleted(1L, 0)).thenReturn(Optional.of(
                session(1L, 10L, "对话", 2, LocalDateTime.now())));
        when(messageRepo.findBySessionIdAndDeletedOrderByCreatedAtAsc(1L, 0))
                .thenReturn(List.of(message(1L, 1L, "user", "问题1"),
                        message(2L, 1L, "assistant", "回答1")));

        List<AiChatMessageResponse> result = service.getMessages(1L, 10L);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(AiChatMessageResponse::getRole)
                .containsExactly("user", "assistant");
    }

    @Test
    void getMessagesRequiresOwnership() {
        when(sessionRepo.findByIdAndDeleted(1L, 0)).thenReturn(Optional.of(
                session(1L, 20L, "别人的对话", 2, LocalDateTime.now())));

        assertThatThrownBy(() -> service.getMessages(1L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问");
    }

    @Test
    void getMessagesReturnsEmptyForNewSession() {
        when(sessionRepo.findByIdAndDeleted(1L, 0)).thenReturn(Optional.of(
                session(1L, 10L, "空对话", 0, LocalDateTime.now())));
        when(messageRepo.findBySessionIdAndDeletedOrderByCreatedAtAsc(1L, 0)).thenReturn(List.of());

        List<AiChatMessageResponse> result = service.getMessages(1L, 10L);

        assertThat(result).isEmpty();
    }

    // ================================================================
    // SSE 流式对话 — 前置校验
    // ================================================================

    @Test
    void streamChatRejectsMessageThatExceedsMaxLength() {
        SseEmitter emitter = mock(SseEmitter.class);
        AiChatRequest request = new AiChatRequest();
        request.setSessionId(1L);
        request.setMessage("A".repeat(10001));

        service.streamChat(request, 10L, emitter);

        verify(emitter).completeWithError(any(BusinessException.class));
    }

    @Test
    void streamChatThrowsWhenSessionNotOwnedByUser() {
        // getRequiredSession throws BusinessException directly (not via emitter)
        when(sessionRepo.findByIdAndDeleted(1L, 0)).thenReturn(Optional.of(
                session(1L, 20L, "别人的对话", 0, LocalDateTime.now())));
        SseEmitter emitter = mock(SseEmitter.class);
        AiChatRequest request = new AiChatRequest();
        request.setSessionId(1L);
        request.setMessage("你好");

        // The exception propagates up from doStreamChat without being caught,
        // so it's thrown directly rather than via emitter
        assertThatThrownBy(() -> service.streamChat(request, 10L, emitter))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权访问");
    }

    // ================================================================
    // 会话不存在
    // ================================================================

    @Test
    void getMessagesThrowsWhenSessionNotFound() {
        when(sessionRepo.findByIdAndDeleted(999L, 0)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getMessages(999L, 10L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("对话会话不存在");
    }

    // ================================================================
    // DTO 映射 — 字段完整性
    // ================================================================

    @Test
    void sessionResponseIncludesAllRequiredFields() {
        when(sessionRepo.save(any(AiChatSession.class))).thenAnswer(invocation -> {
            AiChatSession s = invocation.getArgument(0);
            s.setId(5L);
            s.setCreatedAt(LocalDateTime.of(2026, 7, 1, 14, 30));
            return s;
        });

        AiChatSessionResponse result = service.createSession(10L, "完整测试");

        assertThat(result.getId()).isEqualTo(5L);
        assertThat(result.getTitle()).isEqualTo("完整测试");
        assertThat(result.getModel()).isEqualTo("deepseek-chat");
        assertThat(result.getMessageCount()).isZero();
        assertThat(result.getCreatedAt()).isNotNull();
    }

    // ================================================================
    // Helper methods
    // ================================================================

    private AiChatSession session(Long id, Long userId, String title, int messageCount,
                                   LocalDateTime updatedAt) {
        AiChatSession s = new AiChatSession();
        s.setId(id);
        s.setUserId(userId);
        s.setTitle(title);
        s.setModel("deepseek-chat");
        s.setMessageCount(messageCount);
        s.setDeleted(0);
        s.setCreatedAt(updatedAt.minusHours(1));
        s.setUpdatedAt(updatedAt);
        s.setLastMessageAt(updatedAt);
        return s;
    }

    private AiChatMessage message(Long id, Long sessionId, String role, String content) {
        AiChatMessage m = new AiChatMessage();
        m.setId(id);
        m.setSessionId(sessionId);
        m.setRole(role);
        m.setContent(content);
        m.setTokenCount(content.length() / 4 + 1);
        m.setDeleted(0);
        m.setCreatedAt(LocalDateTime.now());
        return m;
    }
}
