package com.aiflow.service;

import com.aiflow.dto.ProcessRoutePreviewDTO;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.model.SysUser;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.service.impl.NodeConfigParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProcessRoutePreviewServiceTest {

    private final NodeConfigParser nodeConfigParser = mock(NodeConfigParser.class);
    private final ApproverResolverService approverResolverService = mock(ApproverResolverService.class);
    private final SysUserRepository sysUserRepository = mock(SysUserRepository.class);
    private final ProcessRoutePreviewService service = new ProcessRoutePreviewService(
            nodeConfigParser, approverResolverService, sysUserRepository);

    @Test
    void previewResolvesApprovalUsersForCurrentApplicant() {
        ProcessTemplate template = ProcessTemplate.builder().id(8L).nodeConfig("{}").build();
        SysUser applicant = user(10L, "Applicant");
        SysUser approver = user(20L, "Manager");
        when(sysUserRepository.findById(10L)).thenReturn(Optional.of(applicant));
        when(nodeConfigParser.asOrderedList("{}")).thenReturn(List.of(
                Map.of("businessType", "start", "nodeId", "Start_1"),
                Map.of("businessType", "approval", "nodeId", "Approve_1", "nodeName", "Manager approval",
                        "assignStrategy", "DEPARTMENT_MANAGER", "approvalMode", "SINGLE")));
        when(approverResolverService.resolveApproversForApplicant(
                10L, "Approve_1", "DEPARTMENT_MANAGER", "")).thenReturn(List.of(20L));
        when(sysUserRepository.findAllById(List.of(20L))).thenReturn(List.of(approver));

        ProcessRoutePreviewDTO result = service.preview(template, 10L);

        assertThat(result.getApplicantName()).isEqualTo("Applicant");
        assertThat(result.getApprovalSteps()).hasSize(1);
        assertThat(result.getApprovalSteps().get(0).getApprovers())
                .extracting(ProcessRoutePreviewDTO.Approver::getUserName)
                .containsExactly("Manager");
    }

    private SysUser user(Long id, String name) {
        return SysUser.builder().id(id).nickname(name).enabled(1).deleted(0).build();
    }
}
