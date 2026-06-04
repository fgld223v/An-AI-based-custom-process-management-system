package com.aiflow.repository;

import com.aiflow.model.ProcessFragment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessFragmentRepository extends JpaRepository<ProcessFragment, Long> {
}
