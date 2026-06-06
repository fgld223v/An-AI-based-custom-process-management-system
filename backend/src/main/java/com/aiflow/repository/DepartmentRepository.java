package com.aiflow.repository;

import com.aiflow.model.Department;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DepartmentRepository extends JpaRepository<Department, Long> {

    boolean existsByDeptCode(String deptCode);

    Department findFirstByDeptCode(String deptCode);
}
