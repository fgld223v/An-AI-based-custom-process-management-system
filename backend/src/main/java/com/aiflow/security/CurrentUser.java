package com.aiflow.security;

import com.aiflow.entity.UserEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

/**
 * 当前登录用户主体，实现 Spring Security UserDetails 接口。
 * 封装用户实体信息，提供 id、角色、部门、主管等字段的便捷访问。
 */
public class CurrentUser implements UserDetails {

    private final UserEntity user;
    private final List<GrantedAuthority> authorities;

    public CurrentUser(UserEntity user, List<GrantedAuthority> authorities) {
        this.user = user;
        this.authorities = authorities;
    }

    public UserEntity getUser() {
        return user;
    }

    public Long getId() {
        return user.getId();
    }

    public String getNickname() {
        return user.getNickname();
    }

    public String getRole() {
        return user.getRole();
    }

    public String getSystemRole() {
        return user.getSystemRole();
    }

    public Long getDepartmentId() {
        return user.getDepartmentId();
    }

    public Long getSupervisorId() {
        return user.getSupervisorId();
    }

    public String getManagedBizTypeIds() {
        return user.getManagedBizTypeIds();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override
    public boolean isEnabled() {
        return user.getEnabled() != null && user.getEnabled() == 1
                && !Integer.valueOf(1).equals(user.getDeleted());
    }
}
