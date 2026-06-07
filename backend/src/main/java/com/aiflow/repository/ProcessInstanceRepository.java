package com.aiflow.repository;

import com.aiflow.model.ProcessInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProcessInstanceRepository extends JpaRepository<ProcessInstance, Long> {

    List<ProcessInstance> findByApplicantIdAndDeletedOrderByCreatedAtDesc(Long applicantId, Integer deleted);

    List<ProcessInstance> findByStatusAndDeletedOrderByCreatedAtDesc(String status, Integer deleted);

    List<ProcessInstance> findByDeletedOrderByCreatedAtDesc(Integer deleted);

    ProcessInstance findByInstanceCodeAndDeleted(String instanceCode, Integer deleted);

    ProcessInstance findByFlowableProcessInstanceIdAndDeleted(String flowableProcessInstanceId, Integer deleted);
}
