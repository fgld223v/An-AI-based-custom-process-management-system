package com.aiflow.model;

import com.aiflow.enums.FragmentSyncStatus;
import com.aiflow.enums.FragmentSyncStatusConverter;
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
@Table(name = "template_fragment_ref")
public class TemplateFragmentRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "fragment_id")
    private Long fragmentId;

    @Column(name = "fragment_version")
    private Integer fragmentVersion;

    @Convert(converter = FragmentSyncStatusConverter.class)
    @Column(name = "sync_status", columnDefinition = "ENUM('synced','pending_update','unbound')")
    private FragmentSyncStatus syncStatus;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
