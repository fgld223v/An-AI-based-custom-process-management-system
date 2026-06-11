package com.aiflow.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.aiflow.module.user.entity.SysUser;
import com.aiflow.module.user.mapper.SysUserMapper;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final SysUserMapper sysUserMapper;

    public UserDetailsServiceImpl(SysUserMapper sysUserMapper) {
        this.sysUserMapper = sysUserMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username)
                .last("LIMIT 1"));
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }
        return new CurrentUser(user, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
    }
}
