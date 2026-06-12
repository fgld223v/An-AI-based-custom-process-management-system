package com.aiflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class FormCreateRequest {

    @NotBlank(message = "不能为空")
    private String formCode;

    @NotBlank(message = "不能为空")
    private String formName;

    @NotNull(message = "不能为空")
    private Long bizTypeId;

    private Integer version;
    private String fieldList;
    private String formSchema;
}
