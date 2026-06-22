package com.aiflow.service;

import org.flowable.engine.RuntimeService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ApprovalVariableService {

    public static final String RESULT_AGREE = "agree";
    public static final String RESULT_REJECT = "reject";
    public static final String ACTION_APPROVE = "approve";
    public static final String ACTION_REJECT = "reject";

    private final RuntimeService runtimeService;

    public ApprovalVariableService(RuntimeService runtimeService) {
        this.runtimeService = runtimeService;
    }

    public Map<String, Object> build(String flowableProcessInstanceId,
                                     String nodeKey,
                                     String result,
                                     String comment,
                                     boolean automatic,
                                     String automaticReason,
                                     LocalDateTime operatedAt) {
        String normalizedResult = normalizeResult(result);
        boolean approved = RESULT_AGREE.equals(normalizedResult);
        LocalDateTime time = operatedAt == null ? LocalDateTime.now() : operatedAt;

        Map<String, Object> approvalData = new LinkedHashMap<>();
        approvalData.put("approvalResult", normalizedResult);
        approvalData.put("approvalComment", normalize(comment));
        approvalData.put("approved", approved);
        approvalData.put("rejected", !approved);
        approvalData.put("automatic", automatic);
        approvalData.put("automaticReason", normalize(automaticReason));
        approvalData.put("operatedAt", time.toString());

        Map<String, Object> existing = existingVariables(flowableProcessInstanceId);
        Map<String, Object> allFormData = mutableMap(existing.get("allFormData"));
        allFormData.put(nodeKey, approvalData);
        Map<String, Object> approvalResults = mutableMap(existing.get("approvalResults"));
        approvalResults.put(nodeKey, approvalData);

        Map<String, Object> variables = new HashMap<>();
        variables.put("allFormData", allFormData);
        variables.put("approvalResults", approvalResults);
        variables.put("approvalResult", normalizedResult);
        variables.put("approvalComment", normalize(comment));
        variables.put("approved", approved);
        variables.put("rejected", !approved);
        variables.put("lastApprovalNode", nodeKey);
        variables.put("lastApprovalAutomatic", automatic);
        if (nodeKey.matches("[A-Za-z_][A-Za-z0-9_]*")) {
            variables.put(nodeKey + "_result", normalizedResult);
            variables.put(nodeKey + "_approved", approved);
        }
        return variables;
    }

    private Map<String, Object> existingVariables(String processInstanceId) {
        try {
            return runtimeService.getVariables(processInstanceId);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mutableMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            return new LinkedHashMap<>((Map<String, Object>) map);
        }
        return new LinkedHashMap<>();
    }

    private String normalizeResult(String result) {
        if (result == null || result.trim().isEmpty()) {
            throw new IllegalArgumentException("approval result must not be blank");
        }
        return switch (result.trim().toLowerCase()) {
            case "agree", "approve", "approved", "pass" -> RESULT_AGREE;
            case "reject", "rejected", "deny", "denied" -> RESULT_REJECT;
            default -> throw new IllegalArgumentException("unsupported approval result: " + result);
        };
    }

    private String normalize(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
