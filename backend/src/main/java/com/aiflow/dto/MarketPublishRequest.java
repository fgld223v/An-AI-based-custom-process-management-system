package com.aiflow.dto;

import lombok.Data;

@Data
/**
 * 模板市场发布请求DTO：将模板发布到市场
 */
public class MarketPublishRequest {

    private Long templateId;
    private Long publisherId;
    private String title;
    private String description;
    private String coverUrl;
    private String tags;
}
