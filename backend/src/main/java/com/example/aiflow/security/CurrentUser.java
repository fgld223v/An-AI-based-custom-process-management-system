package com.example.aiflow.security;

import com.example.aiflow.module.user.entity.SysUser;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public class CurrentUser implements UserDetails {

    private final SysUser user;
    private final List<GrantedAuthority> authorities;

    public CurrentUser(SysUser user, List<GrantedAuthority> authorities) {
        this.user = user;
        this.authorities = authorities;
    }

    public SysUser getUser() {
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
        return Boolean.TRUE.equals(user.getEnabled());
    }
}
