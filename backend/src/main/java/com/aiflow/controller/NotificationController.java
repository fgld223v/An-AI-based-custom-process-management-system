package com.aiflow.controller;

import com.aiflow.common.ApiResponse;
import com.aiflow.dto.NotificationCreateRequest;
import com.aiflow.dto.NotificationDTO;
import com.aiflow.dto.NotificationUpdateRequest;
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

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<List<NotificationDTO>> listNotifications(@RequestParam(required = false) Long receiverId,
                                                                @RequestParam(required = false) String type,
                                                                @RequestParam(required = false) Boolean isRead,
                                                                @RequestParam(required = false) String keyword) {
        return ApiResponse.success(notificationService.listNotifications(receiverId, type, isRead, keyword));
    }

    @GetMapping("/{id}")
    public ApiResponse<NotificationDTO> getNotification(@PathVariable Long id) {
        return ApiResponse.success(notificationService.getNotification(id));
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Long>> countUnread(@RequestParam(required = false) Long receiverId) {
        return ApiResponse.success(Map.of("count", notificationService.countUnread(receiverId)));
    }

    @PostMapping
    public ApiResponse<NotificationDTO> createNotification(@RequestBody NotificationCreateRequest request) {
        return ApiResponse.success(notificationService.createNotification(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<NotificationDTO> updateNotification(@PathVariable Long id,
                                                           @RequestBody NotificationUpdateRequest request) {
        return ApiResponse.success(notificationService.updateNotification(id, request));
    }

    @PutMapping("/{id}/read")
    public ApiResponse<NotificationDTO> markRead(@PathVariable Long id) {
        return ApiResponse.success(notificationService.markRead(id));
    }

    @PutMapping("/{id}/unread")
    public ApiResponse<NotificationDTO> markUnread(@PathVariable Long id) {
        return ApiResponse.success(notificationService.markUnread(id));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteNotification(@PathVariable Long id) {
        notificationService.deleteNotification(id);
        return ApiResponse.success();
    }
}
