package com.aiflow.config;

import com.aiflow.model.FormDefinition;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.FormDefinitionRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 表单所有权初始化器，为历史遗留的无所有者表单自动关联到唯一引用该表单的模板创建者。
 */
@Slf4j
@Order(100)
@Component
@RequiredArgsConstructor
public class FormOwnershipInitializer implements CommandLineRunner {

    private final FormDefinitionRepository formDefinitionRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) {
        List<FormDefinition> unownedForms = formDefinitionRepository.findByCreatedByIsNullAndDeleted(0);
        if (unownedForms.isEmpty()) return;

        Map<Long, Set<Long>> ownersByFormId = new HashMap<>();
        for (ProcessTemplate template : processTemplateRepository.findByDeletedOrderByUpdatedAtDesc(0)) {
            if (template.getCreatedBy() == null) continue;
            Set<Long> formIds = new HashSet<>();
            if (template.getFormId() != null) formIds.add(template.getFormId());
            collectFormIds(template.getNodeConfig(), formIds);
            collectFormIds(template.getFormBindConfig(), formIds);
            formIds.forEach(formId -> ownersByFormId
                    .computeIfAbsent(formId, ignored -> new HashSet<>())
                    .add(template.getCreatedBy()));
        }

        int migrated = 0;
        for (FormDefinition form : unownedForms) {
            Set<Long> owners = ownersByFormId.getOrDefault(form.getId(), Set.of());
            if (owners.size() == 1) {
                form.setCreatedBy(owners.iterator().next());
                if (form.getSourceType() == null || form.getSourceType().isBlank()) {
                    form.setSourceType("legacy");
                }
                formDefinitionRepository.save(form);
                migrated++;
            } else {
                log.warn("历史表单 {} 无法唯一确定所有者，将仅对超级管理员可见 (候选所有者={})",
                        form.getId(), owners);
            }
        }
        if (migrated > 0) {
            log.info("已为 {} 个历史表单补充所有者", migrated);
        }
    }

    private void collectFormIds(String json, Set<Long> formIds) {
        if (json == null || json.isBlank()) return;
        try {
            collectFormIds(objectMapper.readTree(json), formIds);
        } catch (Exception ex) {
            log.warn("跳过无法解析的历史表单绑定配置: {}", ex.getMessage());
        }
    }

    private void collectFormIds(JsonNode node, Set<Long> formIds) {
        if (node == null) return;
        if (node.isObject()) {
            node.fields().forEachRemaining(entry -> {
                if ("formId".equals(entry.getKey()) && entry.getValue().canConvertToLong()) {
                    formIds.add(entry.getValue().longValue());
                } else {
                    collectFormIds(entry.getValue(), formIds);
                }
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectFormIds(child, formIds));
        }
    }
}
