package com.aiflow.repository;

import com.aiflow.model.WorkflowRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 工作流角色Repository：按编码查询，支持启用状态过滤。
 */
public interface WorkflowRoleRepository extends JpaRepository<WorkflowRole, Long> {

    Optional<WorkflowRole> findByIdAndDeleted(Long id, Integer deleted);

    Optional<WorkflowRole> findByRoleCodeAndDeleted(String roleCode, Integer deleted);

    boolean existsByRoleCode(String roleCode);

    List<WorkflowRole> findByDeletedOrderByRoleNameAsc(Integer deleted);

    List<WorkflowRole> findByDeletedAndEnabledOrderByRoleNameAsc(Integer deleted, Integer enabled);
}
