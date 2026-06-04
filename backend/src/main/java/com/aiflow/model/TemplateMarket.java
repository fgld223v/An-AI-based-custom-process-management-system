package com.aiflow.model;

import com.aiflow.enums.MarketType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private MarketType type;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "code", unique = true, nullable = false, length = 100)
    private String code;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "preview_image", length = 500)
    private String previewImage;

    @Column(name = "download_count")
    private Integer downloadCount;

    @Column(name = "rating")
    private Double rating;

    @Column(name = "status")
    private Integer status;

    @Column(name = "provider_id")
    private Long providerId;

    @Column(name = "provider_name", length = 100)
    private String providerName;

    @Column(name = "version")
    private String version;

    @Column(name = "compatible_version", length = 100)
    private String compatibleVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
