package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TemplateFormBindingDTO {

    private ProcessTemplateDTO template;

    private FormDefinitionDTO form;
}
