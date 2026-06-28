package com.aiflow.repository;

import com.aiflow.model.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 系统配置Repository：按配置键查询系统配置项。
 */
@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    Optional<SystemConfig> findByConfigKeyAndDeleted(String configKey, int deleted);

    List<SystemConfig> findByDeletedOrderByConfigKeyAsc(int deleted);
}
