package com.aiflow.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
/**
 * 模板市场DTO：包含市场中的模板/片段信息、使用统计及评分
 */
public class TemplateMarketDTO {

    private Long id;
    private Long sourceId;
    private String type;
    private String title;
    private String description;
    private String coverUrl;
    private Long bizTypeId;
    private Long publisherId;
    private Long useCount;
    private BigDecimal rating;
    private String tags;
    private LocalDateTime publishedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
