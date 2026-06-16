package com.aiflow.service.impl;

import com.aiflow.model.ProcessTemplate;
import com.aiflow.service.FlowableDeploymentService;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class FlowableDeploymentServiceImpl implements FlowableDeploymentService {

    private static final Pattern PROCESS_START_TAG_PATTERN = Pattern.compile("<((?:\\w+:)?process)\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    private static final Pattern EXECUTABLE_ATTRIBUTE_PATTERN = Pattern.compile("isExecutable\\s*=\\s*(['\"])(.*?)\\1", Pattern.CASE_INSENSITIVE);

    private final RepositoryService repositoryService;
    private final BpmnXmlEnhancer bpmnXmlEnhancer;

    @Override
    public ProcessTemplate deployProcessTemplate(ProcessTemplate template) {
        if (template == null || template.getId() == null) {
            throw new IllegalArgumentException("流程模板不存在。");
        }

        String bpmnXml = normalizeText(template.getBpmnXml());
        validateBpmnXml(bpmnXml);
        bpmnXml = ensureExecutableProcess(bpmnXml);

        // 注入多实例（会签/或签）和抄送扩展元素
        String nodeConfigJson = normalizeText(template.getNodeConfig());
        if (hasText(nodeConfigJson)) {
            bpmnXml = bpmnXmlEnhancer.enhance(bpmnXml, nodeConfigJson);
        }
        template.setBpmnXml(bpmnXml);

        String deploymentName = buildDeploymentName(template);
        String resourceName = buildResourceName(template);

        try {
            Deployment deployment = repositoryService.createDeployment()
                    .name(deploymentName)
                    .addString(resourceName, bpmnXml)
                    .deploy();

            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                    .deploymentId(deployment.getId())
                    .singleResult();

            if (processDefinition == null) {
                throw new IllegalStateException("Flowable 部署成功，但未找到流程定义。");
            }

            template.setFlowableDeploymentId(deployment.getId());
            template.setFlowableProcessDefinitionId(processDefinition.getId());
            return template;
        } catch (FlowableException ex) {
            throw new IllegalStateException("Flowable 流程定义部署失败：" + safeMessage(ex), ex);
        }
    }

    private void validateBpmnXml(String bpmnXml) {
        if (!hasText(bpmnXml)) {
            throw new IllegalStateException("流程模板缺少 BPMN XML，无法发布到流程引擎。");
        }
        String normalized = bpmnXml.toLowerCase();
        boolean hasDefinitions = normalized.contains("bpmn:definitions") || normalized.contains("<definitions");
        boolean hasProcess = normalized.contains("bpmn:process") || normalized.contains("<process");
        if (!hasDefinitions || !hasProcess) {
            throw new IllegalStateException("BPMN XML 格式不正确，无法部署。");
        }
    }

    private String ensureExecutableProcess(String bpmnXml) {
        Matcher matcher = PROCESS_START_TAG_PATTERN.matcher(bpmnXml);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String tagName = matcher.group(1);
            String attributes = matcher.group(2);
            Matcher executableMatcher = EXECUTABLE_ATTRIBUTE_PATTERN.matcher(attributes);
            if (executableMatcher.find()) {
                attributes = executableMatcher.replaceFirst("isExecutable=\"true\"");
            } else {
                attributes = attributes + " isExecutable=\"true\"";
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement("<" + tagName + attributes + ">"));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private String buildDeploymentName(ProcessTemplate template) {
        String code = normalizeText(template.getTemplateCode());
        String name = normalizeText(template.getTemplateName());
        if (hasText(code) && hasText(name)) {
            return code + "-" + name;
        }
        if (hasText(code)) {
            return code;
        }
        return "process-template-" + template.getId();
    }

    private String buildResourceName(ProcessTemplate template) {
        String code = normalizeText(template.getTemplateCode());
        if (hasText(code)) {
            return code + ".bpmn20.xml";
        }
        return "process-template-" + template.getId() + ".bpmn20.xml";
    }

    private String normalizeText(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String safeMessage(Exception ex) {
        return hasText(ex.getMessage()) ? ex.getMessage() : ex.getClass().getSimpleName();
    }
}