package com.aiflow.model;

import com.aiflow.enums.TaskStatus;
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
@Table(name = "task")
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_code", unique = true, nullable = false, length = 100)
    private String taskCode;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TaskStatus status;

    @Column(name = "assignee_id", nullable = false)
    private Long assigneeId;

    @Column(name = "task_title", length = 200)
    private String taskTitle;

    @Column(name = "task_desc", length = 1000)
    private String taskDesc;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "due_time")
    private LocalDateTime dueTime;

    @Column(name = "created_time", nullable = false)
    private LocalDateTime createdTime;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    @Column(name = "duration")
    private Long duration;

    @Column(name = "delegated_to")
    private Long delegatedTo;

    @Column(name = "delegated_time")
    private LocalDateTime delegatedTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
