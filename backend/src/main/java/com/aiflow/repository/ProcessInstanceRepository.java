package com.aiflow.repository;

import com.aiflow.model.ProcessInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessInstanceRepository extends JpaRepository<ProcessInstance, Long> {
}
