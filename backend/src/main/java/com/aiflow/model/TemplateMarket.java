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

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "template_market")
public class TemplateMarket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "source_id")
    private Long sourceId;

    @Convert(converter = MarketTypeConverter.class)
    @Column(name = "type", columnDefinition = "ENUM('template','fragment')")
    private MarketType type;

    @Column(name = "title")
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "cover_url")
    private String coverUrl;

    @Column(name = "biz_type_id")
    private Long bizTypeId;

    @Column(name = "publisher_id")
    private Long publisherId;

    @Column(name = "use_count")
    private Long useCount;

    @Column(name = "rating")
    private BigDecimal rating;

    @Column(name = "tags", columnDefinition = "JSON")
    private String tags;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "deleted", columnDefinition = "TINYINT")
    private Integer deleted;
}
