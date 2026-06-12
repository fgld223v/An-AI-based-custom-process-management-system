package com.aiflow.repository;

import com.aiflow.model.FormSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FormSubmissionRepository extends JpaRepository<FormSubmission, Long> {

    List<FormSubmission> findByProcessInstanceIdAndDeletedOrderByUpdateTimeDescCreateTimeDesc(Long processInstanceId, Integer deleted);

    List<FormSubmission> findByProcessInstanceIdAndDeleted(Long processInstanceId, Integer deleted);

    Optional<FormSubmission> findByProcessInstanceIdAndNodeKeyAndDeleted(Long processInstanceId, String nodeKey, Integer deleted);
}
