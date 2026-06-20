package com.aiflow.controller;

import com.aiflow.annotation.AuditLog;
import com.aiflow.common.ApiResponse;
import com.aiflow.dto.AiChatCreateSessionRequest;
import com.aiflow.dto.AiChatMessageResponse;
import com.aiflow.dto.AiChatRequest;
import com.aiflow.dto.AiChatSessionResponse;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.AiChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/chat")
public class AiChatController {

    private final AiChatService aiChatService;

    @GetMapping("/sessions")
    public ApiResponse<List<AiChatSessionResponse>> listSessions() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(aiChatService.listSessions(userId));
    }

    @AuditLog("AI_CHAT_CREATE_SESSION")
    @PostMapping("/sessions")
    public ApiResponse<AiChatSessionResponse> createSession(
            @RequestBody(required = false) AiChatCreateSessionRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        String title = (req != null && req.getTitle() != null) ? req.getTitle() : null;
        return ApiResponse.success(aiChatService.createSession(userId, title));
    }

    @AuditLog("AI_CHAT_DELETE_SESSION")
    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> deleteSession(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        aiChatService.deleteSession(id, userId);
        return ApiResponse.success();
    }

    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<List<AiChatMessageResponse>> getMessages(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(aiChatService.getMessages(id, userId));
    }

    /**
     * Streaming chat endpoint — returns raw SseEmitter (not ApiResponse).
     * Does NOT use @AuditLog because the AOP around-advice would block
     * until the stream completes, defeating the purpose of SSE.
     */
    @PostMapping("/stream")
    public SseEmitter streamChat(@Valid @RequestBody AiChatRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        SseEmitter emitter = new SseEmitter(300_000L); // 5 minute timeout
        aiChatService.streamChat(request, userId, emitter);
        return emitter;
    }
}
