package com.aiflow.security;

import com.aiflow.entity.UserEntity;
import com.aiflow.mapper.SysUserMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aiflow.entity.UserEntity;
import com.aiflow.model.SysUser;
import com.aiflow.repository.SysUserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 使用 JPA Repository 加载用户，避免 MyBatis-Plus JDK 代理问题。
 */
@Slf4j
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserRepository sysUserRepository;

    public UserDetailsServiceImpl(SysUserRepository sysUserRepository) {
        this.sysUserRepository = sysUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser jpaUser = sysUserRepository.findByUsername(username)
                .orElseThrow(() -> {
                    log.warn("用户不存在: {}", username);
                    return new UsernameNotFoundException("用户不存在");
                });

        log.info("用户登录: username={}, systemRole={}, role={}, enabled={}",
                jpaUser.getUsername(), jpaUser.getSystemRole(), jpaUser.getRole(), jpaUser.getEnabled());

        // 转换为 MyBatis-Plus UserEntity（CurrentUser 依赖）
        UserEntity userEntity = new UserEntity();
        userEntity.setId(jpaUser.getId());
        userEntity.setUsername(jpaUser.getUsername());
        userEntity.setPassword(jpaUser.getPassword());
        userEntity.setNickname(jpaUser.getNickname());
        userEntity.setRole(jpaUser.getRole());
        userEntity.setSystemRole(jpaUser.getSystemRole());
        userEntity.setDepartmentId(jpaUser.getDepartmentId());
        userEntity.setSupervisorId(jpaUser.getSupervisorId());
        userEntity.setManagedBizTypeIds(jpaUser.getManagedBizTypeIds());
        userEntity.setEnabled(jpaUser.getEnabled());
        userEntity.setDeleted(jpaUser.getDeleted());
        userEntity.setCreatedTime(jpaUser.getCreatedTime());
        userEntity.setUpdatedTime(jpaUser.getUpdatedTime());

        String sysRole = userEntity.getSystemRole() != null
                ? userEntity.getSystemRole().toUpperCase()
                : "NORMAL_USER";
        String legacyRole = userEntity.getRole() != null
                ? userEntity.getRole().toUpperCase()
                : "USER";
        List<GrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority("ROLE_" + sysRole),
                new SimpleGrantedAuthority("ROLE_" + legacyRole)
        );
        return new CurrentUser(userEntity, authorities);
    }
}
