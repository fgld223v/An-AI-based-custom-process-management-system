package com.aiflow.repository;

import com.aiflow.model.TemplateFragmentRef;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface TemplateFragmentRefRepository extends JpaRepository<TemplateFragmentRef, Long> {
}
