package com.aiflow.repository;

import com.aiflow.model.ProcessTemplate;
import com.aiflow.enums.ProcessResourceType;
import com.aiflow.enums.TemplateStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

public interface ProcessTemplateRepository extends JpaRepository<ProcessTemplate, Long> {

    Optional<ProcessTemplate> findByTemplateCodeAndVersion(String templateCode, Integer version);

    Optional<ProcessTemplate> findByIdAndDeleted(Long id, Integer deleted);

    boolean existsByTemplateCodeAndVersion(String templateCode, Integer version);

    List<ProcessTemplate> findByDeletedOrderByUpdatedAtDesc(Integer deleted);

    List<ProcessTemplate> findByCreatedByAndDeletedOrderByUpdatedAtDesc(Long createdBy, Integer deleted);

    List<ProcessTemplate> findByResourceTypeAndDeletedOrderByUpdatedAtDesc(ProcessResourceType resourceType,
                                                                            Integer deleted);

    List<ProcessTemplate> findByCreatedByAndResourceTypeAndDeletedOrderByUpdatedAtDesc(Long createdBy,
                                                                                        ProcessResourceType resourceType,
                                                                                        Integer deleted);

    List<ProcessTemplate> findByResourceTypeAndStatusAndFlowableDeploymentIdIsNotNullAndFlowableProcessDefinitionIdIsNotNullAndDeletedOrderByUpdatedAtDesc(
            ProcessResourceType resourceType, TemplateStatus status, Integer deleted);

    Optional<ProcessTemplate> findByIdAndResourceTypeAndStatusAndFlowableDeploymentIdIsNotNullAndFlowableProcessDefinitionIdIsNotNullAndDeleted(
            Long id, ProcessResourceType resourceType, TemplateStatus status, Integer deleted);

    List<ProcessTemplate> findByResourceTypeIsNullAndDeleted(Integer deleted);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<ProcessTemplate> findByTemplateCodeAndResourceTypeAndDeletedOrderByVersionDesc(
            String templateCode, ProcessResourceType resourceType, Integer deleted);

    List<ProcessTemplate> findByTemplateCodeAndResourceTypeAndStatusAndDeleted(
            String templateCode, ProcessResourceType resourceType, TemplateStatus status, Integer deleted);
}
