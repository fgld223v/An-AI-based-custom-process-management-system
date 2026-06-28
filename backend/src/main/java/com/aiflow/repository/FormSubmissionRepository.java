package com.aiflow.repository;

import com.aiflow.model.FormSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 表单提交记录Repository：按流程实例和节点查询表单提交。
 */
public interface FormSubmissionRepository extends JpaRepository<FormSubmission, Long> {

    List<FormSubmission> findByProcessInstanceIdAndDeletedOrderByUpdatedAtDescCreatedAtDesc(Long processInstanceId, Integer deleted);

    Optional<FormSubmission> findByProcessInstanceIdAndNodeKeyAndDeleted(Long processInstanceId, String nodeKey, Integer deleted);
}