package com.aiflow.config;

import com.aiflow.enums.FormStatus;
import com.aiflow.enums.MarketType;
import com.aiflow.enums.ProcessResourceType;
import com.aiflow.enums.TemplateSourceType;
import com.aiflow.enums.TemplateStatus;
import com.aiflow.model.BizTypeDict;
import com.aiflow.model.Department;
import com.aiflow.model.FormDefinition;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.model.SysUser;
import com.aiflow.model.TemplateMarket;
import com.aiflow.repository.BizTypeDictRepository;
import com.aiflow.repository.DepartmentRepository;
import com.aiflow.repository.FormDefinitionRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.repository.SysUserRepository;
import com.aiflow.repository.TemplateMarketRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.xml.parsers.DocumentBuilderFactory;

@Slf4j
@Order(40)
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "aiflow.initializer", name = "market-templates-enabled",
        havingValue = "true")
public class MarketTemplateInitializer implements CommandLineRunner {

    private static final String INDEX_RESOURCE = "classpath:seed/templates/index.json";

    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final FormDefinitionRepository formDefinitionRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final TemplateMarketRepository templateMarketRepository;
    private final BizTypeDictRepository bizTypeDictRepository;
    private final DepartmentRepository departmentRepository;
    private final SysUserRepository sysUserRepository;

    @Override
    @Transactional
    public void run(String... args) {
        SeedIndex index = readJson(INDEX_RESOURCE, SeedIndex.class);
        SysUser publisher = sysUserRepository.findByUsername(index.getPublisherUsername())
                .orElseThrow(() -> new IllegalStateException(
                        "Market seed publisher does not exist: " + index.getPublisherUsername()));

        for (SeedTemplate seed : index.getTemplates()) {
            BizTypeDict bizType = bizTypeDictRepository.findByTypeCode(seed.getBizTypeCode())
                    .orElseThrow(() -> new IllegalStateException(
                            "Market seed business type does not exist: " + seed.getBizTypeCode()));
            FormDefinition form = ensureForm(seed, bizType.getId(), publisher.getId());
            ProcessTemplate template = ensureTemplate(seed, bizType.getId(), form.getId(), publisher.getId());
            ensureMarketItem(seed, template, publisher.getId());
        }
        log.info("Initialized {} reusable market templates", index.getTemplates().size());
    }

    private FormDefinition ensureForm(SeedTemplate seed, Long bizTypeId, Long publisherId) {
        JsonNode formAsset = readJsonTree(seed.getFormResource());
        String fieldList = writeJson(formAsset.path("fieldList"));
        String formSchema = writeJson(formAsset.path("formSchema"));
        FormDefinition existing = formDefinitionRepository
                .findByFormCodeAndVersion(seed.getFormCode(), 1).orElse(null);
        if (existing != null) {
            if (!"system_seed".equals(existing.getSourceType())) {
                throw new IllegalStateException("Seed form code is occupied by a non-seed form: "
                        + seed.getFormCode());
            }
            boolean changed = !Objects.equals(existing.getFormName(), seed.getFormName())
                    || !Objects.equals(existing.getBizTypeId(), bizTypeId)
                    || !Objects.equals(existing.getFieldList(), fieldList)
                    || !Objects.equals(existing.getFormSchema(), formSchema)
                    || existing.getStatus() != FormStatus.PUBLISHED
                    || !Integer.valueOf(0).equals(existing.getDeleted());
            if (!changed) return existing;

            existing.setFormName(seed.getFormName());
            existing.setBizTypeId(bizTypeId);
            existing.setFieldList(fieldList);
            existing.setFormSchema(formSchema);
            existing.setStatus(FormStatus.PUBLISHED);
            existing.setDeleted(0);
            existing.setPublishedAt(LocalDateTime.now());
            existing.setUpdatedAt(LocalDateTime.now());
            return formDefinitionRepository.save(existing);
        }

        LocalDateTime now = LocalDateTime.now();
        return formDefinitionRepository.save(FormDefinition.builder()
                .formCode(seed.getFormCode())
                .formName(seed.getFormName())
                .bizTypeId(bizTypeId)
                .version(1)
                .status(FormStatus.PUBLISHED)
                .fieldList(fieldList)
                .formSchema(formSchema)
                .createdBy(publisherId)
                .sourceType("system_seed")
                .publishedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build());
    }

