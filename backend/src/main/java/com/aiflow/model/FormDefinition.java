package com.aiflow.model;

import com.aiflow.enums.FormStatus;
import com.aiflow.enums.FormStatusConverter;
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
@Table(name = "form_definition")
public class FormDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "form_code")
    private String formCode;

    @Column(name = "form_name")
    private String formName;

    @Column(name = "biz_type_id")
    private Long bizTypeId;

    @Column(name = "version")
    private Integer version;

    @Convert(converter = FormStatusConverter.class)
    @Column(name = "status", columnDefinition = "ENUM('draft','published','disabled')")
    private FormStatus status;

    @Column(name = "field_list", columnDefinition = "JSON")
    private String fieldList;

    @Column(name = "form_schema", columnDefinition = "JSON")
    private String formSchema;

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
