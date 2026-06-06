package com.aiflow.dto;

import lombok.Data;

@Data
public class FormUpdateRequest {

    private String formName;
    private Long bizTypeId;
    private String fieldList;
    private String formSchema;
}
