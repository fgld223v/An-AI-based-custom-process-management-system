package com.aiflow.model;

import com.aiflow.enums.NotificationType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private NotificationType type;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "content", length = 1000)
    private String content;

    @Column(name = "related_id")
    private Long relatedId;

    @Column(name = "read")
    private Integer read;

    @Column(name = "read_time")
    private LocalDateTime readTime;

    @Column(name = "send_time", nullable = false)
    private LocalDateTime sendTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
