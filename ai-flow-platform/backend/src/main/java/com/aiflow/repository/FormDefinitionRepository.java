package com.aiflow.repository;

import com.aiflow.model.FormDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FormDefinitionRepository extends JpaRepository<FormDefinition, Long> {
}
