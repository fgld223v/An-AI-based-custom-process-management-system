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
import com.aiflow.security.SecurityUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import org.springframework.core.io.buffer.DataBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private static final String SYSTEM_PROMPT = """
        你是一个企业流程管理 AI 助手（BPM AI Assistant）。你可以帮助用户：
        1. 理解和设计 BPMN 工作流
        2. 解答流程管理、审批流设计、表单设计相关问题
        3. 分析流程瓶颈和优化建议
        4. 解释企业流程自动化概念（Flowable、Activiti、Camunda）
        5. 帮助用户梳理业务需求并转化为流程设计

        回答要求：
        - 使用中文回复
        - 保持友好、专业、简洁
        - 涉及代码时给出可运行的示例
        - 当用户描述业务需求时，主动建议流程设计方案
        """;

    private static final int MAX_CONTENT_LENGTH = 10000;

    private final AiChatSessionRepository sessionRepo;
    private final AiChatMessageRepository messageRepo;
    private final WebClient deepseekWebClient;
    private final AiConfig aiConfig;
    private final ObjectMapper objectMapper;

    /** Per-session lock to prevent concurrent streaming to the same session */
    private final ConcurrentHashMap<Long, ReentrantLock> sessionLocks = new ConcurrentHashMap<>();

    // ================================================================
    // Session CRUD
    // ================================================================

    @Transactional
    public AiChatSessionResponse createSession(Long userId, String title) {
        AiChatSession session = new AiChatSession();
        session.setUserId(userId);
        session.setTitle(hasText(title) ? title.trim() : "新对话");
        session.setModel(aiConfig.getModel());
        session.setMessageCount(0);
        session.setDeleted(0);
        AiChatSession saved = sessionRepo.save(session);
        log.info("Created chat session id={} for userId={}", saved.getId(), userId);
        return toSessionResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AiChatSessionResponse> listSessions(Long userId) {
        return sessionRepo.findByUserIdAndDeletedOrderByUpdatedAtDesc(userId, 0).stream()
                .map(this::toSessionResponse)
                .toList();
    }

    @Transactional
    public void deleteSession(Long sessionId, Long userId) {
        AiChatSession session = getRequiredSession(sessionId, userId);
        session.setDeleted(1);
        session.setUpdatedAt(LocalDateTime.now());
        sessionRepo.save(session);
        // soft-delete all messages too
        List<AiChatMessage> messages = messageRepo.findBySessionIdAndDeletedOrderByCreatedAtAsc(sessionId, 0);
        for (AiChatMessage msg : messages) {
            msg.setDeleted(1);
        }
        messageRepo.saveAll(messages);
        log.info("Deleted chat session id={} and {} messages", sessionId, messages.size());
    }

    @Transactional(readOnly = true)
    public List<AiChatMessageResponse> getMessages(Long sessionId, Long userId) {
        getRequiredSession(sessionId, userId); // verify ownership
        return messageRepo.findBySessionIdAndDeletedOrderByCreatedAtAsc(sessionId, 0).stream()
                .map(this::toMessageResponse)
                .toList();
    }

    // ================================================================
    // Streaming Chat
    // ================================================================

    /**
     * Stream an AI chat response via SseEmitter.
     * The emitter is created by the controller and passed here.
     */
    public void streamChat(AiChatRequest request, Long userId, SseEmitter emitter) {
        Long sessionId = request.getSessionId();
        String userMessage = request.getMessage().trim();

        if (userMessage.length() > MAX_CONTENT_LENGTH) {
            emitter.completeWithError(
                    new BusinessException("消息过长，最多 " + MAX_CONTENT_LENGTH + " 字符"));
            return;
        }

        ReentrantLock lock = sessionLocks.computeIfAbsent(sessionId, k -> new ReentrantLock());
        lock.lock();
        try {
            doStreamChat(sessionId, userId, userMessage, emitter);
        } finally {
            lock.unlock();
            sessionLocks.remove(sessionId);
        }
    }

    private void doStreamChat(Long sessionId, Long userId, String userMessage, SseEmitter emitter) {
        // 1. Validate session and save user message
        AiChatSession session = getRequiredSession(sessionId, userId);
        saveMessage(sessionId, "user", userMessage, estimateTokens(userMessage));
        updateSessionMeta(session);

        // 2. Build context messages
        List<Map<String, Object>> llmMessages = buildContextMessages(sessionId, userMessage);

        // 3. Build request body
        Map<String, Object> requestBody = Map.of(
            "model", aiConfig.getModel(),
            "messages", llmMessages,
            "stream", true,
            "temperature", 0.7,
            "max_tokens", 4096
        );

        // 4. Call DeepSeek streaming API — use DataBuffer to avoid buffering
        StringBuilder fullResponse = new StringBuilder();
        try {
            log.info("Starting DeepSeek stream for session {}", sessionId);
            deepseekWebClient.post()
                .uri("/chat/completions")
                .bodyValue(requestBody)
                .accept(org.springframework.http.MediaType.TEXT_EVENT_STREAM)
                .retrieve()
                .bodyToFlux(DataBuffer.class)
                .timeout(Duration.ofSeconds(aiConfig.getTimeoutSeconds()))
                .doOnNext(dataBuffer -> {
                    byte[] bytes = new byte[dataBuffer.readableByteCount()];
                    dataBuffer.read(bytes);
                    org.springframework.core.io.buffer.DataBufferUtils.release(dataBuffer);
                    String chunk = new String(bytes, StandardCharsets.UTF_8);
                    processStreamChunk(chunk, emitter, fullResponse);
                })
                .doOnComplete(() -> onStreamComplete(sessionId, session, fullResponse.toString(), emitter))
                .doOnError(error -> onStreamError(emitter, error))
                .subscribe(
                    v -> {},
                    err -> {},
                    () -> {}
                );
        } catch (Exception e) {
            log.error("DeepSeek streaming call failed for session {}", sessionId, e);
            try {
                emitter.send(SseEmitter.event().name("error").data("AI 服务调用失败：" + e.getMessage()));
            } catch (Exception ignored) {}
            emitter.completeWithError(e);
        }
    }

    /**
     * Parse a raw SSE chunk from DeepSeek and emit tokens to the client.
     */
    private void processStreamChunk(String rawChunk, SseEmitter emitter, StringBuilder fullResponse) {
        if (rawChunk == null) return;
        String[] lines = rawChunk.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("data:")) continue;
            String data = trimmed.substring(5).trim();
            if (data.isEmpty() || "[DONE]".equals(data)) continue;

            try {
                Map<String, Object> parsed = objectMapper.readValue(data,
                        new TypeReference<Map<String, Object>>() {});
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) parsed.get("choices");
                if (choices == null || choices.isEmpty()) continue;
                @SuppressWarnings("unchecked")
                Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                if (delta == null) continue;
                Object content = delta.get("content");
                if (content == null) continue;
                String token = content.toString();
                fullResponse.append(token);
                // Send each token as a separate SSE event
                emitter.send(SseEmitter.event()
                        .data(Map.of("content", token)));
            } catch (Exception e) {
                // Skip unparseable chunks gracefully
            }
        }
    }

    /**
     * Called when the DeepSeek stream completes normally.
     */
    private void onStreamComplete(Long sessionId, AiChatSession session, String fullContent, SseEmitter emitter) {
        try {
            if (fullContent.isEmpty()) {
                fullContent = "(AI 未返回内容，请重试)";
            }
            // Save assistant message
            Long msgId = saveMessage(sessionId, "assistant", fullContent, estimateTokens(fullContent)).getId();

            // Auto-title on first exchange
            if ("新对话".equals(session.getTitle())) {
                String autoTitle = generateTitle(fullContent);
                if (autoTitle != null) {
                    session.setTitle(autoTitle);
                } else {
                    session.setTitle(truncateTitle(fullContent));
                }
            }
            updateSessionMeta(session);

            // Send done event with messageId
            emitter.send(SseEmitter.event()
                    .name("done")
                    .data(Map.of("type", "done", "messageId", msgId)));
            emitter.complete();
        } catch (Exception e) {
            log.error("Failed to complete stream for session {}", sessionId, e);
            emitter.completeWithError(e);
        }
    }

    private void onStreamError(SseEmitter emitter, Throwable error) {
        log.error("Stream error", error);
        try {
            emitter.send(SseEmitter.event()
                    .name("error")
                    .data(Map.of("type", "error", "message", "AI 回复中断：" + error.getMessage())));
        } catch (Exception ignored) {}
        emitter.completeWithError(error);
    }

    // ================================================================
    // Context Building
    // ================================================================

    private List<Map<String, Object>> buildContextMessages(Long sessionId, String userMessage) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", SYSTEM_PROMPT));

        // Load history (sliding window)
        int maxCtx = aiConfig.getMaxContextMessages();
        List<AiChatMessage> history = messageRepo.findBySessionIdAndDeletedOrderByCreatedAtDesc(
                sessionId, 0, PageRequest.of(0, maxCtx));

        // Reverse to chronological order
        List<AiChatMessage> chronological = new ArrayList<>(history);
        java.util.Collections.reverse(chronological);

        int estimatedTokens = 0;
        for (AiChatMessage msg : chronological) {
            int tokens = msg.getTokenCount() != null ? msg.getTokenCount() : estimateTokens(msg.getContent());
            estimatedTokens += tokens;
            // Keep under ~6000 tokens to leave room for response
            if (estimatedTokens > 6000) break;
            messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }

        // The current user message is already saved and at the end of history,
        // but we need to ensure it's in the messages list.
        // Since we saved it above with role="user", the historical query includes it.
        // Double-check: if history is empty or truncated, add the user message explicitly.
        String lastRole = chronological.isEmpty() ? null : chronological.get(chronological.size() - 1).getRole();
        if (!"user".equals(lastRole)) {
            messages.add(Map.of("role", "user", "content", userMessage));
        }

        return messages;
    }

    // ================================================================
    // Helpers
    // ================================================================

    private AiChatMessage saveMessage(Long sessionId, String role, String content, int tokens) {
        AiChatMessage msg = new AiChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setTokenCount(tokens);
        msg.setDeleted(0);
        return messageRepo.save(msg);
    }

    private void updateSessionMeta(AiChatSession session) {
        long count = messageRepo.countBySessionIdAndDeleted(session.getId(), 0);
        session.setMessageCount((int) count);
        session.setLastMessageAt(LocalDateTime.now());
        sessionRepo.save(session);
    }

    private String generateTitle(String aiResponse) {
        // Simple approach: use the first meaningful line as title
        String firstLine = aiResponse.split("\n")[0].trim();
        // Remove markdown headers and common prefixes
        firstLine = firstLine.replaceAll("^#+\\s*", "");
        firstLine = firstLine.replaceAll("^[*\\-]\\s*", "");
        return truncateTitle(firstLine);
    }

    private String truncateTitle(String text) {
        if (text == null || text.isBlank()) return "新对话";
        String cleaned = text.replaceAll("\\s+", " ").trim();
        return cleaned.length() <= 20 ? cleaned : cleaned.substring(0, 19) + "…";
    }

    private int estimateTokens(String text) {
        if (text == null) return 0;
        return text.getBytes(StandardCharsets.UTF_8).length / 4 + 1;
    }

    private AiChatSession getRequiredSession(Long sessionId, Long userId) {
        AiChatSession session = sessionRepo.findByIdAndDeleted(sessionId, 0)
                .orElseThrow(() -> new BusinessException(404, "对话会话不存在"));
        if (!session.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权访问该对话");
        }
        return session;
    }

    // ================================================================
    // DTO mapping
    // ================================================================

    private AiChatSessionResponse toSessionResponse(AiChatSession entity) {
        return AiChatSessionResponse.builder()
                .id(entity.getId())
                .title(entity.getTitle())
                .model(entity.getModel())
                .messageCount(entity.getMessageCount())
                .lastMessageAt(entity.getLastMessageAt())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private AiChatMessageResponse toMessageResponse(AiChatMessage entity) {
        return AiChatMessageResponse.builder()
                .id(entity.getId())
                .sessionId(entity.getSessionId())
                .role(entity.getRole())
                .content(entity.getContent())
                .createdAt(entity.getCreatedAt())
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
