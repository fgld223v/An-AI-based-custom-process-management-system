package com.aiflow.repository;

import com.aiflow.model.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    boolean existsByUsername(String username);

    Optional<SysUser> findByUsername(String username);
}
