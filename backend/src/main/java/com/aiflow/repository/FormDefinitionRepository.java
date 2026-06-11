package com.aiflow.repository;

import com.aiflow.model.FormDefinition;
import com.aiflow.enums.FormStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormDefinitionRepository extends JpaRepository<FormDefinition, Long> {

    Optional<FormDefinition> findByFormCodeAndVersion(String formCode, Integer version);

    Optional<FormDefinition> findByIdAndDeleted(Long id, Integer deleted);

    boolean existsByFormCodeAndVersion(String formCode, Integer version);

    List<FormDefinition> findByDeletedOrderByUpdatedAtDesc(Integer deleted);

    List<FormDefinition> findByStatusAndDeletedOrderByUpdatedAtDesc(FormStatus status, Integer deleted);
}
