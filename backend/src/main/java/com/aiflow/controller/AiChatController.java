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

/**
 * AI 聊天控制器 -- 管理 AI 对话会话和流式消息交互。
 *
 * <p>端点一览：
 * <ul>
 *   <li>GET    /api/ai/chat/sessions              -- 获取当前用户的所有聊天会话列表</li>
 *   <li>POST   /api/ai/chat/sessions              -- 创建新的聊天会话（可选标题）</li>
 *   <li>DELETE /api/ai/chat/sessions/{id}         -- 删除指定聊天会话</li>
 *   <li>GET    /api/ai/chat/sessions/{id}/messages -- 获取指定会话的历史消息</li>
 *   <li>POST   /api/ai/chat/stream                -- SSE 流式聊天（实时推送 AI 回复）</li>
 * </ul>
 *
 * <p>除流式端点外，其他增删操作记录审计日志。流式端点不使用 @AuditLog，
 * 因为 AOP 环绕通知会阻塞等待流完成，破坏了 SSE 的实时性。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/ai/chat")
public class AiChatController {

    private final AiChatService aiChatService;

    /**
     * 查询当前用户的聊天会话列表。
     *
     * <p>GET /api/ai/chat/sessions -- 返回当前登录用户的所有会话（按时间倒序）。
     * 需要登录。
     *
     * @return 会话列表，每个会话包含 ID、标题、创建时间等信息
     */
    @GetMapping("/sessions")
    public ApiResponse<List<AiChatSessionResponse>> listSessions() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(aiChatService.listSessions(userId));
    }

    /**
     * 创建新的聊天会话。
     *
     * <p>POST /api/ai/chat/sessions -- 为当前用户创建一个新对话会话，
     * 可指定标题，未指定时自动生成。需要登录，记录审计日志。
     *
     * @param req 创建请求体（可选，可携带 title）
     * @return 新创建的会话信息
     */
    @AuditLog("AI_CHAT_CREATE_SESSION")
    @PostMapping("/sessions")
    public ApiResponse<AiChatSessionResponse> createSession(
            @RequestBody(required = false) AiChatCreateSessionRequest req) {
        Long userId = SecurityUtils.getCurrentUserId();
        // 参数为空时标题传 null，由服务层自动生成
        String title = (req != null && req.getTitle() != null) ? req.getTitle() : null;
        return ApiResponse.success(aiChatService.createSession(userId, title));
    }

    /**
     * 删除聊天会话。
     *
     * <p>DELETE /api/ai/chat/sessions/{id} -- 删除指定 ID 的会话及其所有消息。
     * 仅允许删除自己的会话。需要登录，记录审计日志。
     *
     * @param id 会话 ID
     * @return 空成功响应
     */
    @AuditLog("AI_CHAT_DELETE_SESSION")
    @DeleteMapping("/sessions/{id}")
    public ApiResponse<Void> deleteSession(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        aiChatService.deleteSession(id, userId);
        return ApiResponse.success();
    }

    /**
     * 获取会话的历史消息。
     *
     * <p>GET /api/ai/chat/sessions/{id}/messages -- 返回指定会话的完整对话记录。
     * 仅允许查看自己的会话。需要登录。
     *
     * @param id 会话 ID
     * @return 消息列表，按时间正序排列
     */
    @GetMapping("/sessions/{id}/messages")
    public ApiResponse<List<AiChatMessageResponse>> getMessages(@PathVariable Long id) {
        Long userId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(aiChatService.getMessages(id, userId));
    }

    /**
     * SSE 流式聊天端点。
     *
     * <p>POST /api/ai/chat/stream -- 使用 Server-Sent Events 实现 AI 流式回复，
     * 前端可实时接收 AI 逐词输出的内容。需要登录。
     *
     * <p>注意：此端点返回原始 SseEmitter（非 ApiResponse 包装），且不使用 @AuditLog，
     * 因为 AOP 环绕通知会阻塞等待流完成，破坏 SSE 的实时推送效果。
     *
     * @param request 聊天请求体，包含会话 ID 和用户消息
     * @return SseEmitter 实例，超时时间 5 分钟
     */
    @PostMapping("/stream")
    public SseEmitter streamChat(@Valid @RequestBody AiChatRequest request) {
        Long userId = SecurityUtils.getCurrentUserId();
        // 创建 SSE 发射器，设置 5 分钟超时
        SseEmitter emitter = new SseEmitter(300_000L);
        // 将流式处理逻辑委托给服务层
        aiChatService.streamChat(request, userId, emitter);
        return emitter;
    }
}
