package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BizTypeDTO {

    private Long id;
    private Long parentId;
    private String typeCode;
    private String typeName;
    private String description;
    private Integer sortOrder;
}
