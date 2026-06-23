package com.aiflow.service;

import com.aiflow.dto.ProcessRoutePreviewDTO;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.model.SysUser;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.service.impl.NodeConfigParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProcessRoutePreviewService {

    private final NodeConfigParser nodeConfigParser;
    private final ApproverResolverService approverResolverService;
    private final SysUserRepository sysUserRepository;

    @Transactional(readOnly = true)
    public ProcessRoutePreviewDTO preview(ProcessTemplate template, Long applicantId) {
        SysUser applicant = activeUser(applicantId);
        List<ProcessRoutePreviewDTO.ApprovalStep> steps = nodeConfigParser
                .asOrderedList(template.getNodeConfig())
                .stream()
                .filter(config -> "approval".equalsIgnoreCase(stringValue(config.get("businessType"))))
                .map(config -> toStep(config, applicantId))
                .toList();
        return ProcessRoutePreviewDTO.builder()
                .templateId(template.getId())
                .applicantId(applicantId)
                .applicantName(displayName(applicant))
                .approvalSteps(steps)
                .build();
    }

    private ProcessRoutePreviewDTO.ApprovalStep toStep(Map<String, Object> config, Long applicantId) {
        String nodeKey = firstText(config.get("nodeId"), config.get("nodeKey"));
        String strategy = firstText(config.get("assignStrategy"), config.get("assigneeType"));
        String assignValue = assignValue(config);
        List<Long> approverIds = approverResolverService.resolveApproversForApplicant(
                applicantId, nodeKey, strategy, assignValue);
        Map<Long, SysUser> users = sysUserRepository.findAllById(approverIds).stream()
                .filter(this::isActive)
                .collect(Collectors.toMap(SysUser::getId, Function.identity()));
        List<ProcessRoutePreviewDTO.Approver> approvers = approverIds.stream()
                .map(users::get)
                .filter(java.util.Objects::nonNull)
                .map(user -> ProcessRoutePreviewDTO.Approver.builder()
                        .userId(user.getId())
                        .userName(displayName(user))
                        .departmentId(user.getDepartmentId())
                        .build())
                .toList();
        return ProcessRoutePreviewDTO.ApprovalStep.builder()
                .nodeKey(nodeKey)
                .nodeName(firstText(config.get("nodeName"), "审批处理"))
                .approvalMode(firstText(config.get("approvalMode"), "SINGLE"))
                .assignStrategy(strategy)
                .approvers(approvers)
                .build();
    }

    private String assignValue(Map<String, Object> config) {
        Object value = firstNonNull(config.get("assignValue"), config.get("assigneeValue"));
        if (value == null && config.get("assigneeUserIds") instanceof Collection<?> ids) {
            value = ids;
        }
        if (value == null) value = config.get("assigneeRoleCode");
        return value == null ? "" : String.valueOf(value);
    }

    private SysUser activeUser(Long userId) {
        return sysUserRepository.findById(userId)
                .filter(this::isActive)
                .orElseThrow(() -> new IllegalStateException("当前申请人不存在或账号已停用"));
    }

    private boolean isActive(SysUser user) {
        return user != null
                && !Integer.valueOf(1).equals(user.getDeleted())
                && Integer.valueOf(1).equals(user.getEnabled());
    }

    private String displayName(SysUser user) {
        return hasText(user.getNickname()) ? user.getNickname() : user.getUsername();
    }

    private Object firstNonNull(Object... values) {
        for (Object value : values) if (value != null) return value;
        return null;
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = stringValue(value);
            if (hasText(text)) return text;
        }
        return "";
    }

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
