package com.aiflow.repository;

import com.aiflow.model.FormDefinition;
import com.aiflow.enums.FormStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 表单定义Repository：按编码/版本、状态、创建者查询表单定义。
 */
public interface FormDefinitionRepository extends JpaRepository<FormDefinition, Long> {

    Optional<FormDefinition> findByFormCodeAndVersion(String formCode, Integer version);

    Optional<FormDefinition> findByIdAndDeleted(Long id, Integer deleted);

    boolean existsByFormCodeAndVersion(String formCode, Integer version);

    List<FormDefinition> findByDeletedOrderByUpdatedAtDesc(Integer deleted);

    List<FormDefinition> findByStatusAndDeletedOrderByUpdatedAtDesc(FormStatus status, Integer deleted);

    List<FormDefinition> findByCreatedByAndDeletedOrderByUpdatedAtDesc(Long createdBy, Integer deleted);

    List<FormDefinition> findByCreatedByAndStatusAndDeletedOrderByUpdatedAtDesc(
            Long createdBy, FormStatus status, Integer deleted);

    List<FormDefinition> findByCreatedByIsNullAndDeleted(Integer deleted);
}
