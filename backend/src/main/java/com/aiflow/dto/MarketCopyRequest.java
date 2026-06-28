package com.aiflow.dto;

import lombok.Data;

@Data
/**
 * 模板市场复制请求DTO：从市场复制模板到个人空间
 */
public class MarketCopyRequest {

    private Long userId;
    private String newTemplateName;
}
