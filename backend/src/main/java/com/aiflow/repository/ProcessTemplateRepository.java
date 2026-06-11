package com.aiflow.repository;

import com.aiflow.model.ProcessTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProcessTemplateRepository extends JpaRepository<ProcessTemplate, Long> {

    Optional<ProcessTemplate> findByTemplateCodeAndVersion(String templateCode, Integer version);

    Optional<ProcessTemplate> findByIdAndDeleted(Long id, Integer deleted);

    boolean existsByTemplateCodeAndVersion(String templateCode, Integer version);

    List<ProcessTemplate> findByDeletedOrderByUpdatedAtDesc(Integer deleted);
}
