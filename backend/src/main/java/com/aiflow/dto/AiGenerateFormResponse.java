package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
/**
 * AI生成表单响应DTO：包含字段列表和表单Schema
 */
public class AiGenerateFormResponse {
    private String fieldList;
    private String formSchema;
}
