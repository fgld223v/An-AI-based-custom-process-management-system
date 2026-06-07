package com.aiflow.service.impl;

import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.FlowableProcessService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.runtime.ProcessInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Flowable流程引擎服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FlowableProcessServiceImpl implements FlowableProcessService {

    private final RepositoryService repositoryService;
    private final RuntimeService runtimeService;
    private final ProcessTemplateRepository processTemplateRepository;

    @Override
    public Map<String, String> deployProcess(Long templateId) {
        ProcessTemplate template = processTemplateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("流程模板不存在: " + templateId));

        String bpmnXml = template.getBpmnXml();
        if (bpmnXml == null || bpmnXml.trim().isEmpty()) {
            throw new IllegalStateException("流程模板BPMN XML为空，无法部署");
        }

        String templateCode = template.getTemplateCode();
        String resourceName = templateCode + ".bpmn20.xml";

        // 部署流程定义
        Deployment deployment = repositoryService.createDeployment()
                .name(template.getTemplateName())
                .addString(resourceName, bpmnXml)
                .deploy();

        String deploymentId = deployment.getId();

        // 获取流程定义ID
        ProcessDefinition processDefinition = repositoryService.createProcessDefinitionQuery()
                .deploymentId(deploymentId)
                .singleResult();

        if (processDefinition == null) {
            throw new IllegalStateException("部署后未找到流程定义");
        }

        String processDefinitionId = processDefinition.getId();
        String processDefinitionKey = processDefinition.getKey();

        log.info("流程部署成功: templateId={}, deploymentId={}, processDefinitionId={}, processDefinitionKey={}",
                templateId, deploymentId, processDefinitionId, processDefinitionKey);

        // 更新模板的Flowable关联字段
        template.setFlowableDeploymentId(deploymentId);
        template.setFlowableProcessDefinitionId(processDefinitionId);
        processTemplateRepository.save(template);

        Map<String, String> result = new HashMap<>();
        result.put("deploymentId", deploymentId);
        result.put("processDefinitionId", processDefinitionId);
        result.put("processDefinitionKey", processDefinitionKey);
        return result;
    }

    @Override
    public void undeployProcess(String deploymentId) {
        if (deploymentId == null || deploymentId.trim().isEmpty()) {
            throw new IllegalArgumentException("部署ID不能为空");
        }
        repositoryService.deleteDeployment(deploymentId, true);
        log.info("流程部署删除成功: deploymentId={}", deploymentId);
    }

    @Override
    public String startProcessInstance(String processDefinitionKey, String businessKey, Map<String, Object> variables) {
        if (processDefinitionKey == null || processDefinitionKey.trim().isEmpty()) {
            throw new IllegalArgumentException("流程定义Key不能为空");
        }

        ProcessInstance processInstance;
        if (variables != null && !variables.isEmpty()) {
            processInstance = runtimeService.startProcessInstanceByKey(processDefinitionKey, businessKey, variables);
        } else {
            processInstance = runtimeService.startProcessInstanceByKey(processDefinitionKey, businessKey);
        }

        String processInstanceId = processInstance.getId();
        log.info("流程实例启动成功: processDefinitionKey={}, businessKey={}, processInstanceId={}",
                processDefinitionKey, businessKey, processInstanceId);

        return processInstanceId;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> listDeployedProcessDefinitions() {
        List<ProcessDefinition> definitions = repositoryService.createProcessDefinitionQuery()
                .orderByProcessDefinitionKey()
                .asc()
                .list();

        return definitions.stream()
                .map(pd -> String.format("%s (v%d) - %s", pd.getKey(), pd.getVersion(), pd.getName()))
                .collect(Collectors.toList());
    }
}
