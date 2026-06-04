package com.aiflow.model;

import com.aiflow.enums.FragmentStatus;
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
@Table(name = "process_fragment")
public class ProcessFragment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fragment_name", nullable = false, length = 200)
    private String fragmentName;

    @Column(name = "fragment_code", unique = true, nullable = false, length = 100)
    private String fragmentCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private FragmentStatus status;

    @Column(name = "flow_json", columnDefinition = "JSON")
    private String flowJson;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "usage_count")
    private Integer usageCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
