package com.aiflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class FormUpdateRequest {

    @NotBlank(message = "不能为空")
    private String formName;

    private Long bizTypeId;
    private String fieldList;
    private String formSchema;
}
