package com.aiflow.model;

import com.aiflow.enums.MarketType;
import com.aiflow.enums.MarketTypeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模板市场表 (template_market)
 * 存储发布到模板市场的流程模板或片段，供其他用户浏览、复制和使用。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "template_market")
public class TemplateMarket {

    /** 市场条目主键 ID */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** 源模板/片段 ID */
    @Column(name = "source_id")
    private Long sourceId;

    /** 市场资源类型：template(完整模板) / fragment(流程片段) */
    @Convert(converter = MarketTypeConverter.class)
    @Column(name = "type", columnDefinition = "ENUM('template','fragment')")
    private MarketType type;

    /** 市场展示标题 */
    @Column(name = "title")
    private String title;

    /** 市场展示描述 */
    @Column(name = "description")
    private String description;

    /** 封面图片 URL */
    @Column(name = "cover_url")
    private String coverUrl;

    /** 业务类型 ID */
    @Column(name = "biz_type_id")
    private Long bizTypeId;

    /** 发布者用户 ID */
    @Column(name = "publisher_id")
    private Long publisherId;

    /** 使用次数统计 */
    @Column(name = "use_count")
    private Long useCount;

    /** 平均评分 */
    @Column(name = "rating")
    private BigDecimal rating;

    /** 标签列表 JSON */
    @Column(name = "tags", columnDefinition = "JSON")
    private String tags;

    /** 发布时间 */
    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    /** 创建时间 */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 最后更新时间 */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 逻辑删除标记：1-已删除，0-正常 */
    @Column(name = "deleted", columnDefinition = "TINYINT")
    private Integer deleted;
}