    private ProcessTemplate ensureTemplate(SeedTemplate seed, Long bizTypeId, Long formId, Long publisherId) {
        String bpmnXml = readText(seed.getBpmnResource());
        SeedProcessConfig processConfig = buildProcessConfig(
                resolveDepartmentPlaceholders(readText(seed.getNodeConfigResource())), bpmnXml, formId);
        ProcessTemplate existing = processTemplateRepository
                .findByTemplateCodeAndVersion(seed.getTemplateCode(), 1).orElse(null);
        if (existing != null) {
            if (existing.getResourceType() != ProcessResourceType.SYSTEM_TEMPLATE) {
                throw new IllegalStateException("Seed template code is occupied by a business process: "
                        + seed.getTemplateCode());
            }
            boolean changed = !Objects.equals(existing.getTemplateName(), seed.getTemplateName())
                    || !Objects.equals(existing.getBizTypeId(), bizTypeId)
                    || !Objects.equals(existing.getFormId(), formId)
                    || !Objects.equals(existing.getBpmnXml(), bpmnXml)
                    || !Objects.equals(existing.getNodeConfig(), processConfig.nodeConfig())
                    || !Objects.equals(existing.getFormBindConfig(), processConfig.formBindConfig())
                    || existing.getStatus() != TemplateStatus.PUBLISHED
                    || !Integer.valueOf(0).equals(existing.getDeleted());
            if (!changed) return existing;

            existing.setTemplateName(seed.getTemplateName());
            existing.setBizTypeId(bizTypeId);
            existing.setFormId(formId);
            existing.setStatus(TemplateStatus.PUBLISHED);
            existing.setSourceType(TemplateSourceType.MANUAL);
            existing.setBpmnXml(bpmnXml);
            existing.setNodeConfig(processConfig.nodeConfig());
            existing.setFormBindConfig(processConfig.formBindConfig());
            existing.setFlowableDeploymentId(null);
            existing.setFlowableProcessDefinitionId(null);
            existing.setDeleted(0);
            existing.setPublishedAt(LocalDateTime.now());
            existing.setUpdatedAt(LocalDateTime.now());
            return processTemplateRepository.save(existing);
        }

        LocalDateTime now = LocalDateTime.now();
        return processTemplateRepository.save(ProcessTemplate.builder()
                .templateCode(seed.getTemplateCode())
                .templateName(seed.getTemplateName())
                .bizTypeId(bizTypeId)
                .formId(formId)
                .version(1)
                .status(TemplateStatus.PUBLISHED)
                .sourceType(TemplateSourceType.MANUAL)
                .resourceType(ProcessResourceType.SYSTEM_TEMPLATE)
                .bpmnXml(bpmnXml)
                .nodeConfig(processConfig.nodeConfig())
                .formBindConfig(processConfig.formBindConfig())
                .createdBy(publisherId)
                .publishedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build());
    }

    private SeedProcessConfig buildProcessConfig(String rawNodeConfig, String bpmnXml, Long formId) {
        JsonNode parsed = readJsonTree(rawNodeConfig, "resolved node config");
        if (!(parsed instanceof ObjectNode nodeConfig)) {
            throw new IllegalStateException("Market seed node config must be a JSON object");
        }

        Set<String> configuredUserTasks = new HashSet<>();
        ObjectNode formBindings = objectMapper.createObjectNode();
        Iterator<Map.Entry<String, JsonNode>> fields = nodeConfig.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (!(entry.getValue() instanceof ObjectNode config)) {
                throw new IllegalStateException("Invalid node config for node: " + entry.getKey());
            }
            String nodeId = config.path("nodeId").asText(entry.getKey());
            if (!entry.getKey().equals(nodeId)) {
                throw new IllegalStateException("Node config key does not match nodeId: " + entry.getKey());
            }
            String bpmnType = config.path("bpmnType").asText();
            String businessType = config.path("businessType").asText();
            if ("bpmn:UserTask".equals(bpmnType)) {
                configuredUserTasks.add(nodeId);
                String assignStrategy = config.path("assignStrategy").asText();
                if (!"approval".equals(businessType) || assignStrategy.isBlank()) {
                    throw new IllegalStateException("Seed user task lacks approval assignment: " + nodeId);
                }
                config.put("assigneeType", toDesignerAssigneeType(assignStrategy));
                if (config.hasNonNull("assignValue")) {
                    config.put("assigneeValue", config.path("assignValue").asText());
                }
                if (config.hasNonNull("rejectStrategy")) {
                    config.put("rejectRule", config.path("rejectStrategy").asText());
                }
            }
            if ("start".equals(businessType) || "form_fill".equals(businessType)
                    || "approval".equals(businessType)) {
                config.put("formBindingMode", "node_form");
                config.put("formId", formId);
                config.put("useTemplateFallback", true);
                formBindings.putObject(nodeId).put("formId", formId);
            }
        }

