package com.aiflow.dto;

import lombok.Data;

@Data
public class FormCreateRequest {

    private String formCode;
    private String formName;
    private Long bizTypeId;
    private Integer version;
    private String fieldList;
    private String formSchema;
}
