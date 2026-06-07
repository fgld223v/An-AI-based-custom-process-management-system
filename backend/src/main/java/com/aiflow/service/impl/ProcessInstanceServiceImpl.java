package com.aiflow.service.impl;

import com.aiflow.enums.TemplateStatus;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.FlowableProcessService;
import com.aiflow.service.ProcessInstanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProcessInstanceServiceImpl implements ProcessInstanceService {

    private static final DateTimeFormatter INSTANCE_CODE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final FlowableProcessService flowableProcessService;

    @Override
    public ProcessInstance startProcess(Long templateId, Long applicantId, String title, String formData, Map<String, Object> variables) {
        // 验证模板
        ProcessTemplate template = processTemplateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalArgumentException("流程模板不存在: " + templateId));

        if (template.getStatus() != TemplateStatus.PUBLISHED) {
            throw new IllegalStateException("只能发起已发布的流程模板");
        }

        if (template.getFlowableProcessDefinitionId() == null) {
            throw new IllegalStateException("流程模板未部署到Flowable，请先发布模板");
        }

        // 生成实例编码（PI前缀 + 时间戳）
        String instanceCode = "PI" + LocalDateTime.now().format(INSTANCE_CODE_FORMATTER);

        // 提取流程定义Key（从Flowable流程定义ID中提取，格式为 key:version:id）
        String processDefinitionKey = extractProcessDefinitionKey(template.getFlowableProcessDefinitionId());

        // 如果没有传入变量，创建空变量
        if (variables == null) {
            variables = new HashMap<>();
        }

        // 设置申请人变量（用于后续任务分配）
        variables.put("applicantId", applicantId);

        // 启动Flowable流程实例
        String flowableProcessInstanceId = flowableProcessService.startProcessInstance(
                processDefinitionKey, instanceCode, variables);

        // 创建业务流程实例记录
        LocalDateTime now = LocalDateTime.now();
        ProcessInstance instance = ProcessInstance.builder()
                .instanceCode(instanceCode)
                .templateId(templateId)
                .formId(template.getFormId())
                .applicantId(applicantId)
                .bizTypeId(template.getBizTypeId())
                .title(title != null ? title : template.getTemplateName())
                .status("running")
                .formData(formData)
                .flowableProcessInstanceId(flowableProcessInstanceId)
                .startedAt(now)
                .createdAt(now)
                .updatedAt(now)
                .deleted(0)
                .build();

        ProcessInstance saved = processInstanceRepository.save(instance);

        log.info("流程实例启动成功: instanceId={}, instanceCode={}, templateId={}, flowableProcessInstanceId={}",
                saved.getId(), instanceCode, templateId, flowableProcessInstanceId);

        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProcessInstance> findById(Long id) {
        return processInstanceRepository.findById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public ProcessInstance findByInstanceCode(String instanceCode) {
        return processInstanceRepository.findByInstanceCodeAndDeleted(instanceCode, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcessInstance> listMyProcesses(Long applicantId) {
        return processInstanceRepository.findByApplicantIdAndDeletedOrderByCreatedAtDesc(applicantId, 0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcessInstance> listAllProcesses() {
        return processInstanceRepository.findByDeletedOrderByCreatedAtDesc(0);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProcessInstance> listByStatus(String status) {
        return processInstanceRepository.findByStatusAndDeletedOrderByCreatedAtDesc(status, 0);
    }

    /**
     * 从Flowable流程定义ID中提取流程定义Key
     * Flowable流程定义ID格式: key:version:id
     */
    private String extractProcessDefinitionKey(String flowableProcessDefinitionId) {
        if (flowableProcessDefinitionId == null) {
            return null;
        }
        String[] parts = flowableProcessDefinitionId.split(":");
        return parts.length > 0 ? parts[0] : null;
    }
}
