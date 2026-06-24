package com.aiflow.repository;

import com.aiflow.model.SystemConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SystemConfigRepository extends JpaRepository<SystemConfig, Long> {

    Optional<SystemConfig> findByConfigKeyAndDeleted(String configKey, int deleted);

    List<SystemConfig> findByDeletedOrderByConfigKeyAsc(int deleted);
}
