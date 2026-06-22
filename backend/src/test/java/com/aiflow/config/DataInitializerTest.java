package com.aiflow.config;

import com.aiflow.model.BizTypeDict;
import com.aiflow.model.Department;
import com.aiflow.model.SysUser;
import com.aiflow.repository.BizTypeDictRepository;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.SysUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataInitializerTest {

    @Mock
    private SysUserRepository sysUserRepository;

    @Mock
    private BizTypeDictRepository bizTypeDictRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void existingBusinessConfigurationIsNotOverwrittenOnRestart() {
        Department finance = department(3L, "finance_dept", 2L);
        when(departmentRepository.findFirstByDeptCode(any())).thenAnswer(invocation -> {
            String code = invocation.getArgument(0);
            return "finance_dept".equals(code) ? finance : department(10L, code, null);
        });
        when(sysUserRepository.findByUsername(any())).thenAnswer(invocation ->
                Optional.of(user(invocation.getArgument(0))));
        when(bizTypeDictRepository.findByTypeCode(any())).thenAnswer(invocation ->
                Optional.of(BizTypeDict.builder().id(1L).typeCode(invocation.getArgument(0)).build()));

        new DataInitializer(sysUserRepository, bizTypeDictRepository,
                departmentRepository, passwordEncoder).run();

        assertThat(finance.getLeaderUserId()).isEqualTo(2L);
        verify(departmentRepository, never()).save(any());
        verify(sysUserRepository, never()).save(any());
        verify(bizTypeDictRepository, never()).save(any());
        verify(passwordEncoder, never()).encode(any());
    }

    private Department department(Long id, String code, Long leaderUserId) {
        return Department.builder()
                .id(id)
                .deptCode(code)
                .deptName(code)
                .leaderUserId(leaderUserId)
                .status(1)
                .deleted(0)
                .build();
    }

    private SysUser user(String username) {
        return SysUser.builder()
                .id(1L)
                .username(username)
                .enabled(1)
                .deleted(0)
                .build();
    }
}
