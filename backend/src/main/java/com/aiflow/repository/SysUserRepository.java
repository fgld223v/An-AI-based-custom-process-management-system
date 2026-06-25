package com.aiflow.repository;

import com.aiflow.model.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    boolean existsByUsername(String username);

    Optional<SysUser> findByUsername(String username);

    Optional<SysUser> findByUsernameAndDeleted(String username, Integer deleted);

    Optional<SysUser> findByIdAndDeleted(Long id, Integer deleted);

    List<SysUser> findByDeletedOrderByIdAsc(Integer deleted);

    boolean existsBySupervisorIdAndDeleted(Long supervisorId, Integer deleted);

    boolean existsByDepartmentIdAndDeleted(Long departmentId, Integer deleted);

    long countBySystemRoleAndDeleted(String systemRole, Integer deleted);

    /** 按 ID 查询未删除的用户 */
    Optional<SysUser> findByIdAndEnabledNotNull(Long id);
}
