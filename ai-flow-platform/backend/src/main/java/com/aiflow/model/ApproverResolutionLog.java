package com.aiflow.model;

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
@Table(name = "approver_resolution_log")
public class ApproverResolutionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "node_id", nullable = false)
    private Long nodeId;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "rule_expression", length = 500)
    private String ruleExpression;

    @Column(name = "resolved_users", length = 500)
    private String resolvedUsers;

    @Column(name = "resolve_time", nullable = false)
    private LocalDateTime resolveTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
