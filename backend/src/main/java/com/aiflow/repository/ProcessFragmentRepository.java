package com.aiflow.repository;

import com.aiflow.model.ProcessFragment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProcessFragmentRepository extends JpaRepository<ProcessFragment, Long> {

    Optional<ProcessFragment> findByFragmentCode(String fragmentCode);

    boolean existsByFragmentCode(String fragmentCode);

    List<ProcessFragment> findByDeletedOrderByUpdatedAtDesc(Integer deleted);
}
