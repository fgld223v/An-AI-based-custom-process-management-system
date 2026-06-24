package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormDefinitionDTO {

    private Long id;
    private String formCode;
    private String formName;
    private Long bizTypeId;
    private Integer version;
    private String status;
    private String fieldList;
    private String formSchema;
    private Long createdBy;
    private String sourceType;
    private Long sourceFormId;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
