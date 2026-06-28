package com.aiflow.repository;

import com.aiflow.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 部门Repository：按编码、父级ID、负责人等查询部门信息。
 */
public interface DepartmentRepository extends JpaRepository<Department, Long> {

    boolean existsByDeptCode(String deptCode);

    Department findFirstByDeptCode(String deptCode);

    Optional<Department> findByIdAndDeleted(Long id, Integer deleted);

    List<Department> findByDeletedOrderBySortOrder(Integer deleted);

    boolean existsByLeaderUserIdAndDeleted(Long leaderUserId, Integer deleted);

    boolean existsByParentIdAndDeleted(Long parentId, Integer deleted);
}
