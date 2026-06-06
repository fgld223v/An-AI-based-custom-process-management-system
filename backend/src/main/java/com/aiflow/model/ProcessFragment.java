package com.aiflow.model;

import com.aiflow.enums.FragmentStatus;
import com.aiflow.enums.FragmentStatusConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
    @Column(name = "id")
    private Long id;

    @Column(name = "fragment_code")
    private String fragmentCode;

    @Column(name = "fragment_name")
    private String fragmentName;

    @Column(name = "biz_type_id")
    private Long bizTypeId;

    @Column(name = "description")
    private String description;

    @Column(name = "fragment_type")
    private String fragmentType;

    @Convert(converter = FragmentStatusConverter.class)
    @Column(name = "status", columnDefinition = "ENUM('draft','published','disabled')")
    private FragmentStatus status;

    @Column(name = "bpmn_xml", columnDefinition = "LONGTEXT")
    private String bpmnXml;

    @Column(name = "node_config", columnDefinition = "JSON")
    private String nodeConfig;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted", columnDefinition = "TINYINT")
    private Integer deleted;
}
