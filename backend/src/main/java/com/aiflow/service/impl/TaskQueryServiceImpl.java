package com.aiflow.service.impl;

import com.aiflow.dto.TaskDTO;
import com.aiflow.model.ProcessInstance;
import com.aiflow.model.ProcessTemplate;
import com.aiflow.repository.ProcessInstanceRepository;
import com.aiflow.repository.ProcessTemplateRepository;
import com.aiflow.service.TaskQueryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 任务查询服务实现 — 双数据源。
 *
 * <ul>
 *   <li>待办：TaskService → ACT_RU_TASK</li>
 *   <li>已办：HistoryService → ACT_HI_TASKINST</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskQueryServiceImpl implements TaskQueryService {

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_COMPLETED = "completed";

    private final TaskService taskService;
    private final HistoryService historyService;
    private final RuntimeService runtimeService;
    private final ProcessInstanceRepository processInstanceRepository;
    private final ProcessTemplateRepository processTemplateRepository;
    private final ObjectMapper objectMapper;

    // ========================================================================
    // 待办（ACT_RU_TASK）
    // ========================================================================

    @Override
    public List<TaskDTO> listMyTasks() {
        List<Task> tasks = taskService.createTaskQuery()
                .orderByTaskCreateTime().desc()
                .list();

        List<TaskDTO> result = new ArrayList<>();
        for (Task task : tasks) {
            ProcessInstance instance = processInstanceRepository
                    .findByFlowableProcessInstanceIdAndDeleted(task.getProcessInstanceId(), 0)
                    .orElse(null);
            if (instance == null) {
                continue;
            }
            result.add(toTaskDTO(task, instance));
        }
        return result;
    }

    // ========================================================================
    // 已办（ACT_HI_TASKINST）
    // ========================================================================

    @Override
    public List<TaskDTO> listDoneTasks() {
        List<HistoricTaskInstance> historicTasks = historyService
                .createHistoricTaskInstanceQuery()
                .finished()
                .orderByHistoricTaskInstanceEndTime().desc()
                .list();

        List<TaskDTO> result = new ArrayList<>();
        for (HistoricTaskInstance ht : historicTasks) {
            ProcessInstance instance = processInstanceRepository
                    .findByFlowableProcessInstanceIdAndDeleted(ht.getProcessInstanceId(), 0)
                    .orElse(null);
            if (instance == null) {
                continue;
            }
            result.add(toTaskDTO(ht, instance));
        }
        return result;
    }

    // ========================================================================
    // 单任务查询（ACT_RU_TASK → 不存在则 ACT_HI_TASKINST）
    // ========================================================================

    @Override
    public TaskDTO getTask(String taskId) {
        // 先查运行中任务
        Task task = taskService.createTaskQuery()
                .taskId(taskId)
                .singleResult();
        if (task != null) {
            ProcessInstance instance = processInstanceRepository
                    .findByFlowableProcessInstanceIdAndDeleted(task.getProcessInstanceId(), 0)
                    .orElseThrow(() -> new IllegalArgumentException("关联的业务流程实例不存在。"));
            return toTaskDTO(task, instance);
        }

        // 再查历史任务
        HistoricTaskInstance historicTask = historyService
                .createHistoricTaskInstanceQuery()
                .taskId(taskId)
                .singleResult();
        if (historicTask == null) {
            throw new IllegalArgumentException("任务不存在。");
        }

        ProcessInstance instance = processInstanceRepository
                .findByFlowableProcessInstanceIdAndDeleted(historicTask.getProcessInstanceId(), 0)
                .orElseThrow(() -> new IllegalArgumentException("关联的业务流程实例不存在。"));
        return toTaskDTO(historicTask, instance);
    }

    // ========================================================================
    // DTO 映射
    // ========================================================================

    /**
     * 从 ACT_RU_TASK 映射 TaskDTO，含多实例信息。
     */
    private TaskDTO toTaskDTO(Task task, ProcessInstance instance) {
        Long formId = resolveFormId(instance.getTemplateId(), task.getTaskDefinitionKey());
        TaskDTO dto = TaskDTO.builder()
                .taskId(task.getId())
                .taskName(task.getName())
                .taskDefinitionKey(task.getTaskDefinitionKey())
                .flowableProcessInstanceId(task.getProcessInstanceId())
                .businessInstanceId(instance.getId())
                .instanceCode(instance.getInstanceCode())
                .instanceTitle(instance.getTitle())
                .assignee(task.getAssignee())
                .createTime(task.getCreateTime() != null
                        ? task.getCreateTime().toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime()
                        : null)
                .dueDate(task.getDueDate() != null
                        ? task.getDueDate().toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime()
                        : null)
                .endTime(null)
                .status(STATUS_ACTIVE)
                .formId(formId)
                .build();

        // 填充多实例（会签/或签）相关信息
        enrichMultiInstanceInfo(dto, task, instance.getTemplateId());
        return dto;
    }

    /**
     * 从 ACT_HI_TASKINST 映射 TaskDTO。
     */
    private TaskDTO toTaskDTO(HistoricTaskInstance historicTask, ProcessInstance instance) {
        Long formId = resolveFormId(instance.getTemplateId(), historicTask.getTaskDefinitionKey());
        return TaskDTO.builder()
                .taskId(historicTask.getId())
                .taskName(historicTask.getName())
                .taskDefinitionKey(historicTask.getTaskDefinitionKey())
                .flowableProcessInstanceId(historicTask.getProcessInstanceId())
                .businessInstanceId(instance.getId())
                .instanceCode(instance.getInstanceCode())
                .instanceTitle(instance.getTitle())
                .assignee(historicTask.getAssignee())
                .createTime(historicTask.getCreateTime() != null
                        ? historicTask.getCreateTime().toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime()
                        : null)
                .dueDate(historicTask.getDueDate() != null
                        ? historicTask.getDueDate().toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime()
                        : null)
                .endTime(historicTask.getEndTime() != null
                        ? historicTask.getEndTime().toInstant()
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDateTime()
                        : null)
                .status(STATUS_COMPLETED)
                .formId(formId)
                .build();
    }

    // ================================================================
    // 多实例信息增强
    // ================================================================

    /**
     * 为 TaskDTO 填充多实例相关信息（approvalMode、进度、所有审批人）。
     */
    private void enrichMultiInstanceInfo(TaskDTO dto, Task task, Long templateId) {
        // 1. 从模板 nodeConfig 获取 approvalMode
        String approvalMode = resolveApprovalMode(templateId, task.getTaskDefinitionKey());
        dto.setApprovalMode(approvalMode);

        // 2. 如果是多实例任务，尝试获取进度信息
        if ("ALL".equals(approvalMode) || "ANY".equals(approvalMode)) {
            try {
                // 多实例的 nrOf* 变量存在于父执行中
                String executionId = task.getExecutionId();
                if (executionId != null) {
                    // 从当前执行获取多实例变量（Flowable 会在子执行上暴露这些变量）
                    Object nrOfInstances = runtimeService.getVariable(executionId, "nrOfInstances");
                    Object nrOfCompleted = runtimeService.getVariable(executionId, "nrOfCompletedInstances");
                    Object nrOfActive = runtimeService.getVariable(executionId, "nrOfActiveInstances");

                    if (nrOfInstances instanceof Number) {
                        dto.setNrOfInstances(((Number) nrOfInstances).intValue());
                    }
                    if (nrOfCompleted instanceof Number) {
                        dto.setNrOfCompletedInstances(((Number) nrOfCompleted).intValue());
                    }
                    if (nrOfActive instanceof Number) {
                        dto.setNrOfActiveInstances(((Number) nrOfActive).intValue());
                    }
                }

                // 3. 获取所有审批人列表
                String collectionVar = "assigneeList_" + task.getTaskDefinitionKey();
                Object collectionObj = runtimeService.getVariable(
                        task.getProcessInstanceId(), collectionVar);
                if (collectionObj instanceof List<?> list) {
                    dto.setAllAssignees(list.stream()
                            .map(String::valueOf)
                            .collect(Collectors.joining(",")));
                }
            } catch (Exception ignored) {
                // 多实例变量获取失败不阻塞任务列表
            }
        }
    }

    /**
     * 从模板 nodeConfig 解析指定节点的 approvalMode。
     */
    private String resolveApprovalMode(Long templateId, String nodeKey) {
        ProcessTemplate template = processTemplateRepository
                .findByIdAndDeleted(templateId, 0)
                .orElse(null);
        if (template == null || !hasText(template.getNodeConfig())) {
            return "SINGLE";
        }
        try {
            Map<String, Map<String, Object>> map = objectMapper.readValue(
                    template.getNodeConfig(),
                    new TypeReference<Map<String, Map<String, Object>>>() {});
            Map<String, Object> config = map.get(nodeKey);
            if (config != null) {
                Object mode = config.get("approvalMode");
                return mode != null ? mode.toString() : "SINGLE";
            }
            // 遍历查找匹配的 nodeKey / nodeId
            for (Map<String, Object> cfg : map.values()) {
                Object nk = cfg.get("nodeKey");
                Object nid = cfg.get("nodeId");
                if (nodeKey.equals(stringValue(nk)) || nodeKey.equals(stringValue(nid))) {
                    Object mode = cfg.get("approvalMode");
                    return mode != null ? mode.toString() : "SINGLE";
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return "SINGLE";
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString().trim();
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private Long resolveFormId(Long templateId, String taskDefinitionKey) {
        ProcessTemplate template = processTemplateRepository
                .findByIdAndDeleted(templateId, 0)
                .orElse(null);
        if (template == null || !hasText(template.getFormBindConfig())) {
            return null;
        }
        try {
            Map<String, Map<String, Object>> bindConfig = objectMapper.readValue(
                    template.getFormBindConfig(),
                    new TypeReference<Map<String, Map<String, Object>>>() {}
            );
            Map<String, Object> nodeBinding = bindConfig.get(taskDefinitionKey);
            if (nodeBinding != null && nodeBinding.get("formId") != null) {
                Object formIdObj = nodeBinding.get("formId");
                if (formIdObj instanceof Number) {
                    return ((Number) formIdObj).longValue();
                }
            }
        } catch (Exception ignored) {
            // formBindConfig 解析失败不阻塞任务列表
        }
        return null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
