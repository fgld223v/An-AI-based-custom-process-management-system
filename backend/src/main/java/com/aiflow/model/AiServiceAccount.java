package com.aiflow.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "ai_service_account")
public class AiServiceAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_name", nullable = false, length = 64)
    private String accountName;

    @Column(name = "api_key", nullable = false, length = 128)
    private String apiKey;

    @Column(name = "status", nullable = false,
            columnDefinition = "ENUM('active','disabled')")
    private String status;  // active / disabled

    @Column(name = "granted_endpoints", columnDefinition = "JSON")
    private String grantedEndpoints;

    @Column(name = "rate_limit")
    private Integer rateLimit;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted", nullable = false, columnDefinition = "TINYINT")
    private Integer deleted = 0;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