        Set<String> bpmnUserTasks = readBpmnUserTaskIds(bpmnXml);
        if (!configuredUserTasks.equals(bpmnUserTasks)) {
            Set<String> missing = new HashSet<>(bpmnUserTasks);
            missing.removeAll(configuredUserTasks);
            Set<String> obsolete = new HashSet<>(configuredUserTasks);
            obsolete.removeAll(bpmnUserTasks);
            throw new IllegalStateException("Seed BPMN and node config do not match. missing="
                    + missing + ", obsolete=" + obsolete);
        }
        return new SeedProcessConfig(writeJson(nodeConfig), writeJson(formBindings));
    }

    private String toDesignerAssigneeType(String assignStrategy) {
        return switch (assignStrategy) {
            case "DIRECT_SUPERVISOR" -> "MANAGER";
            case "DEPARTMENT_MANAGER" -> "DEPT_LEADER";
            case "SPECIFIC_USERS" -> "USER";
            case "ROLE", "ROLE_IN_APPLICANT_DEPT" -> "ROLE";
            case "SPECIFIED_DEPARTMENT_MANAGER" -> "SPECIFIED_DEPT_LEADER";
            case "ROLE_IN_SPECIFIED_DEPT" -> "ROLE_IN_SPECIFIED_DEPT";
            case "GLOBAL_ROLE" -> "GLOBAL_ROLE";
            default -> throw new IllegalStateException(
                    "Unsupported seed approval assignment strategy: " + assignStrategy);
        };
    }

    private Set<String> readBpmnUserTaskIds(String bpmnXml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var document = factory.newDocumentBuilder().parse(
                    new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
            var tasks = document.getElementsByTagNameNS(
                    "http://www.omg.org/spec/BPMN/20100524/MODEL", "userTask");
            Set<String> ids = new HashSet<>();
            for (int i = 0; i < tasks.getLength(); i++) {
                String id = tasks.item(i).getAttributes().getNamedItem("id").getNodeValue();
                if (!ids.add(id)) {
                    throw new IllegalStateException("Duplicate BPMN userTask id: " + id);
                }
            }
            if (ids.isEmpty()) {
                throw new IllegalStateException("Seed BPMN must contain at least one userTask");
            }
            return ids;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid seed BPMN XML", ex);
        }
    }

    private void ensureMarketItem(SeedTemplate seed, ProcessTemplate template, Long publisherId) {
        if (templateMarketRepository
                .findByTypeAndSourceIdAndDeleted(MarketType.TEMPLATE, template.getId(), 0)
                .isPresent()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        templateMarketRepository.save(TemplateMarket.builder()
                .sourceId(template.getId())
                .type(MarketType.TEMPLATE)
                .title(seed.getTitle())
                .description(seed.getDescription())
                .bizTypeId(template.getBizTypeId())
                .publisherId(publisherId)
                .useCount(0L)
                .rating(new BigDecimal(seed.getRating()))
                .tags(writeJson(seed.getTags()))
                .publishedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build());
    }

    private String resolveDepartmentPlaceholders(String content) {
        String resolved = content;
        for (Department department : departmentRepository.findByDeletedOrderBySortOrder(0)) {
            resolved = resolved.replace("${dept." + department.getDeptCode() + "}",
                    String.valueOf(department.getId()));
        }
        if (resolved.contains("${dept.")) {
            throw new IllegalStateException("Market template contains an unresolved department placeholder");
        }
        try {
            objectMapper.readTree(resolved);
            return resolved;
        } catch (IOException ex) {
            throw new IllegalStateException("Resolved market node config is not valid JSON", ex);
        }
    }

    private String readText(String location) {
        Resource resource = resourceLoader.getResource(location);
        try (var input = resource.getInputStream()) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot read seed resource: " + location, ex);
        }
    }

    private JsonNode readJsonTree(String location) {
        try {
            return objectMapper.readTree(readText(location));
        } catch (IOException ex) {
            throw new IllegalStateException("Invalid seed JSON: " + location, ex);
        }
    }

    private JsonNode readJsonTree(String content, String description) {
        try {
            return objectMapper.readTree(content);
        } catch (IOException ex) {
            throw new IllegalStateException("Invalid JSON in " + description, ex);
        }
    }

    private <T> T readJson(String location, Class<T> type) {
        try {
            return objectMapper.readValue(readText(location), type);
        } catch (IOException ex) {
            throw new IllegalStateException("Invalid seed JSON: " + location, ex);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot serialize seed data", ex);
        }
    }

    private record SeedProcessConfig(String nodeConfig, String formBindConfig) {}

    @Data
    public static class SeedIndex {
        private String publisherUsername;
        private List<SeedTemplate> templates = List.of();
    }

    @Data
    public static class SeedTemplate {
        private String templateCode;
        private String templateName;
        private String bizTypeCode;
        private String formCode;
        private String formName;
        private String title;
        private String description;
        private String rating = "0.00";
        private List<String> tags = List.of();
        private String formResource;
        private String bpmnResource;
        private String nodeConfigResource;
    }
}
