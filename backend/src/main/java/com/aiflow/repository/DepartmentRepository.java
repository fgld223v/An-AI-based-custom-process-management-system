package com.aiflow.repository;

import com.aiflow.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    boolean existsByDeptCode(String deptCode);

    Department findFirstByDeptCode(String deptCode);

    Optional<Department> findByIdAndDeleted(Long id, Integer deleted);

    List<Department> findByDeletedOrderBySortOrder(Integer deleted);
}
