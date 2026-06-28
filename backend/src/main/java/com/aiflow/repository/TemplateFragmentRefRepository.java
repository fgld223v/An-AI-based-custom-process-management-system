package com.aiflow.repository;

import com.aiflow.model.TemplateFragmentRef;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 模板片段关联Repository：管理模板与片段的引用关系。
 */
public interface TemplateFragmentRefRepository extends JpaRepository<TemplateFragmentRef, Long> {
}
