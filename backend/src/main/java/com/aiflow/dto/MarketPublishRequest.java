package com.aiflow.dto;

import lombok.Data;

@Data
public class MarketPublishRequest {

    private Long templateId;
    private Long publisherId;
    private String title;
    private String description;
    private String coverUrl;
    private String tags;
}
