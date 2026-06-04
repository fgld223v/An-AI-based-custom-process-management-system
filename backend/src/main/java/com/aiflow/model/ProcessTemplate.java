package com.aiflow.model;

import com.aiflow.enums.TemplateSourceType;
import com.aiflow.enums.TemplateStatus;
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
@Table(name = "process_template")
public class ProcessTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_name", nullable = false, length = 200)
    private String templateName;

    @Column(name = "template_code", unique = true, nullable = false, length = 100)
    private String templateCode;

    @Column(name = "biz_type_id")
    private Long bizTypeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TemplateStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private TemplateSourceType sourceType;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "form_def_id")
    private Long formDefId;

    @Column(name = "flow_json", columnDefinition = "JSON")
    private String flowJson;

    @Column(name = "ai_suggestion", length = 2000)
    private String aiSuggestion;

    @Column(name = "version")
    private Integer version;

    @Column(name = "used_count")
    private Integer usedCount;

    @Column(name = "reviewer_id")
    private Long reviewerId;

    @Column(name = "review_time")
    private LocalDateTime reviewTime;

    @Column(name = "review_comment", length = 500)
    private String reviewComment;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "updated_by")
    private Long updatedBy;
}
