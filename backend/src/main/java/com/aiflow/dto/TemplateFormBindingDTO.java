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
 * 模板表单绑定DTO：包含流程模板及其绑定的表单定义
 */
public class TemplateFormBindingDTO {

    private ProcessTemplateDTO template;

    private FormDefinitionDTO form;
}
