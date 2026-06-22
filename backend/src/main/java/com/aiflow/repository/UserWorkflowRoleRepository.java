package com.aiflow.repository;

import com.aiflow.model.UserWorkflowRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserWorkflowRoleRepository extends JpaRepository<UserWorkflowRole, Long> {

    Optional<UserWorkflowRole> findByIdAndDeleted(Long id, Integer deleted);

    Optional<UserWorkflowRole> findByUserIdAndRoleIdAndDepartmentIdAndDeleted(
            Long userId, Long roleId, Long departmentId, Integer deleted);

    Optional<UserWorkflowRole> findByUserIdAndRoleIdAndDepartmentId(
            Long userId, Long roleId, Long departmentId);

    List<UserWorkflowRole> findByRoleIdAndDeletedOrderByIdAsc(Long roleId, Integer deleted);

    List<UserWorkflowRole> findByUserIdAndDeletedOrderByIdAsc(Long userId, Integer deleted);

    long countByRoleIdAndDeleted(Long roleId, Integer deleted);
}
