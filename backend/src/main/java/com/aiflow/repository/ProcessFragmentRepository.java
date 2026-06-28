package com.aiflow.repository;

import com.aiflow.model.ProcessFragment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 流程片段Repository：按编码查询流程片段。
 */
public interface ProcessFragmentRepository extends JpaRepository<ProcessFragment, Long> {

    Optional<ProcessFragment> findByFragmentCode(String fragmentCode);

    boolean existsByFragmentCode(String fragmentCode);

    List<ProcessFragment> findByDeletedOrderByUpdatedAtDesc(Integer deleted);
}
