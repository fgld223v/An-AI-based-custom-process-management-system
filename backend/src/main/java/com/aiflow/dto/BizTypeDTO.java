package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * 业务类型DTO：用于业务分类字典数据传输
 */
public class BizTypeDTO {

    private Long id;
    private Long parentId;
    private String typeCode;
    private String typeName;
    private String description;
    private Integer sortOrder;
}
