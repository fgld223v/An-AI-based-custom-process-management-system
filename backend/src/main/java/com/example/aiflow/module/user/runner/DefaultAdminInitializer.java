package com.example.aiflow.module.user.runner;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.aiflow.module.user.entity.SysUser;
import com.example.aiflow.module.user.mapper.SysUserMapper;
import com.example.aiflow.security.RoleType;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DefaultAdminInitializer implements CommandLineRunner {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;

    public DefaultAdminInitializer(SysUserMapper sysUserMapper, PasswordEncoder passwordEncoder) {
        this.sysUserMapper = sysUserMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        Long count = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, "admin"));
        if (count > 0) {
            return;
        }
        SysUser user = new SysUser();
        user.setUsername("admin");
        user.setPassword(passwordEncoder.encode("admin123"));
        user.setNickname("系统管理员");
        user.setRole(RoleType.ADMIN.name());
        user.setEnabled(true);
        sysUserMapper.insert(user);
    }
}
