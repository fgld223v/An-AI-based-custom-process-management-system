package com.aiflow.model;

import com.aiflow.enums.NodeType;
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
@Table(name = "flow_node")
public class FlowNode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "instance_id", nullable = false)
    private Long instanceId;

    @Column(name = "node_key", nullable = false, length = 100)
    private String nodeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false, length = 30)
    private NodeType nodeType;

    @Column(name = "node_name", length = 200)
    private String nodeName;

    @Column(name = "node_config", columnDefinition = "JSON")
    private String nodeConfig;

    @Column(name = "assignee_ids", length = 500)
    private String assigneeIds;

    @Column(name = "cc_user_ids", length = 500)
    private String ccUserIds;

    @Column(name = "order_num")
    private Integer orderNum;

    @Column(name = "parent_node_id")
    private Long parentNodeId;

    @Column(name = "completed")
    private Integer completed;

    @Column(name = "completed_time")
    private LocalDateTime completedTime;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
