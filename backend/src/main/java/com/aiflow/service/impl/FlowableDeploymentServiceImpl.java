package com.aiflow.service.impl;

import com.aiflow.model.ProcessTemplate;
import com.aiflow.service.FlowableDeploymentService;
import com.aiflow.service.ProcessAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.flowable.common.engine.api.FlowableException;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Flowable 部署服务实现。
 *
 * <p>核心职责：将流程模板的 BPMN 2.0 XML 部署到 Flowable 流程引擎，
 * 并在部署前后进行 BPMN XML 增强和校验。</p>
 *
 * <p>部署流程：</p>
 * <ol>
 *   <li>权限校验 — 调用 ProcessAuthorizationService 验证部署权限</li>
 *   <li>BPMN XML 校验 — 确保包含 definitions 和 process 标签</li>
 *   <li>补充 isExecutable — 确保 process 标签含 isExecutable="true"</li>
 *   <li>BPMN XML 增强 — 注入多实例（会签/或签）和抄送扩展元素</li>
 *   <li>调用 Flowable RepositoryService 执行部署</li>
 *   <li>回写 flowableDeploymentId 和 flowableProcessDefinitionId</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class FlowableDeploymentServiceImpl implements FlowableDeploymentService {

    /** 匹配 BPMN process 开始标签的正则（支持命名空间前缀） */
    private static final Pattern PROCESS_START_TAG_PATTERN = Pattern.compile("<((?:\\w+:)?process)\\b([^>]*)>", Pattern.CASE_INSENSITIVE);
    /** 匹配 isExecutable 属性的正则 */
    private static final Pattern EXECUTABLE_ATTRIBUTE_PATTERN = Pattern.compile("isExecutable\\s*=\\s*(['\"])(.*?)\\1", Pattern.CASE_INSENSITIVE);

    private final RepositoryService repositoryService;
    private final BpmnXmlEnhancer bpmnXmlEnhancer;
    private final ProcessAuthorizationService processAuthorizationService;

    /**
     * 部署流程模板到 Flowable 引擎。
     *
     * <p>执行步骤：</p>
     * <ol>
     *   <li>权限校验</li>
     *   <li>校验 BPMN XML 包含 definitions 和 process 标签</li>
     *   <li>补充 isExecutable="true" 属性（确保流程可执行）</li>
     *   <li>根据 nodeConfig 注入多实例和抄送扩展元素</li>
     *   <li>调用 Flowable RepositoryService.deploy() 执行部署</li>
     *   <li>查询部署产生的 ProcessDefinition，回写 ID 到模板</li>
     * </ol>
     */
    @Override
    public ProcessTemplate deployProcessTemplate(ProcessTemplate template) {
        if (template == null || template.getId() == null) {
            throw new IllegalArgumentException("流程模板不存在。");
        }
        // 权限校验：验证当前用户是否有部署此模板的权限
        processAuthorizationService.assertCanDeploy(template);

        String bpmnXml = normalizeText(template.getBpmnXml());
        // 校验 BPMN XML 必须包含 definitions 和 process 标签
        validateBpmnXml(bpmnXml);
        // 确保 process 标签含 isExecutable="true"
        bpmnXml = ensureExecutableProcess(bpmnXml);

        // 根据 nodeConfig 注入扩展元素：多实例（会签/或签）和抄送（通知）配置
        String nodeConfigJson = normalizeText(template.getNodeConfig());
        bpmnXml = bpmnXmlEnhancer.enhance(bpmnXml, nodeConfigJson);
        template.setBpmnXml(bpmnXml);

        String deploymentName = buildDeploymentName(template);
        String resourceName = buildResourceName(template);

        try {
            // 执行 Flowable 部署：将 BPMN XML 作为字符串资源部署到流程引擎
            Deployment deployment = repositoryService.createDeployment()
                    .name(deploymentName)
                    .addString(resourceName, bpmnXml)
                    .deploy();

            // 查询部署产生的流程定义
            ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                    .deploymentId(deployment.getId())
                    .singleResult();

            if (processDefinition == null) {
                throw new IllegalStateException("Flowable 部署成功，但未找到流程定义。");
            }

            // 回写 Flowable 关联 ID
            template.setFlowableDeploymentId(deployment.getId());
            template.setFlowableProcessDefinitionId(processDefinition.getId());
            return template;
        } catch (FlowableException ex) {
            throw new IllegalStateException("Flowable 流程定义部署失败：" + safeMessage(ex), ex);
        }
    }

    /** 校验 BPMN XML 合法性 — 必须包含 definitions 和 process 标签 */
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

    /** 确保 BPMN process 标签包含 isExecutable="true"，不满足则自动补充 */
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
