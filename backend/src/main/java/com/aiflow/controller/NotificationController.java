package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.NotificationCreateRequest;
import com.aiflow.dto.NotificationDTO;
import com.aiflow.dto.NotificationUpdateRequest;
import com.aiflow.security.SecurityUtils;
import com.aiflow.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Map;

/**
 * 通知控制器 -- 管理系统通知的查询、创建、编辑、标记已读/未读及删除。
 *
 * <p>通知用于站内消息推送，如任务催办、审批提醒、系统公告等。
 * 普通用户可查询、标记自己的通知；超级管理员可创建和编辑全局通知内容。
 *
 * <p>端点一览：
 * <ul>
 *   <li>GET    /api/notifications               -- 通知列表（支持接收人/类型/已读/关键字过滤）</li>
 *   <li>GET    /api/notifications/{id}          -- 通知详情</li>
 *   <li>GET    /api/notifications/unread-count  -- 未读通知数量</li>
 *   <li>POST   /api/notifications               -- 创建通知（仅超管）</li>
 *   <li>PUT    /api/notifications/{id}          -- 编辑通知（仅超管）</li>
 *   <li>PUT    /api/notifications/{id}/read     -- 标记通知为已读</li>
 *   <li>PUT    /api/notifications/{id}/unread   -- 标记通知为未读</li>
 *   <li>DELETE /api/notifications/{id}          -- 删除通知</li>
 * </ul>
 *
 * <p>所有端点均需要登录。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 通知列表（支持多条件过滤）。
     *
     * <p>GET /api/notifications -- 分角色返回通知：
     * 普通用户只能看到发给自己的通知；管理员可看到所有通知。
     * 需要登录。
     *
     * @param receiverId 接收人 ID（可选，管理员使用）
     * @param type       通知类型过滤（可选），如 urge / approval / system
     * @param isRead     已读状态过滤（可选）
     * @param keyword    关键字搜索（可选）
     * @return 通知列表
     */
    @GetMapping
    public ApiResponse<List<NotificationDTO>> listNotifications(@RequestParam(required = false) Long receiverId,
                                                                @RequestParam(required = false) String type,
                                                                @RequestParam(required = false) Boolean isRead,
                                                                @RequestParam(required = false) String keyword) {
        return ApiResponse.success(notificationService.listNotifications(receiverId, type, isRead, keyword));
    }

    /**
     * 通知详情。
     *
     * <p>GET /api/notifications/{id} -- 查看单条通知的具体内容。
     * 需要登录，仅允许查看发给自己的通知（管理员不受限）。
     *
     * @param id 通知 ID
     * @return 通知详细信息
     */
    @GetMapping("/{id}")
    public ApiResponse<NotificationDTO> getNotification(@PathVariable Long id) {
        return ApiResponse.success(notificationService.getNotification(id));
    }

    /**
     * 未读通知数量。
     *
     * <p>GET /api/notifications/unread-count -- 返回指定用户的未读通知总数，
     * 用于前端红点/角标提示。需要登录。
     *
     * @param receiverId 接收人 ID（可选，默认当前登录用户）
     * @return 包含 count 字段的 Map
     */
    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> countUnread(@RequestParam(required = false) Long receiverId) {
        return ApiResponse.success(Map.of("count", notificationService.countUnread(receiverId)));
    }

    /**
     * 创建通知（仅超级管理员）。
     *
     * <p>POST /api/notifications -- 创建自定义通知内容，可指定接收人和通知类型。
     * 需要超级管理员权限。
     *
     * @param request 通知创建请求，包含标题、内容、类型、接收人 ID 等
     * @return 创建成功的通知信息
     * @throws AccessDeniedException 当前用户不是超级管理员
     */
    @PostMapping
    public ApiResponse<NotificationDTO> createNotification(@RequestBody NotificationCreateRequest request) {
        requireSuperAdmin();
        return ApiResponse.success(notificationService.createNotification(request));
    }

    /**
     * 编辑通知（仅超级管理员）。
     *
     * <p>PUT /api/notifications/{id} -- 更新通知的标题、内容等字段。
     * 需要超级管理员权限。
     *
     * @param id      通知 ID
     * @param request 更新请求体
     * @return 更新后的通知信息
     * @throws AccessDeniedException 当前用户不是超级管理员
     */
    @PutMapping("/{id}")
    public ApiResponse<NotificationDTO> updateNotification(@PathVariable Long id,
                                                           @RequestBody NotificationUpdateRequest request) {
        requireSuperAdmin();
        return ApiResponse.success(notificationService.updateNotification(id, request));
    }

    /**
     * 标记通知为已读。
     *
     * <p>PUT /api/notifications/{id}/read -- 将指定通知标记为已读状态。
     * 需要登录，仅允许操作发给自己的通知。
     *
     * @param id 通知 ID
     * @return 更新后的通知信息
     */
    @PutMapping("/{id}/read")
    public ApiResponse<NotificationDTO> markRead(@PathVariable Long id) {
        return ApiResponse.success(notificationService.markRead(id));
    }

    /**
     * 标记通知为未读。
     *
     * <p>PUT /api/notifications/{id}/unread -- 将已读通知恢复为未读状态。
     * 需要登录，仅允许操作发给自己的通知。
     *
     * @param id 通知 ID
     * @return 更新后的通知信息
     */
    @PutMapping("/{id}/unread")
    public ApiResponse<NotificationDTO> markUnread(@PathVariable Long id) {
        return ApiResponse.success(notificationService.markUnread(id));
    }

    /**
     * 删除通知。
     *
     * <p>DELETE /api/notifications/{id} -- 删除通知记录。
     * 普通用户仅可删除自己的通知；管理员可删除任意通知。需要登录。
     *
     * @param id 通知 ID
     * @return 空成功响应
     */
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return ApiResponse.success();
    }

    /**
     * 权限校验 -- 确保当前用户是超级管理员，否则抛出权限异常。
     *
     * @throws AccessDeniedException 当前用户不是超级管理员
     */
    private void requireSuperAdmin() {
        if (!SecurityUtils.isSuperAdmin()) {
            throw new AccessDeniedException("only super administrators can create or edit notification content");
        }
    }
}
