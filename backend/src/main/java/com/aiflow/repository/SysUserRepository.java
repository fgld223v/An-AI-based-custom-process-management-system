package com.aiflow.repository;

import com.aiflow.model.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    boolean existsByUsername(String username);

    Optional<SysUser> findByUsername(String username);

    /** 查询未删除的用户（用于列表） */
    List<SysUser> findAllByOrderByIdAsc();

    /** 按 ID 查询未删除的用户 */
    Optional<SysUser> findByIdAndEnabledNotNull(Long id);
}
